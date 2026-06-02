package com.example.springbootapisign;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SpringBoot 接口签名验证 - 生产级主启动类
 *
 * 核心特性：
 * ✅ AppKey + Secret 签名认证
 * ✅ HMAC-SHA256 高安全加密（可配置）
 * ✅ 原子性防重放攻击
 * ✅ 接口调用频率限制
 * ✅ 多客户端数据库管理
 * ✅ 完整审计日志记录
 * ✅ 统一异常处理
 */
@SpringBootApplication
@MapperScan("com.example.springbootapisign.mapper")
public class SpringbootApiSignApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootApiSignApplication.class, args);
    }
}
