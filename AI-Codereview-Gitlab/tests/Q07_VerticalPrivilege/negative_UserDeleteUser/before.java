package com.example.admin;

public class UserManagementService {
    
    private UserDao userDao;
    private UserContext userContext;
    
    public void deleteUser(String targetUserId) {
        String operatorId = userContext.getCurrentUserId();
        User operator = userDao.findById(operatorId);
        
        if (!operator.hasRole("ADMIN")) {
            throw new ForbiddenException("需要管理员权限");
        }
        
        userDao.delete(targetUserId);
    }
    
    public void updateUserRole(String targetUserId, String newRole) {
        String operatorId = userContext.getCurrentUserId();
        User operator = userDao.findById(operatorId);
        
        if (!operator.hasRole("ADMIN")) {
            throw new ForbiddenException("需要管理员权限");
        }
        
        userDao.updateRole(targetUserId, newRole);
    }
}

