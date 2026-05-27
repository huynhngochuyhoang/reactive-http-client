package io.github.huynhngochuyhoang.httpstarter.core;

enum RetrySafetyClassification {
    SAFE_METHOD,
    EXPLICIT_IDEMPOTENCY_KEY,
    UNSAFE_RETRY
}
