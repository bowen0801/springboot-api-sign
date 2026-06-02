# SpringBoot 接口签名验证（AppKey/Secret）

## 项目简介

这是一个完整的 SpringBoot 接口签名验证实现，基于 **AppKey + AppSecret** 方案，提供以下安全能力：

- ✅ **身份认证** - 通过 AppKey 识别调用方身份
- ✅ **防参数篡改** - MD5/HMAC-SHA256 签名确保参数完整性
- ✅ **防重放攻击** - 时间戳 + Nonce 双重防护
- ✅ **请求时效控制** - 5分钟请求有效期
- ✅ **多客户端支持** - 数据库管理多套密钥

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 1.8+ | 开发语言 |
| Spring Boot | 2.7.18 | 基础框架 |
| Redis | - | 随机串防重放缓存 |
| MyBatis Plus | 3.5.5 | 多客户端数据库操作（可选） |
| Lombok | - | 简化代码 |

---

## 快速开始

### 环境准备

1. **JDK 1.8+**
2. **Maven 3.x**
3. **Redis** (本地启动或使用远程服务)
4. **MySQL** (可选，用于多客户端管理)

### 安装步骤

```bash
# 1. 克隆项目
git clone <your-repo-url>
cd springboot-api-sign

# 2. 启动 Redis 服务
redis-server

# 3. 初始化数据库（可选）
mysql -u root -p < sql/init.sql

# 4. 修改配置文件
# 编辑 src/main/resources/application.yml
# 配置 Redis 连接信息和密钥

# 5. 编译运行
mvn spring-boot:run

# 或者打包运行
mvn clean package -DskipTests
java -jar target/springboot-api-sign-0.0.1-SNAPSHOT.jar
```

### 访问测试

服务启动后访问：`http://localhost:8080`

---

## 核心配置说明

### application.yml 关键配置

```yaml
api:
  sign-expire: 300        # 请求有效期（秒），默认5分钟
  app:
    key: test_app_key_123456           # 客户端AppKey
    secret: test_app_secret_654321_... # 客户端AppSecret（绝不能泄露！）
```

---

## 客户端对接规范

### 请求头必须携带的参数

```
AppKey:    test_app_key_123456                    # 客户端唯一标识
Timestamp: 1745425632                             # 当前时间戳（秒级）
Nonce:     a1b2c3d4-e5f6-7890-abcd-ef1234567890  # 唯一随机字符串（UUID）
Sign:      ABCDEF1234567890ABCDEF1234567890       # 签名字符串
```

### 签名生成算法

**示例请求参数：** `name=zhangsan&age=22&phone=13800138000`

**签名步骤：**

1️⃣ 参数按 Key 字典序排序：
```
age=22&name=zhangsan&phone=13800138000
```

2️⃣ 拼接 AppSecret：
```
age=22&name=zhangsan&phone=13800138000test_app_secret_654321_abcdef123456
```

3️⃣ MD5 加密（32位大写）：
```
Sign = MD5(拼接后的字符串)
```

### 完整请求示例（cURL）

```bash
# GET 请求
curl -X GET "http://localhost:8080/api/get/data?name=zhangsan&age=22" \
  -H "AppKey: test_app_key_123456" \
  -H "Timestamp: $(date +%s)" \
  -H "Nonce: $(uuidgen)" \
  -H "Sign: <计算得到的签名>"

# POST JSON 请求
curl -X POST "http://localhost:8080/api/post/json" \
  -H "Content-Type: application/json" \
  -H "AppKey: test_app_key_123456" \
  -H "Timestamp: $(date +%s)" \
  -H "Nonce: $(uuidgen)" \
  -H "Sign: <计算得到的签名>" \
  -d '{"username":"admin","password":"123456"}'
```

---

## 测试接口列表

| 方法 | 路径 | 说明 | 是否需要签名 |
|------|------|------|-------------|
| GET | `/api/health` | 健康检查 | ❌ 免签名 |
| GET | `/api/public/info` | 公开信息 | ❌ 免签名 |
| GET | `/api/get/data` | GET数据接口 | ✅ 需要签名 |
| POST | `/api/post/form` | POST表单接口 | ✅ 需要签名 |
| POST | `/api/post/json` | POST JSON接口 | ✅ 需要签名 |

---

## 项目结构

```
src/main/java/com/example/springbootapisign/
├── SpringbootApiSignApplication.java   # 主启动类
├── annotation/
│   └── NoSign.java                      # 免签名注解
├── common/
│   └── Result.java                      # 统一返回结果
├── config/
│   └── WebConfig.java                   # Web配置（拦截器注册）
├── controller/
│   └── TestController.java              # 测试控制器
├── entity/
│   └── AppClient.java                   # 客户端实体类
├── interceptor/
│   └── SignInterceptor.java             # 签名校验拦截器
└── util/
    ├── RedisUtil.java                   # Redis工具类
    └── SignUtil.java                    # 签名工具类

src/main/resources/
└── application.yml                       # 应用配置文件

sql/
└── init.sql                              # 数据库初始化脚本
```

---

## 安全机制详解

### 1. 签名验证流程

```
客户端请求 → 提取请求头参数 → 校验AppKey → 校验时间戳 
→ 检查Nonce是否重复 → 提取全部业务参数 → 重算签名 
→ 对比签名 → 全部通过放行 / 任一失败拒绝
```

### 2. 双重防重放攻击

- **时间戳防护**: 请求必须在 ±5分钟 内到达服务器
- **随机串防护**: 每个 Nonce 在 Redis 中缓存 5 分钟，相同 Nonce 再次请求直接拒绝

### 3. 生产环境建议

✅ **升级加密算法**: 使用 HMAC-SHA256 替代 MD5  
✅ **动态密钥管理**: 从数据库查询客户端密钥，支持多平台独立密钥  
✅ **接口限流**: 基于 AppKey 维度做调用频率限制  
✅ **密钥轮换**: 支持秘钥平滑更新和版本兼容  
✅ **审计日志**: 记录每次签名的校验结果用于安全分析  
✅ **数据加密**: 敏感字段额外 AES 加密（双层保护）

---

## 常见问题

### Q: 如何添加免签名接口？
A: 在 Controller 方法上标注 `@NoSign` 注解即可：

```java
@NoSign
@GetMapping("/public/api")
public Result<String> publicApi() {
    return Result.success("无需签名");
}
```

### Q: 签名验证失败的常见原因？
1. 时间戳超过 5 分钟有效窗口
2. Nonce 重复使用（已被缓存）
3. 参数顺序或值与服务端不一致
4. AppKey 或 AppSecret 不匹配
5. JSON 请求体格式错误

### Q: 如何切换到 HMAC-SHA256？
修改 `SignInterceptor.java` 第 150 行左右，将 `verifySign` 改为 `verifySignHmac`。

---

## License

MIT License

---

## 参考文档

基于微信公众号文章《SpringBoot 接口签名验证（AppKey/Secret）》实现
