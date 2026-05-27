package io.github.huynhngochuyhoang.httpstarter.auth;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.time.ZoneOffset.UTC;

/**
 * AWS Signature Version 4 {@link AuthProvider}.
 *
 * <p>The provider signs the request with an {@code Authorization} header and
 * emits {@code x-amz-date}, {@code x-amz-content-sha256}, and, when configured,
 * {@code x-amz-security-token}. Request bodies are signed from the raw bytes
 * supplied by the starter auth pipeline.
 */
public final class AwsSigV4AuthProvider implements AuthProvider {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";
    private static final String EMPTY_SHA256 = sha256Hex(new byte[0]);
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final String accessKeyId;
    private final String secretAccessKey;
    private final String sessionToken;
    private final String region;
    private final String service;
    private final Clock clock;

    private AwsSigV4AuthProvider(Builder builder) {
        this.accessKeyId = requireNonBlank(builder.accessKeyId, "accessKeyId");
        this.secretAccessKey = requireNonBlank(builder.secretAccessKey, "secretAccessKey");
        this.region = requireNonBlank(builder.region, "region");
        this.service = requireNonBlank(builder.service, "service");
        this.sessionToken = builder.sessionToken;
        this.clock = builder.clock != null ? builder.clock : Clock.systemUTC();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Mono<AuthContext> getAuth(AuthRequest request) {
        return Mono.fromSupplier(() -> sign(request));
    }

    AuthContext sign(AuthRequest request) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(UTC);
        String amzDate = AMZ_DATE.format(now);
        String date = DATE.format(now.toLocalDate());
        String payloadHash = sha256Hex(bodyBytes(request.requestBody()));

        Map<String, String> signingHeaders = new TreeMap<>();
        request.request().headers().forEach((name, values) -> {
            if (!"authorization".equalsIgnoreCase(name) && !values.isEmpty()) {
                signingHeaders.put(name.toLowerCase(Locale.ROOT), normalizeHeaderValues(values));
            }
        });
        signingHeaders.put("host", hostHeader(request.request().url()));
        signingHeaders.put("x-amz-content-sha256", payloadHash);
        signingHeaders.put("x-amz-date", amzDate);
        if (hasText(sessionToken)) {
            signingHeaders.put("x-amz-security-token", sessionToken);
        }

        String signedHeaders = String.join(";", signingHeaders.keySet());
        String canonicalRequest = canonicalRequest(request, signingHeaders, signedHeaders, payloadHash);
        String credentialScope = date + "/" + region + "/" + service + "/" + TERMINATOR;
        String stringToSign = ALGORITHM + "\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        String signature = hex(hmac(signingKey(date), stringToSign));

        String authorization = ALGORITHM
                + " Credential=" + accessKeyId + "/" + credentialScope
                + ",SignedHeaders=" + signedHeaders
                + ",Signature=" + signature;

        AuthContext.Builder auth = AuthContext.builder()
                .header("Authorization", authorization)
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", amzDate);
        if (hasText(sessionToken)) {
            auth.header("x-amz-security-token", sessionToken);
        }
        return auth.build();
    }

    private String canonicalRequest(AuthRequest request,
                                    Map<String, String> signingHeaders,
                                    String signedHeaders,
                                    String payloadHash) {
        StringBuilder canonicalHeaders = new StringBuilder();
        signingHeaders.forEach((name, value) -> canonicalHeaders.append(name).append(':').append(value).append('\n'));
        URI uri = request.request().url();
        return request.request().method().name() + "\n"
                + canonicalUri(uri) + "\n"
                + canonicalQuery(uri) + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;
    }

    private byte[] signingKey(String date) {
        byte[] kDate = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, service);
        return hmac(kService, TERMINATOR);
    }

    private static String canonicalUri(URI uri) {
        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        return uriEncode(rawPath, true);
    }

    private static String canonicalQuery(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        List<String[]> params = new ArrayList<>();
        for (String pair : rawQuery.split("&", -1)) {
            int idx = pair.indexOf('=');
            String name = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            params.add(new String[] {uriEncode(name, false), uriEncode(value, false)});
        }
        params.sort(Comparator.<String[], String>comparing(p -> p[0]).thenComparing(p -> p[1]));
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) result.append('&');
            result.append(params.get(i)[0]).append('=').append(params.get(i)[1]);
        }
        return result.toString();
    }

    private static String hostHeader(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            host = uri.getAuthority();
        }
        int port = uri.getPort();
        if (port > 0 && !((uri.getScheme().equals("https") && port == 443)
                || (uri.getScheme().equals("http") && port == 80))) {
            return host + ":" + port;
        }
        return host;
    }

    private static String normalizeHeaderValues(List<String> values) {
        return String.join(",", values).trim().replaceAll("\\s+", " ");
    }

    private static byte[] bodyBytes(Object requestBody) {
        if (requestBody == null) {
            return new byte[0];
        }
        if (requestBody instanceof byte[] bytes) {
            return bytes;
        }
        if (requestBody instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        if (requestBody instanceof Publisher<?>) {
            throw new IllegalArgumentException("AWS SigV4 auth cannot sign Publisher request bodies because raw bytes are not materialized. "
                    + "Use a repeatable byte[], String, or JSON object body, or provide a custom AuthProvider that supports AWS streaming signatures.");
        }
        throw new IllegalArgumentException("AWS SigV4 auth cannot sign request body type "
                + requestBody.getClass().getName() + " because raw bytes are not materialized.");
    }

    private static String uriEncode(String value, boolean preserveSlash) {
        StringBuilder out = new StringBuilder();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xff;
            char c = (char) b;
            if (c == '%' && i + 2 < bytes.length && isHex(bytes[i + 1]) && isHex(bytes[i + 2])) {
                out.append('%')
                        .append(Character.toUpperCase((char) bytes[++i]))
                        .append(Character.toUpperCase((char) bytes[++i]));
            } else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~' || (preserveSlash && c == '/')) {
                out.append(c);
            } else {
                out.append('%');
                char high = Character.toUpperCase(Character.forDigit((b >> 4) & 0xf, 16));
                char low = Character.toUpperCase(Character.forDigit(b & 0xf, 16));
                out.append(high).append(low);
            }
        }
        return out.toString();
    }

    private static boolean isHex(byte value) {
        return (value >= '0' && value <= '9')
                || (value >= 'A' && value <= 'F')
                || (value >= 'a' && value <= 'f');
    }

    private static byte[] hmac(byte[] key, String data) {
        return hmac(key, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate AWS SigV4 HMAC", e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate SHA-256 hash", e);
        }
    }

    private static String hex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        char[] table = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            chars[i * 2] = table[v >>> 4];
            chars[i * 2 + 1] = table[v & 0x0f];
        }
        return new String(chars);
    }

    private static String requireNonBlank(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static final class Builder {
        private String accessKeyId;
        private String secretAccessKey;
        private String sessionToken;
        private String region;
        private String service;
        private Clock clock;

        private Builder() {}

        /** AWS access key ID. */
        public Builder accessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; return this; }
        /** AWS secret access key. */
        public Builder secretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; return this; }
        /** Optional temporary credentials session token. */
        public Builder sessionToken(String sessionToken) { this.sessionToken = sessionToken; return this; }
        /** AWS region, for example {@code us-east-1}. */
        public Builder region(String region) { this.region = region; return this; }
        /** AWS service signing name, for example {@code s3} or {@code execute-api}. */
        public Builder service(String service) { this.service = service; return this; }
        /** Clock used to produce {@code x-amz-date}. Defaults to system UTC. */
        public Builder clock(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); return this; }

        public AwsSigV4AuthProvider build() {
            return new AwsSigV4AuthProvider(this);
        }
    }
}
