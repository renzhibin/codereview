package ai.src.main.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserModule {
    
    public class UserDao {
        
        public User findUserById(Connection conn, String userId) throws SQLException {
            String sql = "SELECT * FROM users WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new User(rs.getString("user_id"), rs.getString("username"));
                    }
                }
            }
            return null;
        }
        
        public void updateUser(Connection conn, String userId, String username) throws SQLException {
            String sql = "UPDATE users SET username = ? WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, userId);
                stmt.executeUpdate();
            }
        }
        
        public void deleteUser(Connection conn, String userId) throws SQLException {
            String sql = "DELETE FROM users WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, userId);
                stmt.executeUpdate();
            }
        }
        
        public void insertUser(Connection conn, String userId, String username) throws SQLException {
            String sql = "INSERT INTO users (user_id, username) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, userId);
                stmt.setString(2, username);
                stmt.executeUpdate();
            }
        }
        
        public User findUserByUsername(Connection conn, String username) throws SQLException {
            String sql = "SELECT * FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new User(rs.getString("user_id"), rs.getString("username"));
                    }
                }
            }
            return null;
        }
    }
    
    // 辅助类
    public static class User {
        private String userId;
        private String username;
        
        public User(String userId, String username) {
            this.userId = userId;
            this.username = username;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getUsername() {
            return username;
        }
    }
}