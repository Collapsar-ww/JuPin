package com.jupin.server.service;

import com.jupin.pojo.dto.LoginRequest;
import com.jupin.pojo.dto.RegisterRequest;
import com.jupin.pojo.dto.UserUpdateRequest;
import com.jupin.pojo.entity.User;

public interface UserService {
    User register(RegisterRequest request);
    User login(LoginRequest request);
    void logout(String token);
    String refreshToken(String refreshToken);
    User getCurrentUser(Long userId);
    User updateUser(Long userId, UserUpdateRequest request);
}
