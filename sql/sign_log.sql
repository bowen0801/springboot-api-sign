-- =============================================
-- 签名审计日志表（生产环境可选）
-- =============================================

USE api_sign_db;

-- ---------------------------------------------
-- 接口签名校验审计日志表
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_sign_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_key` varchar(64) DEFAULT NULL COMMENT '客户端AppKey',
  `request_uri` varchar(512) DEFAULT NULL COMMENT '请求URI路径',
  `request_method` varchar(10) DEFAULT NULL COMMENT '请求方法GET/POST',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '客户端IP地址',
  `timestamp` bigint DEFAULT NULL COMMENT '请求时间戳（秒级）',
  `nonce` varchar(128) DEFAULT NULL COMMENT '随机串',
  `client_sign` varchar(128) DEFAULT NULL COMMENT '客户端提交的签名',
  `server_sign` varchar(128) DEFAULT NULL COMMENT '服务端计算的签名',
  `verify_result` varchar(32) DEFAULT NULL COMMENT '校验结果：SUCCESS/FAIL_XXX',
  `error_message` varchar(512) DEFAULT NULL COMMENT '错误信息',
  `cost_time` bigint DEFAULT NULL COMMENT '处理耗时（毫秒）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_app_key` (`app_key`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_verify_result` (`verify_result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口签名校验审计日志表';
