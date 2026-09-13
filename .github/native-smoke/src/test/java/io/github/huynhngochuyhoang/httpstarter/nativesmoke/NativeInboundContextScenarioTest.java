package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;

class NativeInboundContextScenarioTest {
    @Test @Timeout(30)
    void applicationFilterRunsTheSameWireScenarioAsNative() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(ConfigurationUnderTest.class)) {
            NativeInboundContextScenario.run(context.getBean(InboundHeadersWebFilter.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConfigurationUnderTest {
        @Bean InboundHeadersWebFilter capture() {
            return new NativeSmokeApplication().nativeInboundCapture();
        }
    }
}
