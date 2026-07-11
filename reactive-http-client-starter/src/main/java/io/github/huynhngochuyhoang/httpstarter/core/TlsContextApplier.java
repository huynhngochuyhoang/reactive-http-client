package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;

/**
 * Applies a {@link ReactiveHttpClientProperties.TlsConfig} to a Reactor Netty
 * {@link HttpClient}. Builds an {@code SslContext} from the configured
 * truststore / keystore (resolved via Spring's {@code DefaultResourceLoader}, so
 * {@code classpath:} / {@code file:} / absolute paths all work) and wires it via
 * {@link HttpClient#secure(java.util.function.Consumer)}.
 */
final class TlsContextApplier {

    private static final Logger log = LoggerFactory.getLogger(TlsContextApplier.class);
    private static final DefaultResourceLoader RESOURCE_LOADER = new DefaultResourceLoader();

    private TlsContextApplier() {}

    static HttpClient apply(HttpClient httpClient,
                            ReactiveHttpClientProperties.TlsConfig config,
                            String clientName) {
        try {
            TrustManagerFactory trustManagerFactory = resolveTrustManagerFactory(config, clientName);
            KeyManagerFactory keyManagerFactory = resolveKeyManagerFactory(config);

            if (usesTlsHttp2(httpClient)) {
                Http2SslContextSpec sslContextSpec = Http2SslContextSpec.forClient()
                        .configure(builder -> configureBuilder(builder, config, trustManagerFactory, keyManagerFactory));
                SslContext sslContext = sslContextSpec.sslContext();
                return httpClient.secure(spec -> spec.sslContext(sslContext));
            }

            SslContextBuilder builder = SslContextBuilder.forClient();
            configureBuilder(builder, config, trustManagerFactory, keyManagerFactory);
            SslContext sslContext = builder.build();
            return httpClient.secure(spec -> spec.sslContext(sslContext));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build SSL context for client '" + clientName + "'", e);
        }
    }

    static boolean usesTlsHttp2(HttpClient httpClient) {
        return Arrays.asList(httpClient.configuration().protocols()).contains(HttpProtocol.H2);
    }

    private static TrustManagerFactory resolveTrustManagerFactory(
            ReactiveHttpClientProperties.TlsConfig config, String clientName) throws Exception {
        if (config.isInsecureTrustAll()) {
            log.warn("[{}] reactive HTTP client TLS configured with insecure-trust-all=true — "
                    + "certificate validation is DISABLED. Do not use in production.", clientName);
            return InsecureTrustManagerFactory.INSTANCE;
        }
        if (StringUtils.hasText(config.getTrustStore())) {
            return buildTrustManagerFactory(config);
        }
        return null;
    }

    private static KeyManagerFactory resolveKeyManagerFactory(
            ReactiveHttpClientProperties.TlsConfig config) throws Exception {
        if (StringUtils.hasText(config.getKeyStore())) {
            return buildKeyManagerFactory(config);
        }
        return null;
    }

    private static void configureBuilder(SslContextBuilder builder,
                                         ReactiveHttpClientProperties.TlsConfig config,
                                         TrustManagerFactory trustManagerFactory,
                                         KeyManagerFactory keyManagerFactory) {
        if (trustManagerFactory != null) {
            builder.trustManager(trustManagerFactory);
        }

        if (keyManagerFactory != null) {
            builder.keyManager(keyManagerFactory);
        }

        List<String> protocols = config.getProtocols();
        if (protocols != null && !protocols.isEmpty()) {
            builder.protocols(protocols.toArray(new String[0]));
        }
        List<String> ciphers = config.getCiphers();
        if (ciphers != null && !ciphers.isEmpty()) {
            builder.ciphers(ciphers);
        }
    }

    private static TrustManagerFactory buildTrustManagerFactory(ReactiveHttpClientProperties.TlsConfig config) throws Exception {
        KeyStore trustStore = loadKeyStore(config.getTrustStore(), config.getTrustStorePassword(), config.getTrustStoreType());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf;
    }

    private static KeyManagerFactory buildKeyManagerFactory(ReactiveHttpClientProperties.TlsConfig config) throws Exception {
        KeyStore keyStore = loadKeyStore(config.getKeyStore(), config.getKeyStorePassword(), config.getKeyStoreType());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        char[] password = config.getKeyStorePassword() == null ? new char[0] : config.getKeyStorePassword().toCharArray();
        kmf.init(keyStore, password);
        return kmf;
    }

    private static KeyStore loadKeyStore(String location, String password, String type) throws Exception {
        Resource resource = RESOURCE_LOADER.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Keystore / truststore not found: " + location);
        }
        KeyStore store = KeyStore.getInstance(StringUtils.hasText(type) ? type : "PKCS12");
        try (InputStream in = resource.getInputStream()) {
            store.load(in, password == null ? null : password.toCharArray());
        }
        return store;
    }
}
