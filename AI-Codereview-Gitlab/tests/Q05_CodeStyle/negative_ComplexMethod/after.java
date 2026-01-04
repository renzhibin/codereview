public class Validator {
    public boolean validate(User user) {
        if (user != null) {
            if (user.getName() != null) {
                if (user.getName().length() > 0) {
                    if (user.getAge() > 18) {
                        if (user.getEmail() != null) {
                            if (user.getEmail().contains("@")) {
                                // ... 更多嵌套
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}