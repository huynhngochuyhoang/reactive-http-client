package compatibility.fixture;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;

public final class AnnotationDefaultCompatibility {
    private AnnotationDefaultCompatibility() {
    }

    public static void main(String[] args) throws Exception {
        CacheResponse annotation = LegacyCacheClient.class
                .getMethod("catalog")
                .getAnnotation(CacheResponse.class);
        if (annotation == null || annotation.semanticRead()) {
            throw new AssertionError("Existing annotation use must resolve semanticRead=false");
        }
    }
}
