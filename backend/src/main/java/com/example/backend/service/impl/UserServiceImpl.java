package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserLoginMapper userLoginMapper;

    //登录
    @Override
    public String login(Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        username = StringUtils.hasText(username) ? username.trim() : null;
        password = StringUtils.hasText(password) ? password : null;

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return "用户名和密码不能为空";
        }

        QueryWrapper<UserLogin> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        queryWrapper.eq("password", password);
        UserLogin user = userLoginMapper.selectOne(queryWrapper);
        if (user == null) {
            return "用户名或密码错误";
        }
        return "登录成功";
    }
    //注册
    @Override
    public String register(Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String email = request.get("email");
        username = StringUtils.hasText(username) ? username.trim() : null;
        password = StringUtils.hasText(password) ? password : null;
        email = StringUtils.hasText(email) ? email.trim() : null;

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return "用户名和密码不能为空";
        }

        QueryWrapper<UserLogin> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        UserLogin user = userLoginMapper.selectOne(queryWrapper);
        if (user != null) {
            return "用户名已存在";
        }
        UserLogin newUser = new UserLogin();
        newUser.setUsername(username);
        newUser.setPassword(password);
        newUser.setEmail(email);
        userLoginMapper.insert(newUser);
        return "注册成功";
    }

}
