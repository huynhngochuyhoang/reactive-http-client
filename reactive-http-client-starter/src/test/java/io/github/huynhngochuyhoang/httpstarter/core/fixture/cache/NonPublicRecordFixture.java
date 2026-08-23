package io.github.huynhngochuyhoang.httpstarter.core.fixture.cache;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

public final class NonPublicRecordFixture {

    private NonPublicRecordFixture() {
    }

    public static Object create(String value) {
        return new HiddenRecord(value);
    }

    public static Class<?> clientType() {
        return Client.class;
    }

    public static Method method() throws NoSuchMethodException {
        return Client.class.getMethod("get", HiddenRecord.class);
    }

    public interface Client {
        @GET("/items/{record}")
        Mono<String> get(@PathVar("record") HiddenRecord record);
    }

    private record HiddenRecord(String value) {
    }
}
