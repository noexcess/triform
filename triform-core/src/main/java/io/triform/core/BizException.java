package io.triform.core;

/**
 * 业务异常。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        super(message);
        this.code = ResultCode.SYSTEM_ERROR;
    }

    public int getCode() {
        return code;
    }
}
