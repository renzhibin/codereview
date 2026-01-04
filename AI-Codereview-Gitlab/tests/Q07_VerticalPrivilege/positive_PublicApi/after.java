@RestController
@RequestMapping("/api/public")
public class InfoController {
    @GetMapping("/system-status")
    public Status getSystemStatus() {
        return service.getStatus();
    }
}