# Reactive HTTP Client Benchmark Report

> **Immutable historical benchmark evidence.** These numbers apply only to the
> recorded `2.12.0` environment and scenarios. Use [Benchmarks](22-benchmarks.md)
> for current commands and interpretation rules.

## Promotion Metadata

- Report version: `2.12.0`.
- Starter version under test: `2.12.0`.
- Benchmark input commit: `1394920` (release input tree; collected after the post-2.12 baseline-transition reactor version bump).
- Evidence level: **Release-quality**, not smoke evidence.
- Machine limits: local loopback run on Linux/amd64 with `8` available processors; JVM warmup, CPU scheduling, and Netty event-loop scheduling affect the numbers.
- Generated source artifacts are retained during release evidence collection but are not committed: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json` and its adjacent environment properties file.

- Generated at: `2026-07-03T16:24:48.168383762Z`
- Result file: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json`
- Run label: **Release-quality candidate**

## Interpretation

- Client-side overhead comparisons use the same local loopback server, request shape, transport, codecs, and validation guardrails for raw `WebClient`, Spring HTTP Interface, and the starter.
- Smoke-only results prove the harness starts and writes artifacts; do not publish them as performance evidence.
- Optional starter feature rows enable exactly one feature at a time and are not claims about default runtime overhead.
- Starter-only rows measure starter-specific work, such as Problem Detail error mapping, where the baselines do not install equivalent behavior.
- Local loopback, JVM warmup, CPU scheduling, and Netty event-loop behavior affect the numbers; use this report as trend evidence for named scenarios.

## Report Pairing

- Current candidate: this promoted report measures starter `2.12.0`; its generated source report is `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`.
- Published baseline: the paired baseline report path is `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.11.0/release-jmh.md`, which measures the published starter `2.11.0` artifact when regenerated.
- Baseline artifacts must resolve before this report is used for release-to-release comparison.
- Numeric rows in this promoted report are current-candidate `2.12.0` rows unless a row explicitly names another starter version.

## Environment

| Key | Value |
| --- | --- |
| `generatedAt` | 2026-07-03T16:24:48.168383762Z |
| `projectVersion` | 2.12.0 |
| `starterVersion` | 2.12.0 |
| `apiCompatibilityBaselineVersion` | 2.11.0 |
| `benchmarkCommit` | 1394920 |
| `springBootVersion` | 3.5.0 |
| `springWebFluxVersion` | 6.2.7 |
| `reactorNettyVersion` | 1.2.6 |
| `baselineSpringWebFluxVersion` | 6.2.7 |
| `baselineReactorNettyVersion` | 1.2.6 |
| `dependencyManagement` | spring-boot-dependencies:3.5.0 |
| `javaVersion` | 21.0.8 |
| `javaVm` | Java HotSpot(TM) 64-Bit Server VM |
| `osName` | Linux |
| `osArch` | amd64 |
| `availableProcessors` | 8 |
| `jvmInputArguments` | Sanitized; release run used project/starter/baseline versions, resolved Spring WebFlux/Reactor Netty artifacts, benchmark commit, and benchmark logger redirection. |
| `resultFile` | reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json |

## Comparison Summary

| Scenario | Starter avg | vs raw WebClient | vs Spring HTTP Interface |
| --- | ---: | ---: | ---: |
| Client Error Small Body | 53.238 us/op | 25.277% faster | 27.277% faster |
| Get No Body | 46.85 us/op | 14.935% slower | 6.422% slower |
| Get Path Query Header | 53.029 us/op | 9.106% slower | 5.01% faster |
| Post Json | 61.091 us/op | 13.455% faster | 12.121% slower |
| Response Entity | 52.298 us/op | 12.308% slower | 5.746% faster |
| Server Error Small Body | 52.489 us/op | 22.366% faster | 28.414% faster |

## Starter-Only and Optional Feature Rows

| Scenario | Label | Average |
| --- | --- | ---: |
| Circuit Breaker Wrapper Get No Body | Optional starter feature | 51.214 us/op |
| Exchange Logging Metadata Only Get No Body | Optional starter feature | 51.469 us/op |
| Micrometer Observer Get No Body | Optional starter feature | 56.632 us/op |
| Retry Wrapper Get No Body | Optional starter feature | 51.681 us/op |
| Problem Detail Small Body | Starter-only error-mapping overhead | 60.852 us/op |

## Raw Results

| Benchmark | Label | Mode | Score | p50 | p95 | p99 | Allocation rate | Allocation/op |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `thrpt` | 0.009 ops/us | 0.01 ops/us | 0.015 ops/us | 0.015 ops/us | 304.058 MB/sec | 36540.268 B/op |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `avgt` | 71.246 us/op | 70.098 us/op | 80.673 us/op | 80.673 us/op | 492.844 MB/sec | 36715.432 B/op |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `sample` | 70.777 us/op | 65.152 us/op | 99.2 us/op | 129.92 us/op | 490.692 MB/sec | 36594.789 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `thrpt` | 0.013 ops/us | 0.014 ops/us | 0.014 ops/us | 0.014 ops/us | 472.724 MB/sec | 36997.263 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `avgt` | 73.206 us/op | 71.987 us/op | 84.288 us/op | 84.288 us/op | 482.26 MB/sec | 36982.181 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `sample` | 84.122 us/op | 71.168 us/op | 125.312 us/op | 225.28 us/op | 419.043 MB/sec | 37162.562 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `thrpt` | 0.019 ops/us | 0.019 ops/us | 0.021 ops/us | 0.021 ops/us | 515.09 MB/sec | 28660.587 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `avgt` | 53.238 us/op | 53.229 us/op | 59.003 us/op | 59.003 us/op | 518.586 MB/sec | 28888.071 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `sample` | 51.068 us/op | 47.872 us/op | 67.84 us/op | 92.516 us/op | 549.909 MB/sec | 28836.738 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `thrpt` | 0.023 ops/us | 0.022 ops/us | 0.025 ops/us | 0.025 ops/us | 473.345 MB/sec | 21946.75 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `avgt` | 40.762 us/op | 40.104 us/op | 45.076 us/op | 45.076 us/op | 513.63 MB/sec | 21961.798 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `sample` | 43.217 us/op | 39.808 us/op | 58.496 us/op | 81.408 us/op | 498.82 MB/sec | 22104.221 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `thrpt` | 0.023 ops/us | 0.023 ops/us | 0.024 ops/us | 0.024 ops/us | 494.756 MB/sec | 22269.417 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `avgt` | 44.023 us/op | 43.424 us/op | 48.297 us/op | 48.297 us/op | 483.514 MB/sec | 22325.073 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `sample` | 45.544 us/op | 41.856 us/op | 62.656 us/op | 82.304 us/op | 480.436 MB/sec | 22525.341 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `thrpt` | 0.019 ops/us | 0.02 ops/us | 0.023 ops/us | 0.023 ops/us | 396.966 MB/sec | 22446.747 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `avgt` | 46.85 us/op | 46.972 us/op | 50.763 us/op | 50.763 us/op | 457.283 MB/sec | 22486.13 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `sample` | 49.919 us/op | 47.104 us/op | 67.328 us/op | 89.6 us/op | 434.865 MB/sec | 22324.13 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `thrpt` | 0.021 ops/us | 0.021 ops/us | 0.022 ops/us | 0.022 ops/us | 486.986 MB/sec | 24368.724 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `avgt` | 48.603 us/op | 48.323 us/op | 54.935 us/op | 54.935 us/op | 479.049 MB/sec | 24335.612 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `sample` | 48.086 us/op | 44.864 us/op | 64.064 us/op | 89.6 us/op | 497.547 MB/sec | 24496.555 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `thrpt` | 0.018 ops/us | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 567.394 MB/sec | 32560.941 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `avgt` | 55.826 us/op | 53.999 us/op | 64.164 us/op | 64.164 us/op | 563.492 MB/sec | 32888.289 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `sample` | 58.311 us/op | 54.656 us/op | 80.256 us/op | 104.088 us/op | 532.105 MB/sec | 32699.691 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `thrpt` | 0.019 ops/us | 0.019 ops/us | 0.02 ops/us | 0.02 ops/us | 489.661 MB/sec | 27232.398 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `avgt` | 53.029 us/op | 52.904 us/op | 55.411 us/op | 55.411 us/op | 490.136 MB/sec | 27288.051 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `sample` | 53.701 us/op | 50.176 us/op | 73.216 us/op | 97.664 us/op | 488.239 MB/sec | 27354.112 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `thrpt` | 0.018 ops/us | 0.018 ops/us | 0.021 ops/us | 0.021 ops/us | 463.649 MB/sec | 26880.373 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `avgt` | 70.588 us/op | 50.98 us/op | 219.897 us/op | 219.897 us/op | 450.095 MB/sec | 27014.342 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `sample` | 54.703 us/op | 48.384 us/op | 88.704 us/op | 138.24 us/op | 472.94 MB/sec | 27043.498 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `thrpt` | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 0.019 ops/us | 462.189 MB/sec | 26794.378 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `avgt` | 54.487 us/op | 53.456 us/op | 61.474 us/op | 61.474 us/op | 468.876 MB/sec | 26773.151 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `sample` | 58.414 us/op | 52.8 us/op | 92.544 us/op | 133.632 us/op | 438.079 MB/sec | 26962.599 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `thrpt` | 0.017 ops/us | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 449.347 MB/sec | 27393.693 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `avgt` | 61.091 us/op | 57.766 us/op | 77.632 us/op | 77.632 us/op | 434.168 MB/sec | 27519.639 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `sample` | 62.292 us/op | 55.808 us/op | 99.718 us/op | 152.527 us/op | 416.595 MB/sec | 27357.56 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `thrpt` | 0.021 ops/us | 0.021 ops/us | 0.022 ops/us | 0.022 ops/us | 491.382 MB/sec | 24663.573 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `avgt` | 46.566 us/op | 45.93 us/op | 50.663 us/op | 50.663 us/op | 506.018 MB/sec | 24726.735 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `sample` | 49.306 us/op | 45.056 us/op | 69.504 us/op | 95.488 us/op | 487.834 MB/sec | 24715.519 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `thrpt` | 0.019 ops/us | 0.019 ops/us | 0.019 ops/us | 0.019 ops/us | 582.839 MB/sec | 33008.41 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `avgt` | 55.486 us/op | 55.583 us/op | 58.652 us/op | 58.652 us/op | 565.506 MB/sec | 32921.932 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `sample` | 55.917 us/op | 51.968 us/op | 77.312 us/op | 105.088 us/op | 555.579 MB/sec | 32719.254 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `thrpt` | 0.017 ops/us | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 457.216 MB/sec | 27520.878 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `avgt` | 52.298 us/op | 52.115 us/op | 54.081 us/op | 54.081 us/op | 504.2 MB/sec | 27696.566 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `sample` | 55.123 us/op | 52.032 us/op | 72.832 us/op | 97.152 us/op | 473.78 MB/sec | 27510.369 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `thrpt` | 0.014 ops/us | 0.014 ops/us | 0.015 ops/us | 0.015 ops/us | 502.496 MB/sec | 36984.769 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `avgt` | 67.611 us/op | 67.4 us/op | 72.086 us/op | 72.086 us/op | 522.531 MB/sec | 37077.902 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `sample` | 69.321 us/op | 64.384 us/op | 92.288 us/op | 118.912 us/op | 504.034 MB/sec | 36787.832 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `thrpt` | 0.014 ops/us | 0.014 ops/us | 0.014 ops/us | 0.014 ops/us | 490.354 MB/sec | 37509.199 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `avgt` | 73.324 us/op | 72.487 us/op | 78.19 us/op | 78.19 us/op | 485.102 MB/sec | 37339.114 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `sample` | 74.83 us/op | 70.656 us/op | 98.176 us/op | 129.769 us/op | 470.791 MB/sec | 37099.162 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `thrpt` | 0.019 ops/us | 0.02 ops/us | 0.021 ops/us | 0.021 ops/us | 527.7 MB/sec | 29049.045 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `avgt` | 52.489 us/op | 51.124 us/op | 62.396 us/op | 62.396 us/op | 530.069 MB/sec | 29095.632 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `sample` | 51.264 us/op | 48.64 us/op | 66.56 us/op | 90.752 us/op | 542.145 MB/sec | 29047.514 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `thrpt` | 0.02 ops/us | 0.02 ops/us | 0.02 ops/us | 0.02 ops/us | 495.662 MB/sec | 26642.095 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `avgt` | 51.214 us/op | 51.042 us/op | 54.405 us/op | 54.405 us/op | 493.186 MB/sec | 26514.933 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `sample` | 59.449 us/op | 49.216 us/op | 86.144 us/op | 151.808 us/op | 431.927 MB/sec | 26522.661 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `thrpt` | 0.02 ops/us | 0.02 ops/us | 0.021 ops/us | 0.021 ops/us | 512.543 MB/sec | 27087.169 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `avgt` | 51.469 us/op | 49.849 us/op | 60.75 us/op | 60.75 us/op | 505.197 MB/sec | 27171.643 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `sample` | 51.895 us/op | 47.104 us/op | 73.984 us/op | 107.136 us/op | 504.548 MB/sec | 27233.51 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `thrpt` | 0.017 ops/us | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 484.228 MB/sec | 29508.146 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `avgt` | 56.632 us/op | 55.361 us/op | 63.931 us/op | 63.931 us/op | 496.791 MB/sec | 29467.494 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `sample` | 56.164 us/op | 52.16 us/op | 76.288 us/op | 105.856 us/op | 498.424 MB/sec | 29497.319 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `thrpt` | 0.019 ops/us | 0.019 ops/us | 0.02 ops/us | 0.02 ops/us | 512.144 MB/sec | 27714.788 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `avgt` | 51.681 us/op | 51.522 us/op | 54.079 us/op | 54.079 us/op | 514.061 MB/sec | 27883.62 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `sample` | 53.45 us/op | 50.304 us/op | 71.04 us/op | 91.776 us/op | 496.485 MB/sec | 27953.455 B/op |
| argumentResolutionPathQueryHeaderFromMetadata | No-network starter invocation | `thrpt` | 5224691.988 ops/s | 5240029.691 ops/s | 5323733.934 ops/s | 5323733.934 ops/s | 4902.832 MB/sec | 984 B/op |
| argumentResolutionPathQueryHeaderFromPlan | No-network starter invocation | `thrpt` | 5159672.376 ops/s | 5192538.545 ops/s | 5324340.666 ops/s | 5324340.666 ops/s | 4841.807 MB/sec | 984 B/op |
| cachedMethodMetadataLookup | No-network starter invocation | `thrpt` | 120796021.84 ops/s | 121456094.984 ops/s | 124690123.081 ops/s | 124690123.081 ops/s | 1843.145 MB/sec | 16 B/op |
| cachedRequestPlanLookup | No-network starter invocation | `thrpt` | 118095589.86 ops/s | 118120096.373 ops/s | 122010908.535 ops/s | 122010908.535 ops/s | 1801.955 MB/sec | 16 B/op |
| diagnosticsDisabledGetNoBody | No-network starter invocation | `thrpt` | 194648.319 ops/s | 194624.672 ops/s | 200257.059 ops/s | 200257.059 ops/s | 2014.99 MB/sec | 10856.006 B/op |
| diagnosticsNoNetworkMultipleLifecycleHooksGetNoBody | No-network starter invocation | `thrpt` | 130197.748 ops/s | 130581.264 ops/s | 134815.386 ops/s | 134815.386 ops/s | 1848.974 MB/sec | 14892.011 B/op |
| diagnosticsNoNetworkMultipleObserversGetNoBody | No-network starter invocation | `thrpt` | 128329.97 ops/s | 127672.174 ops/s | 131405.94 ops/s | 131405.94 ops/s | 1855.252 MB/sec | 15160.011 B/op |
| diagnosticsNoNetworkOneLifecycleHookGetNoBody | No-network starter invocation | `thrpt` | 140563.291 ops/s | 142609.606 ops/s | 144985.934 ops/s | 144985.934 ops/s | 1977.291 MB/sec | 14752.01 B/op |
| diagnosticsNoNetworkOneObserverGetNoBody | No-network starter invocation | `thrpt` | 138863.319 ops/s | 139726.315 ops/s | 143226.034 ops/s | 143226.034 ops/s | 1962.49 MB/sec | 14820.01 B/op |
| metadataLookup | No-network starter invocation | `thrpt` | 128949195.565 ops/s | 128641275.381 ops/s | 134191943.936 ops/s | 134191943.936 ops/s | 1967.499 MB/sec | 16 B/op |
| metadataOnlyExchangeLoggingGetNoBody | No-network starter invocation | `thrpt` | 95023.57 ops/s | 95119.07 ops/s | 99108.931 ops/s | 99108.931 ops/s | 1531.752 MB/sec | 16904.016 B/op |
| micrometerObserverGetNoBody | No-network starter invocation | `thrpt` | 116095.083 ops/s | 116703.865 ops/s | 121345.484 ops/s | 121345.484 ops/s | 1804.181 MB/sec | 16297.035 B/op |
| proxyInvocationCreatesPublisher | No-network starter invocation | `thrpt` | 2750874.533 ops/s | 2760800.2 ops/s | 2820075.5 ops/s | 2820075.5 ops/s | 4197.288 MB/sec | 1600.001 B/op |
| proxyInvocationWithMockExchange | No-network starter invocation | `thrpt` | 140745.595 ops/s | 140649.052 ops/s | 144341.42 ops/s | 144341.42 ops/s | 1868.871 MB/sec | 13924.008 B/op |
| runtimeDiagnosticsProviderClientSummaries | No-network starter invocation | `thrpt` | 28414.484 ops/s | 28605.172 ops/s | 30398.007 ops/s | 30398.007 ops/s | 1885.389 MB/sec | 69592.077 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `thrpt` | 0.016 ops/us | 0.016 ops/us | 0.017 ops/us | 0.017 ops/us | 1444.741 MB/sec | 92381.14 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `avgt` | 60.852 us/op | 60.67 us/op | 64.392 us/op | 64.392 us/op | 1444.633 MB/sec | 92271.515 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `sample` | 63.172 us/op | 59.52 us/op | 83.84 us/op | 112.128 us/op | 1389.488 MB/sec | 92444.186 B/op |

## Promotion Notes

- Generated reports live under `reactive-http-client-benchmarks/target/benchmark-reports/` and are ignored by source control through the existing `target/` rule.
- To promote a release report, copy a selected release-quality Markdown report into `docs/`, give it a versioned name, and link it from release notes only when performance claims rely on that report.
- Do not promote smoke-only reports.
