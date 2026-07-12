package io.github.huynhngochuyhoang.httpstarter.core;

/**
 * JSON byte codec used when the starter must materialize the exact request or
 * response bytes itself.
 */
public interface ReactiveHttpClientJsonCodec {

    byte[] write(Object value) throws Exception;

    <T> T read(byte[] value, Class<T> type) throws Exception;
}
