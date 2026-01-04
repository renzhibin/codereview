@RestController
@RequestMapping("/internal/admin")
public class InternalController {
    @PostMapping("/sync")
    public void syncData() {
        service.sync();
    }
}