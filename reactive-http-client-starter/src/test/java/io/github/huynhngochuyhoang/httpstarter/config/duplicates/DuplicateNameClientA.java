package io.github.huynhngochuyhoang.httpstarter.config.duplicates;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;

/**
 * First client in the duplicates package. Intentionally shares the same
 * {@code name} as {@link DuplicateNameClientB} to trigger duplicate-name detection.
 */
@ReactiveHttpClient(name = "duplicate-fixture", baseUrl = "http://localhost")
public interface DuplicateNameClientA {
}
