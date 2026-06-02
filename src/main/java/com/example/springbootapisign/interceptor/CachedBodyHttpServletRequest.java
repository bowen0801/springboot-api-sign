package com.example.springbootapisign.interceptor;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 可重复读取的请求体包装器
 * 
 * 问题背景：
 * 原始 HttpServletRequest 的 InputStream/Reader 只能读取一次，
 * 签名拦截器读取了 JSON body 后，Controller 的 @RequestBody 就无法再获取参数。
 * 
 * 解决方案：
 * 使用此包装器将请求体缓存到内存中，支持多次读取。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    /** 缓存的请求体字节数组 */
    private byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        // 首次读取时缓存请求体
        InputStream requestInputStream = request.getInputStream();
        this.cachedBody = toByteArray(requestInputStream);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new CachedBodyServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
        return new BufferedReader(new InputStreamReader(byteArrayInputStream, StandardCharsets.UTF_8));
    }

    /**
     * 获取缓存的请求体字符串
     */
    public String getCachedBodyAsString() {
        return new String(cachedBody, StandardCharsets.UTF_8);
    }

    /**
     * 将 InputStream 转为字节数组
     */
    private static byte[] toByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int bytesRead;
        byte[] data = new byte[1024];
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    /**
     * 自定义 ServletInputStream 实现
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream cachedBodyInputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.cachedBodyInputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return cachedBodyInputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public int read() {
            return cachedBodyInputStream.read();
        }
    }
}
