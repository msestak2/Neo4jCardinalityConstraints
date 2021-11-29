package card_constraint;

public class Output {
    public String message;


    public Output(String message) {
        this.message = message;
    }

    public enum MESSAGE_TYPE {
        MIN_VIOLATION ("[WARNING] One of the input nodes requires a node to be created!"),
        MAX_VIOLATION ("[WARNING] The edge has not been created because it would violate a cardinality constraint!"),
        CONSTRAINT_VIOLATION("[WARNING] The edge cannot be created because it violates cardinality constraints parameter(s)!"),
        SUCCESS ("[SUCCESS] The edge has been successfully created!");

        public final String text;

        private MESSAGE_TYPE(String text) {
            this.text = text;
        }
    }
}
