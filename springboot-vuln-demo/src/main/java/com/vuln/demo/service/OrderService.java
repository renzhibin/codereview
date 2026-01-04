package com.vuln.demo.service;

import com.vuln.demo.model.Order;
import com.vuln.demo.repository.OrderRepository;
import com.vuln.demo.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    /**
     * 安全路径：获取用户订单（带权限校验）
     * 
     * Case C：这个方法会调用 AuthUtil.checkUserAccess 进行权限校验。
     * 当 Controller 从调用这个方法切换到 getUserOrdersInsecure 时，
     * 只有通过上下文分析才能发现安全路径被替换了。
     */
    public List<Order> getUserOrdersSecure(Long userId, Long currentUserId, boolean isAdmin) {
        // 权限校验
        AuthUtil.checkUserAccess(currentUserId, userId, isAdmin);
        
        // 安全地查询订单
        return orderRepository.findByUserId(userId);
    }
    
    /**
     * 原本设计为「不安全路径」的获取用户订单方法。
     *
     * 为了验证上下文能力在「修复后」的表现，这里补上与安全路径等价的权限校验：
     * - 仍然保留方法名 getUserOrdersInsecure（方便 Case C 对比）
     * - 但内部先调用 AuthUtil.checkUserAccess 再查询订单
     */
    public List<Order> getUserOrdersInsecure(Long userId, Long currentUserId, boolean isAdmin) {
        // 补充权限校验，使其与 getUserOrdersSecure 等价
        AuthUtil.checkUserAccess(currentUserId, userId, isAdmin);

        return orderRepository.findByUserId(userId);
    }
}

