-- =============================================
-- SpringBoot 接口签名验证 - 数据库初始化脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS api_sign_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE api_sign_db;

-- ---------------------------------------------
-- 接口调用方客户端密钥表
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_app_client` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_key` varchar(64) NOT NULL COMMENT '客户端唯一AppKey',
  `app_secret` varchar(128) NOT NULL COMMENT '客户端私密秘钥',
  `app_name` varchar(64) NOT NULL COMMENT '客户端名称',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 0禁用 1正常',
  `limit_count` int DEFAULT '0' COMMENT '每日接口调用限流次数，0表示不限流',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_key` (`app_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口调用方客户端密钥表';

-- 插入测试客户端数据（与application.yml配置保持一致）
INSERT INTO `sys_app_client` (`app_key`, `app_secret`, `app_name`, `status`, `limit_count`) VALUES 
('test_app_key_123456', 'test_app_secret_654321_abcdef123456', '测试客户端', 1, 0);

-- 可选：插入更多示例客户端
-- INSERT INTO `sys_app_client` (`app_key`, `app_secret`, `app_name`, `status`, `limit_count`) VALUES 
-- ('prod_app_key_abc', 'prod_app_secret_xyz_2024', '生产环境App', 1, 10000),
-- ('partner_app_key_def', 'partner_app_secret_uvwx', '合作方对接平台', 1, 5000);
