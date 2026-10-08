package io.triform.rpc.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 内部 RPC 端点的应用内路径匹配(D1): 宿主配置 server.servlet.context-path 后,
 * requestURI 携带前缀(/app/internal/rpc/...), 直接 startsTo 会同时绕过鉴权过滤器与上下文恢复过滤器。
 * 统一按"剥离 contextPath 后的应用内路径"匹配。
 */
public final class InternalPaths {

    private InternalPaths() {
    }

    /** 剥离 contextPath 的应用内路径 */
    public static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /** 是否内部 RPC 端点(应用内路径口径) */
    public static boolean isInternalRpc(HttpServletRequest request) {
        return pathOf(request).startsWith(HttpRpcTargets.BASE_PATH + "/");
    }
}
