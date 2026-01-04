package com.example.user;

public class UserProfileService {
    
    private UserProfileDao profileDao;
    private UserContext userContext;
    
    public void updateUserProfile(String userId, UserProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("资料不能为空");
        }
        
        profileDao.update(userId, profile);
    }
    
    public void updateEmail(String userId, String newEmail) {
        profileDao.updateEmail(userId, newEmail);
    }
}

