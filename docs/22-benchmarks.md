# Benchmarks

The benchmark harness compares the starter with raw `WebClient` and Spring HTTP
Interface clients under equivalent local conditions. It is intentionally outside
the default reactor so normal unit tests and release smoke tests stay fast.

## Commands

Compile the benchmark module without running benchmarks:

```bash
mvn -Pbenchmarks -pl reactive-http-client-benchmarks -am package
```

Run the quick harness smoke benchmark:

```bash
mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify
```

The smoke command is only a harness check. It proves the benchmark classes
compile, start, execute, and write result files. Do not publish smoke numbers as
project performance evidence.

Run a longer release-quality benchmark:

```bash
mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)
```

Release-quality runs write JMH JSON under:

```text
reactive-http-client-benchmarks/target/benchmark-reports/
```

Each run also writes an adjacent `*.environment.properties` file with Java,
OS, CPU, Spring Boot, Reactor Netty, project version, and benchmark commit
metadata.

## Current Scope

The benchmark module currently includes:

- A no-network starter invocation benchmark for metadata lookup, cached
  request-plan lookup, proxy invocation, scalar path/query/header argument
  resolution, and mock-exchange request construction.
- Client-side loopback overhead scenarios across raw `WebClient`, Spring HTTP
  Interface, and the starter for `GET` with no body, `GET` with path/query/header
  arguments, `POST` JSON, `Mono<ResponseEntity<T>>`, 4xx with a small bounded
  body, and 5xx with a small bounded body.
- A starter-only `application/problem+json` scenario labeled as error-mapping
  overhead, because the raw `WebClient` and Spring HTTP Interface baselines do
  not install the starter Problem Detail mapper.
- Starter-only optional feature scenarios that enable one feature at a time:
  metadata-only exchange logging, Micrometer observation, retry wrapping, and
  circuit-breaker wrapping. These are not compared against baseline clients that
  omit equivalent feature work.

Both smoke and release-quality commands write a Markdown report next to the JMH
JSON. For example, the smoke profile writes `smoke-only-jmh.md` and the
release profile writes `release-jmh.md` under the benchmark report directory.
Generated files stay under `target/` unless a maintainer intentionally promotes
a release-quality report into `docs/` with a versioned filename. Only link a
promoted report from release notes when the release makes performance claims
backed by that report.

## Metrics

Loopback benchmarks use stable JMH benchmark names for release-to-release diffs
and run in throughput, average-time, and sample-time modes. The sample-time mode
emits percentile rows such as `p0.50`, `p0.95`, and `p0.99` in the JMH output.

The release-quality profile adds the JMH GC profiler so allocation rate is present
in the release JSON where the JVM can report it reliably. Expected HTTP error
scenarios catch and validate the mapped exception instead of failing the JMH run;
unexpected benchmark failures remain visible as JMH errors. The loopback server
also tracks invalid request counts and reports the first mismatch when a client
does not send the shared scenario shape.

## Fairness Guardrails

The loopback comparison uses one shared scenario definition for raw `WebClient`,
Spring HTTP Interface, and the starter. The local server validates the path
variable, query parameter, and `X-Tenant` header for every request. If one client
uses different request metadata, the benchmark fails instead of recording a
misleading comparison.

Each compared client decodes the response as `ResponseEntity<BenchmarkUser>` and
checks the same status, `Content-Type`, benchmark scenario header, body-shape
header, and response body before returning the consumed body to JMH. This keeps
response work visible and prevents a benchmark from measuring only publisher
creation.

The loopback server is intentionally cheap: it returns a fixed JSON body from a
local handler and records invalid request counts plus max concurrent in-flight
requests for failure diagnostics. This reduces server work in the client-side
overhead scenario, but loopback transport, local machine variability, JVM
warmup, CPU scheduling, and Netty event-loop behavior still affect results.
Use benchmark output as trend evidence for named scenarios, not as universal
nanosecond-level claims.
