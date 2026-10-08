package io.triform.core.trace;

/**
 * 观测性常量:MDC 键与 RPC 透传键。
 *
 * <p>traceId 由 sleuth 在 HTTP 入口生成并写入 MDC(键名 {@link #MDC_TRACE_ID}),
 * 日志格式通过 {@code logging.pattern.level} 输出该值,使同一次请求的日志可被 grep 串联。
 *
 * <p>跨服务:Dubbo 不在 sleuth 的自动埋点范围内(其支持 web/feign/resttemplate 等),
 * 故由 triform-rpc 的两个过滤器在 RPC attachment 中携带 {@link #RPC_TRACE_ID},
 * 使 provider 侧日志与调用方使用同一个 traceId。provider 不新建 span(仅继承调用方标识),
 * 因此这批观测不产出调用链拓扑图——如需拓扑需接入 sleuth+zipkin 并实现 Dubbo 的 span 提取。
 */
public final class TraceKeys {

    private TraceKeys() {
    }

    /** sleuth 写入 MDC 的 traceId 键(Brave 的 Slf4jScopeDecorator 约定)。 */
    public static final String MDC_TRACE_ID = "traceId";

    /** sleuth 写入 MDC 的 spanId 键。 */
    public static final String MDC_SPAN_ID = "spanId";

    /** Dubbo attachment 中透传 traceId 的键(自定义前缀,避免与 B3 头部混淆)。 */
    public static final String RPC_TRACE_ID = "ticket-trace-id";

    /** Dubbo attachment 中透传 spanId 的键。 */
    public static final String RPC_SPAN_ID = "ticket-span-id";
}
