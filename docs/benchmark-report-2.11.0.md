# Reactive HTTP Client Benchmark Report

## Promotion Metadata

- Report version: `2.11.0`.
- Starter version under test: `2.11.0`.
- Benchmark input commit: `4ec8c6a` (clean committed release input tree).
- Evidence level: **Release-quality**, not smoke evidence.
- Machine limits: local loopback run on Linux/amd64 with `8` available processors; JVM warmup, CPU scheduling, and Netty event-loop scheduling affect the numbers.
- Generated source artifacts are retained during release evidence collection but are not committed: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json` and its adjacent environment properties file.

- Generated at: `2026-06-30T02:28:13.009668068Z`
- Result file: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json`
- Run label: **Release-quality candidate**

## Interpretation

- Client-side overhead comparisons use the same local loopback server, request shape, transport, codecs, and validation guardrails for raw `WebClient`, Spring HTTP Interface, and the starter.
- Smoke-only results prove the harness starts and writes artifacts; do not publish them as performance evidence.
- Optional starter feature rows enable exactly one feature at a time and are not claims about default runtime overhead.
- Starter-only rows measure starter-specific work, such as Problem Detail error mapping, where the baselines do not install equivalent behavior.
- Local loopback, JVM warmup, CPU scheduling, and Netty event-loop behavior affect the numbers; use this report as trend evidence for named scenarios.

## Report Pairing

- Current candidate: this promoted report measures starter `2.11.0`; its generated source report is `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`.
- Published baseline: the paired baseline report path is `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.10.0/release-jmh.md`, which measures the published starter `2.10.0` artifact when regenerated.
- Baseline artifacts must resolve before this report is used for release-to-release comparison.
- Numeric rows in this promoted report are current-candidate `2.11.0` rows unless a row explicitly names another starter version.

## Environment

| Key | Value |
| --- | --- |
| `generatedAt` | 2026-06-30T02:28:13.009668068Z |
| `projectVersion` | 2.11.0 |
| `starterVersion` | 2.11.0 |
| `apiCompatibilityBaselineVersion` | 2.10.0 |
| `benchmarkCommit` | 4ec8c6a |
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
| Client Error Small Body | 56.609 us/op | 31.631% faster | 28.328% faster |
| Get No Body | 51.295 us/op | 37.544% faster | 87.828% faster |
| Get Path Query Header | 56.514 us/op | 16.318% slower | 13.465% faster |
| Post Json | 74.904 us/op | 27.658% slower | 14.617% slower |
| Response Entity | 68.008 us/op | 36.843% slower | 9.038% slower |
| Server Error Small Body | 54.536 us/op | 22.915% faster | 28.233% faster |

## Starter-Only and Optional Feature Rows

| Scenario | Label | Average |
| --- | --- | ---: |
| Circuit Breaker Wrapper Get No Body | Optional starter feature | 53.615 us/op |
| Exchange Logging Metadata Only Get No Body | Optional starter feature | 52.84 us/op |
| Micrometer Observer Get No Body | Optional starter feature | 68.336 us/op |
| Retry Wrapper Get No Body | Optional starter feature | 52.675 us/op |
| Problem Detail Small Body | Starter-only error-mapping overhead | 66.603 us/op |

## Raw Results

| Benchmark | Label | Mode | Score | p50 | p95 | p99 | Allocation rate | Allocation/op |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `thrpt` | 0.009 ops/us | 0.01 ops/us | 0.011 ops/us | 0.011 ops/us | 312.185 MB/sec | 36459.181 B/op |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `avgt` | 82.8 us/op | 72.49 us/op | 163.431 us/op | 163.431 us/op | 448.555 MB/sec | 36588.16 B/op |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `sample` | 70.401 us/op | 64.256 us/op | 95.872 us/op | 143.616 us/op | 492.026 MB/sec | 36476.213 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `thrpt` | 0.009 ops/us | 0.01 ops/us | 0.011 ops/us | 0.011 ops/us | 320.928 MB/sec | 37164.087 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `avgt` | 78.984 us/op | 78.278 us/op | 88.8 us/op | 88.8 us/op | 445.934 MB/sec | 36939.975 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `sample` | 408.781 us/op | 240.384 us/op | 1277.952 us/op | 3248.128 us/op | 87.483 MB/sec | 38310.23 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `thrpt` | 0.014 ops/us | 0.014 ops/us | 0.015 ops/us | 0.015 ops/us | 375.801 MB/sec | 28772.352 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `avgt` | 56.609 us/op | 56.089 us/op | 62.228 us/op | 62.228 us/op | 485.978 MB/sec | 28838.157 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `sample` | 72.668 us/op | 58.816 us/op | 125.696 us/op | 208.64 us/op | 373.548 MB/sec | 28637.86 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `thrpt` | 0.017 ops/us | 0.018 ops/us | 0.02 ops/us | 0.02 ops/us | 355.346 MB/sec | 21590.848 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `avgt` | 82.131 us/op | 60.025 us/op | 185.188 us/op | 185.188 us/op | 324.404 MB/sec | 21901.199 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `sample` | 50.645 us/op | 39.872 us/op | 84.608 us/op | 153.856 us/op | 437.809 MB/sec | 21987.208 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `thrpt` | 0.016 ops/us | 0.017 ops/us | 0.018 ops/us | 0.018 ops/us | 341.17 MB/sec | 22400.515 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `avgt` | 421.409 us/op | 235.035 us/op | 1633.048 us/op | 1633.048 us/op | 205.891 MB/sec | 22982.844 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `sample` | 68.829 us/op | 54.272 us/op | 116.992 us/op | 251.392 us/op | 321.206 MB/sec | 22233.626 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `thrpt` | 0.015 ops/us | 0.015 ops/us | 0.016 ops/us | 0.016 ops/us | 322.558 MB/sec | 22647.89 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `avgt` | 51.295 us/op | 49.885 us/op | 61.431 us/op | 61.431 us/op | 419.862 MB/sec | 22414.114 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `sample` | 83.529 us/op | 51.648 us/op | 153.856 us/op | 366.019 us/op | 263.951 MB/sec | 22565.46 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `thrpt` | 0.013 ops/us | 0.015 ops/us | 0.017 ops/us | 0.017 ops/us | 308.094 MB/sec | 24816.686 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `avgt` | 48.586 us/op | 48.173 us/op | 54.742 us/op | 54.742 us/op | 479.944 MB/sec | 24409.785 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `sample` | 95.427 us/op | 73.984 us/op | 158.208 us/op | 476.611 us/op | 252.15 MB/sec | 24258.127 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `thrpt` | 0.012 ops/us | 0.013 ops/us | 0.015 ops/us | 0.015 ops/us | 360.552 MB/sec | 32784.299 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `avgt` | 65.308 us/op | 61.992 us/op | 81.534 us/op | 81.534 us/op | 482.548 MB/sec | 32617.854 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `sample` | 84.343 us/op | 63.936 us/op | 154.88 us/op | 328.192 us/op | 367.134 MB/sec | 32725.871 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `thrpt` | 0.012 ops/us | 0.012 ops/us | 0.014 ops/us | 0.014 ops/us | 303.244 MB/sec | 27434.146 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `avgt` | 56.514 us/op | 56.501 us/op | 60.887 us/op | 60.887 us/op | 459.451 MB/sec | 27234.299 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `sample` | 84.517 us/op | 61.952 us/op | 156.416 us/op | 311.875 us/op | 307.873 MB/sec | 27514.887 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `thrpt` | 0.011 ops/us | 0.011 ops/us | 0.014 ops/us | 0.014 ops/us | 275.91 MB/sec | 27477.102 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `avgt` | 58.675 us/op | 54.142 us/op | 87.588 us/op | 87.588 us/op | 451.477 MB/sec | 27047.762 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `sample` | 56.541 us/op | 48.32 us/op | 96 us/op | 147.062 us/op | 458.721 MB/sec | 27130.557 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `thrpt` | 0.011 ops/us | 0.013 ops/us | 0.014 ops/us | 0.014 ops/us | 288.205 MB/sec | 27018.501 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `avgt` | 65.352 us/op | 60.846 us/op | 88.551 us/op | 88.551 us/op | 399.241 MB/sec | 26834.509 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `sample` | 100.935 us/op | 70.144 us/op | 218.88 us/op | 469.284 us/op | 252.995 MB/sec | 27109.213 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `thrpt` | 0.01 ops/us | 0.011 ops/us | 0.013 ops/us | 0.013 ops/us | 272.814 MB/sec | 27689.502 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `avgt` | 74.904 us/op | 66.684 us/op | 118.483 us/op | 118.483 us/op | 369.985 MB/sec | 27625.608 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `sample` | 116.175 us/op | 73.344 us/op | 250.637 us/op | 559.524 us/op | 224.637 MB/sec | 27842.745 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `thrpt` | 0.012 ops/us | 0.012 ops/us | 0.016 ops/us | 0.016 ops/us | 285.499 MB/sec | 24989.216 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `avgt` | 49.698 us/op | 49.215 us/op | 52.427 us/op | 52.427 us/op | 471.836 MB/sec | 24615.874 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `sample` | 49.473 us/op | 44.416 us/op | 68.224 us/op | 110.464 us/op | 483.524 MB/sec | 24569.19 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `thrpt` | 0.012 ops/us | 0.013 ops/us | 0.015 ops/us | 0.015 ops/us | 380.647 MB/sec | 32845.907 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `avgt` | 62.371 us/op | 61.568 us/op | 68.518 us/op | 68.518 us/op | 502.247 MB/sec | 32849.478 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `sample` | 104.097 us/op | 77.056 us/op | 203.52 us/op | 479.744 us/op | 299.808 MB/sec | 33089.046 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `thrpt` | 0.012 ops/us | 0.012 ops/us | 0.014 ops/us | 0.014 ops/us | 306.612 MB/sec | 27770.343 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `avgt` | 68.008 us/op | 61.025 us/op | 131.139 us/op | 131.139 us/op | 405.444 MB/sec | 27359.733 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `sample` | 64.9 us/op | 56.32 us/op | 110.592 us/op | 168.96 us/op | 401.711 MB/sec | 27500.378 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `thrpt` | 0.01 ops/us | 0.01 ops/us | 0.011 ops/us | 0.011 ops/us | 338.483 MB/sec | 36392.821 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `avgt` | 70.748 us/op | 70.091 us/op | 76.196 us/op | 76.196 us/op | 495.802 MB/sec | 36804.988 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `sample` | 73.67 us/op | 64.256 us/op | 107.008 us/op | 163.584 us/op | 475.209 MB/sec | 36863.475 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `thrpt` | 0.009 ops/us | 0.01 ops/us | 0.011 ops/us | 0.011 ops/us | 324.273 MB/sec | 37419.756 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `avgt` | 75.99 us/op | 75.961 us/op | 78.629 us/op | 78.629 us/op | 469.435 MB/sec | 37465.595 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `sample` | 87.56 us/op | 76.416 us/op | 146.944 us/op | 214.528 us/op | 402.171 MB/sec | 37128.075 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `thrpt` | 0.015 ops/us | 0.014 ops/us | 0.016 ops/us | 0.016 ops/us | 403.153 MB/sec | 29051.783 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `avgt` | 54.536 us/op | 54.068 us/op | 63.607 us/op | 63.607 us/op | 507.467 MB/sec | 28964.23 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `sample` | 67.893 us/op | 55.936 us/op | 112.512 us/op | 184.832 us/op | 406.012 MB/sec | 29083.258 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `thrpt` | 0.013 ops/us | 0.014 ops/us | 0.015 ops/us | 0.015 ops/us | 327.509 MB/sec | 26693.637 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `avgt` | 53.615 us/op | 51.924 us/op | 63.149 us/op | 63.149 us/op | 472.53 MB/sec | 26509.128 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `sample` | 56.991 us/op | 50.496 us/op | 90.112 us/op | 140.032 us/op | 442.349 MB/sec | 26581.461 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `thrpt` | 0.014 ops/us | 0.015 ops/us | 0.016 ops/us | 0.016 ops/us | 375.698 MB/sec | 27356.71 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `avgt` | 52.84 us/op | 53.161 us/op | 55.367 us/op | 55.367 us/op | 490.446 MB/sec | 27201.805 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `sample` | 78.112 us/op | 52.672 us/op | 135.936 us/op | 378.757 us/op | 329.234 MB/sec | 27256.823 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `thrpt` | 0.01 ops/us | 0.012 ops/us | 0.013 ops/us | 0.013 ops/us | 295.306 MB/sec | 29834.379 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `avgt` | 68.336 us/op | 60.068 us/op | 117.677 us/op | 117.677 us/op | 436.597 MB/sec | 29655.529 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `sample` | 63.113 us/op | 54.4 us/op | 103.936 us/op | 163.835 us/op | 446.487 MB/sec | 29712.849 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `thrpt` | 0.011 ops/us | 0.012 ops/us | 0.014 ops/us | 0.014 ops/us | 288.859 MB/sec | 27690.805 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `avgt` | 52.675 us/op | 52.35 us/op | 56.542 us/op | 56.542 us/op | 503.118 MB/sec | 27828.198 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `sample` | 86.688 us/op | 53.248 us/op | 203.008 us/op | 464.896 us/op | 303.176 MB/sec | 28087.837 B/op |
| argumentResolutionPathQueryHeaderFromMetadata | No-network starter invocation | `thrpt` | 4423457.923 ops/s | 4372542.957 ops/s | 5038400.583 ops/s | 5038400.583 ops/s | 4150.951 MB/sec | 984 B/op |
| argumentResolutionPathQueryHeaderFromPlan | No-network starter invocation | `thrpt` | 4557443.416 ops/s | 4655764.997 ops/s | 5103105.541 ops/s | 5103105.541 ops/s | 4276.676 MB/sec | 984 B/op |
| cachedMethodMetadataLookup | No-network starter invocation | `thrpt` | 120057721.842 ops/s | 121841758.4 ops/s | 129261842.343 ops/s | 129261842.343 ops/s | 1831.864 MB/sec | 16 B/op |
| cachedRequestPlanLookup | No-network starter invocation | `thrpt` | 114437486.316 ops/s | 115599030.12 ops/s | 119426675.146 ops/s | 119426675.146 ops/s | 1746.14 MB/sec | 16 B/op |
| diagnosticsDisabledGetNoBody | No-network starter invocation | `thrpt` | 179749.965 ops/s | 180082.975 ops/s | 183391.32 ops/s | 183391.32 ops/s | 1862.202 MB/sec | 10864.007 B/op |
| diagnosticsNoNetworkMultipleLifecycleHooksGetNoBody | No-network starter invocation | `thrpt` | 97328.552 ops/s | 107132.536 ops/s | 120553.278 ops/s | 120553.278 ops/s | 1374.329 MB/sec | 14808.014 B/op |
| diagnosticsNoNetworkMultipleObserversGetNoBody | No-network starter invocation | `thrpt` | 106599.861 ops/s | 105953.468 ops/s | 117061.301 ops/s | 117061.301 ops/s | 1549.594 MB/sec | 15244.014 B/op |
| diagnosticsNoNetworkOneLifecycleHookGetNoBody | No-network starter invocation | `thrpt` | 114264.472 ops/s | 123379.671 ops/s | 128780.378 ops/s | 128780.378 ops/s | 1607.914 MB/sec | 14752.017 B/op |
| diagnosticsNoNetworkOneObserverGetNoBody | No-network starter invocation | `thrpt` | 113883.426 ops/s | 113697.439 ops/s | 128407.925 ops/s | 128407.925 ops/s | 1599.44 MB/sec | 14732.013 B/op |
| metadataLookup | No-network starter invocation | `thrpt` | 116855173.259 ops/s | 117012193.273 ops/s | 118350898.833 ops/s | 118350898.833 ops/s | 1782.967 MB/sec | 16 B/op |
| metadataOnlyExchangeLoggingGetNoBody | No-network starter invocation | `thrpt` | 73258.23 ops/s | 75266.477 ops/s | 79286.286 ops/s | 79286.286 ops/s | 1180.832 MB/sec | 16904.021 B/op |
| micrometerObserverGetNoBody | No-network starter invocation | `thrpt` | 95351.748 ops/s | 95824.616 ops/s | 99453.155 ops/s | 99453.155 ops/s | 1489.74 MB/sec | 16384.015 B/op |
| proxyInvocationCreatesPublisher | No-network starter invocation | `thrpt` | 2553874.559 ops/s | 2554711.003 ops/s | 2597078.337 ops/s | 2597078.337 ops/s | 3896.689 MB/sec | 1600.001 B/op |
| proxyInvocationWithMockExchange | No-network starter invocation | `thrpt` | 132229.45 ops/s | 132187.409 ops/s | 138851.09 ops/s | 138851.09 ops/s | 1749.861 MB/sec | 13876.009 B/op |
| runtimeDiagnosticsProviderClientSummaries | No-network starter invocation | `thrpt` | 26780.943 ops/s | 26970.279 ops/s | 27589.092 ops/s | 27589.092 ops/s | 1727.069 MB/sec | 67628.082 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `thrpt` | 0.01 ops/us | 0.011 ops/us | 0.012 ops/us | 0.012 ops/us | 916.91 MB/sec | 92466.821 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `avgt` | 66.603 us/op | 65.548 us/op | 72.778 us/op | 72.778 us/op | 1323.607 MB/sec | 92345.608 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `sample` | 76.01 us/op | 67.328 us/op | 119.424 us/op | 186.368 us/op | 1148.174 MB/sec | 92029.109 B/op |

## Promotion Notes

- Generated reports live under `reactive-http-client-benchmarks/target/benchmark-reports/` and are ignored by source control through the existing `target/` rule.
- To promote a release report, copy a selected release-quality Markdown report into `docs/`, give it a versioned name, and link it from release notes only when performance claims rely on that report.
- Do not promote smoke-only reports.
