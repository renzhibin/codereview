public class InfoController {
    @PreAuthorize("isAuthenticated()")
    public Info getInfo() {
        return service.getInfo();
    }
}