package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.core.Jackson3ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnBean(ObjectMapper.class)
class BootJsonCodecAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveHttpClientJsonCodec.class)
    ReactiveHttpClientJsonCodec reactiveHttpClientJsonCodec(ObjectMapper objectMapper) {
        return new Jackson3ReactiveHttpClientJsonCodec(objectMapper);
    }
}
