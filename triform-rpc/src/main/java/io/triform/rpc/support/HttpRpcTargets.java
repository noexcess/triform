package io.triform.rpc.support;

/**
 * HTTP(备选)传输的约定:路径、请求头、服务名映射。
 *
 * <p>服务名解析:优先读配置 {@code triform.rpc.http.service-names.<facadeFqn>};
 * 未配置时退回历史包名约定(仅对 io.triform.<svc>.api... 形态兜底)。
 */
public final class HttpRpcTargets {

    /** 内部 RPC 端点的根路径。网关必须拒绝外部访问该前缀(见《11-备选传输方案》§4)。 */
    public static final String BASE_PATH = "/internal/rpc";

    /** 内部鉴权令牌请求头(必须与 triform.rpc.internal-token 一致)。 */
    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    /** 会话令牌请求头(与 Dubbo 侧 attachment 用的常量一致)。 */
    public static final String HEADER_TOKEN = "X-Token";

    /** traceId / spanId 请求头(与 Dubbo 侧 attachment 的键名一致,便于对照排查)。 */
    public static final String HEADER_TRACE_ID = "triform-trace-id";
    public static final String HEADER_SPAN_ID = "triform-span-id";

    private HttpRpcTargets() {
    }

    /**
     * legacy 包名约定兜底: 取包路径中 "api" 段的前一段作为服务名段 ——
     * 同时兼容 io.triform.&lt;svc&gt;.api...(中性化后, svc=api 前一段) 与历史 com.p0.ticket.&lt;svc&gt;.api... 布局。
     * C-2 起仅为 HttpServiceNameResolver 三级解析的最末级; "api" 段之前不足两段(如 lab.api.xxx)则拒绝,
     * 避免对任意包布局静默产出错误目标。
     */
    public static String legacyServiceNameOf(String facadeFqn) {
        String[] parts = facadeFqn.split("\\.");
        for (int i = 3; i < parts.length; i++) {
            if ("api".equals(parts[i])) {
                return "ticket-" + parts[i - 1];
            }
        }
        throw new IllegalArgumentException("无法按包名约定解析 Facade 目标服务名(非 <group...>.<svc>.api 布局): "
                + facadeFqn + " —— 请配置 triform.rpc.http.service-names 或依赖注册中心元数据发现");
    }

    /** 组装调用路径:{@code /internal/rpc/{facadeFqn}/{methodName}}。 */
    public static String pathOf(String facadeFqn, String methodName) {
        return BASE_PATH + "/" + facadeFqn + "/" + methodName;
    }
}
