package io.github.huynhngochuyhoang.httpstarter.config.smoke;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;

@ReactiveHttpClient(name = "inherited-release-smoke", baseUrl = "http://inherited-release-smoke.test")
public interface InheritedAotSmokeClient extends InheritedAotSmokeOperations {
}
