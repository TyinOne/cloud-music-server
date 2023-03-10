package com.tyin.file.controller;

import com.tyin.core.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author TyinZero
 * @date 2022/12/27
 * @description ...
 */
@RestController
public class CommonController {
    @GetMapping("/test")
    public Result<?> getTest() {
        return Result.success("444414",213);
    }

    @PostMapping("/upload")
    public Result<?> upload(@RequestParam MultipartFile file, @RequestParam String remark) {
        return Result.success();
    }
}
