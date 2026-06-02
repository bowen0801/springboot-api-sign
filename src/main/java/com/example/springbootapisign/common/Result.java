package com.example.springbootapisign.common;

import lombok.Data;

/**
 * 统一返回结果类
 */
@Data
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.setCode(400);
        result.setMsg(msg);
        return result;
    }

    /**
     * 未授权专用状态码
     */
    public static <T> Result<T> unAuth(String msg) {
        Result<T> result = new Result<>();
        result.setCode(401);
        result.setMsg(msg);
        return result;
    }

    @Override
    public String toString() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            return "{\"code\":" + code + ",\"msg\":\"" + msg + "\"}";
        }
    }
}
