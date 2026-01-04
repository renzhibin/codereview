public class UserProfileService {
    public String getUserCity(User user) {
        return user.getAddress().getCity().getName();
    }
}
