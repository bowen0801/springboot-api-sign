# 生产级改造完成 - 部署指南

## ✅ 改造清单（已完成）

| 序号 | 改进项 | 状态 | 文件位置 |
|------|--------|------|---------|
| 1 | Redis 原子防重放（SET NX EX） | ✅ | `RedisUtil.java` |
| 2 | 接口调用频率限制 | ✅ | `RedisUtil.java` + `SignInterceptor.java` |
| 3 | 多客户端数据库支持 | ✅ | `AppClientMapper.java` + SQL脚本 |
| 4 | HMAC-SHA256 高安全加密 | ✅ | `SignInterceptor.java` (可配置切换) |
| 5 | 完整审计日志系统 | ✅ | `SignLog.java` + `SignLogService.java` |
| 6 | 统一异常处理 | ✅ | `GlobalExceptionHandler.java` |
| 7 | JSON 请求体可重复读取 | ✅ | `CachedBodyHttpServletRequest.java` + `ContentCachingFilter.java` |
| 8 | 详细日志和 IP 追踪 | ✅ | `SignInterceptor.java` |
| 9 | 异步日志写入 | ✅ | `WebConfig.java` (线程池配置) |

---

## 🚀 快速启动（开发/测试环境）

### 方式一：单客户端模式（无需数据库）

**适用场景**: 开发测试、演示、单应用对接

```bash
# 1. 启动 Redis
redis-server

# 2. 使用默认配置启动（application.yml 已配置好）
mvn spring-boot:run

# 或打包运行
mvn clean package -DskipTests
java -jar target/springboot-api-sign-0.0.1-SNAPSHOT.jar
```

**配置说明** (`application.yml`):
```yaml
api:
  use-database: false          # 关闭数据库，使用配置文件中的密钥
  use-hmac: false              # 使用 MD5（兼容性好）
  enable-rate-limit: true      # 开启限流
  rate-limit-count: 1000       # 每分钟最大请求次数
  
  app:
    key: test_app_key_123456   # 测试用 AppKey
    secret: test_app_secret_654321_abcdef123456  # 测试用 Secret
```

---

## 🏭 生产环境部署

### 步骤 1: 数据库初始化

```bash
# 执行 SQL 脚本
mysql -u root -p < sql/init.sql           # 客户端表
mysql -u root -p < sql/sign_log.sql        # 审计日志表（可选）

# 插入生产客户端数据
mysql -u root -p api_sign_db

INSERT INTO sys_app_client (app_key, app_secret, app_name, status, limit_count) VALUES
('prod_app_web', 'prod_secret_web_xxxxxxxx', 'Web前端', 1, 5000),
('prod_app_mobile', 'prod_secret_mobile_yyyyyyyy', '移动端App', 1, 10000),
('partner_api_abc', 'partner_secret_zzzzzz', '合作方A平台', 1, 2000);
```

### 步骤 2: 修改生产配置

编辑 `src/main/resources/application-prod.yml`:

```yaml
server:
  port: 8080

spring:
  redis:
    host: prod-redis.internal.com
    port: 6379
    password: ${REDIS_PASSWORD}            # 从环境变量读取
    database: 0

  datasource:
    url: jdbc:mysql://prod-mysql.internal.com:3306/api_sign_db?useSSL=true&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

api:
  sign-expire: 300                         # 5分钟有效期
  use-database: true                       # ✅ 启用数据库查询
  use-hmac: true                           # ✅ 使用 HMAC-SHA256 高安全加密
  enable-rate-limit: true                  # ✅ 启用限流
  rate-limit-count: 2000                   # 每分钟限流阈值

logging:
  level:
    com.example.springbootapisign.interceptor: INFO  # 生产环境降低日志级别
  file:
    name: /var/log/springboot-api-sign/app.log
```

### 步骤 3: 启动服务

```bash
# 使用生产 profile 启动
java -jar springboot-api-sign.jar --spring.profiles.active=prod

# 或者设置环境变量
export REDIS_PASSWORD=your_redis_password
export DB_USERNAME=app_user
export DB_PASSWORD=secure_password
java -jar springboot-api-sign.jar --spring.profiles.active=prod
```

---

## 🔧 配置项详解

### 核心安全参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `api.sign-expire` | long | 300 | 请求有效期（秒），建议 180-600 |
| `api.use-database` | boolean | false | 是否启用数据库动态密钥管理 |
| `api.use-hmac` | boolean | false | 是否使用 HMAC-SHA256 加密（**生产强烈推荐 true**） |
| `api.enable-rate-limit` | boolean | true | 是否启用接口限流 |
| `api.rate-limit-count` | int | 1000 | 每分钟每 AppKey 最大请求数 |

### 密钥管理策略

#### 单客户端模式 (use-database=false)
```yaml
api:
  app:
    key: your_fixed_app_key
    secret: your_fixed_app_secret  # ⚠️ 注意：不要提交到代码仓库！
```
**适用**: 内部系统、单方对接、开发测试

#### 多客户端模式 (use-database=true) ✅ 推荐
- 所有密钥存储在 `sys_app_client` 表
- 支持 AppKey 维度的独立状态管理和限流
- 可随时新增/禁用/删除客户端
- **生产环境必须使用此模式**

---

## 📊 监控与运维

### 审计日志查看

方式一：查看日志文件
```bash
tail -f logs/springboot-api-sign.log | grep "[签名审计]"
```

方式二：数据库查询（如果启用了持久化）
```sql
-- 查询最近失败的签名验证
SELECT * FROM sys_sign_log 
WHERE verify_result != 'SUCCESS' 
ORDER BY create_time DESC 
LIMIT 50;

-- 统计各AppKey调用情况
SELECT app_key, COUNT(*) as total_count,
       SUM(CASE WHEN verify_result = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
       SUM(CASE WHEN verify_result != 'SUCCESS' THEN 1 ELSE 0 END) as fail_count
FROM sys_sign_log 
WHERE create_time > DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY app_key;
```

### 性能监控指标

建议集成 Prometheus + Grafana 监控：
- 签名验证耗时 P99
- 签名失败率
- 各 AppKey 调用 QPS
- Redis 连接池状态
- Nonce 缓存命中率

---

## ⚠️ 安全注意事项

### 1. **AppSecret 必须严格保密**
- ❌ 绝对不能出现在日志、错误响应中
- ❌ 不能硬编码在前端代码
- ❌ 不能通过接口返回
- ✅ 仅保存在数据库或加密配置中心

### 2. **定期轮换密钥**
```sql
-- 更新客户端密钥（平滑过渡方案）
UPDATE sys_app_client 
SET app_secret = 'new_secret_new_timestamp',
    update_time = NOW()
WHERE app_key = 'client_app_key';
```

建议每 90-180 天轮换一次密钥。

### 3. **网络传输加密**
- 强制使用 HTTPS（TLS 1.2+）
- 在反向代理层（Nginx）强制跳转 HTTPS
- 禁止 HTTP 明文传输签名信息

### 4. **IP 白名单（可选）**
对于高敏感接口，可在 Nginx 层添加 IP 限制：
```nginx
location /api/sensitive {
    allow 192.168.1.0/24;
    allow 10.0.0.0/8;
    deny all;
    
    proxy_pass http://backend;
}
```

---

## 🔍 故障排查指南

### 场景1：所有请求都返回 "签名验证失败"

**可能原因**:
1. 时间戳不同步 → 检查服务器 NTP 时钟同步
2. 参数顺序不一致 → 确认客户端按 ASCII 字典序排序
3. 编码问题 → 统一使用 UTF-8

**排查命令**:
```bash
# 检查服务器时间
date +%s

# 对比客户端和服务端时间差（应在5分钟内）
```

### 场景2：偶发性"重复请求被拦截"

**可能原因**:
1. 客户端重试机制导致 Nonce 复用
2. 网络超时后客户端重新发送相同请求

**解决方案**:
- 确保每次请求生成全新 UUID 作为 Nonce
- 客户端实现幂等性设计

### 场景3：触发频率限制

**排查**:
```bash
# 查看 Redis 中当前计数
redis-cli GET "api:sign:rate:your_app_key"

# 查看限流配置是否合理
grep rate-limit application.yml
```

**优化**:
- 根据 QPS 需求调整 `rate-limit-count`
- 区分不同 AppKey 设置差异化限流策略（需要二次开发）

---

## 📝 客户端对接示例

### Python 示例

```python
import requests
import time
import uuid
import hashlib
from urllib.parse import urlencode

def call_signed_api(url, params, app_key, app_secret):
    # 1. 准备基础参数
    timestamp = str(int(time.time()))
    nonce = str(uuid.uuid4())
    
    # 2. 参数字典序排序并拼接
    sorted_params = sorted(params.items())
    param_str = "&".join([f"{k}={v}" for k, v in sorted_params])
    
    # 3. 计算签名
    sign_str = param_str + app_secret
    if use_hmac:
        import hmac
        sign = hmac.new(app_secret.encode(), sign_str.encode(), hashlib.sha256).hexdigest().upper()
    else:
        sign = hashlib.md5(sign_str.encode()).hexdigest().upper()
    
    # 4. 发起请求
    headers = {
        "AppKey": app_key,
        "Timestamp": timestamp,
        "Nonce": nonce,
        "Sign": sign
    }
    
    response = requests.get(url, params=params, headers=headers)
    return response.json()

# 使用示例
result = call_signed_api(
    "http://localhost:8080/api/get/data",
    {"name": "test", "age": "25"},
    "test_app_key_123456",
    "test_app_secret_654321_abcdef123456"
)
print(result)
```

### Java HttpClient 示例

```java
// 见项目 README.md 完整 Java 客户端示例
```

---

## 📞 技术支持

遇到问题？检查清单：
1. ✅ Redis 服务是否正常
2. ✅ 数据库连接是否成功（如果使用多客户端模式）
3. ✅ AppKey 和 AppSecret 是否正确匹配
4. ✅ 服务器时钟是否准确（NTP 同步）
5. ✅ 网络是否能正常访问 Redis 和数据库

---

## 🎉 版本更新记录

| 版本 | 日期 | 主要改动 |
|------|------|---------|
| v1.0.0 | 2026-06-01 | 初始版本（基础功能）|
| v2.0.0 | 2026-06-01 | **生产级增强**（本次改造）|

### v2.0.0 改动明细
- ✅ Redis 原子操作防止并发竞态条件
- ✅ 接口限流防护（滑动窗口算法）
- ✅ 多客户端数据库动态密钥管理
- ✅ HMAC-SHA256 可选高安全加密
- ✅ 完整的审计日志系统
- ✅ 全局统一异常处理
- ✅ JSON 请求体缓存包装器
- ✅ 异步非阻塞日志写入
- ✅ 客户端真实 IP 获取
- ✅ 详细的调试日志输出

