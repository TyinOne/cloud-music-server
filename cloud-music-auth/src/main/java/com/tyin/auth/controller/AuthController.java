package com.tyin.auth.controller;

import com.tyin.auth.service.impl.UserServiceImpl;
import com.tyin.core.api.Result;
import com.tyin.core.modules.res.AdminUserLoginRes;
import com.tyin.core.modules.valid.AdminLoginValid;
import com.tyin.core.modules.valid.sequence.AdminUserLoginValidSequence;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author TyinZero
 * @date 2022/12/29
 * @description ...
 */
@RestController
@RequiredArgsConstructor
public class AuthController {
    private final UserServiceImpl userService;
    @PostMapping("/login")
    @Operation(description = "用户登录", tags = "用户登录")
    public Result<AdminUserLoginRes> login(@RequestBody @Validated(AdminUserLoginValidSequence.class) AdminLoginValid valid, HttpServletRequest httpServletRequest) {
        return Result.success();
    }

    @GetMapping("/test")
    public Result<AdminUserLoginRes> test() {
        return Result.success();
    }
}
