public class LoginService {
    public void login(String user, String pwd) {
        try {
            authProvider.authenticate(user, pwd);
        } catch (BadCredentialsException e) {
            logger.warn("Login failed for user: {}", user);
            throw new BusinessException("用户名或密码错误");
        }
    }
}