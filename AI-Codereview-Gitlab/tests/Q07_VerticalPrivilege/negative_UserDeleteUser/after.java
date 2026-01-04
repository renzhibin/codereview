package com.example.admin;

public class UserManagementService {
    
    private UserDao userDao;
    private UserContext userContext;
    
    public void deleteUser(String targetUserId) {
        userDao.delete(targetUserId);
    }
    
    public void updateUserRole(String targetUserId, String newRole) {
        userDao.updateRole(targetUserId, newRole);
    }
}

