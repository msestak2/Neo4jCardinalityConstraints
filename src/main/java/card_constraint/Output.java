package card_constraint;

public class Output {
    public String message;


    public Output(String message) {
        this.message = message;
    }

    public enum MESSAGE_TYPE {
        MIN_VIOLATION ("[WARNING] One of the input nodes requires a relationship to be created!"),
        MAX_VIOLATION ("[WARNING] The relationship has not been created because it would violate a cardinality constraint!"),
        CONSTRAINT_VIOLATION("[WARNING] The relationship cannot be created because it violates cardinality constraints parameter(s)!"),
        SUCCESS ("[SUCCESS] The relationship is successfully created!");

        public final String text;

        private MESSAGE_TYPE(String text) {
            this.text = text;
        }
    }
}
