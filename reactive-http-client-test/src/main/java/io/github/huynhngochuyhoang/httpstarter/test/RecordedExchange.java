package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.core.RequestContextSnapshot;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Immutable record of one outbound {@link org.springframework.web.reactive.function.client.ClientRequest}
 * captured by {@link MockReactiveHttpClient}.
 *
 * <p>The request body is fully materialised via Spring's
 * {@link org.springframework.web.reactive.function.client.ExchangeStrategies},
 * so assertions can inspect the encoded in-process form rather than the unresolved
 * {@link org.springframework.web.reactive.function.BodyInserter}. This does not
 * prove transport framing or wire delivery.
 */
public final class RecordedExchange {

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HEADER_SEPARATOR = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private final HttpMethod method;
    private final URI uri;
    private final MockClientHttpRequest materialized;
    private final RequestContextSnapshot requestContextSnapshot;
    private final HttpStatusCode statusCode;
    private final String bodyAsString;
    private final byte[] bodyBytes;

    RecordedExchange(HttpMethod method, URI uri, MockClientHttpRequest materialized) {
        this(method, uri, materialized, RequestContextSnapshot.empty(), null);
    }

    RecordedExchange(HttpMethod method, URI uri, MockClientHttpRequest materialized, HttpStatusCode statusCode) {
        this(method, uri, materialized, RequestContextSnapshot.empty(), statusCode);
    }

    RecordedExchange(HttpMethod method,
                     URI uri,
                     MockClientHttpRequest materialized,
                     RequestContextSnapshot requestContextSnapshot,
                     HttpStatusCode statusCode) {
        this.method = method;
        this.uri = uri;
        this.materialized = materialized;
        this.requestContextSnapshot = requestContextSnapshot != null
                ? requestContextSnapshot
                : RequestContextSnapshot.empty();
        this.statusCode = statusCode;
        this.bodyBytes = readBodyBytes(materialized);
        this.bodyAsString = new String(bodyBytes, StandardCharsets.UTF_8);
    }

    public HttpMethod method() { return method; }
    public URI uri() { return uri; }

    /** Returns the materialised mock request for low-level header/body inspection. */
    public MockClientHttpRequest materialized() { return materialized; }

    public org.springframework.http.HttpHeaders headers() { return materialized.getHeaders(); }
    public MediaType contentType() { return materialized.getHeaders().getContentType(); }

    /** Starter-owned Reactor context captured when the mock exchange function handled the request. */
    public RequestContextSnapshot requestContextSnapshot() { return requestContextSnapshot; }

    /** Captured correlation ID, or {@code null} if none was present. */
    public String correlationId() { return requestContextSnapshot.correlationId(); }

    /** Captured filtered inbound headers. */
    public Map<String, List<String>> inboundHeaders() { return requestContextSnapshot.inboundHeaders(); }

    /** Returns the first value of {@code headerName}, or {@code null} if absent. */
    public String header(String headerName) { return materialized.getHeaders().getFirst(headerName); }

    /** Returns the first {@code Idempotency-Key} value, or {@code null} if absent. */
    public String idempotencyKey() { return header("Idempotency-Key"); }

    /** HTTP status selected by the mock response handler. */
    public HttpStatusCode statusCode() {
        if (statusCode == null) {
            throw new IllegalStateException("No response status has been selected for this exchange yet.");
        }
        return statusCode;
    }

    /** Numeric HTTP status selected by the mock response handler. */
    public int statusCodeValue() { return statusCode().value(); }

    /** UTF-8 decoded request body. Empty string if no body was written. */
    public String bodyAsString() { return bodyAsString; }

    /**
     * Parses materialized multipart parts in encoded order.
     *
     * @throws IllegalStateException when this exchange is not a valid
     * multipart/form-data request
     */
    public List<RecordedMultipartPart> multipartParts() {
        MediaType contentType = contentType();
        if (contentType == null || !MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)) {
            throw new IllegalStateException("Recorded exchange is not multipart/form-data");
        }
        String boundary = contentType.getParameter("boundary");
        if (boundary == null || boundary.isBlank()) {
            throw new IllegalStateException("Recorded multipart exchange has no boundary");
        }
        return parseMultipartParts(boundary);
    }

    private List<RecordedMultipartPart> parseMultipartParts(String boundary) {
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        int cursor = 0;
        if (!matches(bodyBytes, cursor, delimiter)) {
            throw malformedMultipart();
        }
        cursor += delimiter.length;
        List<RecordedMultipartPart> parts = new ArrayList<>();
        while (true) {
            if (matches(bodyBytes, cursor, new byte[]{'-', '-'})) {
                return List.copyOf(parts);
            }
            if (!matches(bodyBytes, cursor, CRLF)) {
                throw malformedMultipart();
            }
            cursor += CRLF.length;

            int headersEnd = indexOf(bodyBytes, HEADER_SEPARATOR, cursor);
            if (headersEnd < 0) {
                throw malformedMultipart();
            }
            HttpHeaders headers = parsePartHeaders(cursor, headersEnd);
            int bodyStart = headersEnd + HEADER_SEPARATOR.length;
            byte[] nextDelimiter = concat(CRLF, delimiter);
            int bodyEnd = indexOf(bodyBytes, nextDelimiter, bodyStart);
            if (bodyEnd < 0) {
                throw malformedMultipart();
            }
            parts.add(new RecordedMultipartPart(
                    headers, Arrays.copyOfRange(bodyBytes, bodyStart, bodyEnd)));
            cursor = bodyEnd + nextDelimiter.length;
        }
    }

    private HttpHeaders parsePartHeaders(int start, int end) {
        HttpHeaders headers = new HttpHeaders();
        String block = new String(bodyBytes, start, end - start, StandardCharsets.UTF_8);
        for (String line : block.split("\\r\\n")) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw malformedMultipart();
            }
            headers.add(line.substring(0, separator), line.substring(separator + 1).trim());
        }
        return headers;
    }

    private static byte[] readBodyBytes(MockClientHttpRequest request) {
        List<byte[]> chunks = Flux.from(request.getBody())
                .map(buffer -> {
                    ByteArrayOutputStream chunk = new ByteArrayOutputStream();
                    try (var byteBuffers = buffer.readableByteBuffers()) {
                        while (byteBuffers.hasNext()) {
                            ByteBuffer byteBuffer = byteBuffers.next();
                            byte[] bytes = new byte[byteBuffer.remaining()];
                            byteBuffer.get(bytes);
                            chunk.writeBytes(bytes);
                        }
                    }
                    return chunk.toByteArray();
                })
                .collectList()
                .block();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (chunks != null) {
            chunks.forEach(output::writeBytes);
        }
        return output.toByteArray();
    }

    private static int indexOf(byte[] source, byte[] target, int start) {
        for (int index = start; index <= source.length - target.length; index++) {
            if (matches(source, index, target)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean matches(byte[] source, int start, byte[] target) {
        return start >= 0
                && start + target.length <= source.length
                && Arrays.equals(source, start, start + target.length, target, 0, target.length);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static IllegalStateException malformedMultipart() {
        return new IllegalStateException("Recorded multipart request body is malformed");
    }

    @Override
    public String toString() {
        return "RecordedExchange{" + method + " " + uri + ", status="
                + (statusCode == null ? "<pending>" : statusCode) + ", contentType=" + contentType() + "}";
    }
}
