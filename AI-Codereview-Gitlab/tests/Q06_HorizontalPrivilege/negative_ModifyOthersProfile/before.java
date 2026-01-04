package com.example.user;

public class UserProfileService {
    
    private UserProfileDao profileDao;
    private UserContext userContext;
    
    public void updateUserProfile(String userId, UserProfile profile) {
        String currentUserId = userContext.getCurrentUserId();
        
        if (!userId.equals(currentUserId)) {
            throw new UnauthorizedException("无权修改他人资料");
        }
        
        if (profile == null) {
            throw new IllegalArgumentException("资料不能为空");
        }
        
        profileDao.update(userId, profile);
    }
    
    public void updateEmail(String userId, String newEmail) {
        String currentUserId = userContext.getCurrentUserId();
        
        if (!userId.equals(currentUserId)) {
            throw new UnauthorizedException("无权修改他人邮箱");
        }
        
        profileDao.updateEmail(userId, newEmail);
    }
}

