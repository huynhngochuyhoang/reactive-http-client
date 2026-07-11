package io.github.huynhngochuyhoang.httpstarter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.web.reactive.function.client.WebClient;

final class Boot4WebClientCustomizers implements BootWebClientCustomizers {
    private static final Logger log = LoggerFactory.getLogger(ReactiveHttpClientAutoConfiguration.class);
    private final ObjectProvider<WebClientCustomizer> customizers;

    Boot4WebClientCustomizers(ObjectProvider<WebClientCustomizer> customizers) {
        this.customizers = customizers;
    }

    @Override
    public void customize(WebClient.Builder builder) {
        customizers.orderedStream().forEach(customizer -> {
            if (log.isDebugEnabled()) {
                log.debug("Applying WebClientCustomizer [{}] to starter WebClient.Builder",
                        customizer.getClass().getName());
            }
            customizer.customize(builder);
        });
    }
}
