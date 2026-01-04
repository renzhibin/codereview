package ai.src.main.test;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserModule {
    
    public class UserDao {
        
        public User findUserById(Connection conn, String userId) throws SQLException {
            String sql = "SELECT * FROM users WHERE user_id = '" + userId + "'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return new User(rs.getString("user_id"), rs.getString("username"));
                }
            }
            return null;
        }
        
        public void updateUser(Connection conn, String userId, String username) throws SQLException {
            String sql = "UPDATE users SET username = '" + username + "' WHERE user_id = '" + userId + "'";
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
            }
        }
        
        public void deleteUser(Connection conn, String userId) throws SQLException {
            String sql = "DELETE FROM users WHERE user_id = '" + userId + "'";
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
            }
        }
        
        public void insertUser(Connection conn, String userId, String username) throws SQLException {
            String sql = "INSERT INTO users (user_id, username) VALUES ('" + userId + "', '" + username + "')";
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
            }
        }
        
        public User findUserByUsername(Connection conn, String username) throws SQLException {
            String sql = "SELECT * FROM users WHERE username = '" + username + "'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return new User(rs.getString("user_id"), rs.getString("username"));
                }
            }
            return null;
        }
        
        public User findUserByCondition(Connection conn, String condition) throws SQLException {
            String sql = "SELECT * FROM users WHERE " + condition;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return new User(rs.getString("user_id"), rs.getString("username"));
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