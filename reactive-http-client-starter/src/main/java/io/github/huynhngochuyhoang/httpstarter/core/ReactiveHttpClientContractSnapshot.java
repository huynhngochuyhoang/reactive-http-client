package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Test/support helper that renders sanitized effective HTTP client contracts as
 * deterministic Markdown without creating a Spring application context.
 */
public final class ReactiveHttpClientContractSnapshot {

    private ReactiveHttpClientContractSnapshot() {
    }

    public static Client client(Class<?> clientInterface,
                                String clientName,
                                ReactiveHttpClientProperties.ClientConfig clientConfig) {
        return new Client(clientInterface, clientName, clientConfig);
    }

    public static Client client(Class<?> clientInterface,
                                ReactiveHttpClientProperties.ClientConfig clientConfig) {
        ReactiveHttpClient annotation = Objects.requireNonNull(clientInterface, "clientInterface")
                .getAnnotation(ReactiveHttpClient.class);
        if (annotation == null || !StringUtils.hasText(annotation.name())) {
            throw new IllegalArgumentException("clientName is required when the interface is not annotated with @ReactiveHttpClient.");
        }
        return client(clientInterface, annotation.name(), clientConfig);
    }

    public static Builder markdown() {
        return new Builder();
    }

    public static String markdown(Client... clients) {
        Builder builder = markdown();
        if (clients != null) {
            for (Client client : clients) {
                builder.client(client);
            }
        }
        return builder.render();
    }

    public record Client(Class<?> clientInterface,
                         String clientName,
                         ReactiveHttpClientProperties.ClientConfig clientConfig) {

        public Client {
            Objects.requireNonNull(clientInterface, "clientInterface");
            if (!StringUtils.hasText(clientName)) {
                throw new IllegalArgumentException("clientName must not be blank");
            }
            clientConfig = clientConfig != null ? clientConfig : new ReactiveHttpClientProperties.ClientConfig();
        }
    }

    public static final class Builder {
        private final List<Client> clients = new ArrayList<>();
        private final Set<String> clientNameFilters = new LinkedHashSet<>();
        private final Set<String> methodNameFilters = new LinkedHashSet<>();
        private ResilienceOperatorApplier resilienceOperatorApplier;

        private Builder() {
        }

        public Builder client(Class<?> clientInterface,
                              String clientName,
                              ReactiveHttpClientProperties.ClientConfig clientConfig) {
            clients.add(ReactiveHttpClientContractSnapshot.client(clientInterface, clientName, clientConfig));
            return this;
        }

        public Builder client(Class<?> clientInterface,
                              ReactiveHttpClientProperties.ClientConfig clientConfig) {
            clients.add(ReactiveHttpClientContractSnapshot.client(clientInterface, clientConfig));
            return this;
        }

        public Builder client(Client client) {
            clients.add(Objects.requireNonNull(client, "client"));
            return this;
        }

        public Builder filterClient(String clientName) {
            if (!StringUtils.hasText(clientName)) {
                throw new IllegalArgumentException("clientName filter must not be blank");
            }
            clientNameFilters.add(clientName);
            return this;
        }

        public Builder filterMethod(String methodName) {
            if (!StringUtils.hasText(methodName)) {
                throw new IllegalArgumentException("methodName filter must not be blank");
            }
            methodNameFilters.add(methodName);
            return this;
        }

        public Builder resilienceOperatorApplier(ResilienceOperatorApplier resilienceOperatorApplier) {
            this.resilienceOperatorApplier = resilienceOperatorApplier;
            return this;
        }

        public String render() {
            MethodMetadataCache metadataCache = new MethodMetadataCache();
            List<EffectiveHttpClientContract> contracts = clients.stream()
                    .filter(this::matchesClient)
                    .flatMap(client -> EffectiveHttpClientContractExporter.export(
                            client.clientInterface(),
                            client.clientName(),
                            client.clientConfig(),
                            metadataCache,
                            resilienceOperatorApplier).stream())
                    .filter(this::matchesMethod)
                    .sorted(Comparator.comparing(EffectiveHttpClientContract::clientName)
                            .thenComparing(EffectiveHttpClientContract::declaringInterface)
                            .thenComparing(EffectiveHttpClientContract::javaMethodSignature))
                    .toList();

            StringBuilder markdown = new StringBuilder();
            markdown.append("| Client | Interface | Declared By | Inherited | Method | HTTP | Path | Base URL | Base URL Source | API Name | API Ref | Timeout | Resilience | Redirect | Body |\n");
            markdown.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
            for (EffectiveHttpClientContract contract : contracts) {
                markdown.append("| ")
                        .append(cell(contract.clientName())).append(" | ")
                        .append(cell(contract.concreteClientInterface())).append(" | ")
                        .append(cell(contract.declaringInterface())).append(" | ")
                        .append(contract.inherited()).append(" | ")
                        .append(cell(contract.javaMethodSignature())).append(" | ")
                        .append(cell(contract.httpMethod())).append(" | ")
                        .append(cell(contract.pathTemplate())).append(" | ")
                        .append(cell(contract.baseUrl())).append(" | ")
                        .append(cell(contract.baseUrlSource())).append(" | ")
                        .append(cell(contract.apiName())).append(" | ")
                        .append(cell(contract.apiRef())).append(" | ")
                        .append(cell(timeout(contract.timeout()))).append(" | ")
                        .append(cell(resilience(contract.resilience()))).append(" | ")
                        .append(cell(contract.redirectPolicy())).append(" | ")
                        .append(cell(contract.bodyRepeatability())).append(" |\n");
            }
            return markdown.toString();
        }

        private boolean matchesClient(Client client) {
            return clientNameFilters.isEmpty() || clientNameFilters.contains(client.clientName());
        }

        private boolean matchesMethod(EffectiveHttpClientContract contract) {
            if (methodNameFilters.isEmpty()) {
                return true;
            }
            return methodNameFilters.contains(methodName(contract.javaMethodSignature()))
                    || methodNameFilters.contains(contract.javaMethodSignature());
        }
    }

    private static String methodName(String javaMethodSignature) {
        int parametersStart = javaMethodSignature.indexOf('(');
        return parametersStart >= 0 ? javaMethodSignature.substring(0, parametersStart) : javaMethodSignature;
    }

    private static String timeout(EffectiveHttpClientContract.TimeoutPolicy timeout) {
        return timeout.source() + ":" + timeout.timeoutMs() + "ms";
    }

    private static String resilience(EffectiveHttpClientContract.ResiliencePolicy resilience) {
        return "retry=" + resilience.retry()
                + ", rateLimiter=" + resilience.rateLimiter()
                + ", circuitBreaker=" + resilience.circuitBreaker()
                + ", bulkhead=" + resilience.bulkhead();
    }

    private static String cell(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString()
                .replace("|", "\\|")
                .replace("\r", " ")
                .replace("\n", " ");
    }
}
