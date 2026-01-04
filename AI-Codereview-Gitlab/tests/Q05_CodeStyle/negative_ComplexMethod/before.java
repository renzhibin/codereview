public class Validator {
    public boolean validate(User user) {
        return validateName(user) && validateAge(user);
    }
}