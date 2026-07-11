package io.github.huynhngochuyhoang.httpstarter.config;

import org.springframework.web.reactive.function.client.WebClient;

interface BootWebClientCustomizers {
    void customize(WebClient.Builder builder);
}
