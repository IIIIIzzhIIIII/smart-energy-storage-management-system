package com.energy.controller;

import com.energy.common.Result;
import com.energy.context.CurrentUser;
import com.energy.context.UserContext;
import com.energy.dto.LoginRequest;
import com.energy.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/info")
    public Result<CurrentUser> info() {
        return Result.success(UserContext.get());
    }
}