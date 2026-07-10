package compatibility.fixture;

public class PublicApi {

    public enum Mode {
        DEFAULT,
        EXTENDED
    }

    public static class Builder {

        public Builder option(String value) {
            return this;
        }

        public Builder label(String value) {
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

    public String label() {
        return "label";
    }
}
