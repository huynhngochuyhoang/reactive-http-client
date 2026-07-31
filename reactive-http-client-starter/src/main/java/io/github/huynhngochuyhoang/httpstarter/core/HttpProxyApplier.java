package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.util.StringUtils;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

/**
 * Applies a {@link ReactiveHttpClientProperties.ProxyConfig} to a Reactor Netty
 * {@link HttpClient}. Extracted as a separate class so it can be unit-tested
 * without booting the full factory bean.
 */
final class HttpProxyApplier {

    private HttpProxyApplier() {}

    static HttpClient apply(HttpClient httpClient, ReactiveHttpClientProperties.ProxyConfig config) {
        ProxyProvider.Proxy proxyType = mapProxyType(config.getType());
        return httpClient.proxy(spec -> {
            ProxyProvider.Builder builder = spec.type(proxyType)
                    .host(config.getHost())
                    .port(config.getPort());
            if (StringUtils.hasText(config.getUsername())) {
                builder = builder.username(config.getUsername());
            }
            if (StringUtils.hasText(config.getPassword())) {
                builder = builder.password(u -> config.getPassword());
            }
            if (StringUtils.hasText(config.getNonProxyHosts())) {
                builder.nonProxyHosts(config.getNonProxyHosts());
            }
        });
    }

    static ProxyProvider.Proxy mapProxyType(ReactiveHttpClientProperties.ProxyConfig.Type type) {
        if (type == null) return ProxyProvider.Proxy.HTTP;
        return switch (type) {
            case HTTP, NONE -> ProxyProvider.Proxy.HTTP;
            case HTTPS -> ProxyProvider.Proxy.HTTP; // Deprecated compatibility alias; the proxy hop is plaintext HTTP.
            case SOCKS4 -> ProxyProvider.Proxy.SOCKS4;
            case SOCKS5 -> ProxyProvider.Proxy.SOCKS5;
        };
    }
}
