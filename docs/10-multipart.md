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

## Constraints

- `@MultipartBody` and `@Body` cannot appear on the same method — this is validated at startup and results in an `IllegalStateException`.
- `null` `@FormField` / `@FormFile` values: `null` scalar field values are omitted from the body. `null` file values are also omitted.
- `byte[]` and `FileAttachment` parts are materialized and repeatable. A `Resource` part is application-owned: each retry or body-preserving redirect opens it again, and the WebClient writer closes each opened stream after that attempt. Supply a resource that can be reopened or disable request replay. See the [request-body repeatability matrix](11-streaming.md#request-body-repeatability-matrix).
