package com.example.springbootapisign.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 带签名 URL 生成器
 *
 * <p>使用场景：生成可在浏览器地址栏直接访问的完整带签名 URL，
 * 用于接口调试、联调测试等。签名参数通过 Query Parameter 传递。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   String url = UrlSignGenerator.builder()
 *       .baseUrl("http://localhost:8080")
 *       .path("/api/get/data")
 *       .appKey("your_app_key")
 *       .appSecret("your_app_secret")
 *       .param("name", "张三")
 *       .param("age", "25")
 *       .build();
 *   System.out.println(url);
 * </pre>
 */
public class UrlSignGenerator {

    private String baseUrl = "http://localhost:8080";
    private String path;
    private String appKey;
    private String appSecret;
    private final Map<String, Object> params = new TreeMap<>();

    public static UrlSignGenerator builder() {
        return new UrlSignGenerator();
    }

    public UrlSignGenerator baseUrl(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return this;
    }

    public UrlSignGenerator path(String path) {
        this.path = path.startsWith("/") ? path : "/" + path;
        return this;
    }

    public UrlSignGenerator appKey(String appKey) {
        this.appKey = appKey;
        return this;
    }

    public UrlSignGenerator appSecret(String appSecret) {
        this.appSecret = appSecret;
        return this;
    }

    /**
     * 添加业务参数（自动按字典序排序）
     */
    public UrlSignGenerator param(String key, Object value) {
        if (key != null && value != null && !value.toString().isEmpty()) {
            params.put(key, value);
        }
        return this;
    }

    /**
     * 批量添加业务参数
     */
    public UrlSignGenerator params(Map<String, Object> paramMap) {
        if (paramMap != null) {
            paramMap.forEach(this::param);
        }
        return this;
    }

    /**
     * 构建完整的带签名 URL
     *
     * @return 完整URL，格式：baseUrl + path?bizParams&AppKey=xxx&Timestamp=xxx&Nonce=xxx&Sign=xxx
     */
    public String build() {
        // 1. 生成签名所需的元数据
        long timestampSeconds = System.currentTimeMillis() / 1000;
        String timestamp = String.valueOf(timestampSeconds);
        String nonce = UUID.randomUUID().toString();

        // 2. 使用与 SignUtil 完全相同的算法计算签名
        String sign = SignUtil.generateSign(params, appSecret);

        // 3. 构建 URL
        StringBuilder url = new StringBuilder();
        url.append(baseUrl).append(path).append("?");

        // 追加业务参数
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) url.append("&");
            url.append(encode(entry.getKey())).append("=").append(encode(entry.getValue().toString()));
            first = false;
        }

        // 追加签名四大参数
        if (!first) url.append("&");
        url.append("AppKey=").append(encode(appKey));
        url.append("&Timestamp=").append(encode(timestamp));
        url.append("&Nonce=").append(encode(nonce));
        url.append("&Sign=").append(encode(sign));

        return url.toString();
    }

    /**
     * 构建并打印 URL 到控制台（方便调试）
     */
    public String buildAndPrint() {
        String url = build();
        System.out.println("\n========== 带签名访问 URL ==========");
        System.out.println(url);
        System.out.println("======================================\n");
        return url;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
