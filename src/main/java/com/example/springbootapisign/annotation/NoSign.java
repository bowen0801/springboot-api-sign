package com.example.springbootapisign.annotation;

import java.lang.annotation.*;

/**
 * 接口免签名校验注解
 * 标注在Controller方法上，直接放行所有请求
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoSign {
}
