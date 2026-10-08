package io.triform.core.web;

import io.triform.core.BizException;
import io.triform.core.Result;
import io.triform.core.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 平台全局异常处理（公共模块，所有服务共享）：
 * 统一 Result 结构返回，保证跨服务错误响应一致。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String message = e.getBindingResult().getFieldErrors().isEmpty()
                ? "参数错误"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.fail(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("unexpected error", e);
        // 生产脱敏:内部细节( SQL/类名等)只进日志,对外仅给追踪号便于排查
        String traceId = org.slf4j.MDC.get("traceId");
        String message = "系统异常，请稍后重试" + (traceId == null ? "" : "（追踪号 " + traceId + "）");
        return Result.fail(ResultCode.SYSTEM_ERROR, message);
    }
}
