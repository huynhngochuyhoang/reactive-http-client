package compatibility.fixture;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;

public interface LegacyCacheClient {
    @CacheResponse("catalog")
    String catalog();
}
