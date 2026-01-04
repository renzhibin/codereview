package ai.src.main.test;

import java.util.List;
import java.util.ArrayList;

public class R156_UserPermissionModule {
    
    public class UserService {
        
        public UserProfile getUserProfile(String currentUserId, String targetUserId) {
            if (!hasPermission(currentUserId, targetUserId)) {
                throw new SecurityException("Access denied: insufficient permissions");
            }
            
            return new UserProfile(targetUserId);
        }
        
        public void updateUserProfile(String currentUserId, String targetUserId, UserProfile profile) {
            if (!hasPermission(currentUserId, targetUserId)) {
                throw new SecurityException("Access denied: insufficient permissions");
            }
            
            // 更新用户资料
            System.out.println("Updating profile for user: " + targetUserId);
        }
        
        public void deleteUserData(String currentUserId, String targetUserId) {
            if (!hasPermission(currentUserId, targetUserId)) {
                throw new SecurityException("Access denied: insufficient permissions");
            }
            
            // 删除用户数据
            System.out.println("Deleting data for user: " + targetUserId);
        }
        
        public List<Message> getUserMessages(String currentUserId, String targetUserId) {
            if (!hasPermission(currentUserId, targetUserId)) {
                throw new SecurityException("Access denied: insufficient permissions");
            }
            
            return new ArrayList<>();
        }
        
        public List<Order> getUserOrders(String currentUserId, String targetUserId) {
            if (!hasPermission(currentUserId, targetUserId)) {
                throw new SecurityException("Access denied: insufficient permissions");
            }
            
            return new ArrayList<>();
        }
        
        private boolean hasPermission(String currentUserId, String targetUserId) {
            return currentUserId.equals(targetUserId) || isAdmin(currentUserId);
        }
        
        private boolean isAdmin(String userId) {
            return "admin".equals(userId);
        }
    }
    
    // 辅助类
    public static class UserProfile {
        private String userId;
        
        public UserProfile(String userId) {
            this.userId = userId;
        }
        
        public String getUserId() {
            return userId;
        }
    }
    
    public static class Message {
        private String content;
        
        public Message(String content) {
            this.content = content;
        }
        
        public String getContent() {
            return content;
        }
    }
    
    public static class Order {
        private String orderId;
        
        public Order(String orderId) {
            this.orderId = orderId;
        }
        
        public String getOrderId() {
            return orderId;
        }
    }
}