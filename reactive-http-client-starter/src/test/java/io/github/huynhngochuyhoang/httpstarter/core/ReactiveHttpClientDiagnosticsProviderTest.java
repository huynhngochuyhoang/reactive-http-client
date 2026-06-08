package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveHttpClientDiagnosticsProviderTest {

    @Test
    void reportsSanitizedSummariesForRegisteredClients() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticClient.class);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://user:token@example.com");
        config.setRequestTimeoutMs(500);
        config.setAuthProvider("secretAuthProviderBean");
        config.setDefaultHeaders(Map.of("Authorization", "Bearer secret-token"));
        config.setFollowRedirects(true);
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        config.setResilience(resilience);
        properties.setClients(Map.of("diagnostic-client", config));

        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());

        List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries = provider.clientSummaries();

        assertThat(summaries).hasSize(1);
        ReactiveHttpClientDiagnosticsProvider.ClientSummary summary = summaries.get(0);
        assertThat(summary.clientName()).isEqualTo("diagnostic-client");
        assertThat(summary.clientInterface()).isEqualTo(DiagnosticClient.class.getName());
        assertThat(summary.baseUrlSource()).isEqualTo("property");
        assertThat(summary.timeout()).isEqualTo(new ReactiveHttpClientDiagnosticsProvider.TimeoutSummary("client", 500));
        assertThat(summary.resilience().configured()).isTrue();
        assertThat(summary.resilience().retry()).isEqualTo("disabled");
        assertThat(summary.authMode()).isEqualTo("provider-bean");
        assertThat(summary.followRedirects()).isTrue();
        assertThat(summary.endpointCount()).isEqualTo(2);
        assertThat(summary.inheritedEndpointCount()).isEqualTo(1);
        assertThat(summary.toString())
                .doesNotContain("user:token")
                .doesNotContain("secretAuthProviderBean")
                .doesNotContain("secret-token")
                .doesNotContain("Authorization");
    }

    interface SharedOperations {

        @GET("/shared")
        Mono<String> shared();
    }

    @ReactiveHttpClient(name = "diagnostic-client")
    interface DiagnosticClient extends SharedOperations {

        @GET("/direct")
        Mono<String> direct();
    }
}
