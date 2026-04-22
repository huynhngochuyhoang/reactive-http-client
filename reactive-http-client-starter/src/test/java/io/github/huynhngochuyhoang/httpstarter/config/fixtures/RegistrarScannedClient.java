package io.github.huynhngochuyhoang.httpstarter.config.fixtures;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;

@ReactiveHttpClient(name = "registrar-fixture", baseUrl = "http://localhost")
public interface RegistrarScannedClient {
}
