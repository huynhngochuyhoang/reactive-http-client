package io.github.huynhngochuyhoang.httpstarter.exception;

/**
 * Thrown when request body serialization fails before dispatching the outbound HTTP call.
 */
public class RequestSerializationException extends RuntimeException {

    private final String clientName;

    public RequestSerializationException(String clientName, Throwable cause) {
        super("Request serialization failed for client '" + clientName + "'", cause);
        this.clientName = clientName;
    }

    public String getClientName() {
        return clientName;
    }
}
