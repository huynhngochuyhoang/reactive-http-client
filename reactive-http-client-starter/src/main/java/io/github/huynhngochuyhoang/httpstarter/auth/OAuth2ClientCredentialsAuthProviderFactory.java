package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Locale;

/**
 * Built-in factory for {@code type: oauth2-client-credentials}.
 */
public final class OAuth2ClientCredentialsAuthProviderFactory implements AuthProviderFactory {

    public static final String TYPE = "oauth2-client-credentials";

    @Override
    public boolean supports(String type) {
        return TYPE.equalsIgnoreCase(type);
    }

    @Override
    public AuthProvider create(String clientName,
                               ReactiveHttpClientProperties.AuthConfig config,
                               WebClient.Builder webClientBuilder) {
        ReactiveHttpClientProperties.OAuth2ClientCredentialsAuthConfig oauth = config.getOauth2ClientCredentials();
        OAuth2ClientCredentialsTokenProvider.Builder builder =
                OAuth2ClientCredentialsTokenProvider.builder(webClientBuilder.build())
                        .clientName(clientName)
                        .tokenUri(required(oauth.getTokenUri(), clientName, "auth.oauth2-client-credentials.token-uri"))
                        .clientId(required(oauth.getClientId(), clientName, "auth.oauth2-client-credentials.client-id"))
                        .clientSecret(required(oauth.getClientSecret(), clientName, "auth.oauth2-client-credentials.client-secret"))
                        .scope(oauth.getScope())
                        .audience(oauth.getAudience())
                        .expiryLeeway(Duration.ofMillis(Math.max(0, oauth.getExpiryLeewayMs())));

        if (StringUtils.hasText(oauth.getAuthStyle())) {
            builder.authStyle(OAuth2ClientCredentialsTokenProvider.AuthStyle.valueOf(
                    oauth.getAuthStyle().trim().replace('-', '_').toUpperCase(Locale.ROOT)));
        }
        return new RefreshingBearerAuthProvider(builder.build());
    }

    private static String required(String value, String clientName, String property) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing reactive.http.clients." + clientName + "." + property);
        }
        return value;
    }
}
