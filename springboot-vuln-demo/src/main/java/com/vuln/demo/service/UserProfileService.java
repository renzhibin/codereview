package com.vuln.demo.service;

import com.vuln.demo.model.User;
import com.vuln.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserProfileService {

    @Autowired
    private UserRepository userRepository;

    /**
     * 用例B：看上去是“更新当前用户的 Profile”，
     * 实际上完全信任传入的 targetUserId，没有对 currentUserId 做任何权限判断。
     *
     * 只看 Controller 的 diff，很容易以为安全校验在 Service 里，
     * 但顺着调用链看才会发现这里没有任何越权保护。
     */
    public void updateUserProfile(Long currentUserId, Long targetUserId, String email) {
        Optional<User> userOpt = userRepository.findById(targetUserId);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        user.setEmail(email);
        userRepository.save(user);
    }
}


