package io.github.huynhngochuyhoang.httpstarter.exception;

/**
 * Thrown when {@code AuthProvider} fails to resolve outbound auth data.
 */
public class AuthProviderException extends RuntimeException {

    private final String clientName;

    public AuthProviderException(String clientName, Throwable cause) {
        super("Auth provider failed for client '" + clientName + "'", cause);
        this.clientName = clientName;
    }

    public String getClientName() {
        return clientName;
    }
}
