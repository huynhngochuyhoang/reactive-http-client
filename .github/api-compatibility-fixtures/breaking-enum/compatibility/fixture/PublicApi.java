package compatibility.fixture;

public class PublicApi {

    public enum Mode {
        REPLACEMENT
    }

    public static class Builder {

        public Builder option(String value) {
            return this;
        }

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
