package com.example.springbootapisign.config;

import com.example.springbootapisign.interceptor.SignInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Web MVC 配置类 - 生产增强版
 * 功能：注册拦截器、启用异步处理、配置异步线程池
 */
@Configuration
@EnableAsync  // 启用异步支持（审计日志使用）
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private SignInterceptor signInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(signInterceptor)
                // 拦截所有API接口
                .addPathPatterns("/api/**")
                // 排除静态资源和健康检查（可选）
                .excludePathPatterns(
                        "/static/**",
                        "/favicon.ico",
                        "/error"
                );
    }

    /**
     * 配置异步请求处理线程池
     * 用于签名审计日志的异步写入，不阻塞主流程
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(5000); // 异步超时5秒
        configurer.setTaskExecutor(taskExecutor());
    }

    /**
     * 自定义异步任务执行器
     */
    private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor taskExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = 
            new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);           // 核心线程数
        executor.setMaxPoolSize(5);            // 最大线程数
        executor.setQueueCapacity(100);        // 队列容量
        executor.setThreadNamePrefix("sign-log-"); // 线程名前缀
        executor.setKeepAliveSeconds(60);      // 空闲线程存活时间
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()); // 拒绝策略：调用者运行
        return executor;
    }
}
