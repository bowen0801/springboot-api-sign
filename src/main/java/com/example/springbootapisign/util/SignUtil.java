package com.example.springbootapisign.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 接口签名工具类 AppKey+Secret 完整版
 * 兼容GET/POST/JSON请求体、参数排序、加密、签名校验
 */
public class SignUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * MD5加密 32位大写
     */
    public static String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(str.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int i = b & 0xff;
                if (i < 16) {
                    sb.append("0");
                }
                sb.append(Integer.toHexString(i));
            }
            return sb.toString().toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("MD5签名加密异常");
        }
    }

    /**
     * HMAC-SHA256 加密 安全性高于MD5 生产推荐
     */
    public static String hmacSha256(String data, String secret) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] digest = mac.doFinal(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) sb.append("0");
                sb.append(hex);
            }
            return sb.toString().toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256加密异常");
        }
    }

    /**
     * 通用参数排序拼接 过滤空值参数
     */
    public static String paramsSortJoin(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        // 字典序升序排序 自动过滤null、空值参数
        TreeMap<String, Object> treeMap = new TreeMap<>();
        params.forEach((k, v) -> {
            if (v != null && !StringUtils.isEmpty(v.toString())) {
                treeMap.put(k, v);
            }
        });
        // 拼接 key=value&key=value
        return treeMap.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * 生成接口签名 MD5版本
     */
    public static String generateSign(Map<String, Object> params, String appSecret) {
        String paramStr = paramsSortJoin(params);
        // 拼接秘钥后加密
        String allStr = paramStr + appSecret;
        return md5(allStr);
    }

    /**
     * 生成接口签名 HMAC-SHA256版本（生产推荐）
     */
    public static String generateSignHmac(Map<String, Object> params, String appSecret) {
        String paramStr = paramsSortJoin(params);
        // 拼接秘钥后HMAC加密
        String allStr = paramStr + appSecret;
        return hmacSha256(allStr, appSecret);
    }

    /**
     * JSON对象转Map 兼容POST JSON请求体签名
     */
    public static Map<String, Object> jsonToMap(Object jsonData) {
        try {
            if (jsonData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) jsonData;
                return map;
            }
            return OBJECT_MAPPER.convertValue(jsonData, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * JSON字符串转Map
     */
    public static Map<String, Object> jsonStrToMap(String jsonStr) {
        try {
            return OBJECT_MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 签名统一校验 MD5版本
     */
    public static boolean verifySign(Map<String, Object> params, String appSecret, String clientSign) {
        if (StringUtils.isEmpty(clientSign)) {
            return false;
        }
        String serverSign = generateSign(params, appSecret);
        return serverSign.equals(clientSign);
    }

    /**
     * 签名统一校验 HMAC-SHA256版本（生产推荐）
     */
    public static boolean verifySignHmac(Map<String, Object> params, String appSecret, String clientSign) {
        if (StringUtils.isEmpty(clientSign)) {
            return false;
        }
        String serverSign = generateSignHmac(params, appSecret);
        return serverSign.equals(clientSign);
    }
}
