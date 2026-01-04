package com.example.data;

public class DataService {
    
    private DataDao dataDao;
    private UserContext userContext;
    
    public void deleteUserData(Long dataId) {
        Data data = dataDao.findById(dataId);
        
        if (data == null) {
            throw new DataNotFoundException("数据不存在");
        }
        
        String currentUserId = userContext.getCurrentUserId();
        if (!data.getOwnerId().equals(currentUserId)) {
            throw new UnauthorizedException("无权删除他人数据");
        }
        
        dataDao.delete(dataId);
    }
}

