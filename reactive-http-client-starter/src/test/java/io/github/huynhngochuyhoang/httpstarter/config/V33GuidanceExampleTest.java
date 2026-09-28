package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientCustomizer;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V33GuidanceExampleTest {
    @TempDir Path temporary;

    enum Scenario { VALID, REDUNDANT_SAFE, MISSING_SAFE, INCOMPLETE_METADATA }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void documentedPublicConfigurationCompilesAndKeepsSafetyChecks(Scenario scenario) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(root.resolve("docs"))) root = root.getParent();
        String guide = Files.readString(root.resolve("docs/examples/v33-extensions.md"));
        var blocks = Pattern.compile("```java\\R(.*?)```", Pattern.DOTALL).matcher(guide);
        assertThat(blocks.find()).isTrue();
        Path source = temporary.resolve("CatalogExtensionConfiguration.java");
        Files.writeString(source, blocks.group(1));
        assertThat(blocks.find()).isFalse();
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        var errors = new StringWriter();
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            assertThat(compiler.getTask(errors, files, null, List.of("--release", "21", "-classpath",
                            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                            "-d", temporary.toString()), null, files.getJavaFileObjects(source)).call())
                    .as(errors.toString()).isTrue();
        }
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, getClass().getClassLoader());
             var context = new AnnotationConfigApplicationContext()) {
            Thread.currentThread().setContextClassLoader(loader);
            context.setClassLoader(loader);
            var configuration = loader.loadClass("example.v33.CatalogExtensionConfiguration");
            var clientType = loader.loadClass("example.v33.CatalogExtensionConfiguration$CatalogClient");
            List<ClientRequest> dispatched = new ArrayList<>();
            context.registerBean("exampleTransport", ReactiveHttpClientCustomizer.class, () -> builder ->
                    builder.exchangeFunction(request -> {
                        dispatched.add(request);
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "text/plain").body("catalog").build());
                    }));
            context.register(configuration, ReactiveHttpClientAutoConfiguration.class);
            context.refresh();
            var properties = context.getBean(ReactiveHttpClientProperties.class);
            assertThat(properties).isSameAs(context.getBean("catalogProperties"));
            var config = properties.getClients().get("catalog-extension");
            assertThat(config.getCache().getCustomizations()).doesNotContainKey("starterWebClientBuilder");
            config.getCache().getCustomizations().put("exampleTransport",
                    ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            var metadata = context.getBean(MethodMetadataCache.class);
            var method = clientType.getMethod("read");
            if (scenario == Scenario.REDUNDANT_SAFE) {
                config.getCache().getCustomizations().put("starterWebClientBuilder",
                        ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            } else if (scenario == Scenario.MISSING_SAFE) {
                config.getCache().getCustomizations().remove("catalogRepresentation");
            } else if (scenario == Scenario.INCOMPLETE_METADATA) {
                metadata.get(method).setPathTemplate(null);
            }
            var aot = new ReactiveHttpClientBeanFactoryInitializationAotProcessor();
            if (scenario == Scenario.MISSING_SAFE || scenario == Scenario.INCOMPLETE_METADATA) {
                String marker = scenario == Scenario.MISSING_SAFE ? "catalogRepresentation" : "pathTemplate";
                assertThatThrownBy(() -> aot.processAheadOfTime(context.getBeanFactory()))
                        .hasStackTraceContaining(marker);
                assertThatThrownBy(() -> context.getBean(clientType)).hasStackTraceContaining(marker);
                assertThat(dispatched).isEmpty();
                return;
            }
            assertThat(aot.processAheadOfTime(context.getBeanFactory())).isNotNull();
            assertThat(dispatched).isEmpty();
            Object client = context.getBean(clientType);
            var cold = (Mono<?>) method.invoke(client);
            assertThat(dispatched).isEmpty();
            assertThat(cold.block(Duration.ofSeconds(5))).isEqualTo("catalog");
            assertThat(cold.block(Duration.ofSeconds(5))).isEqualTo("catalog");
            assertThat(dispatched).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo(HttpMethod.GET);
                assertThat(request.url().toString()).isEqualTo("https://catalog.example.invalid/catalog");
                assertThat(request.headers().getFirst("Accept")).isEqualTo("text/plain");
            });
            assertThat(metadata.get(method).getApiName()).isEqualTo("catalog.read");
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }
}
