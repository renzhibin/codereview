package com.vuln.demo.service;

import com.vuln.demo.model.User;
import com.vuln.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    /**
     * 删除用户的安全实现：
     * 只有管理员，或者删除自己的用户才允许删除。
     *
     * 这里的 @PreAuthorize 是我们希望通过 JavaParser 上下文暴露给大模型的关键信息。
     */
    @PreAuthorize("hasRole('ADMIN') or #currentUserId == #userId")
    public void deleteUser(Long userId, Long currentUserId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return;
        }
        userRepository.delete(userOpt.get());
    }
}


