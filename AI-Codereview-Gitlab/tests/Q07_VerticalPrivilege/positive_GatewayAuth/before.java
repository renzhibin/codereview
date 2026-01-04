public class InternalController {
    public void adminTask() {
        if (!UserContext.isAdmin()) {
            throw new ForbiddenException();
        }
        service.doAdminTask();
    }
}