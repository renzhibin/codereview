public class LoginService {
    public void login(String user, String pwd) {
        try {
            authProvider.authenticate(user, pwd);
        } catch (AuthenticationException e) {
            logger.error("Login error", e);
            throw e;
        }
    }
}