package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.SpringHttpExchangeBenchmarkClient;
import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.StarterBenchmarkClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.netty.resources.ConnectionProvider;

import java.util.Map;

final class BenchmarkClients implements AutoCloseable {

    private final ConnectionProvider rawConnectionProvider;
    private final ConnectionProvider httpExchangeConnectionProvider;
    private final GenericApplicationContext starterContext;
    private final ReactiveHttpClientFactoryBean<StarterBenchmarkClient> starterFactory;

    private BenchmarkClients(
            ConnectionProvider rawConnectionProvider,
            ConnectionProvider httpExchangeConnectionProvider,
            GenericApplicationContext starterContext,
            ReactiveHttpClientFactoryBean<StarterBenchmarkClient> starterFactory,
            WebClient rawWebClient,
            SpringHttpExchangeBenchmarkClient httpExchangeClient,
            StarterBenchmarkClient starterClient) {
        this.rawConnectionProvider = rawConnectionProvider;
        this.httpExchangeConnectionProvider = httpExchangeConnectionProvider;
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
                .build();

        ConnectionProvider httpExchangeProvider = ConnectionProvider.create("benchmark-http-exchange");
        WebClient httpExchangeWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(BenchmarkHttpConnector.create(httpExchangeProvider))
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
        context.registerBean(ReactiveHttpClientProperties.class, () -> properties);
        context.registerBean(WebClient.Builder.class, WebClient::builder);
        context.refresh();

        ReactiveHttpClientFactoryBean<StarterBenchmarkClient> factoryBean = new ReactiveHttpClientFactoryBean<>();
        factoryBean.setType(StarterBenchmarkClient.class);
        factoryBean.setApplicationContext(context);
        StarterBenchmarkClient starterClient = factoryBean.getObject();

        return new BenchmarkClients(
                rawProvider,
                httpExchangeProvider,
                context,
                factoryBean,
                rawWebClient,
                httpExchangeClient,
                starterClient);
    }

    @Override
    public void close() {
        starterFactory.destroy();
        starterContext.close();
        rawConnectionProvider.disposeLater().block();
        httpExchangeConnectionProvider.disposeLater().block();
    }
}
