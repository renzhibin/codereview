package com.vuln.demo.controller;

import com.vuln.demo.model.Order;
import com.vuln.demo.repository.OrderRepository;
import com.vuln.demo.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderService orderService;
    
    /**
     * Case C: Controller 从安全路径切换到不安全路径。
     * 
     * 原本应该调用 getUserOrdersSecure（带权限校验），
     * 但现在改成了调用 getUserOrdersInsecure（无权限校验）。
     * 
     * 目标：仅看 Controller diff，只是方法名变了（getUserOrdersSecure -> getUserOrdersInsecure），
     *       看不出安全风险；带上下文时，大模型应该看到：
     *       - getUserOrdersSecure 调用了 AuthUtil.checkUserAccess
     *       - getUserOrdersInsecure 直接查询，没有任何校验
     *       从而识别出"从安全路径切换到不安全路径"的风险。
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserOrders(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        
        if (currentUserId == null) {
            return ResponseEntity.badRequest().body("Missing X-User-Id header");
        }
        
        // 原本 Case C 是从安全方法切到不安全方法；这里在 Service 层已经为
        // getUserOrdersInsecure 补上了权限校验逻辑，因此这里直接传入 currentUserId
        // 和 isAdmin（示例中简单地认为普通用户，isAdmin=false）。
        boolean isAdmin = false;
        List<Order> orders = orderService.getUserOrdersInsecure(userId, currentUserId, isAdmin);
        
        return ResponseEntity.ok(orders);
    }
    
    // 漏洞5: 水平越权 - 可以查看任意用户的订单
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderById(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Order order = orderOpt.get();
        
        // VULNERABILITY: 没有验证订单是否属于当前用户
        // 应该添加：if (order.getUserId() != currentUserId && !isAdmin) return 403
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", order.getId());
        response.put("userId", order.getUserId());
        response.put("productName", order.getProductName());
        response.put("amount", order.getAmount());
        response.put("status", order.getStatus());
        
        return ResponseEntity.ok(response);
    }
    
    // 漏洞6: 水平越权 - 可以修改任意用户的订单
    @PutMapping("/{orderId}")
    public ResponseEntity<?> updateOrder(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> updates,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Order order = orderOpt.get();
        
        // VULNERABILITY: 没有验证订单是否属于当前用户
        // 应该添加：if (order.getUserId() != currentUserId && !isAdmin) return 403
        
        if (updates.containsKey("status")) {
            order.setStatus(updates.get("status").toString());
        }
        if (updates.containsKey("amount")) {
            order.setAmount(Double.parseDouble(updates.get("amount").toString()));
        }
        
        orderRepository.save(order);
        
        return ResponseEntity.ok(Map.of("message", "Order updated successfully", "orderId", orderId));
    }
    
    // 漏洞7: 水平越权 - 可以删除任意用户的订单
    @DeleteMapping("/{orderId}")
    public ResponseEntity<?> deleteOrder(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Order order = orderOpt.get();
        
        // VULNERABILITY: 没有验证订单是否属于当前用户
        // 应该添加：if (order.getUserId() != currentUserId && !isAdmin) return 403
        
        orderRepository.delete(order);
        
        return ResponseEntity.ok(Map.of("message", "Order deleted successfully", "orderId", orderId));
    }
    
    // 漏洞8: 垂直越权 - 普通用户可以访问管理员统计功能
    @GetMapping("/admin/stats")
    public ResponseEntity<?> getOrderStats(@RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        // VULNERABILITY: 没有检查当前用户是否是管理员
        // 应该添加：if (currentUser.getRole() != "ADMIN") return 403
        
        List<Order> allOrders = orderRepository.findAll();
        double totalRevenue = allOrders.stream()
                .mapToDouble(Order::getAmount)
                .sum();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", allOrders.size());
        stats.put("totalRevenue", totalRevenue);
        stats.put("message", "Admin statistics - should be protected!");
        
        return ResponseEntity.ok(stats);
    }
}

