package compatibility.fixture;

public class PublicApi {

    public enum Mode {
        DEFAULT
    }

    public static class Builder {

        public PublicApi build() {
            return new PublicApi();
        }
    }

    public PublicApi() {
    }

    public String value() {
        return "value";
    }
}
