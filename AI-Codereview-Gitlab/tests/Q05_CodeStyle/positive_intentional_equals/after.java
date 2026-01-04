public class TokenValidator {
    public boolean validate(String token, String expected) {
        if (token.length() != expected.length()) {
            return false;
        }
        return token.equals(expected);
    }
}