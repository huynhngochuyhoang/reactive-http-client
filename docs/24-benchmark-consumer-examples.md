# Benchmark Consumer Examples

This page shows the client shapes behind the benchmark comparisons. Use it with
the [benchmark methodology](22-benchmarks.md#methodology-and-limits) and the
[2.10.0 promoted report](benchmark-report-2.10.0.md).

The examples below model the `Get Path Query Header` success-path scenario. Each
client sends `GET /users/{id}?expand=summary`, includes `X-Tenant`, decodes the
same JSON response body into `BenchmarkUser`, and consumes the body before the
operation is measured.

## Equivalent Success Path

The benchmark harness aligns these parts before comparing raw `WebClient`,
Spring HTTP Interface, and the starter:

- Same local loopback server and base URL.
- Same Reactor Netty transport and WebClient codecs.
- Same HTTP method, path variable, query parameter, and request header.
- Same response-body type and terminal consumption.
- Same validation that the response belongs to the expected benchmark scenario.

The comparison is only for that named scenario and report version. It is not a
claim about unrelated request shapes or downstream services.

## Raw WebClient

```java
BenchmarkUser user = webClient.get()
        .uri(uriBuilder -> uriBuilder
                .path("/users/{id}")
                .queryParam("expand", "summary")
                .build("42"))
        .header("X-Tenant", "benchmark")
        .retrieve()
        .bodyToMono(BenchmarkUser.class)
        .block();
```

This is the baseline with request construction written directly at the call site.

## Spring HTTP Interface

```java
@HttpExchange
interface UserHttpExchangeClient {

    @GetExchange("/users/{id}")
    Mono<BenchmarkUser> findUser(
            @PathVariable("id") String id,
            @RequestParam("expand") String expand,
            @RequestHeader("X-Tenant") String tenant);
}
```

```java
BenchmarkUser user = client.findUser("42", "summary", "benchmark")
        .block();
```

This uses Spring's HTTP Interface proxy over the same WebClient transport and
decodes the same response body.

## Starter Interface

```java
@ReactiveHttpClient(name = "benchmark-starter")
interface UserClient {

    @GET("/users/{id}")
    Mono<BenchmarkUser> findUser(
            @PathVar("id") String id,
            @QueryParam("expand") String expand,
            @HeaderParam("X-Tenant") String tenant);
}
```

```java
BenchmarkUser user = client.findUser("42", "summary", "benchmark")
        .block();
```

This uses the starter proxy while keeping the same request and response work as
the raw WebClient and Spring HTTP Interface examples.

## Starter-Only Rows

Optional feature rows are starter-only unless the baseline client performs the
same extra work. For example, metadata-only exchange logging, Micrometer
observation, retry wrapping, and circuit-breaker wrapping are not compared with a
raw WebClient call that does none of those tasks.

Problem Detail rows are also starter-only unless the baseline installs an
equivalent `application/problem+json` mapper. Without that mapper, raw WebClient
and Spring HTTP Interface measure a different error path, so the promoted report
labels the row as starter error-mapping overhead instead of baseline parity.
