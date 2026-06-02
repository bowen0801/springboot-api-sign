package com.example.springbootapisign.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Redis工具类 生产增强版
 * 功能：原子防重放、接口限流、缓存管理
 */
@Slf4j
@Component
public class RedisUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String NONCE_PREFIX = "api:sign:nonce:";
    private static final String RATE_LIMIT_PREFIX = "api:sign:rate:";

    /**
     * 原子性检查并设置随机串（防重放）
     * 使用 Redis SET NX EX 命令保证原子性，避免并发竞态条件
     *
     * @param nonce       随机串
     * @param expireSecond 过期时间（秒）
     * @return true=首次使用(放行), false=重复请求(拒绝)
     */
    public boolean setNonceIfAbsent(String nonce, long expireSecond) {
        try {
            String key = NONCE_PREFIX + nonce;
            Boolean result = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", expireSecond, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Redis 设置 Nonce 缓存异常, nonce={}", nonce, e);
            // Redis 异常时选择拒绝请求（安全优先原则）
            return false;
        }
    }

    /**
     * 判断随机串是否已存在（兼容旧逻辑）
     */
    public boolean isNonceExist(String nonce) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(NONCE_PREFIX + nonce));
        } catch (Exception e) {
            log.error("Redis 检查 Nonce 异常", e);
            return true; // 异常时视为已存在，拒绝请求
        }
    }

    /**
     * 存入随机串 绑定过期时间（非原子操作，建议用 setNonceIfAbsent）
     */
    public void setNonceCache(String nonce, long expireSecond) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(NONCE_PREFIX + nonce, "1", expireSecond, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis 存储NonCE异常", e);
        }
    }

    /**
     * 接口调用限流检查（滑动窗口计数器算法）
     *
     * @param appKey       客户端标识
     * @param maxRequests  时间窗口内最大请求数
     * @param windowSeconds 时间窗口大小（秒）
     * @return true=未超限(放行), false=已超限(限流)
     */
    public boolean isRateLimited(String appKey, int maxRequests, int windowSeconds) {
        try {
            String key = RATE_LIMIT_PREFIX + appKey;
            Long currentCount = stringRedisTemplate.opsForValue().increment(key);

            if (currentCount != null && currentCount == 1) {
                // 首次访问，设置过期时间
                stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }

            return currentCount != null && currentCount > maxRequests;
        } catch (Exception e) {
            log.error("Redis 限流检查异常, appKey={}", appKey, e);
            // 限流组件异常时不阻塞请求（可用性优先）
            return false;
        }
    }

    /**
     * 获取当前 AppKey 的调用次数（用于响应头返回）
     */
    public long getCurrentRequestCount(String appKey) {
        try {
            String count = stringRedisTemplate.opsForValue().get(RATE_LIMIT_PREFIX + appKey);
            return count == null ? 0 : Long.parseLong(count);
        } catch (Exception e) {
            return 0;
        }
    }
}
