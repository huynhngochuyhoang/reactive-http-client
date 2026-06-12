# Reactive HTTP Client Benchmark Report

## Promotion Metadata

- Report version: `2.10.0`.
- Starter version under test: `2.10.0`.
- Benchmark input commit: `6b46be8`.
- Benchmark runner property: `04aeb61` from the pre-commit release-prep workspace; the same release inputs are present in `6b46be8`.
- Evidence level: **Release-quality**, not smoke evidence.
- Machine limits: local loopback run on Linux/amd64 with `8` available processors; JVM warmup, CPU scheduling, and Netty event-loop scheduling affect the numbers.
- Generated source artifacts are retained during release evidence collection but are not committed: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json` and its adjacent environment properties file.

- Generated at: `2026-06-11T16:04:16.423880017Z`
- Result file: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json`
- Run label: **Release-quality candidate**

## Interpretation

- Client-side overhead comparisons use the same local loopback server, request shape, transport, codecs, and validation guardrails for raw `WebClient`, Spring HTTP Interface, and the starter.
- Smoke-only results prove the harness starts and writes artifacts; do not publish them as performance evidence.
- Optional starter feature rows enable exactly one feature at a time and are not claims about default runtime overhead.
- Starter-only rows measure starter-specific work, such as Problem Detail error mapping, where the baselines do not install equivalent behavior.
- Local loopback, JVM warmup, CPU scheduling, and Netty event-loop behavior affect the numbers; use this report as trend evidence for named scenarios.

## Report Pairing

- Current candidate: this promoted report measures starter `2.10.0`; its generated source report is `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`.
- Published baseline: the paired baseline report path is `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.9.0/release-jmh.md`, which measures the published starter `2.9.0` artifact when regenerated.
- Baseline artifacts must resolve before this report is used for release-to-release comparison.
- Numeric rows in this promoted report are current-candidate `2.10.0` rows unless a row explicitly names another starter version.

## Environment

| Key | Value |
| --- | --- |
| `generatedAt` | 2026-06-11T16:04:16.423880017Z |
| `projectVersion` | 2.10.0 |
| `starterVersion` | 2.10.0 |
| `apiCompatibilityBaselineVersion` | 2.9.0 |
| `benchmarkCommit` | 6b46be8 (release input tree; runner property was 04aeb61 before release-prep edits were committed) |
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
| Client Error Small Body | 50.853 us/op | 24.463% faster | 31.049% faster |
| Get No Body | 47.44 us/op | 14.434% slower | 10.542% slower |
| Get Path Query Header | 54.238 us/op | 19.087% slower | 8.041% faster |
| Post Json | 62.964 us/op | 23.232% slower | 13.3% slower |
| Response Entity | 54.659 us/op | 20.203% slower | 0.892% faster |
| Server Error Small Body | 50.342 us/op | 26.2% faster | 30.435% faster |

## Starter-Only and Optional Feature Rows

| Scenario | Label | Average |
| --- | --- | ---: |
| Circuit Breaker Wrapper Get No Body | Optional starter feature | 51.454 us/op |
| Exchange Logging Metadata Only Get No Body | Optional starter feature | 48.815 us/op |
| Micrometer Observer Get No Body | Optional starter feature | 57.359 us/op |
| Retry Wrapper Get No Body | Optional starter feature | 51.291 us/op |
| Problem Detail Small Body | Starter-only error-mapping overhead | 61.123 us/op |

## Raw Results

| Benchmark | Label | Mode | Score | p50 | p95 | p99 | Allocation rate | Allocation/op |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `thrpt` | 0.014 ops/us | 0.015 ops/us | 0.015 ops/us | 0.015 ops/us | 482.825 MB/sec | 36281.212 B/op |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `avgt` | 67.322 us/op | 67.118 us/op | 70.602 us/op | 70.602 us/op | 514.427 MB/sec | 36370.654 B/op |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `sample` | 66.752 us/op | 62.016 us/op | 87.68 us/op | 122.111 us/op | 518.918 MB/sec | 36474.294 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `thrpt` | 0.014 ops/us | 0.014 ops/us | 0.014 ops/us | 0.014 ops/us | 475.578 MB/sec | 36908.11 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `avgt` | 73.753 us/op | 72.174 us/op | 82.13 us/op | 82.13 us/op | 478.795 MB/sec | 37025.037 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `sample` | 73.199 us/op | 68.864 us/op | 93.952 us/op | 128.512 us/op | 482.307 MB/sec | 37189.174 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `thrpt` | 0.019 ops/us | 0.02 ops/us | 0.02 ops/us | 0.02 ops/us | 527.563 MB/sec | 28617.923 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `avgt` | 50.853 us/op | 50.6 us/op | 52.479 us/op | 52.479 us/op | 533.811 MB/sec | 28507.566 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `sample` | 56.613 us/op | 52.16 us/op | 81.28 us/op | 110.72 us/op | 490.688 MB/sec | 28822.304 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `thrpt` | 0.024 ops/us | 0.025 ops/us | 0.025 ops/us | 0.025 ops/us | 504.281 MB/sec | 21641.839 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `avgt` | 41.456 us/op | 40.813 us/op | 45.781 us/op | 45.781 us/op | 502.84 MB/sec | 21867.374 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `sample` | 41.589 us/op | 38.336 us/op | 54.272 us/op | 83.84 us/op | 506.289 MB/sec | 21888.994 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `thrpt` | 0.023 ops/us | 0.023 ops/us | 0.024 ops/us | 0.024 ops/us | 500.696 MB/sec | 22593.583 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `avgt` | 42.916 us/op | 42.325 us/op | 46.716 us/op | 46.716 us/op | 495.377 MB/sec | 22306.03 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `sample` | 44.82 us/op | 41.152 us/op | 58.496 us/op | 92.032 us/op | 482.786 MB/sec | 22470.608 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `thrpt` | 0.021 ops/us | 0.021 ops/us | 0.023 ops/us | 0.023 ops/us | 453.843 MB/sec | 22370.742 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `avgt` | 47.44 us/op | 47.302 us/op | 50.165 us/op | 50.165 us/op | 450.246 MB/sec | 22434.178 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `sample` | 49.267 us/op | 45.312 us/op | 67.2 us/op | 97.8 us/op | 441.666 MB/sec | 22427.868 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `thrpt` | 0.017 ops/us | 0.019 ops/us | 0.022 ops/us | 0.022 ops/us | 397.135 MB/sec | 24700.63 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `avgt` | 45.545 us/op | 45.495 us/op | 47.698 us/op | 47.698 us/op | 510.589 MB/sec | 24415.948 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `sample` | 46.277 us/op | 42.88 us/op | 62.528 us/op | 99.328 us/op | 514.229 MB/sec | 24691.41 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `thrpt` | 0.018 ops/us | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 572.999 MB/sec | 32813.183 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `avgt` | 58.981 us/op | 54.96 us/op | 88.734 us/op | 88.734 us/op | 538.729 MB/sec | 32659.163 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `sample` | 54.907 us/op | 51.52 us/op | 73.472 us/op | 101.591 us/op | 566.795 MB/sec | 32779.411 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `thrpt` | 0.019 ops/us | 0.019 ops/us | 0.02 ops/us | 0.02 ops/us | 487.737 MB/sec | 27120.214 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `avgt` | 54.238 us/op | 53.288 us/op | 57.558 us/op | 57.558 us/op | 480.919 MB/sec | 27366.35 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `sample` | 53.998 us/op | 50.176 us/op | 71.68 us/op | 102.784 us/op | 483.073 MB/sec | 27483.651 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `thrpt` | 0.019 ops/us | 0.02 ops/us | 0.02 ops/us | 0.02 ops/us | 492.367 MB/sec | 27150.738 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `avgt` | 51.094 us/op | 50.143 us/op | 60.129 us/op | 60.129 us/op | 510.271 MB/sec | 27303.398 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `sample` | 55.881 us/op | 48.256 us/op | 91.52 us/op | 143.521 us/op | 476.46 MB/sec | 27254.308 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `thrpt` | 0.018 ops/us | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 447.331 MB/sec | 26642.606 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `avgt` | 55.573 us/op | 53.52 us/op | 70.869 us/op | 70.869 us/op | 459.083 MB/sec | 26613.868 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `sample` | 53.894 us/op | 50.304 us/op | 71.808 us/op | 99.712 us/op | 474.445 MB/sec | 26935.311 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `thrpt` | 0.016 ops/us | 0.017 ops/us | 0.017 ops/us | 0.017 ops/us | 420.528 MB/sec | 27516.066 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `avgt` | 62.964 us/op | 60.123 us/op | 82.745 us/op | 82.745 us/op | 418.031 MB/sec | 27318.418 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `sample` | 72.045 us/op | 58.816 us/op | 140.8 us/op | 191.744 us/op | 359.624 MB/sec | 27360.983 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `thrpt` | 0.022 ops/us | 0.022 ops/us | 0.023 ops/us | 0.023 ops/us | 514.424 MB/sec | 24638.511 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `avgt` | 45.472 us/op | 45.333 us/op | 47.424 us/op | 47.424 us/op | 516.057 MB/sec | 24650.81 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `sample` | 47.459 us/op | 43.904 us/op | 62.848 us/op | 104.361 us/op | 504.457 MB/sec | 24772.36 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `thrpt` | 0.018 ops/us | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 573.179 MB/sec | 32891.831 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `avgt` | 55.15 us/op | 55.022 us/op | 58.565 us/op | 58.565 us/op | 569.89 MB/sec | 32996.066 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `sample` | 56.897 us/op | 53.504 us/op | 75.776 us/op | 104.643 us/op | 552.304 MB/sec | 33098.723 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `thrpt` | 0.018 ops/us | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 455.956 MB/sec | 27366.94 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `avgt` | 54.659 us/op | 53.867 us/op | 58.603 us/op | 58.603 us/op | 480.711 MB/sec | 27580.73 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `sample` | 56.459 us/op | 52.928 us/op | 75.52 us/op | 105.221 us/op | 462.04 MB/sec | 27482.734 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `thrpt` | 0.015 ops/us | 0.015 ops/us | 0.016 ops/us | 0.016 ops/us | 514.998 MB/sec | 36726.46 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `avgt` | 68.215 us/op | 67.284 us/op | 73.142 us/op | 73.142 us/op | 515.975 MB/sec | 36942.784 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `sample` | 68.018 us/op | 62.848 us/op | 91.904 us/op | 124.928 us/op | 513.903 MB/sec | 36804.798 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `thrpt` | 0.014 ops/us | 0.014 ops/us | 0.015 ops/us | 0.015 ops/us | 493.96 MB/sec | 37230.793 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `avgt` | 72.368 us/op | 71.56 us/op | 76.11 us/op | 76.11 us/op | 491.15 MB/sec | 37304.127 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `sample` | 74.353 us/op | 69.76 us/op | 97.28 us/op | 135.168 us/op | 477.26 MB/sec | 37374.739 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `thrpt` | 0.02 ops/us | 0.02 ops/us | 0.021 ops/us | 0.021 ops/us | 543.265 MB/sec | 28817.436 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `avgt` | 50.342 us/op | 50.346 us/op | 52.953 us/op | 52.953 us/op | 545.868 MB/sec | 28863.587 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `sample` | 52.423 us/op | 48.128 us/op | 72.96 us/op | 107.008 us/op | 531.983 MB/sec | 29040.848 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `thrpt` | 0.018 ops/us | 0.019 ops/us | 0.02 ops/us | 0.02 ops/us | 452.437 MB/sec | 26692.425 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `avgt` | 51.454 us/op | 51.239 us/op | 54.068 us/op | 54.068 us/op | 491.69 MB/sec | 26570.66 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `sample` | 55.563 us/op | 48.96 us/op | 77.056 us/op | 138.496 us/op | 457.44 MB/sec | 26604.724 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `thrpt` | 0.019 ops/us | 0.02 ops/us | 0.021 ops/us | 0.021 ops/us | 485.63 MB/sec | 27250.339 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `avgt` | 48.815 us/op | 48.696 us/op | 49.608 us/op | 49.608 us/op | 533.145 MB/sec | 27346.172 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `sample` | 49.691 us/op | 45.824 us/op | 66.816 us/op | 100.992 us/op | 528.927 MB/sec | 27263.697 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `thrpt` | 0.018 ops/us | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 493.811 MB/sec | 29572.739 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `avgt` | 57.359 us/op | 55.923 us/op | 65.769 us/op | 65.769 us/op | 491.523 MB/sec | 29532.026 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `sample` | 57.216 us/op | 51.456 us/op | 88.576 us/op | 131.84 us/op | 489.392 MB/sec | 29511.279 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `thrpt` | 0.019 ops/us | 0.02 ops/us | 0.02 ops/us | 0.02 ops/us | 514.127 MB/sec | 27846.989 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `avgt` | 51.291 us/op | 50.834 us/op | 55.13 us/op | 55.13 us/op | 514.144 MB/sec | 27671.516 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `sample` | 52.666 us/op | 49.216 us/op | 70.272 us/op | 98.176 us/op | 502.711 MB/sec | 27785.595 B/op |
| argumentResolutionPathQueryHeaderFromMetadata | No-network starter invocation | `thrpt` | 5204407.695 ops/s | 5205813.223 ops/s | 5283997.643 ops/s | 5283997.643 ops/s | 4883.801 MB/sec | 984 B/op |
| argumentResolutionPathQueryHeaderFromPlan | No-network starter invocation | `thrpt` | 5210273.7 ops/s | 5237991.245 ops/s | 5308324.321 ops/s | 5308324.321 ops/s | 4889.306 MB/sec | 984 B/op |
| cachedMethodMetadataLookup | No-network starter invocation | `thrpt` | 120907394.147 ops/s | 121511059.78 ops/s | 125618393.029 ops/s | 125618393.029 ops/s | 1844.863 MB/sec | 16 B/op |
| cachedRequestPlanLookup | No-network starter invocation | `thrpt` | 118670043.132 ops/s | 118363946.828 ops/s | 122976978.1 ops/s | 122976978.1 ops/s | 1810.723 MB/sec | 16 B/op |
| diagnosticsDisabledGetNoBody | No-network starter invocation | `thrpt` | 192840.14 ops/s | 191688.237 ops/s | 200688.189 ops/s | 200688.189 ops/s | 2014.526 MB/sec | 10956.005 B/op |
| metadataLookup | No-network starter invocation | `thrpt` | 128311767.919 ops/s | 128466294.66 ops/s | 131308034.355 ops/s | 131308034.355 ops/s | 1957.783 MB/sec | 16 B/op |
| metadataOnlyExchangeLoggingGetNoBody | No-network starter invocation | `thrpt` | 95391.889 ops/s | 94715.761 ops/s | 97982.343 ops/s | 97982.343 ops/s | 1535.877 MB/sec | 16884.014 B/op |
| micrometerObserverGetNoBody | No-network starter invocation | `thrpt` | 114744.005 ops/s | 114597.167 ops/s | 118305.408 ops/s | 118305.408 ops/s | 1782.298 MB/sec | 16289.073 B/op |
| proxyInvocationCreatesPublisher | No-network starter invocation | `thrpt` | 2693481.551 ops/s | 2685151.423 ops/s | 2848604.663 ops/s | 2848604.663 ops/s | 4109.695 MB/sec | 1600.001 B/op |
| proxyInvocationWithMockExchange | No-network starter invocation | `thrpt` | 145915.238 ops/s | 146323.196 ops/s | 148989.109 ops/s | 148989.109 ops/s | 1929.722 MB/sec | 13868.007 B/op |
| runtimeDiagnosticsProviderClientSummaries | No-network starter invocation | `thrpt` | 44600.141 ops/s | 44423.487 ops/s | 45741.681 ops/s | 45741.681 ops/s | 1886.665 MB/sec | 44360.046 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `thrpt` | 0.016 ops/us | 0.016 ops/us | 0.017 ops/us | 0.017 ops/us | 1429.074 MB/sec | 92220.001 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `avgt` | 61.123 us/op | 61.038 us/op | 63.163 us/op | 63.163 us/op | 1439.351 MB/sec | 92380.693 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `sample` | 62.523 us/op | 58.304 us/op | 85.76 us/op | 119.936 us/op | 1400.763 MB/sec | 92239.456 B/op |

## Promotion Notes

- Generated reports live under `reactive-http-client-benchmarks/target/benchmark-reports/` and are ignored by source control through the existing `target/` rule.
- To promote a release report, copy a selected release-quality Markdown report into `docs/`, give it a versioned name, and link it from release notes only when performance claims rely on that report.
- Do not promote smoke-only reports.
