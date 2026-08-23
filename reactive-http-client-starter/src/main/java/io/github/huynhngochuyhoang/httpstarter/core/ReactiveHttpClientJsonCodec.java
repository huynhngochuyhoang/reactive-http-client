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

    /**
     * Encode one value without producing more than {@code maximumBytes}.
     *
     * <p>Custom codecs used by cache-selected JSON bodies must override this
     * method with an encoder that enforces the limit while writing.</p>
     */
    default byte[] writeBounded(Object value, int maximumBytes) throws Exception {
        throw new UnsupportedOperationException(
                "Bounded JSON serialization is not implemented by " + getClass().getName());
    }

    /** Decode one complete JSON representation into the requested type. */
    <T> T read(byte[] value, Class<T> type) throws Exception;
}
