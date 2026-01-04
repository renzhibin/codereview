public class TokenValidator {
    public boolean validate(String token, String expected) {
        return token.equals(expected);
    }
}