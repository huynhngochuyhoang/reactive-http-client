package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.SpringHttpExchangeBenchmarkClient;
import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.StarterBenchmarkClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.DefaultErrorDecoder;
import io.github.huynhngochuyhoang.httpstarter.core.DefaultHttpExchangeLogger;
import io.github.huynhngochuyhoang.httpstarter.core.HttpExchangeLogContext;
import io.github.huynhngochuyhoang.httpstarter.core.ProblemDetailErrorResponseMapper;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientCustomizer;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.MicrometerHttpClientObserver;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

final class BenchmarkClients implements AutoCloseable {

    private static final int STARTER_DEFAULT_CODEC_LIMIT_BYTES = 2 * 1024 * 1024;
    private static final Duration DISPOSE_TIMEOUT = Duration.ofSeconds(5);
    private static final String STARTER_CLIENT_NAME = "benchmark-starter";

    private final ConnectionProvider rawConnectionProvider;
    private final ConnectionProvider httpExchangeConnectionProvider;
    private final List<StarterClientResources> starterResources;

    private BenchmarkClients(
            ConnectionProvider rawConnectionProvider,
            ConnectionProvider httpExchangeConnectionProvider,
            WebClient rawWebClient,
            SpringHttpExchangeBenchmarkClient httpExchangeClient,
            StarterClientResources defaultStarter,
            StarterClientResources problemDetailStarter,
            StarterClientResources exchangeLoggingStarter,
            StarterClientResources micrometerStarter,
            StarterClientResources resilienceEnabledOnlyStarter,
            StarterClientResources retryStarter,
            StarterClientResources rateLimiterStarter,
            StarterClientResources circuitBreakerStarter) {
        this.rawConnectionProvider = rawConnectionProvider;
        this.httpExchangeConnectionProvider = httpExchangeConnectionProvider;
        this.starterResources = List.of(
                defaultStarter,
                problemDetailStarter,
                exchangeLoggingStarter,
                micrometerStarter,
                resilienceEnabledOnlyStarter,
                retryStarter,
                rateLimiterStarter,
                circuitBreakerStarter);
        this.rawWebClient = rawWebClient;
        this.httpExchangeClient = httpExchangeClient;
        this.starterClient = defaultStarter.client;
        this.starterProblemDetailClient = problemDetailStarter.client;
        this.starterExchangeLoggingClient = exchangeLoggingStarter.client;
        this.starterMicrometerClient = micrometerStarter.client;
        this.starterResilienceEnabledOnlyClient = resilienceEnabledOnlyStarter.client;
        this.starterRetryClient = retryStarter.client;
        this.starterRateLimiterClient = rateLimiterStarter.client;
        this.starterCircuitBreakerClient = circuitBreakerStarter.client;
    }

    final WebClient rawWebClient;
    final SpringHttpExchangeBenchmarkClient httpExchangeClient;
    final StarterBenchmarkClient starterClient;
    final StarterBenchmarkClient starterProblemDetailClient;
    final StarterBenchmarkClient starterExchangeLoggingClient;
    final StarterBenchmarkClient starterMicrometerClient;
    final StarterBenchmarkClient starterResilienceEnabledOnlyClient;
    final StarterBenchmarkClient starterRetryClient;
    final StarterBenchmarkClient starterRateLimiterClient;
    final StarterBenchmarkClient starterCircuitBreakerClient;

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

        StarterClientResources defaultStarter = createStarter(baseUrl, "benchmark-starter-default", config -> {}, (context, properties) -> {});
        StarterClientResources problemDetailStarter = createStarter(baseUrl, "benchmark-starter-problem-detail", config -> {}, (context, properties) -> {
            ReactiveHttpClientJsonCodec jsonCodec = new BenchmarkJsonCodecFactory().create();
            context.registerBean(ReactiveHttpClientJsonCodec.class, () -> jsonCodec);
            context.registerBean(DefaultErrorDecoder.class, () -> new DefaultErrorDecoder(
                    STARTER_CLIENT_NAME,
                    List.of(new ProblemDetailErrorResponseMapper(jsonCodec))));
        });
        StarterClientResources exchangeLoggingStarter = createStarter(baseUrl, "benchmark-starter-exchange-logging", config -> {
            config.setLogExchange(true);
            config.setLogPreset(ReactiveHttpClientProperties.LogPreset.METADATA_ONLY);
        }, (context, properties) -> context.registerBean(DefaultHttpExchangeLogger.class, BenchmarkExchangeLogger::new));
        StarterClientResources micrometerStarter = createStarter(baseUrl, "benchmark-starter-micrometer", config -> {}, (context, properties) ->
                context.registerBean(HttpClientObserver.class, () -> new MicrometerHttpClientObserver(
                        new SimpleMeterRegistry(),
                        properties.getObservability())));
        StarterClientResources resilienceEnabledOnlyStarter = createStarter(
                baseUrl,
                "benchmark-starter-resilience-enabled-only",
                config -> config.getResilience().setEnabled(true),
                (context, properties) -> { });
        StarterClientResources retryStarter = createStarter(baseUrl, "benchmark-starter-retry", config -> {
            config.getResilience().setEnabled(true);
            config.getResilience().setRetry("default");
            config.getResilience().setRetryMethods(Set.of("GET"));
        }, (context, properties) -> context.registerBean(RetryRegistry.class, RetryRegistry::ofDefaults));
        StarterClientResources rateLimiterStarter = createStarter(baseUrl, "benchmark-starter-rate-limiter", config -> {
                config.getResilience().setEnabled(true);
                config.getResilience().setRateLimiter("default");
            },
                (context, properties) -> context.registerBean(RateLimiterRegistry.class, RateLimiterRegistry::ofDefaults));
        StarterClientResources circuitBreakerStarter = createStarter(baseUrl, "benchmark-starter-circuit-breaker", config -> {
                config.getResilience().setEnabled(true);
                config.getResilience().setCircuitBreaker("default");
            },
                (context, properties) -> context.registerBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults));

        return new BenchmarkClients(
                rawProvider,
                httpExchangeProvider,
                rawWebClient,
                httpExchangeClient,
                defaultStarter,
                problemDetailStarter,
                exchangeLoggingStarter,
                micrometerStarter,
                resilienceEnabledOnlyStarter,
                retryStarter,
                rateLimiterStarter,
                circuitBreakerStarter);
    }

    @Override
    public void close() {
        for (StarterClientResources resource : starterResources) {
            resource.close();
        }
        rawConnectionProvider.disposeLater().block(DISPOSE_TIMEOUT);
        httpExchangeConnectionProvider.disposeLater().block(DISPOSE_TIMEOUT);
    }

    private static StarterClientResources createStarter(
            String baseUrl,
            String providerName,
            Consumer<ReactiveHttpClientProperties.ClientConfig> configCustomizer,
            StarterContextCustomizer contextCustomizer) {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setBaseUrl(baseUrl);
        configCustomizer.accept(clientConfig);
        properties.setClients(Map.of(STARTER_CLIENT_NAME, clientConfig));

        GenericApplicationContext context = new GenericApplicationContext();
        ConnectionProvider starterProvider = ConnectionProvider.create(providerName);
        context.registerBean(ReactiveHttpClientProperties.class, () -> properties);
        context.registerBean(WebClient.Builder.class, WebClient::builder);
        context.registerBean(ReactiveHttpClientCustomizer.class,
                () -> new BenchmarkTransportCustomizer(starterProvider));
        contextCustomizer.customize(context, properties);
        context.refresh();

        ReactiveHttpClientFactoryBean<StarterBenchmarkClient> factoryBean = new ReactiveHttpClientFactoryBean<>();
        factoryBean.setType(StarterBenchmarkClient.class);
        factoryBean.setApplicationContext(context);
        StarterBenchmarkClient starterClient = factoryBean.getObject();

        return new StarterClientResources(starterProvider, context, factoryBean, starterClient);
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

    @FunctionalInterface
    private interface StarterContextCustomizer {
        void customize(GenericApplicationContext context, ReactiveHttpClientProperties properties);
    }

    private record StarterClientResources(
            ConnectionProvider customizerConnectionProvider,
            GenericApplicationContext context,
            ReactiveHttpClientFactoryBean<StarterBenchmarkClient> factoryBean,
            StarterBenchmarkClient client) implements AutoCloseable {

        @Override
        public void close() {
            ConnectionProvider factoryProvider = factoryConnectionProvider(factoryBean);
            factoryBean.destroy();
            context.close();
            if (factoryProvider != null) {
                factoryProvider.disposeLater().block(DISPOSE_TIMEOUT);
            }
            customizerConnectionProvider.disposeLater().block(DISPOSE_TIMEOUT);
        }
    }

    private static final class BenchmarkTransportCustomizer implements ReactiveHttpClientCustomizer {
        private final ConnectionProvider provider;

        private BenchmarkTransportCustomizer(ConnectionProvider provider) {
            this.provider = provider;
        }

        @Override
        public boolean supports(String clientName) {
            return STARTER_CLIENT_NAME.equals(clientName);
        }

        @Override
        public void customize(WebClient.Builder builder) {
            builder.clientConnector(BenchmarkHttpConnector.create(provider));
        }
    }

    private static final class BenchmarkExchangeLogger extends DefaultHttpExchangeLogger {
        private final LongAdder exchanges = new LongAdder();

        @Override
        public void log(HttpExchangeLogContext context) {
            exchanges.increment();
            if (context.clientName() == null || context.httpMethod() == null) {
                throw new IllegalStateException("Exchange log context missed required metadata");
            }
        }
    }
}
