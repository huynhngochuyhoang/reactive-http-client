# Reactive HTTP Client Benchmark Report

## Promotion Metadata

- Report version: `2.9.0`.
- Starter version under test: `2.9.0`.
- Benchmark commit: `c7777ba`.
- Evidence level: **Release-quality**, not smoke evidence.
- Machine limits: local loopback run on Linux/amd64 with `8` available processors; JVM warmup, CPU scheduling, and Netty event-loop scheduling affect the numbers.
- Generated source artifacts are retained during release evidence collection but are not committed: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json` and its adjacent environment properties file.

- Generated at: `2026-06-09T13:27:34.448226010Z`
- Result file: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json`
- Run label: **Release-quality candidate**

## Interpretation

- Client-side overhead comparisons use the same local loopback server, request shape, transport, codecs, and validation guardrails for raw `WebClient`, Spring HTTP Interface, and the starter.
- Smoke-only results prove the harness starts and writes artifacts; do not publish them as performance evidence.
- Optional starter feature rows enable exactly one feature at a time and are not claims about default runtime overhead.
- Starter-only rows measure starter-specific work, such as Problem Detail error mapping, where the baselines do not install equivalent behavior.
- Local loopback, JVM warmup, CPU scheduling, and Netty event-loop behavior affect the numbers; use this report as trend evidence for named scenarios.

## Report Pairing

- Current candidate: this promoted report measures starter `2.9.0`; its generated source report is `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`.
- Published baseline: the paired baseline report path is `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.8.0/release-jmh.md`, which measures the published starter `2.8.0` artifact when regenerated.
- Baseline artifacts must resolve before this report is used for release-to-release comparison.
- Numeric rows in this promoted report are current-candidate `2.9.0` rows unless a row explicitly names another starter version.

## Environment

| Key | Value |
| --- | --- |
| `generatedAt` | 2026-06-09T13:27:34.448226010Z |
| `projectVersion` | 2.9.0 |
| `starterVersion` | 2.9.0 |
| `apiCompatibilityBaselineVersion` | 2.8.0 |
| `benchmarkCommit` | c7777ba |
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
| Client Error Small Body | 80.371 us/op | 17.36% faster | 42.19% faster |
| Get No Body | 75.746 us/op | 27.768% slower | 0.764% slower |
| Get Path Query Header | 112.547 us/op | 77.908% slower | 22.677% slower |
| Post Json | 154.017 us/op | 46.18% slower | 59.356% slower |
| Response Entity | 128.981 us/op | 93.86% slower | 3.547% slower |
| Server Error Small Body | 91.668 us/op | 8.382% faster | 31.011% faster |

## Starter-Only and Optional Feature Rows

| Scenario | Label | Average |
| --- | --- | ---: |
| Circuit Breaker Wrapper Get No Body | Optional starter feature | 80.893 us/op |
| Exchange Logging Metadata Only Get No Body | Optional starter feature | 84.506 us/op |
| Micrometer Observer Get No Body | Optional starter feature | 97.935 us/op |
| Retry Wrapper Get No Body | Optional starter feature | 122.822 us/op |
| Problem Detail Small Body | Starter-only error-mapping overhead | 124.544 us/op |

## Raw Results

| Benchmark | Label | Mode | Score | p50 | p95 | p99 | Allocation rate | Allocation/op |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `thrpt` | 0.01 ops/us | 0.011 ops/us | 0.012 ops/us | 0.012 ops/us | 334.727 MB/sec | 36538.669 B/op |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `avgt` | 97.254 us/op | 89.681 us/op | 155.187 us/op | 155.187 us/op | 368.639 MB/sec | 36544.761 B/op |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `sample` | 93.246 us/op | 78.08 us/op | 170.24 us/op | 259.328 us/op | 372.318 MB/sec | 36614.443 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `thrpt` | 0.009 ops/us | 0.01 ops/us | 0.011 ops/us | 0.011 ops/us | 327.54 MB/sec | 37100.278 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `avgt` | 139.028 us/op | 120.487 us/op | 231.007 us/op | 231.007 us/op | 273.086 MB/sec | 37124.824 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `sample` | 122.423 us/op | 97.152 us/op | 227.328 us/op | 361.482 us/op | 287.731 MB/sec | 37223.456 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `thrpt` | 0.013 ops/us | 0.014 ops/us | 0.015 ops/us | 0.015 ops/us | 395.313 MB/sec | 31945.155 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `avgt` | 80.371 us/op | 73.095 us/op | 117.901 us/op | 117.901 us/op | 389.381 MB/sec | 31780.734 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `sample` | 78.711 us/op | 63.488 us/op | 156.928 us/op | 236.572 us/op | 383.644 MB/sec | 31896.567 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `thrpt` | 0.018 ops/us | 0.019 ops/us | 0.02 ops/us | 0.02 ops/us | 372.461 MB/sec | 21891.31 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `avgt` | 59.284 us/op | 52.43 us/op | 97.602 us/op | 97.602 us/op | 368.708 MB/sec | 22013.675 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `sample` | 53.751 us/op | 45.44 us/op | 91.136 us/op | 154.624 us/op | 391.962 MB/sec | 22094.197 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `thrpt` | 0.016 ops/us | 0.018 ops/us | 0.02 ops/us | 0.02 ops/us | 331.779 MB/sec | 22478.924 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `avgt` | 75.172 us/op | 63.542 us/op | 137.045 us/op | 137.045 us/op | 303.225 MB/sec | 22430.356 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `sample` | 55.853 us/op | 47.872 us/op | 94.848 us/op | 162.304 us/op | 383.697 MB/sec | 22608.6 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `thrpt` | 0.012 ops/us | 0.013 ops/us | 0.016 ops/us | 0.016 ops/us | 301.999 MB/sec | 26383.803 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `avgt` | 75.746 us/op | 63.451 us/op | 152.165 us/op | 152.165 us/op | 357.625 MB/sec | 26337.569 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `sample` | 73.685 us/op | 58.496 us/op | 150.528 us/op | 226.56 us/op | 339.63 MB/sec | 26459.509 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `thrpt` | 0.016 ops/us | 0.017 ops/us | 0.018 ops/us | 0.018 ops/us | 362.177 MB/sec | 24497.204 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `avgt` | 63.261 us/op | 56.658 us/op | 95.171 us/op | 95.171 us/op | 382.912 MB/sec | 24676.692 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `sample` | 63.709 us/op | 50.944 us/op | 120.704 us/op | 206.848 us/op | 363.98 MB/sec | 24498.96 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `thrpt` | 0.012 ops/us | 0.013 ops/us | 0.015 ops/us | 0.015 ops/us | 384.272 MB/sec | 32957.017 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `avgt` | 91.742 us/op | 75.601 us/op | 176.458 us/op | 176.458 us/op | 376.869 MB/sec | 32897.922 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `sample` | 83.712 us/op | 65.152 us/op | 172.8 us/op | 260.864 us/op | 371.638 MB/sec | 32876.001 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `thrpt` | 0.01 ops/us | 0.012 ops/us | 0.015 ops/us | 0.015 ops/us | 305.668 MB/sec | 31704.672 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `avgt` | 112.547 us/op | 89.249 us/op | 207.736 us/op | 207.736 us/op | 304.033 MB/sec | 31616.786 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `sample` | 130.968 us/op | 98.176 us/op | 239.872 us/op | 580.608 us/op | 227.57 MB/sec | 31594.519 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `thrpt` | 0.007 ops/us | 0.006 ops/us | 0.016 ops/us | 0.016 ops/us | 193.557 MB/sec | 27743.381 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `avgt` | 105.362 us/op | 80.802 us/op | 200.913 us/op | 200.913 us/op | 284.712 MB/sec | 27355.554 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `sample` | 78.832 us/op | 57.344 us/op | 161.28 us/op | 289.28 us/op | 325.973 MB/sec | 27190.095 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `thrpt` | 0.01 ops/us | 0.012 ops/us | 0.015 ops/us | 0.015 ops/us | 254.855 MB/sec | 26880.083 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `avgt` | 96.65 us/op | 77.75 us/op | 174.899 us/op | 174.899 us/op | 293.946 MB/sec | 26804.864 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `sample` | 106.215 us/op | 74.496 us/op | 216.32 us/op | 450.56 us/op | 240.929 MB/sec | 27113.206 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `thrpt` | 0.007 ops/us | 0.005 ops/us | 0.012 ops/us | 0.012 ops/us | 214.453 MB/sec | 32234.159 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `avgt` | 154.017 us/op | 142.513 us/op | 276.289 us/op | 276.289 us/op | 237.711 MB/sec | 32063.831 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `sample` | 141.896 us/op | 114.432 us/op | 269.824 us/op | 446.976 us/op | 212.917 MB/sec | 32052.935 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `thrpt` | 0.014 ops/us | 0.017 ops/us | 0.018 ops/us | 0.018 ops/us | 337.692 MB/sec | 24729.177 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `avgt` | 66.533 us/op | 61.095 us/op | 99.171 us/op | 99.171 us/op | 365.191 MB/sec | 24749.87 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `sample` | 67.192 us/op | 53.824 us/op | 126.08 us/op | 213.78 us/op | 344.179 MB/sec | 24436.411 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `thrpt` | 0.01 ops/us | 0.014 ops/us | 0.014 ops/us | 0.014 ops/us | 322.999 MB/sec | 33128.703 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `avgt` | 124.563 us/op | 107.864 us/op | 204.412 us/op | 204.412 us/op | 299.351 MB/sec | 33077.371 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `sample` | 88.934 us/op | 67.968 us/op | 183.552 us/op | 276.992 us/op | 350.664 MB/sec | 32970.367 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `thrpt` | 0.01 ops/us | 0.011 ops/us | 0.013 ops/us | 0.013 ops/us | 320.086 MB/sec | 34475.629 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `avgt` | 128.981 us/op | 102.737 us/op | 188.566 us/op | 188.566 us/op | 281.71 MB/sec | 34177.495 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `sample` | 109.773 us/op | 81.536 us/op | 231.168 us/op | 358.395 us/op | 294.673 MB/sec | 34304.518 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `thrpt` | 0.01 ops/us | 0.011 ops/us | 0.012 ops/us | 0.012 ops/us | 363.007 MB/sec | 36957.901 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `avgt` | 100.055 us/op | 86.37 us/op | 154.221 us/op | 154.221 us/op | 369.717 MB/sec | 36766.359 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `sample` | 94.604 us/op | 77.952 us/op | 176.384 us/op | 276.48 us/op | 368.88 MB/sec | 36838.811 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `thrpt` | 0.009 ops/us | 0.01 ops/us | 0.011 ops/us | 0.011 ops/us | 331.139 MB/sec | 37279.431 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `avgt` | 132.874 us/op | 119.598 us/op | 220.405 us/op | 220.405 us/op | 292.733 MB/sec | 37682.943 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `sample` | 111.663 us/op | 88.832 us/op | 221.696 us/op | 320 us/op | 317.979 MB/sec | 37497.605 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `thrpt` | 0.013 ops/us | 0.013 ops/us | 0.016 ops/us | 0.016 ops/us | 396.281 MB/sec | 31901.798 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `avgt` | 91.668 us/op | 74.075 us/op | 189.43 us/op | 189.43 us/op | 367.212 MB/sec | 31855.051 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `sample` | 74.637 us/op | 62.848 us/op | 139.008 us/op | 217.6 us/op | 410.096 MB/sec | 32292.814 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `thrpt` | 0.013 ops/us | 0.015 ops/us | 0.016 ops/us | 0.016 ops/us | 318.884 MB/sec | 26798.129 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `avgt` | 80.893 us/op | 66.883 us/op | 137.907 us/op | 137.907 us/op | 339.687 MB/sec | 26719.792 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `sample` | 104.09 us/op | 70.912 us/op | 212.48 us/op | 476.16 us/op | 243.321 MB/sec | 26842.174 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `thrpt` | 0.014 ops/us | 0.015 ops/us | 0.018 ops/us | 0.018 ops/us | 368.123 MB/sec | 27417.59 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `avgt` | 84.506 us/op | 64.734 us/op | 166.302 us/op | 166.302 us/op | 347.324 MB/sec | 27399.037 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `sample` | 77.242 us/op | 58.432 us/op | 161.536 us/op | 264.704 us/op | 332.967 MB/sec | 27239.212 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `thrpt` | 0.011 ops/us | 0.013 ops/us | 0.014 ops/us | 0.014 ops/us | 300.227 MB/sec | 30060.136 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `avgt` | 97.935 us/op | 77.216 us/op | 173.128 us/op | 173.128 us/op | 323.948 MB/sec | 29999.754 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `sample` | 94.634 us/op | 70.272 us/op | 203.264 us/op | 311.808 us/op | 299.992 MB/sec | 30096.07 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `thrpt` | 0.011 ops/us | 0.013 ops/us | 0.014 ops/us | 0.014 ops/us | 289.765 MB/sec | 27909.159 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `avgt` | 122.822 us/op | 102.659 us/op | 223.757 us/op | 223.757 us/op | 253.057 MB/sec | 27753.242 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `sample` | 94.866 us/op | 70.144 us/op | 203.008 us/op | 332.288 us/op | 278.375 MB/sec | 27976.193 B/op |
| argumentResolutionPathQueryHeaderFromMetadata | No-network starter invocation | `thrpt` | 4224795.148 ops/s | 4218426.737 ops/s | 4410037.78 ops/s | 4410037.78 ops/s | 3964.524 MB/sec | 984 B/op |
| argumentResolutionPathQueryHeaderFromPlan | No-network starter invocation | `thrpt` | 4149860.09 ops/s | 4181564.679 ops/s | 4386361.269 ops/s | 4386361.269 ops/s | 3894.199 MB/sec | 984 B/op |
| cachedMethodMetadataLookup | No-network starter invocation | `thrpt` | 106585725.494 ops/s | 106697128.72 ops/s | 108897882.618 ops/s | 108897882.618 ops/s | 1626.333 MB/sec | 16 B/op |
| cachedRequestPlanLookup | No-network starter invocation | `thrpt` | 96922122.814 ops/s | 96572003.357 ops/s | 98980649.153 ops/s | 98980649.153 ops/s | 1478.876 MB/sec | 16 B/op |
| diagnosticsDisabledGetNoBody | No-network starter invocation | `thrpt` | 137667.5 ops/s | 137490.441 ops/s | 143636.04 ops/s | 143636.04 ops/s | 1730.763 MB/sec | 13184.009 B/op |
| metadataLookup | No-network starter invocation | `thrpt` | 104871092.827 ops/s | 104455946.401 ops/s | 115029731.551 ops/s | 115029731.551 ops/s | 1600.105 MB/sec | 16 B/op |
| metadataOnlyExchangeLoggingGetNoBody | No-network starter invocation | `thrpt` | 81048.841 ops/s | 81127.758 ops/s | 82756.648 ops/s | 82756.648 ops/s | 1306.489 MB/sec | 16904.018 B/op |
| micrometerObserverGetNoBody | No-network starter invocation | `thrpt` | 98759.167 ops/s | 99293 ops/s | 101968.092 ops/s | 101968.092 ops/s | 1567.287 MB/sec | 16641.804 B/op |
| proxyInvocationCreatesPublisher | No-network starter invocation | `thrpt` | 2193116.621 ops/s | 2225112.472 ops/s | 2340652.432 ops/s | 2340652.432 ops/s | 3580.472 MB/sec | 1712.001 B/op |
| proxyInvocationWithMockExchange | No-network starter invocation | `thrpt` | 110578.649 ops/s | 110343.754 ops/s | 115777.923 ops/s | 115777.923 ops/s | 1607.816 MB/sec | 15248.011 B/op |
| runtimeDiagnosticsProviderClientSummaries | No-network starter invocation | `thrpt` | 37587.066 ops/s | 37827.843 ops/s | 38775.42 ops/s | 38775.42 ops/s | 1616.41 MB/sec | 45096.054 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `thrpt` | 0.009 ops/us | 0.009 ops/us | 0.011 ops/us | 0.011 ops/us | 776.263 MB/sec | 95448.546 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `avgt` | 124.544 us/op | 94.346 us/op | 230.614 us/op | 230.614 us/op | 808.879 MB/sec | 95400.843 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `sample` | 120.855 us/op | 92.032 us/op | 239.36 us/op | 433.664 us/op | 745.652 MB/sec | 95326.163 B/op |

## Promotion Notes

- Generated reports live under `reactive-http-client-benchmarks/target/benchmark-reports/` and are ignored by source control through the existing `target/` rule.
- To promote a release report, copy a selected release-quality Markdown report into `docs/`, give it a versioned name, and link it from release notes only when performance claims rely on that report.
- Do not promote smoke-only reports.
