package com.vuln.demo.config;

import com.vuln.demo.model.Order;
import com.vuln.demo.model.User;
import com.vuln.demo.repository.OrderRepository;
import com.vuln.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Override
    public void run(String... args) {
        // 创建测试用户
        if (userRepository.count() == 0) {
            User admin = new User("admin", "admin123", "admin@example.com", "ADMIN");
            User user1 = new User("alice", "alice123", "alice@example.com", "USER");
            User user2 = new User("bob", "bob123", "bob@example.com", "USER");
            
            userRepository.save(admin);
            userRepository.save(user1);
            userRepository.save(user2);
            
            // 创建测试订单
            User savedUser1 = userRepository.findByUsername("alice").orElse(user1);
            User savedUser2 = userRepository.findByUsername("bob").orElse(user2);
            
            Order order1 = new Order(savedUser1.getId(), "Laptop", 999.99, "PENDING");
            Order order2 = new Order(savedUser1.getId(), "Mouse", 29.99, "COMPLETED");
            Order order3 = new Order(savedUser2.getId(), "Keyboard", 79.99, "PENDING");
            Order order4 = new Order(savedUser2.getId(), "Monitor", 299.99, "SHIPPED");
            
            orderRepository.save(order1);
            orderRepository.save(order2);
            orderRepository.save(order3);
            orderRepository.save(order4);
            
            System.out.println("=== Test Data Initialized ===");
            System.out.println("Admin: id=1, username=admin, role=ADMIN");
            System.out.println("User1: id=2, username=alice, role=USER");
            System.out.println("User2: id=3, username=bob, role=USER");
            System.out.println("Orders: order1(userId=2), order2(userId=2), order3(userId=3), order4(userId=3)");
            System.out.println("=============================");
        }
    }
}

