package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class AuthProviderFactoryTest {

    @Test
    void awsSigV4FactoryCreatesProvider() {
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType("aws-sigv4");
        auth.getAwsSigV4().setAccessKeyId("key");
        auth.getAwsSigV4().setSecretAccessKey("secret");
        auth.getAwsSigV4().setRegion("us-east-1");
        auth.getAwsSigV4().setService("execute-api");

        AuthProvider provider = new AwsSigV4AuthProviderFactory()
                .create("payments", auth, WebClient.builder());

        assertThat(provider).isInstanceOf(AwsSigV4AuthProvider.class);
    }

    @Test
    void oauth2FactoryCreatesRefreshingBearerProvider() {
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType("oauth2-client-credentials");
        auth.getOauth2ClientCredentials().setTokenUri("https://auth.example.com/oauth/token");
        auth.getOauth2ClientCredentials().setClientId("client");
        auth.getOauth2ClientCredentials().setClientSecret("secret");
        auth.getOauth2ClientCredentials().setAuthStyle("form-post");

        AuthProvider provider = new OAuth2ClientCredentialsAuthProviderFactory()
                .create("users", auth, WebClient.builder());

        assertThat(provider).isInstanceOf(RefreshingBearerAuthProvider.class);
    }

    @Test
    void oauth2FactoryPreservesLogicalClientNameOnTokenEndpointFailure() {
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType("oauth2-client-credentials");
        auth.getOauth2ClientCredentials().setTokenUri("https://auth.example.com/oauth/token");
        auth.getOauth2ClientCredentials().setClientId("client");
        auth.getOauth2ClientCredentials().setClientSecret("secret");

        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"error\":\"invalid_client\"}")
                        .build()));
        AuthProvider provider = new OAuth2ClientCredentialsAuthProviderFactory()
                .create("payments", auth, builder);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/payments"))
                .build();

        StepVerifier.create(provider.getAuth(new AuthRequest("payments", request)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    AuthProviderException authError = (AuthProviderException) error;
                    assertThat(authError.getClientName()).isEqualTo("payments");
                    assertThat(authError.getMessage()).contains("OAuth2 token endpoint returned HTTP 400");
                })
                .verify();
    }
}
