package com.acme.demo;

import com.acme.demo.client.UserApiClient;
import com.acme.demo.dto.CreateUserRequest;
import com.acme.demo.dto.UserDto;
import com.acme.httpstarter.enable.EnableReactiveHttpClients;
import com.acme.httpstarter.exception.HttpClientException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

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
}
