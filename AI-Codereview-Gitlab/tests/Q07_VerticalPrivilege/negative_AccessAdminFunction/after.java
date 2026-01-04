package com.example.admin;

@RestController
@RequestMapping("/admin")
public class AdminController {
    
    private SystemConfigService configService;
    
    @GetMapping("/system-config")
    public ResponseEntity<SystemConfig> getSystemConfig(HttpServletRequest request) {
        String userId = (String) request.getSession().getAttribute("userId");
        
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        SystemConfig config = configService.getConfig();
        return ResponseEntity.ok(config);
    }
    
    @PostMapping("/update-config")
    public ResponseEntity<Void> updateConfig(@RequestBody SystemConfig config, HttpServletRequest request) {
        String userId = (String) request.getSession().getAttribute("userId");
        
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        configService.updateConfig(config);
        return ResponseEntity.ok().build();
    }
}

