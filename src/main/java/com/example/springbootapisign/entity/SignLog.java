package com.example.springbootapisign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口签名审计日志实体
 * 用于安全审计、问题排查、统计分析
 */
@Data
@TableName("sys_sign_log")
public class SignLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 客户端AppKey */
    private String appKey;
    
    /** 请求URI */
    private String requestUri;
    
    /** 请求方法 GET/POST */
    private String requestMethod;
    
    /** 客户端IP地址 */
    private String clientIp;
    
    /** 时间戳（秒级） */
    private Long timestamp;
    
    /** 随机串Nonce */
    private String nonce;
    
    /** 客户端提交的签名 */
    private String clientSign;
    
    /** 服务端计算的签名 */
    private String serverSign;
    
    /**
     * 校验结果
     * SUCCESS-成功, FAIL_APPKEY-AppKey无效,
     * FAIL_TIMESTAMP-时间戳过期, FAIL_NONCE-重复请求,
     * FAIL_SIGN-签名不匹配, FAIL_RATELIMIT-限流拦截
     */
    private String verifyResult;
    
    /** 错误信息 */
    private String errorMessage;
    
    /** 响应耗时（毫秒） */
    private Long costTime;
    
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
