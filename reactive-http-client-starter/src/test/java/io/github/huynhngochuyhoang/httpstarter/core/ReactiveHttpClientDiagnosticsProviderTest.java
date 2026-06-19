package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void rendersSanitizedDiagnosticsSnapshot() {
        ReactiveHttpClientDiagnosticsProvider provider = sensitiveDiagnosticsProvider();

        String markdown = ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(provider);
        String json = ReactiveHttpClientDiagnosticsSnapshot.toJson(provider);

        assertThat(markdown)
                .startsWith("# Reactive HTTP Client Diagnostics Snapshot")
                .contains("| Project version | `")
                .contains("| Client count | `1` |")
                .contains("| Endpoint count | `2` |")
                .contains("| Inherited endpoint count | `1` |")
                .contains("| `diagnostic-client` | `" + DiagnosticClient.class.getName() + "` | `property` | `client:500` |")
                .contains("configured=true, retry=disabled")
                .contains("| `provider-bean` | `true` | `2` | `1` |");
        assertThat(json)
                .contains("\"projectVersion\":")
                .contains("\"clientCount\": 1")
                .contains("\"endpointCount\": 2")
                .contains("\"inheritedEndpointCount\": 1")
                .contains("\"clientName\": \"diagnostic-client\"")
                .contains("\"clientInterface\": \"" + DiagnosticClient.class.getName() + "\"")
                .contains("\"baseUrlSource\": \"property\"")
                .contains("\"timeoutSource\": \"client\"")
                .contains("\"timeoutMs\": 500")
                .contains("\"authMode\": \"provider-bean\"")
                .contains("\"followRedirects\": true");
        assertThat(markdown + json)
                .doesNotContain("https://user:token@example.com")
                .doesNotContain("user:token")
                .doesNotContain("secretAuthProviderBean")
                .doesNotContain("secret-token")
                .doesNotContain("Authorization");
    }

    @Test
    void rendersDiagnosticsSnapshotInDeterministicOrder() {
        ReactiveHttpClientDiagnosticsProvider.ClientSummary zClient = summary("z-client", "com.example.ZClient");
        ReactiveHttpClientDiagnosticsProvider.ClientSummary aClient = summary("a-client", "com.example.AClient");

        String markdown = ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(List.of(zClient, aClient));
        String json = ReactiveHttpClientDiagnosticsSnapshot.toJson(List.of(zClient, aClient));

        assertThat(markdown.indexOf("`a-client`")).isLessThan(markdown.indexOf("`z-client`"));
        assertThat(json.indexOf("\"clientName\": \"a-client\""))
                .isLessThan(json.indexOf("\"clientName\": \"z-client\""));
    }

    @Test
    void readsProjectVersionFromPackagedPomProperties(@TempDir Path tempDir) throws Exception {
        Path pomProperties = tempDir.resolve(
                "META-INF/maven/io.github.huynhngochuyhoang/reactive-http-client-starter/pom.properties");
        Files.createDirectories(pomProperties.getParent());
        Files.writeString(pomProperties, "version=9.8.7\n");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, previous)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            String json = ReactiveHttpClientDiagnosticsSnapshot.toJson(List.of(summary("client", "Client")));

            assertThat(json).contains("\"projectVersion\": \"9.8.7\"");
        }
        finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static ReactiveHttpClientDiagnosticsProvider sensitiveDiagnosticsProvider() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticClient.class);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("diagnostic-client", sensitiveClientConfig()));

        return new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());
    }

    private static ReactiveHttpClientProperties.ClientConfig sensitiveClientConfig() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://user:token@example.com");
        config.setRequestTimeoutMs(500);
        config.setAuthProvider("secretAuthProviderBean");
        config.setDefaultHeaders(Map.of("Authorization", "Bearer secret-token"));
        config.setFollowRedirects(true);
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        config.setResilience(resilience);
        return config;
    }

    private static ReactiveHttpClientDiagnosticsProvider.ClientSummary summary(String clientName,
                                                                              String clientInterface) {
        return new ReactiveHttpClientDiagnosticsProvider.ClientSummary(
                clientName,
                clientInterface,
                "property",
                new ReactiveHttpClientDiagnosticsProvider.TimeoutSummary("disabled", 0),
                new ReactiveHttpClientDiagnosticsProvider.ResilienceSummary(
                        false, "disabled", "disabled", "disabled", "disabled"),
                "none",
                false,
                1,
                0);
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
