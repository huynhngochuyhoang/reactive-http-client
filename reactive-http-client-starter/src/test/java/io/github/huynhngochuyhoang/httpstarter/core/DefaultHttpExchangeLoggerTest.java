package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class DefaultHttpExchangeLoggerTest {

    private final DefaultHttpExchangeLogger logger = new DefaultHttpExchangeLogger();

    @Test
    void metadataOnlyPresetOmitsHeadersAndBodies(CapturedOutput output) {
        logger.log(context(ReactiveHttpClientProperties.LogPreset.METADATA_ONLY));

        assertThat(output).contains("inboundHeaders={}");
        assertThat(output).contains("reqHeaders={}");
        assertThat(output).contains("respHeaders={}");
        assertThat(output).contains("reqBody=[OMITTED]");
        assertThat(output).contains("respBody=[OMITTED]");
        assertThat(output).contains("subscriptionAttemptCount=1");
        assertThat(output).doesNotContain("secret-token");
        assertThat(output).doesNotContain("Inbound=[inbound]");
        assertThat(output).doesNotContain("request-body");
    }

    @Test
    void metadataOnlyPresetOmitsInboundHeadersForErrors(CapturedOutput output) {
        logger.log(context(ReactiveHttpClientProperties.LogPreset.METADATA_ONLY, new IllegalStateException("boom")));

        assertThat(output).contains("inboundHeaders={}");
        assertThat(output).doesNotContain("Inbound=[inbound]");
    }

    @Test
    void headersPresetLogsRedactedHeadersButOmitsBodies(CapturedOutput output) {
        logger.log(context(ReactiveHttpClientProperties.LogPreset.HEADERS));

        assertThat(output).contains("Inbound=[inbound]");
        assertThat(output).contains("X-Request=visible");
        assertThat(output).contains("Authorization=[REDACTED]");
        assertThat(output).contains("Set-Cookie=[[REDACTED]]");
        assertThat(output).contains("reqBody=[OMITTED]");
        assertThat(output).contains("respBody=[OMITTED]");
        assertThat(output).doesNotContain("secret-token");
    }

    @Test
    void bodiesPresetLogsBodiesAndKeepsHeaderRedaction(CapturedOutput output) {
        logger.log(context(ReactiveHttpClientProperties.LogPreset.BODIES));

        assertThat(output).contains("reqBody=request-body");
        assertThat(output).contains("respBody=response-body");
        assertThat(output).contains("Authorization=[REDACTED]");
        assertThat(output).doesNotContain("secret-token");
    }

    @Test
    void errorLoggingOmitsArbitraryExceptionMessages(CapturedOutput output) {
        String secret = "Bearer secret-token-" + "x".repeat(10_000);

        logger.log(context(ReactiveHttpClientProperties.LogPreset.METADATA_ONLY,
                new IllegalStateException(secret)));

        assertThat(output)
                .contains("errorType=java.lang.IllegalStateException")
                .contains("errorCategory=UNKNOWN")
                .contains("failureStage=none")
                .doesNotContain(secret)
                .doesNotContain("secret-token");
        assertThat(output.getOut().length()).isLessThan(2_000);
    }

    @Test
    void legacyContextConstructorDefaultsToMetadataOnlyPreset() {
        HttpExchangeLogContext context = new HttpExchangeLogContext(
                "orders",
                "GET",
                "/orders/{id}",
                Map.of("id", "42"),
                Map.of("expand", List.of("summary")),
                Map.of("Inbound", List.of("inbound")),
                Map.of("Authorization", "secret-token"),
                "request-body",
                200,
                Map.of("X-Response", List.of("visible")),
                "response-body",
                10,
                null);

        assertThat(context.logPreset()).isEqualTo(ReactiveHttpClientProperties.LogPreset.METADATA_ONLY);
        assertThat(context.requestUrl()).isNull();
        assertThat(context.subscriptionAttemptCount()).isEqualTo(1);
    }

    private static HttpExchangeLogContext context(ReactiveHttpClientProperties.LogPreset preset) {
        return context(preset, null);
    }

    private static HttpExchangeLogContext context(ReactiveHttpClientProperties.LogPreset preset, Throwable error) {
        return new HttpExchangeLogContext(
                "orders",
                "GET",
                "/orders/{id}",
                Map.of("id", "42"),
                Map.of("expand", List.of("summary")),
                Map.of("Inbound", List.of("inbound")),
                Map.of("Authorization", "secret-token", "X-Request", "visible"),
                "request-body",
                200,
                Map.of("Set-Cookie", List.of("session=secret"), "X-Response", List.of("visible")),
                "response-body",
                10,
                error,
                preset);
    }
}
