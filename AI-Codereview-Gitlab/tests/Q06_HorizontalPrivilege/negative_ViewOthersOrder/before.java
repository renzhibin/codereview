package com.example.order;

import java.util.List;

public class OrderService {
    
    private OrderDao orderDao;
    private UserContext userContext;
    
    public Order getOrderById(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        
        String currentUserId = userContext.getCurrentUserId();
        Order order = orderDao.findById(orderId);
        
        if (order == null) {
            throw new OrderNotFoundException("订单不存在");
        }
        
        if (!order.getUserId().equals(currentUserId)) {
            throw new UnauthorizedException("无权访问该订单");
        }
        
        return order;
    }
    
    public List<Order> getUserOrders(String userId) {
        String currentUserId = userContext.getCurrentUserId();
        
        if (!userId.equals(currentUserId)) {
            throw new UnauthorizedException("只能查询自己的订单");
        }
        
        return orderDao.findByUserId(userId);
    }
    
    public void deleteOrder(String orderId) {
        Order order = orderDao.findById(orderId);
        
        if (order == null) {
            throw new OrderNotFoundException("订单不存在");
        }
        
        String currentUserId = userContext.getCurrentUserId();
        if (!order.getUserId().equals(currentUserId)) {
            throw new UnauthorizedException("无权删除该订单");
        }
        
        orderDao.delete(orderId);
    }
}

