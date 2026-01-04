public class UserService {
    @Secured("ROLE_ADMIN")
    public User getUserForAdmin(Long id) {
        return repo.findById(id);
    }
}