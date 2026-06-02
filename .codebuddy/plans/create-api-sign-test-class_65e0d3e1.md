---
name: create-api-sign-test-class
overview: 创建一个使用 AppKey 进行 API 签名请求的 Spring Boot 集成测试类，覆盖 GET/POST JSON/POST Form 三种请求方式
todos:
  - id: add-test-dep
    content: 在 pom.xml 中添加 spring-boot-starter-test 测试依赖
    status: completed
  - id: create-test-config
    content: 创建 application-test.yml 测试配置文件（关闭限流、使用YAML默认密钥）
    status: completed
    dependencies:
      - add-test-config
  - id: create-api-sign-test
    content: 创建 ApiSignTest.java 主测试类，含签名头构建器和全部 10 个测试用例
    status: completed
    dependencies:
      - add-test-dep
---

## Product Overview

创建一个完整的 SpringBoot 接口签名验证集成测试类，使用 AppKey + Secret 进行签名请求，覆盖所有接口类型和异常场景的测试用例。

## Core Features

- **签名请求构造工具方法**：自动生成 Timestamp、Nonce、Sign，封装为请求头，供各测试用例复用
- **GET 签名请求测试**：对 `/api/get/data` 接口发起带签名的 GET 请求，验证 200 成功响应
- **POST 表单签名请求测试**：对 `/api/post/form` 接口发起带签名的表单 POST 请求
- **POST JSON 签名请求测试**：对 `/api/post/json` 接口发起带签名的 JSON POST 请求（验证 ContentCachingFilter 缓存机制）
- **免签名公开接口测试**：验证 `/api/public/info` 和 `/api/health` 无需签名即可访问
- **缺失请求头测试**：缺少 AppKey/Timestamp/Nonce/Sign 中任一参数时返回 401
- **无效 AppKey 测试**：传入错误 AppKey 时返回 "AppKey无效"
- **过期时间戳测试**：传入超过 5 分钟的时间戳时返回 "请求已过期"
- **重放攻击防护测试**：相同 Nonce 第二次请求被拦截返回 401
- **篡改签名测试**：正确参数但 Sign 被篡改时返回 "签名验证失败"

## Tech Stack

- **测试框架**: JUnit 5 (`spring-boot-starter-test` 自带)
- **Mock 框架**: Mockito (`spring-boot-starter-test` 自带)
- **测试方式**: `@SpringBootTest` + `@AutoConfigureMockMvc` 全量集成测试（包含 Filter 链、Interceptor 链、Controller 层）
- **外部依赖 Mock**: `@MockBean` 注入 `RedisUtil` 和 `AppClientMapper`（避免依赖真实 Redis 和 MySQL）
- **签名生成**: 直接复用项目已有的 `SignUtil.generateSign()` 工具类（与服务端拦截器使用完全相同的算法）

## Implementation Approach

采用全量 Spring Context 集成测试策略。核心思路：

1. **pom.xml 补充测试依赖**：添加 `spring-boot-starter-test`（当前项目缺失此依赖）
2. **测试配置文件**：创建 `application-test.yml`，关闭限流（避免干扰）、使用配置文件默认密钥模式
3. **主测试类**：`ApiSignTest.java`

- 使用 `@SpringBootTest(webEnvironment = MOCK)` 启动完整上下文
- 使用 `@AutoConfigureMockMvc` 注入 MockMvc
- 使用 `@MockBean` mock `RedisUtil`（默认 `setNonceIfAbsent` 返回 true 放行，`isRateLimited` 返回 false 不限流）和 `AppClientMapper`（不触发 DB 查询）
- 核心辅助方法 `buildSignHeaders(Map<String,Object> params)` 封装签名头构建逻辑：
    - 生成当前秒级时间戳 Timestamp
    - 生成 UUID 作为 Nonce
    - 用 `SignUtil.paramsSortJoin(params)` 排序拼接参数
    - 用 `SignUtil.generateSign(params, appSecret)` 计算 MD5 签名
    - 返回包含 AppKey / Timestamp / Nonce / Sign 四个头的 Header 设置
- 测试按场景分组：正向通过、免签名、各类失败拒绝

## Architecture Design

```
ApiSignTest (@SpringBootTest + @AutoConfigureMockMvc)
├── @MockBean RedisUtil        (模拟 Redis: Nonce 原子操作 + 限流)
├── @MockBean AppClientMapper  (模拟数据库: 多客户端查询)
├── buildSignHeaders()         (签名请求头构建器 - 复用 SignUtil)
│   ├── generate timestamp     当前秒级时间戳
│   ├── generate nonce         UUID 随机串  
│   └── generate sign          SignUtil.generateSign() MD5签名
├── Group 1: 正向测试           GET/POST Form/POST JSON 签名通过
├── Group 2: 免签名测试         public/info, health 无需签名
└── Group 3: 异常测试           缺失头/无效AppKey/过期时间戳/重放攻击/篡改签名
```

## Directory Structure

```
project-root/
├── pom.xml                                    # [MODIFY] 添加 spring-boot-starter-test 依赖
├── src/
│   ├── main/java/...                          # 已有源码（不动）
│   └── test/
│       └── java/com/example/springbootapisign/
│           ├── ApiSignTest.java               # [NEW] 主测试类 - 完整签名接口集成测试
│       └── resources/
│           └── application-test.yml           # [NEW] 测试专用配置文件
```

## Key Code Structures

```java
// 测试类核心结构
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:application-test.yml")
class ApiSignTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RedisUtil redisUtil;        // Mock Redis
    @MockBean private AppClientMapper appClientMapper; // Mock DB

    static final String APP_KEY = "test_app_key_123456";
    static final String APP_SECRET = "test_app_secret_654321_abcdef123456";

    @BeforeEach void setUp() {
        // 默认 mock 行为：Nonce 首次使用放行、不限流
        given(redisUtil.setNonceIfAbsent(any(), anyLong())).willReturn(true);
        given(redisUtil.isRateLimited(anyString(), anyInt(), anyInt())).willReturn(false);
    }

    /** 构建签名请求头 */
    MockHttpServletRequestBuilder signHeaders(MockHttpServletRequestBuilder builder, Map<String, Object> params) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString();
        String sign = SignUtil.generateSign(params, APP_SECRET);
        return builder.header("AppKey", APP_KEY).header("Timestamp", timestamp).header("Nonce", nonce).header("Sign", sign);
    }
}
```