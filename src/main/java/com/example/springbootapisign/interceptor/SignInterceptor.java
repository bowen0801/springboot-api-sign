package com.example.springbootapisign.interceptor;

import com.example.springbootapisign.annotation.NoSign;
import com.example.springbootapisign.common.Result;
import com.example.springbootapisign.entity.AppClient;
import com.example.springbootapisign.entity.SignLog;
import com.example.springbootapisign.mapper.AppClientMapper;
import com.example.springbootapisign.service.SignLogService;
import com.example.springbootapisign.util.RedisUtil;
import com.example.springbootapisign.util.SignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局签名校验拦截器 - 生产增强版
 *
 * 核心特性：
 * ✅ 多客户端数据库动态密钥管理
 * ✅ 原子性 Nonce 防重放（Redis SET NX EX）
 * ✅ 接口调用频率限制（基于 AppKey 维度）
 * ✅ HMAC-SHA256 高安全加密（可配置切换）
 * ✅ 完整的审计日志记录
 * ✅ 请求体可重复读取（解决 JSON 只能读一次问题）
 * ✅ 统一异常处理和友好错误响应
 * ✅ 详细的日志输出便于排查
 */
@Slf4j
@Component
public class SignInterceptor implements HandlerInterceptor {

    @Value("${api.sign-expire}")
    private long signExpire;

    @Value("${api.app.key:#{null}}")
    private String defaultAppKey;

    @Value("${api.app.secret:#{null}}")
    private String defaultAppSecret;

    /** 是否启用数据库查询（生产环境设为 true） */
    @Value("${api.use-database:false}")
    private boolean useDatabase;

    /** 是否使用 HMAC-SHA256 加密（推荐 true） */
    @Value("${api.use-hmac:false}")
    private boolean useHmac;

    /** 是否启用接口限流 */
    @Value("${api.enable-rate-limit:true}")
    private boolean enableRateLimit;

    /** 默认限流次数（每分钟） */
    @Value("${api.rate-limit-count:1000}")
    private int rateLimitCount;

    private final RedisUtil redisUtil;
    private final AppClientMapper appClientMapper;
    private final SignLogService signLogService;

    public SignInterceptor(RedisUtil redisUtil, 
                           AppClientMapper appClientMapper,
                           SignLogService signLogService) {
        this.redisUtil = redisUtil;
        this.appClientMapper = appClientMapper;
        this.signLogService = signLogService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        long startTime = System.currentTimeMillis();
        
        // 设置响应编码
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();

        try {
            // 非Controller接口直接放行
            if (!(handler instanceof HandlerMethod)) {
                return true;
            }

            HandlerMethod handlerMethod = (HandlerMethod) handler;

            // 标注@NoSign注解 免签名直接放行
            if (handlerMethod.hasMethodAnnotation(NoSign.class)) {
                return true;
            }

            // 获取请求基本信息
            String requestUri = request.getRequestURI();
            String method = request.getMethod();
            String clientIp = getClientIp(request);

            log.debug("[签名校验] 开始处理请求: {} {}, IP={}", method, requestUri, clientIp);

            // ============================================
            // 1. 获取四大核心参数（Header 优先，Query Parameter 兜底）
            //    支持 Postman/Header 方式 和 浏览器URL参数方式
            // ============================================
            String clientAppKey = getHeaderValue(request, "AppKey");
            String timestampStr = getHeaderValue(request, "Timestamp");
            String nonce = getHeaderValue(request, "Nonce");
            String clientSign = getHeaderValue(request, "Sign");

            // 头部参数非空统一校验
            if (clientAppKey == null || timestampStr == null || nonce == null || clientSign == null) {
                log.warn("[签名校验失败] 请求头参数缺失, ip={}, uri={}, missingHeaders={}", 
                        clientIp, requestUri, getMissingHeaders(clientAppKey, timestampStr, nonce, clientSign));
                
                // 记录审计日志
                signLogService.saveLogAsync(signLogService.buildFailLog(
                    clientAppKey, requestUri, method, clientIp, null, nonce, clientSign,
                    "FAIL_HEADERS", "请求头参数缺失，请携带AppKey、Timestamp、Nonce、Sign"
                ));
                
                writeErrorResponse(writer, "请求头参数缺失，请携带AppKey、Timestamp、Nonce、Sign");
                return false;
            }

            // ============================================
            // 2. 校验AppKey合法性并获取密钥（支持多客户端）
            // ============================================            
            String appSecret = getAppSecret(clientAppKey);
            if (appSecret == null) {
                log.warn("[签名校验失败] AppKey无效, appKey={}, ip={}", clientAppKey, clientIp);
                
                signLogService.saveLogAsync(signLogService.buildFailLog(
                    clientAppKey, requestUri, method, clientIp, parseLong(timestampStr), 
                    nonce, clientSign, "FAIL_APPKEY", "AppKey无效，客户端身份认证失败"
                ));
                
                writeErrorResponse(writer, "AppKey无效，客户端身份认证失败");
                return false;
            }

            // ============================================
            // 3. 时间戳校验 拦截过期请求
            // ============================================
            long requestTime = parseTimestamp(timestampStr);
            if (requestTime == -1) {
                signLogService.saveLogAsync(signLogService.buildFailLog(
                    clientAppKey, requestUri, method, clientIp, null, nonce, clientSign,
                    "FAIL_TIMESTAMP_FORMAT", "时间戳格式错误"
                ));
                writeErrorResponse(writer, "时间戳格式错误");
                return false;
            }
            
            long nowTime = System.currentTimeMillis() / 1000;
            long diff = Math.abs(nowTime - requestTime);
            if (diff > signExpire) {
                log.warn("[签名校验失败] 请求已过期, appKey={}, diff={}秒, 有效期={}秒", 
                        clientAppKey, diff, signExpire);
                
                signLogService.saveLogAsync(signLogService.buildFailLog(
                    clientAppKey, requestUri, method, clientIp, requestTime, nonce, clientSign,
                    "FAIL_TIMESTAMP_EXPIRED", "请求已过期，请重新发起请求"
                ));
                
                writeErrorResponse(writer, "请求已过期，请重新发起请求");
                return false;
            }

            // ============================================
            // 4. 接口限流检查（可选功能）
            // ============================================
            if (enableRateLimit && isRateLimited(clientAppKey)) {
                log.warn("[签名限流] 触发频率限制, appKey={}, ip={}", clientAppKey, clientIp);
                
                signLogService.saveLogAsync(signLogService.buildFailLog(
                    clientAppKey, requestUri, method, clientIp, requestTime, nonce, clientSign,
                    "FAIL_RATELIMIT", "接口调用频率超限，请稍后重试"
                ));
                
                // 限流返回429状态码
                response.setStatus(429);
                writeErrorResponse(writer, "接口调用频率超限，请稍后重试");
                return false;
            }

            // ============================================
            // 5. 唯一随机串防重放校验（原子操作）
            // ============================================
            boolean isFirstRequest = redisUtil.setNonceIfAbsent(nonce, signExpire);
            if (!isFirstRequest) {
                log.warn("[签名校验失败] 重复请求被拦截, appKey={}, nonce={}", clientAppKey, nonce);
                
                signLogService.saveLogAsync(signLogService.buildFailLog(
                    clientAppKey, requestUri, method, clientIp, requestTime, nonce, clientSign,
                    "FAIL_NONCE", "重复请求，接口已拦截，禁止重放调用"
                ));
                
                writeErrorResponse(writer, "重复请求，接口已拦截，禁止重放调用");
                return false;
            }

            // ============================================
            // 6. 提取所有请求参数（兼容GET/POST表单/JSON）
            // ============================================
            Map<String, Object> paramMap = extractAllParams(request);

            // ============================================
            // 7. 服务端重新计算签名并校验
            // ============================================
            String serverSign;
            boolean verifyResult;
            
            if (useHmac) {
                serverSign = SignUtil.generateSignHmac(paramMap, appSecret);
                verifyResult = SignUtil.verifySignHmac(paramMap, appSecret, clientSign);
            } else {
                serverSign = SignUtil.generateSign(paramMap, appSecret);
                verifyResult = SignUtil.verifySign(paramMap, appSecret, clientSign);
            }

            if (!verifyResult) {
                log.error("[签名校验失败] 签名不匹配, appKey={}, clientSign={}, serverSign={}", 
                         clientAppKey, clientSign, serverSign);
                
                signLogService.saveLogAsync(signLogService.buildFailLog(
                    clientAppKey, requestUri, method, clientIp, requestTime, nonce, 
                    clientSign, "FAIL_SIGN", "接口签名验证失败，参数可能被篡改或秘钥不匹配"
                ));
                
                writeErrorResponse(writer, "接口签名验证失败，参数可能被篡改或秘钥不匹配");
                return false;
            }

            // ============================================
            // ✅ 全部校验通过 - 记录成功日志
            // ============================================
            long costTime = System.currentTimeMillis() - startTime;
            log.info("[签名校验通过] appKey={}, uri={}, cost={}ms, encrypt={}", 
                     clientAppKey, requestUri, costTime, useHmac ? "HMAC-SHA256" : "MD5");

            signLogService.saveLogAsync(signLogService.buildSuccessLog(
                clientAppKey, requestUri, method, clientIp, requestTime, 
                nonce, clientSign, serverSign, costTime
            ));

            // 在响应头中返回剩余调用次数信息（可选）
            if (enableRateLimit) {
                long currentCount = redisUtil.getCurrentRequestCount(clientAppKey);
                response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, rateLimitCount - currentCount)));
            }

            return true;

        } catch (Exception e) {
            log.error("[签名校验异常] 系统内部错误", e);
            
            // 记录异常日志
            signLogService.saveLogAsync(new SignLog());
            
            response.setStatus(500);
            writeErrorResponse(writer, "系统内部错误，请联系管理员");
            return false;
        }
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取缺失的请求头/参数列表（用于详细错误提示）
     */
    private String getMissingHeaders(String appKey, String timestamp, String nonce, String sign) {
        StringBuilder sb = new StringBuilder();
        if (appKey == null) sb.append("AppKey ");
        if (timestamp == null) sb.append("Timestamp ");
        if (nonce == null) sb.append("Nonce ");
        if (sign == null) sb.append("Sign ");
        return sb.toString().trim();
    }

    /**
     * 获取签名参数值（Header 优先，Query Parameter 兜底）
     * 支持两种调用方式：
     * - HTTP Header 方式（Postman、curl 等工具推荐）
     * - URL Query Parameter 方式（浏览器直接访问、调试便捷）
     */
    private String getHeaderValue(HttpServletRequest request, String name) {
        // 优先从 Header 获取
        String value = request.getHeader(name);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        // 兜底：从 Query Parameter 获取
        value = request.getParameter(name);
        return value;
    }

    /**
     * 安全解析时间戳字符串
     */
    private long parseTimestamp(String timestampStr) {
        try {
            return Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            return -1; // 表示格式错误
        }
    }

    /**
     * 简化的 Long 解析方法（用于审计日志）
     */
    private Long parseLong(String str) {
        try {
            return Long.parseLong(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 动态获取 AppSecret（支持多客户端 + 兜底默认值）
     */
    private String getAppSecret(String appKey) {
        // 方式1：从数据库动态查询（生产环境推荐）
        if (useDatabase) {
            try {
                AppClient client = appClientMapper.selectActiveByAppKey(appKey);
                if (client != null) {
                    log.debug("[密钥查询] 数据库命中: appKey={}, appName={}", appKey, client.getAppName());
                    return client.getAppSecret();
                }
            } catch (Exception e) {
                log.error("[密钥查询] 数据库查询异常, appKey={}", appKey, e);
                // 数据库异常时降级使用配置文件
            }
        }

        // 方式2：使用配置文件中的默认值（开发/测试环境）
        if (defaultAppKey != null && defaultAppKey.equals(appKey)) {
            log.debug("[密钥查询] 配置文件命中: appKey={}", appKey);
            return defaultAppSecret;
        }

        // 未找到对应的密钥
        return null;
    }

    /**
     * 检查是否触发限流
     */
    private boolean isRateLimited(String appKey) {
        // 60秒时间窗口内的限流检查
        return redisUtil.isRateLimited(appKey, rateLimitCount, 60);
    }

    /**
     * 提取所有请求参数（兼容 GET/POST表单/POST JSON）
     */
    private Map<String, Object> extractAllParams(HttpServletRequest request) throws Exception {
        Map<String, Object> paramMap = new HashMap<>();
        String contentType = request.getContentType();

        // 1. GET、POST表单参数提取
        if (request.getParameterMap() != null) {
            request.getParameterMap().forEach((k, v) -> {
                if (v != null && v.length > 0) {
                    paramMap.put(k, v[0]);
                }
            });
        }

        // 2. 兼容 POST JSON 请求体参数全部参与签名
        if (contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            StringBuilder jsonBody = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBody.append(line);
                }
            }

            String jsonStr = jsonBody.toString();
            if (!jsonStr.isEmpty()) {
                Map<String, Object> bodyMap = SignUtil.jsonStrToMap(jsonStr);
                paramMap.putAll(bodyMap);
                log.debug("[参数提取] JSON body 参数数量: {}", bodyMap.size());
            }
        }

        return paramMap;
    }

    /**
     * 统一错误响应写入方法
     */
    private void writeErrorResponse(PrintWriter writer, String errorMsg) {
        writer.write(Result.unAuth(errorMsg).toString());
        writer.flush();
    }
}
