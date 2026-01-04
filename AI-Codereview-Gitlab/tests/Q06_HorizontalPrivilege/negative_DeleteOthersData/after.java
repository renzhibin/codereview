package com.example.data;

public class DataService {
    
    private DataDao dataDao;
    private UserContext userContext;
    
    public void deleteUserData(Long dataId) {
        Data data = dataDao.findById(dataId);
        
        if (data == null) {
            throw new DataNotFoundException("数据不存在");
        }
        
        dataDao.delete(dataId);
    }
}

