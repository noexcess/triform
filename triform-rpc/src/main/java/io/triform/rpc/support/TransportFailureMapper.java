package io.triform.rpc.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.triform.core.BizException;
import io.triform.core.ResultCode;

/**
 * 把 HTTP 传输的失败响应映射成业务异常 —— 关键是**保留提供侧的业务错误码**。
 *
 * <p>提供侧对业务异常返回 400 + {@code Result{code,message}};若消费侧只取 HTTP status,
 * 业务码(如 10010 无权限、10011 锁定)在切换传输后会静默丢失,调用方据码分支的逻辑随之失效。
 * 故这里优先解析错误体还原 code/message;解析不出(非本系统响应、被网关拦截)才退回通用错误码,
 * 并补上能指向根因的提示(403 令牌不一致 / 404 未开 http-expose)。
 *
 * <p>抽成静态工具是为了可单测(映射逻辑在代理 Bean 里难以直接测)。
 */
public final class TransportFailureMapper {

    private TransportFailureMapper() {
    }

    /**
     * @param where  调用点描述(如 {@code PermissionFacade#visibleScope}),用于错误信息定位
     * @param status HTTP 状态码
     * @param body   响应体原文(可为 null/空)
     */
    public static BizException map(String where, int status, String body, ObjectMapper mapper) {
        JsonNode parsed = parse(body, mapper);
        if (parsed != null && parsed.hasNonNull("code")) {
            int code = parsed.get("code").asInt();
            String message = parsed.hasNonNull("message") ? parsed.get("message").asText() : "";
            // 业务错误码原样透传(10010/10011/... 与 Dubbo 侧完全一致)
            return new BizException(code, message);
        }
        switch (status) {
            case 403:
                return new BizException(ResultCode.PERMISSION_DENIED,
                        "远程调用被拒(" + where + "):内部令牌不一致 —— 消费侧与提供侧的 triform.rpc.internal-token 必须相同");
            case 404:
                return new BizException(ResultCode.SYSTEM_ERROR,
                        "远程调用失败(" + where + "):提供侧未开启 triform.rpc.http-expose,或该 Facade 不在其白名单内");
            default:
                return new BizException(ResultCode.SYSTEM_ERROR,
                        "远程调用失败(" + where + "): HTTP " + status);
        }
    }

    private static JsonNode parse(String body, ObjectMapper mapper) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }
}
