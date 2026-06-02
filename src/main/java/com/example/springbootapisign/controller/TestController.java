package com.example.springbootapisign.controller;

import com.example.springbootapisign.annotation.NoSign;
import com.example.springbootapisign.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 * 演示需要签名验证和免签名的接口
 */
@RestController
@RequestMapping("/api")
public class TestController {

    /**
     * GET请求 需签名校验接口
     */
    @GetMapping("/get/data")
    public Result<Map<String, Object>> getData(@RequestParam Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "GET请求成功");
        result.put("receivedParams", params);
        return Result.success(result);
    }

    /**
     * POST JSON请求体 需签名校验接口
     */
    @PostMapping("/post/json")
    public Result<Object> postJson(@RequestBody Object body) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "POST JSON请求成功");
        result.put("receivedBody", body);
        return Result.success(result);
    }

    /**
     * POST表单请求 需签名校验接口
     */
    @PostMapping("/post/form")
    public Result<Map<String, Object>> postForm(@RequestParam Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "POST表单请求成功");
        result.put("receivedParams", params);
        return Result.success(result);
    }

    /**
     * 公开接口 免签名校验
     */
    @NoSign
    @GetMapping("/public/info")
    public Result<String> publicApi() {
        return Result.success("公开接口，无需签名验证，可直接访问");
    }

    /**
     * 健康检查 免签名
     */
    @NoSign
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("服务运行正常");
    }
}
