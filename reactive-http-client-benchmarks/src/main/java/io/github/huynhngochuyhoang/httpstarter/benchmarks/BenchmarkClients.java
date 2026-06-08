package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.SpringHttpExchangeBenchmarkClient;
import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.StarterBenchmarkClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientCustomizer;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;

final class BenchmarkClients implements AutoCloseable {

    private static final int STARTER_DEFAULT_CODEC_LIMIT_BYTES = 2 * 1024 * 1024;
    private static final Duration DISPOSE_TIMEOUT = Duration.ofSeconds(5);

    private final ConnectionProvider rawConnectionProvider;
    private final ConnectionProvider httpExchangeConnectionProvider;
    private final ConnectionProvider starterConnectionProvider;
    private final GenericApplicationContext starterContext;
    private final ReactiveHttpClientFactoryBean<StarterBenchmarkClient> starterFactory;

    private BenchmarkClients(
            ConnectionProvider rawConnectionProvider,
            ConnectionProvider httpExchangeConnectionProvider,
            ConnectionProvider starterConnectionProvider,
            GenericApplicationContext starterContext,
            ReactiveHttpClientFactoryBean<StarterBenchmarkClient> starterFactory,
            WebClient rawWebClient,
            SpringHttpExchangeBenchmarkClient httpExchangeClient,
            StarterBenchmarkClient starterClient) {
        this.rawConnectionProvider = rawConnectionProvider;
        this.httpExchangeConnectionProvider = httpExchangeConnectionProvider;
        this.starterConnectionProvider = starterConnectionProvider;
        this.starterContext = starterContext;
        this.starterFactory = starterFactory;
        this.rawWebClient = rawWebClient;
        this.httpExchangeClient = httpExchangeClient;
        this.starterClient = starterClient;
    }

    final WebClient rawWebClient;
    final SpringHttpExchangeBenchmarkClient httpExchangeClient;
    final StarterBenchmarkClient starterClient;

    static BenchmarkClients create(String baseUrl) {
        ConnectionProvider rawProvider = ConnectionProvider.create("benchmark-raw-webclient");
        WebClient rawWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(BenchmarkHttpConnector.create(rawProvider))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(STARTER_DEFAULT_CODEC_LIMIT_BYTES))
                .build();

        ConnectionProvider httpExchangeProvider = ConnectionProvider.create("benchmark-http-exchange");
        WebClient httpExchangeWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(BenchmarkHttpConnector.create(httpExchangeProvider))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(STARTER_DEFAULT_CODEC_LIMIT_BYTES))
                .build();
        SpringHttpExchangeBenchmarkClient httpExchangeClient = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(httpExchangeWebClient))
                .build()
                .createClient(SpringHttpExchangeBenchmarkClient.class);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setBaseUrl(baseUrl);
        properties.setClients(Map.of("benchmark-starter", clientConfig));

        GenericApplicationContext context = new GenericApplicationContext();
        ConnectionProvider starterProvider = ConnectionProvider.create("benchmark-starter");
        context.registerBean(ReactiveHttpClientProperties.class, () -> properties);
        context.registerBean(WebClient.Builder.class, WebClient::builder);
        context.registerBean(ReactiveHttpClientCustomizer.class,
                () -> new BenchmarkTransportCustomizer(starterProvider));
        context.refresh();

        ReactiveHttpClientFactoryBean<StarterBenchmarkClient> factoryBean = new ReactiveHttpClientFactoryBean<>();
        factoryBean.setType(StarterBenchmarkClient.class);
        factoryBean.setApplicationContext(context);
        StarterBenchmarkClient starterClient = factoryBean.getObject();

        return new BenchmarkClients(
                rawProvider,
                httpExchangeProvider,
                starterProvider,
                context,
                factoryBean,
                rawWebClient,
                httpExchangeClient,
                starterClient);
    }

    @Override
    public void close() {
        ConnectionProvider factoryProvider = factoryConnectionProvider(starterFactory);
        starterFactory.destroy();
        starterContext.close();
        if (factoryProvider != null) {
            factoryProvider.disposeLater().block(DISPOSE_TIMEOUT);
        }
        starterConnectionProvider.disposeLater().block(DISPOSE_TIMEOUT);
        rawConnectionProvider.disposeLater().block(DISPOSE_TIMEOUT);
        httpExchangeConnectionProvider.disposeLater().block(DISPOSE_TIMEOUT);
    }

    private static ConnectionProvider factoryConnectionProvider(
            ReactiveHttpClientFactoryBean<StarterBenchmarkClient> factoryBean) {
        try {
            Field field = ReactiveHttpClientFactoryBean.class.getDeclaredField("connectionProvider");
            field.setAccessible(true);
            Object provider = field.get(factoryBean);
            return provider instanceof ConnectionProvider connectionProvider ? connectionProvider : null;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to inspect starter benchmark connection provider", ex);
        }
    }

    private static final class BenchmarkTransportCustomizer implements ReactiveHttpClientCustomizer {
        private final ConnectionProvider provider;

        private BenchmarkTransportCustomizer(ConnectionProvider provider) {
            this.provider = provider;
        }

        @Override
        public boolean supports(String clientName) {
            return "benchmark-starter".equals(clientName);
        }

        @Override
        public void customize(WebClient.Builder builder) {
            builder.clientConnector(BenchmarkHttpConnector.create(provider));
        }
    }
}
