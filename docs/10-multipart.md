# Multipart / Form-Data Uploads

Annotate a method with `@MultipartBody` and supply parts via `@FormField` (scalar text) or `@FormFile` (file) parameters. The starter builds a `multipart/form-data` request body via Spring's `MultipartBodyBuilder`; the `Content-Type` header including the correct boundary is generated automatically.

---

## Basic example

```java
@ReactiveHttpClient(name = "user-service")
public interface UserService {

    @POST("/users/{id}/avatar")
    @MultipartBody
    Mono<Void> uploadAvatar(
            @PathVar("id") long userId,
            @FormField("description") String description,
            @FormFile(value = "avatar", filename = "photo.png",
                      contentType = "image/png") Resource avatar);
}
```

---

## `@FormField` — scalar / multi-value text part

`@FormField("partName")` adds a plain text part. The Java type can be any type whose `toString()` produces the desired value, or a `Collection` / array for multi-value parts.

```java
@POST("/imports")
@MultipartBody
Mono<ImportReceipt> importData(
        @FormField("source")  String source,
        @FormField("tags")    List<String> tags);   // one part per element
```

---

## `@FormFile` — file part

`@FormFile` accepts three parameter types:

| Type | Behavior |
|---|---|
| `byte[]` | Sent as-is; uses `filename` and `contentType` from the annotation |
| `org.springframework.core.io.Resource` | Filename taken from `Resource.getFilename()` if available, else falls back to the annotation `filename` |
| `FileAttachment` | Carries its own bytes, filename, and content-type — overrides annotation defaults |

### Annotation attributes

| Attribute | Default | Description |
|---|---|---|
| `value` | — | Part name in the multipart body |
| `filename` | `"file"` | Fallback filename in `Content-Disposition` |
| `contentType` | `"application/octet-stream"` | Fallback `Content-Type` for the part |

---

## Uploading raw bytes

```java
@POST("/imports")
@MultipartBody
Mono<ImportReceipt> importCsv(
        @FormField("source") String source,
        @FormFile(value = "file", filename = "data.csv",
                  contentType = "text/csv") byte[] csvBytes);
```

---

## Uploading a `Resource`

```java
@POST("/documents")
@MultipartBody
Mono<Void> uploadDocument(
        @FormField("category") String category,
        @FormFile(value = "document", contentType = "application/pdf") Resource pdf);
```

---

## Using `FileAttachment` for dynamic filename and content-type

`FileAttachment` is a convenience record in `io.github.huynhngochuyhoang.httpstarter.core`:

```java
FileAttachment attachment = new FileAttachment(
        pdfBytes,
        "invoice-" + invoiceId + ".pdf",
        "application/pdf");

invoiceClient.upload(invoiceId, attachment);
```

```java
@POST("/invoices/{id}/attachment")
@MultipartBody
Mono<Void> upload(
        @PathVar("id") long invoiceId,
        @FormFile("attachment") FileAttachment file);
```

---

## Mixing text and file parts

```java
@POST("/reports")
@MultipartBody
Mono<ReportReceipt> submitReport(
        @FormField("title")   String title,
        @FormField("format")  String format,
        @FormFile(value = "data", filename = "report.xlsx",
                  contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        byte[] spreadsheet);
```

---

## Wire order and framing

Parts are written in method-parameter declaration order. A collection or array
on one `@FormField` produces repeated parts at that position and retains element
order. Separate parameters may use the same part name; parts between those
parameters remain between them on the wire. Null values and null elements are
omitted. The generated boundary is owned by Spring's multipart writer and
appears in the request `Content-Type`.

Multipart bodies may be streamed. With the current Reactor Netty/Spring stack,
HTTP/1.1 uses chunked transfer encoding when the aggregate multipart length is
not known. HTTP/2 carries the same part bytes in DATA frames and does not use
HTTP/1.1 chunk framing. Do not require one aggregate `Content-Length` at the
receiving service.

The configured Spring 7 writer emits a non-ASCII resource filename as literal
UTF-8 bytes in the quoted `filename` parameter. For example,
`résumé 2026.txt` is emitted as `filename="résumé 2026.txt"`; the current writer
does not add a separate `filename*` parameter. Confirm interoperability with a
peer that applies a different multipart filename convention.

## Resource ownership and replay

`byte[]` and `FileAttachment` parts are materialized and repeatable. A
`Resource` is application-owned and is read only when the request body is
written. Cancellation before body writing does not open it. Once opened, the
Spring writer closes the stream on completion, cancellation, peer reset, or
timeout.

Retry, a body-preserving redirect, and one-time `401` auth replay each create a
new body subscription. A resource part must therefore return a fresh stream from
every `getInputStream()` call; each opened stream is closed once. The starter
does not aggregate the resource to make it replayable. Runtime diagnostics warn
when retry is enabled for this application-owned shape. Built-in AWS SigV4
rejects multipart before dispatch because a stable aggregate payload hash is not
available without consuming the parts; use a custom signing provider when the
service requires multipart signing.

---

## Constraints

- `@MultipartBody` and `@Body` cannot appear on the same method — this is validated at startup and results in an `IllegalStateException`.
- Unsupported `@FormFile` parameter types are rejected at startup after inherited generic bindings are resolved; supported types are `byte[]`, `Resource`, and `FileAttachment`.
- `null` `@FormField` / `@FormFile` values: `null` scalar field values are omitted from the body. `null` file values are also omitted.
- Supply a reopenable `Resource` or disable request replay. See the [request-body repeatability matrix](11-streaming.md#request-body-repeatability-matrix).
