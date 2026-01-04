package com.vuln.demo.util;

/**
 * 权限校验工具类
 * 
 * Case C：这个工具类提供了安全的权限校验方法。
 * 当 Controller 从调用 getUserOrdersSecure 切换到 getUserOrdersInsecure 时，
 * 只有通过上下文分析才能发现安全路径被替换成了不安全路径。
 */
public class AuthUtil {
    
    /**
     * 检查用户是否有权限访问目标用户的数据
     * 
     * @param currentUserId 当前登录用户ID
     * @param targetUserId 目标用户ID
     * @param isAdmin 当前用户是否是管理员
     * @return true 如果有权限，false 如果没有权限
     * @throws SecurityException 如果没有权限则抛出异常
     */
    public static void checkUserAccess(Long currentUserId, Long targetUserId, boolean isAdmin) {
        if (currentUserId == null) {
            throw new SecurityException("Current user ID is required");
        }
        
        if (targetUserId == null) {
            throw new SecurityException("Target user ID is required");
        }
        
        // 只有管理员或者访问自己的数据才允许
        if (!isAdmin && !currentUserId.equals(targetUserId)) {
            throw new SecurityException("Access denied: user can only access their own data");
        }
    }
}


