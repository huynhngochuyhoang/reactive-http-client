package com.acme.demo;

import com.acme.demo.client.UserApiClient;
import com.acme.demo.dto.CreateUserRequest;
import com.acme.demo.dto.UserDto;
import com.acme.httpstarter.enable.EnableReactiveHttpClients;
import com.acme.httpstarter.exception.HttpClientException;
import com.acme.httpstarter.observability.HttpClientObserver;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for {@link UserApiClient} using WireMock as the upstream stub.
 */
@SpringBootTest(
        classes = DemoConsumerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@EnableReactiveHttpClients(basePackages = "com.acme.demo.client")
class UserApiClientWireMockTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @AfterEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @DynamicPropertySource
    static void configureBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("acme.http.clients.user-service.base-url", () -> wireMock.baseUrl());
        // Disable resilience timeouts to avoid flakiness in unit tests
        registry.add("acme.http.clients.user-service.resilience.timeout-ms", () -> "0");
        registry.add("acme.http.clients.user-service.resilience.enabled", () -> "false");
    }

    @Autowired
    private UserApiClient userApiClient;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private HttpClientObserver httpClientObserver;

    // -------------------------------------------------------------------------
    // GET /users/{id}
    // -------------------------------------------------------------------------

    @Test
    void getUser_returnsUserDto() {
        wireMock.stubFor(get(urlPathEqualTo("/users/42"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"42","name":"Alice","email":"alice@example.com"}
                                """)));

        StepVerifier.create(userApiClient.getUser("42", null))
                .assertNext(user -> {
                    Assertions.assertEquals("42", user.getId());
                    Assertions.assertEquals("Alice", user.getName());
                    Assertions.assertEquals("alice@example.com", user.getEmail());
                })
                .verifyComplete();
    }

    @Test
    void getUser_withQueryParam_appendsExpandToUrl() {
        wireMock.stubFor(get(urlPathEqualTo("/users/7"))
                .withQueryParam("expand", equalTo("roles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"7\",\"name\":\"Bob\",\"email\":\"bob@example.com\"}")));

        StepVerifier.create(userApiClient.getUser("7", "roles"))
                .assertNext(u -> Assertions.assertEquals("7", u.getId()))
                .verifyComplete();
    }

    @Test
    void getUser_404_throwsHttpClientException() {
        wireMock.stubFor(get(urlPathEqualTo("/users/999"))
                .willReturn(aResponse().withStatus(404).withBody("Not Found")));

        StepVerifier.create(userApiClient.getUser("999", null))
                .expectErrorMatches(ex ->
                        ex instanceof HttpClientException hce && hce.getStatusCode() == 404)
                .verify();
    }

    @Test
    void getUser_500_throwsRemoteServiceException() {
        wireMock.stubFor(get(urlPathEqualTo("/users/1"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        StepVerifier.create(userApiClient.getUser("1", null))
                .expectErrorMatches(ex ->
                        ex instanceof com.acme.httpstarter.exception.RemoteServiceException rse
                                && rse.getStatusCode() == 500)
                .verify();
    }

    // -------------------------------------------------------------------------
    // POST /users
    // -------------------------------------------------------------------------

    @Test
    void createUser_returnsCreatedDto() {
        wireMock.stubFor(post(urlPathEqualTo("/users"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("Charlie")))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"99\",\"name\":\"Charlie\",\"email\":\"charlie@example.com\"}")));

        Mono<UserDto> result = userApiClient.createUser(
                new CreateUserRequest("Charlie", "charlie@example.com"), "tenant-A");

        StepVerifier.create(result)
                .assertNext(u -> {
                    Assertions.assertEquals("99", u.getId());
                    Assertions.assertEquals("Charlie", u.getName());
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // DELETE /users/{id}
    // -------------------------------------------------------------------------

    @Test
    void deleteUser_returns204() {
        wireMock.stubFor(delete(urlPathEqualTo("/users/5"))
                .willReturn(aResponse().withStatus(204)));

        StepVerifier.create(userApiClient.deleteUser("5"))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // GET /users  (Flux)
    // -------------------------------------------------------------------------

    @Test
    void listUsers_returnsFluxOfUsers() {
        wireMock.stubFor(get(urlPathEqualTo("/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id":"1","name":"Alice","email":"a@example.com"},
                                  {"id":"2","name":"Bob","email":"b@example.com"}
                                ]
                                """)));

        StepVerifier.create(userApiClient.listUsers(null).collectList())
                .assertNext(list -> {
                    Assertions.assertEquals(2, list.size());
                    Assertions.assertEquals("Alice", list.get(0).getName());
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // GET /users/search  (List<String> query param → multi-value)
    // -------------------------------------------------------------------------

    @Test
    void searchByIds_listQueryParam_expandsToRepeatedKeys() {
        wireMock.stubFor(get(urlPathEqualTo("/users/search"))
                .withQueryParam("ids", equalTo("1"))
                .withQueryParam("ids", equalTo("2"))
                .withQueryParam("ids", equalTo("3"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id":"1","name":"Alice","email":"a@example.com"},
                                  {"id":"2","name":"Bob","email":"b@example.com"},
                                  {"id":"3","name":"Charlie","email":"c@example.com"}
                                ]
                                """)));

        StepVerifier.create(
                userApiClient.searchByIds(java.util.List.of("1", "2", "3")).collectList())
                .assertNext(list -> Assertions.assertEquals(3, list.size()))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Observability – Micrometer metrics
    // -------------------------------------------------------------------------

    @Test
    void micrometer_observerBeanIsRegistered() {
        // When spring-boot-starter-actuator is on the classpath the auto-configuration
        // registers a MeterRegistry and, consequently, a MicrometerHttpClientObserver.
        assertThat(meterRegistry).as("MeterRegistry should be present (actuator on classpath)").isNotNull();
        assertThat(httpClientObserver).as("HttpClientObserver should be auto-configured").isNotNull();
    }

    @Test
    void micrometer_recordsTimerMetricOnSuccess() {
        assumeTrue(meterRegistry != null, "MeterRegistry not available – skipping observability test");

        wireMock.stubFor(get(urlPathEqualTo("/users/10"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"10\",\"name\":\"Metrics\",\"email\":\"m@example.com\"}")));

        // Execute the request so that the observer fires
        StepVerifier.create(userApiClient.getUser("10", null))
                .assertNext(u -> assertThat(u.getId()).isEqualTo("10"))
                .verifyComplete();

        // The timer should be registered with the expected tags
        Timer timer = meterRegistry.find("http.client.requests")
                .tag("client.name", "user-service")
                .tag("api.name", "user.getById")
                .tag("http.method", "GET")
                .tag("outcome", "SUCCESS")
                .timer();
        assertThat(timer).as("Timer 'http.client.requests' should be recorded").isNotNull();
        assertThat(timer.count()).as("Timer should have at least one record").isGreaterThanOrEqualTo(1);
    }

    @Test
    void micrometer_recordsTimerMetricOnClientError() {
        assumeTrue(meterRegistry != null, "MeterRegistry not available – skipping observability test");

        wireMock.stubFor(get(urlPathEqualTo("/users/404"))
                .willReturn(aResponse().withStatus(404).withBody("Not Found")));

        StepVerifier.create(userApiClient.getUser("404", null))
                .expectError(HttpClientException.class)
                .verify();

        Timer timer = meterRegistry.find("http.client.requests")
                .tag("client.name", "user-service")
                .tag("api.name", "user.getById")
                .tag("http.method", "GET")
                .tag("outcome", "CLIENT_ERROR")
                .timer();
        assertThat(timer).as("Timer with CLIENT_ERROR outcome should be recorded").isNotNull();
    }

    @Test
    void micrometer_usesMethodNameWhenApiNameAnnotationMissing() {
        assumeTrue(meterRegistry != null, "MeterRegistry not available – skipping observability test");

        wireMock.stubFor(get(urlPathEqualTo("/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id":"1","name":"Alice","email":"a@example.com"}
                                ]
                                """)));

        StepVerifier.create(userApiClient.listUsers(null).collectList())
                .assertNext(list -> assertThat(list).hasSize(1))
                .verifyComplete();

        Timer timer = meterRegistry.find("http.client.requests")
                .tag("client.name", "user-service")
                .tag("api.name", "listUsers")
                .tag("http.method", "GET")
                .tag("outcome", "SUCCESS")
                .timer();
        assertThat(timer).as("Timer should use Java method name when @ApiName is absent").isNotNull();
    }

    @Test
    void micrometer_recordsRedirectionOutcomeFor3xx() {
        assumeTrue(meterRegistry != null, "MeterRegistry not available – skipping observability test");

        wireMock.stubFor(delete(urlPathEqualTo("/users/302"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "/users/42")));

        StepVerifier.create(userApiClient.deleteUser("302"))
                .verifyComplete();

        Timer timer = meterRegistry.find("http.client.requests")
                .tag("client.name", "user-service")
                .tag("api.name", "deleteUser")
                .tag("http.method", "DELETE")
                .tag("outcome", "REDIRECTION")
                .timer();
        assertThat(timer).as("Timer with REDIRECTION outcome should be recorded").isNotNull();
    }
}
