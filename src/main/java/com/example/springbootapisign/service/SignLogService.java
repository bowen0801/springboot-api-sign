package com.example.springbootapisign.service;

import com.example.springbootapisign.entity.SignLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 签名审计日志服务
 * 异步写入数据库，不影响主流程性能
 */
@Slf4j
@Service
public class SignLogService {

    // 生产环境可以替换为异步队列或消息中间件

    /**
     * 异步保存签名日志（非阻塞）
     * 使用 @Async 注解实现异步执行，避免影响接口响应速度
     *
     * @param signLog 签名日志对象
     */
    @Async
    public void saveLogAsync(SignLog signLog) {
        try {
            // TODO: 集成 MyBatis Plus 后启用下面代码
            // signLogMapper.insert(signLog);
            
            // 当前版本先输出到日志（生产环境建议持久化）
            log.info("[签名审计] appKey={}, uri={}, method={}, result={}, ip={}, timestamp={}, nonce={}, error={}",
                    signLog.getAppKey(),
                    signLog.getRequestUri(),
                    signLog.getRequestMethod(),
                    signLog.getVerifyResult(),
                    signLog.getClientIp(),
                    signLog.getTimestamp(),
                    signLog.getNonce(),
                    signLog.getErrorMessage()
            );
        } catch (Exception e) {
            log.error("[签名审计] 保存日志异常", e);
            // 日志保存失败不影响主业务流程
        }
    }

    /**
     * 构建成功日志对象
     */
    public SignLog buildSuccessLog(String appKey, String uri, String method, 
                                    String clientIp, Long timestamp, 
                                    String nonce, String clientSign, 
                                    String serverSign, long costTime) {
        SignLog log = new SignLog();
        log.setAppKey(appKey);
        log.setRequestUri(uri);
        log.setRequestMethod(method);
        log.setClientIp(clientIp);
        log.setTimestamp(timestamp);
        log.setNonce(nonce);
        log.setClientSign(clientSign);
        log.setServerSign(serverSign);
        log.setVerifyResult("SUCCESS");
        log.setCostTime(costTime);
        return log;
    }

    /**
     * 构建失败日志对象
     */
    public SignLog buildFailLog(String appKey, String uri, String method,
                                 String clientIp, Long timestamp,
                                 String nonce, String clientSign,
                                 String result, String errorMsg) {
        SignLog log = new SignLog();
        log.setAppKey(appKey);
        log.setRequestUri(uri);
        log.setRequestMethod(method);
        log.setClientIp(clientIp);
        log.setTimestamp(timestamp);
        log.setNonce(nonce);
        log.setClientSign(clientSign);
        log.setVerifyResult(result);
        log.setErrorMessage(errorMsg);
        return log;
    }
}
