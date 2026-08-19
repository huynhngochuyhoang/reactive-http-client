# Opt-In Resilience Activation Proposal

> **Status:** deferred proposal; not part of V26 execution or release scope
> **Compatibility:** SemVer-major behavior change

## Problem

`ReactiveHttpClientProperties.ResilienceConfig` currently gives retry, rate
limiter, circuit breaker, and bulkhead the instance name `default`. Once
`resilience.enabled=true`, every available operator can therefore become active
even when a client intended to configure only one behavior.

Registry defaults should remain reusable configuration, not implicit client
activation. The proposed contract is fail-safe by default and explicit by
intent: the master switch makes resilience available, but does not select an
operator for a client.

## Target Contract

- Keep `resilience.enabled` as the client-level master gate.
- Default client-level `retry`, `rate-limiter`, `circuit-breaker`, and `bulkhead`
  selections to absent/disabled.
- Activate an operator only from a non-blank client-level instance property or
  its matching method annotation. The explicit value `default` remains valid.
- Treat `retry-methods` as Retry eligibility, not activation.
- Keep method-level selection above client-level selection while retaining the
  master gate.
- Keep strict retry validation dormant when Retry is not selected or cannot make
  another attempt.

A retry-only client would select only Retry:

```yaml
resilience:
  enabled: true
  retry: default
  retry-methods: [GET, HEAD]
```

The omitted rate limiter, circuit breaker, and bulkhead remain inactive even if
their registries contain instances named `default`.

## Required Evidence

- Prove `enabled: true` alone applies no operator for `Mono` and `Flux`.
- Prove retry-only, rate-limiter-only, circuit-breaker-only, and bulkhead-only
  configurations apply exactly the selected operator.
- Centralize effective selection across invocation, startup validation/logging,
  contract export, diagnostics, configuration metadata, mocks, and consumers.
- Preserve diagnostics distinctions between disabled, unavailable, and unknown
  without instantiating lazy registries or providers.
- Publish a migration table for enabled-only, explicit `default`, named instance,
  method annotation, blank value, retry-method, and strict-validation cases.
- Add configuration and documentation drift checks that reject implicit
  all-operators-on behavior.

## Release Constraint

This changes existing `3.x` behavior for configurations that rely on implicit
`default` operators. It must be planned and released as a deliberate major
version with migration evidence; it must not be folded into an observability
fix, minor release, or patch release.
