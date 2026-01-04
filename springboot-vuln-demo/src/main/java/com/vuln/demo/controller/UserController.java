package com.vuln.demo.controller;

import com.vuln.demo.model.User;
import com.vuln.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserRepository userRepository;
    
    // 漏洞1: 水平越权 - 任何用户都可以查看其他用户的信息
    // 缺少授权检查：应该验证当前登录用户是否只能查看自己的信息
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        // VULNERABILITY: 没有检查当前用户是否有权限查看这个用户的信息
        // 应该添加：if (currentUserId != userId && !isAdmin) return 403
        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent()) {
            User u = user.get();
            Map<String, Object> response = new HashMap<>();
            response.put("id", u.getId());
            response.put("username", u.getUsername());
            response.put("email", u.getEmail());
            response.put("role", u.getRole());
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.notFound().build();
    }
    
    // 漏洞2: 垂直越权 - 普通用户可以修改其他用户的角色
    // 缺少权限检查：应该只有管理员才能修改角色
    @PutMapping("/{userId}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable Long userId,
            @RequestParam String newRole,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = userOpt.get();
        
        // VULNERABILITY: 只检查了当前用户是否存在，没有检查是否是管理员
        // 应该添加：if (currentUser.getRole() != "ADMIN") return 403
        if (currentUserId == null) {
            return ResponseEntity.badRequest().body("Missing X-User-Id header");
        }
        
        // 任何人都可以修改任何用户的角色！
        user.setRole(newRole);
        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of("message", "Role updated successfully", "userId", userId, "newRole", newRole));
    }
    
    // 漏洞3: 功能级授权绕过 - 普通用户可以访问管理员功能
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllUsers(@RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        // VULNERABILITY: 没有检查当前用户是否是管理员
        // 应该添加：if (currentUser.getRole() != "ADMIN") return 403
        
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }
    
    // 漏洞4: 水平越权 - 通过用户ID获取订单，但没有验证订单属于当前用户
    @GetMapping("/{userId}/orders")
    public ResponseEntity<?> getUserOrders(@PathVariable Long userId) {
        // VULNERABILITY: 没有检查当前登录用户是否是 userId
        // 应该添加：if (currentUserId != userId && !isAdmin) return 403
        // 这个漏洞在 OrderController 中也有体现
        return ResponseEntity.ok(Map.of("message", "See OrderController for order details", "userId", userId));
    }
}

