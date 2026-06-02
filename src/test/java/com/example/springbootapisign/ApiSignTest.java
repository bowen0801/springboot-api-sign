package com.example.springbootapisign;

import com.example.springbootapisign.mapper.AppClientMapper;
import com.example.springbootapisign.util.RedisUtil;
import com.example.springbootapisign.util.UrlSignGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 签名 URL 生成测试
 *
 * <p>核心功能：根据 AppKey/Secret + 业务参数，生成可在浏览器直接访问的完整带签名 URL。
 * 签名参数（AppKey、Timestamp、Nonce、Sign）通过 Query Parameter 传递，
 * 兼容 SignInterceptor 的 Header/Parameter 双模式读取策略。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSignTest {

    private static final String APP_KEY = "test_app_key_123456";
    private static final String APP_SECRET = "test_app_secret_654321_abcdef123456";
    private static final String BASE_URL = "http://localhost:8080";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RedisUtil redisUtil;

    @MockBean
    private AppClientMapper appClientMapper;

    @BeforeEach
    void setUp() {
        given(redisUtil.setNonceIfAbsent(anyString(), anyLong())).willReturn(true);
    }

    // ============================================================
    // 核心：生成浏览器可访问的带签名 URL
    // ============================================================

    @Nested
    @DisplayName("URL 生成器 - 输出可浏览器访问的链接")
    class UrlGeneratorTests {

        @Test
        @DisplayName("GET /api/get/data 无参数 - 生成签名URL")
        void generateGetUrlNoParams() {
            UrlSignGenerator.builder()
                    .baseUrl(BASE_URL)
                    .path("/api/get/data")
                    .appKey(APP_KEY)
                    .appSecret(APP_SECRET)
                    .buildAndPrint();

            System.out.println("[TEST] 复制以上URL到浏览器地址栏即可访问");
        }

        @Test
        @DisplayName("GET /api/get/data 带业务参数 - 生成签名URL")
        void generateGetUrlWithParams() {
            UrlSignGenerator.builder()
                    .baseUrl(BASE_URL)
                    .path("/api/get/data")
                    .appKey(APP_KEY)
                    .appSecret(APP_SECRET)
                    .param("name", "张三")
                    .param("age", "25")
                    .param("city", "北京")
                    .buildAndPrint();

            System.out.println("[TEST] 复制以上URL到浏览器地址栏即可访问");
        }

        @Test
        @DisplayName("自定义 baseUrl - 适配不同环境")
        void generateUrlWithCustomBaseUrl() {
            // 开发环境
            UrlSignGenerator.builder()
                    .baseUrl("http://dev.example.com:8080")
                    .path("/api/get/data")
                    .appKey(APP_KEY)
                    .appSecret(APP_SECRET)
                    .param("page", "1")
                    .param("size", "10")
                    .buildAndPrint();
        }
    }

    // ============================================================
    // 验证：生成的 URL 通过服务端签名校验
    // ============================================================

    @Nested
    @DisplayName("签名校验验证 - 确认生成的 URL 可通过拦截器")
    class SignatureVerificationTests {

        @Test
        @DisplayName("无参数请求 - 签名正确应返回200")
        void testNoParamsSignatureValid() throws Exception {
            String url = UrlSignGenerator.builder()
                    .baseUrl(BASE_URL)
                    .path("/api/get/data")
                    .appKey(APP_KEY)
                    .appSecret(APP_SECRET)
                    .build();

            // 从 URL 提取 query string 作为请求参数
            mockMvc.perform(get(url.substring(url.indexOf("/api/get/data"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("带多个业务参数 - 签名正确应返回200")
        void testWithParamsSignatureValid() throws Exception {
            Map<String, Object> bizParams = new HashMap<>();
            bizParams.put("name", "张三");
            bizParams.put("age", "25");

            String url = UrlSignGenerator.builder()
                    .baseUrl(BASE_URL)
                    .path("/api/get/data")
                    .appKey(APP_KEY)
                    .appSecret(APP_SECRET)
                    .params(bizParams)
                    .build();

            System.out.println("\n[验证URL] " + url + "\n");

            mockMvc.perform(get(url.substring(url.indexOf("/api/get/data"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.message").value("GET请求成功"));
        }

        @Test
        @DisplayName("缺少 AppKey 参数 - 应返回401")
        void testMissingAppKeyInUrl() throws Exception {
            mockMvc.perform(get("/api/get/data")
                            .param("Timestamp", String.valueOf(System.currentTimeMillis() / 1000))
                            .param("Nonce", java.util.UUID.randomUUID().toString())
                            .param("Sign", "fake_sign"))
                   .andExpect(status().isUnauthorized())
                   .andExpect(jsonPath("$.code").value(401))
                   .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("AppKey无效")));
        }

        @Test
        @DisplayName("篡改签名参数 - 应返回401")
        void testTamperedSignInUrl() throws Exception {
            String correctUrl = UrlSignGenerator.builder()
                    .baseUrl(BASE_URL)
                    .path("/api/get/data")
                    .appKey(APP_KEY)
                    .appSecret(APP_SECRET)
                    .param("name", "bowen")
                    .build();

            // 截取路径部分，手动篡改 Sign 参数
            String pathWithQuery = correctUrl.substring(correctUrl.indexOf("/api/get/data"));
            String tamperedQuery = pathWithQuery.replaceAll("&Sign=[^&]*", "&Sign=TAMPERED_SIGN_VALUE");

            mockMvc.perform(get(tamperedQuery))
                   .andExpect(status().isUnauthorized())
                   .andExpect(jsonPath("$.code").value(401))
                   .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("签名验证失败")));
        }

        @Test
        @DisplayName("过期时间戳 - 应返回401")
        void testExpiredTimestampInUrl() throws Exception {
            long expiredTs = System.currentTimeMillis() / 1000 - 600; // 10分钟前

            mockMvc.perform(get("/api/get/data")
                            .param("AppKey", APP_KEY)
                            .param("Timestamp", String.valueOf(expiredTs))
                            .param("Nonce", java.util.UUID.randomUUID().toString())
                            .param("Sign", com.example.springbootapisign.util.SignUtil.generateSign(new HashMap<>(), APP_SECRET)))
                   .andExpect(status().isUnauthorized())
                   .andExpect(jsonPath("$.code").value(401))
                   .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("请求已过期")));
        }
    }

    // ============================================================
    // 免签名接口 - 无需任何签名参数即可访问
    // ============================================================

    @Nested
    @DisplayName("免签名接口 - @NoSign 标注")
    class NoSignTests {

        @Test
        @DisplayName("健康检查 - 无需签名")
        void testHealthEndpoint() throws Exception {
            mockMvc.perform(get("/api/health"))
                   .andExpect(status().isOk());
        }

        @Test
        @DisplayName("公开信息 - 无需签名")
        void testPublicInfo() throws Exception {
            mockMvc.perform(get("/api/public/info"))
                   .andExpect(status().isOk());
        }
    }
}
