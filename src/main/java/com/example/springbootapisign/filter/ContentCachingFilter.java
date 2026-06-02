package com.example.springbootapisign.filter;

import com.example.springbootapisign.interceptor.CachedBodyHttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.MediaType;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 请求体缓存过滤器
 * 
 * 作用：
 * 在签名拦截器之前执行，将 JSON 请求体缓存到内存，
 * 支持拦截器和 Controller 多次读取请求体。
 * 
 * 执行顺序：Filter → Interceptor → Controller
 * Order(1) 确保在所有其他过滤器和拦截器之前执行
 */
@Slf4j
@Component
@Order(1)
public class ContentCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, java.io.IOException {
        // 只对 POST/PUT/PATCH 且 Content-Type 为 JSON 的请求进行包装
        String method = request.getMethod();
        String contentType = request.getContentType();
        
        boolean shouldCache = (method != null && 
                ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))
                && contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE));

        if (shouldCache) {
            log.debug("[请求体缓存] 包装JSON请求: {} {}", method, request.getRequestURI());
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
            filterChain.doFilter(wrappedRequest, response);
        } else {
            // 非JSON请求直接放行
            filterChain.doFilter(request, response);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // 排除静态资源等不需要缓存的路径
        String path = request.getRequestURI();
        return path.startsWith("/static/") || path.startsWith("/favicon.ico");
    }
}
