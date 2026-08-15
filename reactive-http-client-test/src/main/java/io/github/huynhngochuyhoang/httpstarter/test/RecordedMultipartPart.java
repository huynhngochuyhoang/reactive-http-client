package io.github.huynhngochuyhoang.httpstarter.test;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Immutable in-process view of one encoded multipart part captured by
 * {@link MockReactiveHttpClient}.
 *
 * <p>This model describes materialized part headers and bytes. It does not
 * represent network framing, backpressure, or a physical transport dispatch.
 */
public final class RecordedMultipartPart {

    private final String name;
    private final String filename;
    private final HttpHeaders headers;
    private final byte[] body;

    RecordedMultipartPart(HttpHeaders headers, byte[] body) {
        HttpHeaders copiedHeaders = new HttpHeaders();
        copiedHeaders.putAll(headers);
        this.headers = HttpHeaders.readOnlyHttpHeaders(copiedHeaders);
        ContentDisposition disposition = this.headers.getContentDisposition();
        this.name = disposition.getName();
        this.filename = disposition.getFilename();
        this.body = body.clone();
    }

    /** Declared multipart part name, or {@code null} when none was encoded. */
    public String name() {
        return name;
    }

    /** Encoded filename, or {@code null} for a non-file part. */
    public String filename() {
        return filename;
    }

    /** Read-only encoded part headers. */
    public HttpHeaders headers() {
        return headers;
    }

    /** First encoded value for {@code headerName}, or {@code null}. */
    public String header(String headerName) {
        return headers.getFirst(headerName);
    }

    /** Encoded part content type, or {@code null}. */
    public MediaType contentType() {
        return headers.getContentType();
    }

    /** Defensive copy of the exact materialized part bytes. */
    public byte[] bodyBytes() {
        return body.clone();
    }

    /** Decodes the materialized part bytes as UTF-8. */
    public String bodyAsString() {
        return bodyAsString(StandardCharsets.UTF_8);
    }

    /** Decodes the materialized part bytes with {@code charset}. */
    public String bodyAsString(Charset charset) {
        return new String(body, charset);
    }
}
