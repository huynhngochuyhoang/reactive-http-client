# Reactive HTTP Client Benchmark Report

## Promotion Metadata

- Report version: `2.11.0`.
- Starter version under test: `2.11.0`.
- Benchmark input commit: `e08876b-dirty` (release input tree; generated before release-prep edits were committed).
- Evidence level: **Release-quality**, not smoke evidence.
- Machine limits: local loopback run on Linux/amd64 with `8` available processors; JVM warmup, CPU scheduling, and Netty event-loop scheduling affect the numbers.
- Generated source artifacts are retained during release evidence collection but are not committed: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json` and its adjacent environment properties file.

- Generated at: `2026-06-30T00:06:55.607238195Z`
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
| `generatedAt` | 2026-06-30T00:06:55.607238195Z |
| `projectVersion` | 2.11.0 |
| `starterVersion` | 2.11.0 |
| `apiCompatibilityBaselineVersion` | 2.10.0 |
| `benchmarkCommit` | e08876b-dirty |
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
| Client Error Small Body | 49.755 us/op | 29.012% faster | 32.735% faster |
| Get No Body | 47.073 us/op | 14.786% slower | 8.676% slower |
| Get Path Query Header | 51.915 us/op | 14.245% slower | 6.99% faster |
| Post Json | 73.924 us/op | 35.712% slower | 33.093% slower |
| Response Entity | 56.956 us/op | 19.644% slower | 0.881% faster |
| Server Error Small Body | 49.72 us/op | 27.652% faster | 33.739% faster |

## Starter-Only and Optional Feature Rows

| Scenario | Label | Average |
| --- | --- | ---: |
| Circuit Breaker Wrapper Get No Body | Optional starter feature | 53.119 us/op |
| Exchange Logging Metadata Only Get No Body | Optional starter feature | 49.468 us/op |
| Micrometer Observer Get No Body | Optional starter feature | 58.648 us/op |
| Retry Wrapper Get No Body | Optional starter feature | 54.85 us/op |
| Problem Detail Small Body | Starter-only error-mapping overhead | 63.297 us/op |

## Raw Results

| Benchmark | Label | Mode | Score | p50 | p95 | p99 | Allocation rate | Allocation/op |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `thrpt` | 0.009 ops/us | 0.008 ops/us | 0.012 ops/us | 0.012 ops/us | 300.257 MB/sec | 36474.83 B/op |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `avgt` | 70.089 us/op | 68.915 us/op | 78.191 us/op | 78.191 us/op | 499.307 MB/sec | 36646.638 B/op |
| clientSideOverheadRawWebClientClientErrorSmallBody | Client-side overhead | `sample` | 70.168 us/op | 64.768 us/op | 97.408 us/op | 121.486 us/op | 495.008 MB/sec | 36570.64 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `thrpt` | 0.01 ops/us | 0.011 ops/us | 0.012 ops/us | 0.012 ops/us | 348.779 MB/sec | 37183.975 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `avgt` | 73.968 us/op | 72.738 us/op | 79.586 us/op | 79.586 us/op | 474.857 MB/sec | 36850.545 B/op |
| clientSideOverheadSpringHttpExchangeClientErrorSmallBody | Client-side overhead | `sample` | 74.948 us/op | 69.76 us/op | 102.784 us/op | 140.032 us/op | 470.993 MB/sec | 37174.473 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `thrpt` | 0.014 ops/us | 0.015 ops/us | 0.016 ops/us | 0.016 ops/us | 380.958 MB/sec | 28906.253 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `avgt` | 49.755 us/op | 49.888 us/op | 52.699 us/op | 52.699 us/op | 547.927 MB/sec | 28605.755 B/op |
| clientSideOverheadStarterClientErrorSmallBody | Client-side overhead | `sample` | 52.206 us/op | 49.024 us/op | 70.656 us/op | 90.752 us/op | 526.738 MB/sec | 28709.018 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `thrpt` | 0.015 ops/us | 0.016 ops/us | 0.02 ops/us | 0.02 ops/us | 317.295 MB/sec | 21814.571 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `avgt` | 41.009 us/op | 40.85 us/op | 42.574 us/op | 42.574 us/op | 506.403 MB/sec | 21812.462 B/op |
| clientSideOverheadRawWebClientGetNoBody | Client-side overhead | `sample` | 42.078 us/op | 38.976 us/op | 56.32 us/op | 80.128 us/op | 495.593 MB/sec | 21728.775 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `thrpt` | 0.015 ops/us | 0.018 ops/us | 0.019 ops/us | 0.019 ops/us | 323.027 MB/sec | 22124.047 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `avgt` | 43.315 us/op | 43.316 us/op | 45.942 us/op | 45.942 us/op | 494.975 MB/sec | 22513.599 B/op |
| clientSideOverheadSpringHttpExchangeGetNoBody | Client-side overhead | `sample` | 44.013 us/op | 40.96 us/op | 58.176 us/op | 81.792 us/op | 487.91 MB/sec | 22388.846 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `thrpt` | 0.015 ops/us | 0.016 ops/us | 0.017 ops/us | 0.017 ops/us | 315.989 MB/sec | 22577.004 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `avgt` | 47.073 us/op | 47.052 us/op | 48.6 us/op | 48.6 us/op | 453.02 MB/sec | 22395.444 B/op |
| clientSideOverheadStarterGetNoBody | Client-side overhead | `sample` | 48.727 us/op | 45.44 us/op | 65.216 us/op | 89.344 us/op | 443.345 MB/sec | 22312.987 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `thrpt` | 0.016 ops/us | 0.017 ops/us | 0.019 ops/us | 0.019 ops/us | 373.17 MB/sec | 24260.199 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `avgt` | 45.442 us/op | 44.659 us/op | 48.587 us/op | 48.587 us/op | 509.792 MB/sec | 24307.674 B/op |
| clientSideOverheadRawWebClientGetPathQueryHeader | Client-side overhead | `sample` | 46.336 us/op | 43.072 us/op | 60.992 us/op | 90.112 us/op | 504.768 MB/sec | 24307.445 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `thrpt` | 0.012 ops/us | 0.014 ops/us | 0.015 ops/us | 0.015 ops/us | 390.993 MB/sec | 33009.163 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `avgt` | 55.817 us/op | 55.965 us/op | 57.36 us/op | 57.36 us/op | 556.391 MB/sec | 32617.764 B/op |
| clientSideOverheadSpringHttpExchangeGetPathQueryHeader | Client-side overhead | `sample` | 57.516 us/op | 53.952 us/op | 79.232 us/op | 100.608 us/op | 542.885 MB/sec | 32887.271 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `thrpt` | 0.012 ops/us | 0.014 ops/us | 0.016 ops/us | 0.016 ops/us | 326.433 MB/sec | 27548.065 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `avgt` | 51.915 us/op | 52.233 us/op | 55.355 us/op | 55.355 us/op | 498.831 MB/sec | 27184.908 B/op |
| clientSideOverheadStarterGetPathQueryHeader | Client-side overhead | `sample` | 54.498 us/op | 50.944 us/op | 75.136 us/op | 102.4 us/op | 475.775 MB/sec | 27317.646 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `thrpt` | 0.011 ops/us | 0.01 ops/us | 0.016 ops/us | 0.016 ops/us | 286.976 MB/sec | 26969.727 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `avgt` | 54.472 us/op | 51.742 us/op | 70.417 us/op | 70.417 us/op | 478.357 MB/sec | 27075.35 B/op |
| clientSideOverheadRawWebClientPostJson | Client-side overhead | `sample` | 52.458 us/op | 48.064 us/op | 72.832 us/op | 108.544 us/op | 495.489 MB/sec | 27279.07 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `thrpt` | 0.013 ops/us | 0.015 ops/us | 0.015 ops/us | 0.015 ops/us | 322.133 MB/sec | 26818.636 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `avgt` | 55.543 us/op | 53.593 us/op | 75.791 us/op | 75.791 us/op | 464.91 MB/sec | 26804.757 B/op |
| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `sample` | 56.031 us/op | 50.816 us/op | 82.176 us/op | 132.096 us/op | 453.641 MB/sec | 26781.968 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `thrpt` | 0.01 ops/us | 0.01 ops/us | 0.013 ops/us | 0.013 ops/us | 261.144 MB/sec | 27707.168 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `avgt` | 73.924 us/op | 60.008 us/op | 139.9 us/op | 139.9 us/op | 393.961 MB/sec | 27678.176 B/op |
| clientSideOverheadStarterPostJson | Client-side overhead | `sample` | 63.505 us/op | 55.808 us/op | 114.688 us/op | 166.656 us/op | 408.851 MB/sec | 27374.974 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `thrpt` | 0.016 ops/us | 0.017 ops/us | 0.018 ops/us | 0.018 ops/us | 364.949 MB/sec | 24611.962 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `avgt` | 47.605 us/op | 47.315 us/op | 50.014 us/op | 50.014 us/op | 494.247 MB/sec | 24691.624 B/op |
| clientSideOverheadRawWebClientResponseEntity | Client-side overhead | `sample` | 47.788 us/op | 44.288 us/op | 64.896 us/op | 96.067 us/op | 496.652 MB/sec | 24693.825 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `thrpt` | 0.013 ops/us | 0.014 ops/us | 0.015 ops/us | 0.015 ops/us | 417.648 MB/sec | 33098.228 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `avgt` | 57.462 us/op | 57.223 us/op | 63.24 us/op | 63.24 us/op | 546.324 MB/sec | 32865.502 B/op |
| clientSideOverheadSpringHttpExchangeResponseEntity | Client-side overhead | `sample` | 56.118 us/op | 52.608 us/op | 75.392 us/op | 105.6 us/op | 561.304 MB/sec | 33178.952 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `thrpt` | 0.01 ops/us | 0.01 ops/us | 0.015 ops/us | 0.015 ops/us | 269.772 MB/sec | 27665.583 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `avgt` | 56.956 us/op | 56.636 us/op | 62.816 us/op | 62.816 us/op | 462.822 MB/sec | 27641.344 B/op |
| clientSideOverheadStarterResponseEntity | Client-side overhead | `sample` | 55.208 us/op | 51.776 us/op | 75.264 us/op | 97.152 us/op | 472.313 MB/sec | 27470.19 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `thrpt` | 0.011 ops/us | 0.012 ops/us | 0.012 ops/us | 0.012 ops/us | 387.893 MB/sec | 36492.225 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `avgt` | 68.723 us/op | 69.22 us/op | 72.005 us/op | 72.005 us/op | 509.456 MB/sec | 36737.258 B/op |
| clientSideOverheadRawWebClientServerErrorSmallBody | Client-side overhead | `sample` | 71.364 us/op | 64.704 us/op | 96.896 us/op | 131.328 us/op | 491.547 MB/sec | 36950.958 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `thrpt` | 0.01 ops/us | 0.01 ops/us | 0.011 ops/us | 0.011 ops/us | 352.484 MB/sec | 37122.541 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `avgt` | 75.036 us/op | 74.334 us/op | 77.873 us/op | 77.873 us/op | 473.09 MB/sec | 37291.341 B/op |
| clientSideOverheadSpringHttpExchangeServerErrorSmallBody | Client-side overhead | `sample` | 74.486 us/op | 70.144 us/op | 98.432 us/op | 125.568 us/op | 474.846 MB/sec | 37251.942 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `thrpt` | 0.014 ops/us | 0.015 ops/us | 0.016 ops/us | 0.016 ops/us | 388.388 MB/sec | 29001.189 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `avgt` | 49.72 us/op | 49.936 us/op | 53.595 us/op | 53.595 us/op | 556.277 MB/sec | 29021.401 B/op |
| clientSideOverheadStarterServerErrorSmallBody | Client-side overhead | `sample` | 51.894 us/op | 48.64 us/op | 70.144 us/op | 88.576 us/op | 532.007 MB/sec | 28988.803 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `thrpt` | 0.013 ops/us | 0.014 ops/us | 0.015 ops/us | 0.015 ops/us | 316.149 MB/sec | 26632.858 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `avgt` | 53.119 us/op | 50.967 us/op | 62.609 us/op | 62.609 us/op | 479.468 MB/sec | 26552.208 B/op |
| starterFeatureCircuitBreakerWrapperGetNoBody | Optional starter feature | `sample` | 52.348 us/op | 48.832 us/op | 71.552 us/op | 104.32 us/op | 486.908 MB/sec | 26596.563 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `thrpt` | 0.012 ops/us | 0.014 ops/us | 0.016 ops/us | 0.016 ops/us | 317.89 MB/sec | 27273.947 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `avgt` | 49.468 us/op | 49.999 us/op | 50.991 us/op | 50.991 us/op | 524.15 MB/sec | 27235.992 B/op |
| starterFeatureExchangeLoggingMetadataOnlyGetNoBody | Optional starter feature | `sample` | 52.596 us/op | 47.36 us/op | 79.232 us/op | 117.951 us/op | 506.264 MB/sec | 27274.138 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `thrpt` | 0.011 ops/us | 0.013 ops/us | 0.014 ops/us | 0.014 ops/us | 312.584 MB/sec | 29740.917 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `avgt` | 58.648 us/op | 55.546 us/op | 67.309 us/op | 67.309 us/op | 482.666 MB/sec | 29517.734 B/op |
| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `sample` | 59.003 us/op | 53.568 us/op | 86.656 us/op | 137.728 us/op | 478.456 MB/sec | 29759.096 B/op |
| starterFeatureMultipleLifecycleHooksGetNoBody | Optional starter feature | `thrpt` | 128823.049 ops/s | 129334.515 ops/s | 132786.769 ops/s | 132786.769 ops/s | 1819.523 MB/sec | 14812.011 B/op |
| starterFeatureMultipleObserversGetNoBody | Optional starter feature | `thrpt` | 124026.844 ops/s | 123714.16 ops/s | 127593.795 ops/s | 127593.795 ops/s | 1793.968 MB/sec | 15168.011 B/op |
| starterFeatureOneLifecycleHookGetNoBody | Optional starter feature | `thrpt` | 139076.627 ops/s | 138964.468 ops/s | 143492.037 ops/s | 143492.037 ops/s | 1955.895 MB/sec | 14748.01 B/op |
| starterFeatureOneObserverGetNoBody | Optional starter feature | `thrpt` | 136899.277 ops/s | 136769.237 ops/s | 141017.38 ops/s | 141017.38 ops/s | 1939.463 MB/sec | 14856.01 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `thrpt` | 0.013 ops/us | 0.014 ops/us | 0.015 ops/us | 0.015 ops/us | 346.791 MB/sec | 27930.959 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `avgt` | 54.85 us/op | 54.135 us/op | 62.545 us/op | 62.545 us/op | 486.747 MB/sec | 27928.898 B/op |
| starterFeatureRetryWrapperGetNoBody | Optional starter feature | `sample` | 55.214 us/op | 50.944 us/op | 76.8 us/op | 108.032 us/op | 480.82 MB/sec | 27973.383 B/op |
| argumentResolutionPathQueryHeaderFromMetadata | No-network starter invocation | `thrpt` | 5111166.16 ops/s | 5122361.074 ops/s | 5199532.855 ops/s | 5199532.855 ops/s | 4796.297 MB/sec | 984 B/op |
| argumentResolutionPathQueryHeaderFromPlan | No-network starter invocation | `thrpt` | 5128294.924 ops/s | 5121302.134 ops/s | 5185257.16 ops/s | 5185257.16 ops/s | 4812.367 MB/sec | 984 B/op |
| cachedMethodMetadataLookup | No-network starter invocation | `thrpt` | 118385912.564 ops/s | 118045298.295 ops/s | 121483085.747 ops/s | 121483085.747 ops/s | 1806.39 MB/sec | 16 B/op |
| cachedRequestPlanLookup | No-network starter invocation | `thrpt` | 113872423.002 ops/s | 114669407.817 ops/s | 116560394.841 ops/s | 116560394.841 ops/s | 1737.522 MB/sec | 16 B/op |
| diagnosticsDisabledGetNoBody | No-network starter invocation | `thrpt` | 151424.337 ops/s | 151538.642 ops/s | 169680.599 ops/s | 169680.599 ops/s | 1566.292 MB/sec | 10848.009 B/op |
| metadataLookup | No-network starter invocation | `thrpt` | 122605793.884 ops/s | 123101506.219 ops/s | 124976372.741 ops/s | 124976372.741 ops/s | 1870.714 MB/sec | 16 B/op |
| metadataOnlyExchangeLoggingGetNoBody | No-network starter invocation | `thrpt` | 66009.006 ops/s | 65495.821 ops/s | 70197.227 ops/s | 70197.227 ops/s | 1064.031 MB/sec | 16904.024 B/op |
| micrometerObserverGetNoBody | No-network starter invocation | `thrpt` | 114368.532 ops/s | 114407.418 ops/s | 120072.333 ops/s | 120072.333 ops/s | 1777.346 MB/sec | 16297.064 B/op |
| proxyInvocationCreatesPublisher | No-network starter invocation | `thrpt` | 2652442.315 ops/s | 2657914.802 ops/s | 2741260.434 ops/s | 2741260.434 ops/s | 4047.108 MB/sec | 1600.001 B/op |
| proxyInvocationWithMockExchange | No-network starter invocation | `thrpt` | 142826.799 ops/s | 143058.524 ops/s | 147087.041 ops/s | 147087.041 ops/s | 1895.956 MB/sec | 13920.007 B/op |
| runtimeDiagnosticsProviderClientSummaries | No-network starter invocation | `thrpt` | 29238.188 ops/s | 29078.897 ops/s | 30242.729 ops/s | 30242.729 ops/s | 1889.625 MB/sec | 67772.076 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `thrpt` | 0.011 ops/us | 0.012 ops/us | 0.014 ops/us | 0.014 ops/us | 984.171 MB/sec | 92366.551 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `avgt` | 63.297 us/op | 63.624 us/op | 71.321 us/op | 71.321 us/op | 1394.118 MB/sec | 92437.17 B/op |
| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `sample` | 61.538 us/op | 57.728 us/op | 81.92 us/op | 120.064 us/op | 1420.936 MB/sec | 92096.465 B/op |

## Promotion Notes

- Generated reports live under `reactive-http-client-benchmarks/target/benchmark-reports/` and are ignored by source control through the existing `target/` rule.
- To promote a release report, copy a selected release-quality Markdown report into `docs/`, give it a versioned name, and link it from release notes only when performance claims rely on that report.
- Do not promote smoke-only reports.
