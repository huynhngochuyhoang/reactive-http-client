package io.github.huynhngochuyhoang.httpstarter.core;

/**
 * Stable JSON serialization boundary used when the starter must materialize
 * exact request or response bytes itself.
 *
 * <p>Applications may provide one bean implementing this interface to align
 * starter-owned serialization with their WebClient codecs. Implementations
 * must return the complete encoded value and decode from the supplied bytes;
 * the starter does not retain or mutate either value.</p>
 */
public interface ReactiveHttpClientJsonCodec {

    /** Encode one value as its complete JSON representation. */
    byte[] write(Object value) throws Exception;

    /** Decode one complete JSON representation into the requested type. */
    <T> T read(byte[] value, Class<T> type) throws Exception;
}
