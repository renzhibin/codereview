package com.example.order;

import java.util.List;

public class OrderService {
    
    private OrderDao orderDao;
    private UserContext userContext;
    
    public Order getOrderById(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        
        Order order = orderDao.findById(orderId);
        
        if (order == null) {
            throw new OrderNotFoundException("订单不存在");
        }
        
        return order;
    }
    
    public List<Order> getUserOrders(String userId) {
        return orderDao.findByUserId(userId);
    }
    
    public void deleteOrder(String orderId) {
        Order order = orderDao.findById(orderId);
        
        if (order == null) {
            throw new OrderNotFoundException("订单不存在");
        }
        
        orderDao.delete(orderId);
    }
}

