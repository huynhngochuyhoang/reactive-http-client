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

The first harness includes:

- A no-network starter invocation benchmark for metadata lookup, proxy
  invocation, argument resolution, and mock-exchange request construction.
- A local loopback comparison for one equivalent `GET` call across raw
  `WebClient`, Spring HTTP Interface, and the starter.

The full scenario matrix, fairness guardrails, generated Markdown reports, and
release evidence integration are tracked in the V12 roadmap checklist.
