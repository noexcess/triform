package io.triform.core;

import java.io.Serializable;

/**
 * 统一返回结果。
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS, "ok", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS, "ok", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(ResultCode.SYSTEM_ERROR, message, null);
    }

    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS;
    }

    /** 追踪号(与日志/跨服务透传同源,sleuth MDC):每个响应都带,用户报障贴号即可全链路定位 */
    public String getTraceId() {
        return org.slf4j.MDC.get("traceId");
    }

    /** 序列化时刻(毫秒) */
    public long getTimestamp() {
        return System.currentTimeMillis();
    }

    /** 部署环境标识(triform.env,非生产如 TEST;生产为空) */
    public String getEnv() {
        return EnvHolder.get();
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
