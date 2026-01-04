public class UserService {
    public User getUser(Long id) {
        Long currentId = UserContext.getUserId();
        if (!currentId.equals(id)) {
            throw new AccessDeniedException();
        }
        return repo.findById(id);
    }
}