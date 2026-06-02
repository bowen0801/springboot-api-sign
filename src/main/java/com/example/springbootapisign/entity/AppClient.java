package com.example.springbootapisign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端密钥实体类
 * 生产环境用于多客户端管理
 */
@Data
@TableName("sys_app_client")
public class AppClient {

    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 客户端唯一AppKey */
    private String appKey;
    
    /** 客户端私密秘钥 */
    private String appSecret;
    
    /** 客户端名称 */
    private String appName;
    
    /** 状态 0禁用 1正常 */
    private Integer status;
    
    /** 每日接口调用限流次数 0表示不限流 */
    private Integer limitCount;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
