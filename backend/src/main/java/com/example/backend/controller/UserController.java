package com.example.backend.controller;

import com.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;


    //注册
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {

        String res =userService.register(request);
        Map<String, Object> response = new HashMap<>();
        if (res.equals("注册成功")){
            response.put("code", 200);
            response.put("msg", res);
            return ResponseEntity.ok(response);
        } else if (res.equals("用户名已存在")) {
            response.put("code", 409);
            response.put("msg", res);
            return ResponseEntity.status(409).body(response);
        } else {
            response.put("code", 400);
            response.put("msg", res);
            return ResponseEntity.badRequest().body(response);
        }

    }


    //登录
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> request) {
        String res=userService.login(request);
        Map<String, Object> response = new HashMap<>();
        if (res.equals("登录成功")){
            response.put("code", 200);
            response.put("msg", res);
            return ResponseEntity.ok(response);
        } else if (res.equals("用户名或密码错误")) {
            response.put("code", 401);
            response.put("msg", res);
            return ResponseEntity.status(401).body(response);
        } else {
            response.put("code", 400);
            response.put("msg", res);
            return ResponseEntity.badRequest().body(response);
        }
    }




}
