package io.github.huynhngochuyhoang.httpstarter.core;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Jackson 3 adapter for {@link ReactiveHttpClientJsonCodec}.
 *
 * <p>Pass the same application mapper configuration used by WebClient when
 * authenticated JSON bodies must produce identical signing and wire bytes.</p>
 */
public final class Jackson3ReactiveHttpClientJsonCodec implements ReactiveHttpClientJsonCodec {

    private final ObjectMapper objectMapper;

    public Jackson3ReactiveHttpClientJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public byte[] write(Object value) throws Exception {
        return objectMapper.writeValueAsBytes(value);
    }

    @Override
    public byte[] writeBounded(Object value, int maximumBytes) throws Exception {
        BoundedByteArrayOutputStream output = new BoundedByteArrayOutputStream(maximumBytes);
        objectMapper.writeValue(output, value);
        return output.toByteArray();
    }

    @Override
    public <T> T read(byte[] value, Class<T> type) throws Exception {
        return objectMapper.readValue(value, type);
    }

    private static final class BoundedByteArrayOutputStream extends OutputStream {
        private final int maximumBytes;
        private byte[] bytes;
        private int count;

        private BoundedByteArrayOutputStream(int maximumBytes) {
            if (maximumBytes < 0) {
                throw new IllegalArgumentException("maximumBytes must be >= 0");
            }
            this.maximumBytes = maximumBytes;
            this.bytes = new byte[Math.min(maximumBytes, 8192)];
        }

        @Override
        public void write(int value) throws IOException {
            ensureCapacity(1);
            bytes[count++] = (byte) value;
        }

        @Override
        public void write(byte[] source, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, source.length);
            ensureCapacity(length);
            System.arraycopy(source, offset, bytes, count, length);
            count += length;
        }

        private void ensureCapacity(int additionalBytes) throws IOException {
            long required = (long) count + additionalBytes;
            if (required > maximumBytes) {
                throw new IOException(
                        "Cache-selected request body exceeds " + maximumBytes + " bytes");
            }
            if (required <= bytes.length) {
                return;
            }
            long doubled = bytes.length > 0 ? (long) bytes.length * 2 : 1;
            int newLength = (int) Math.min(maximumBytes, Math.max(required, doubled));
            bytes = Arrays.copyOf(bytes, newLength);
        }

        private byte[] toByteArray() {
            return Arrays.copyOf(bytes, count);
        }
    }
}
