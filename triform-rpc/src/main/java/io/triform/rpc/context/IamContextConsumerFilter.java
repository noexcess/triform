package io.triform.rpc.context;

import io.triform.core.IamContext;
import io.triform.core.IamContextHolder;
import io.triform.core.session.SessionKeys;
import io.triform.core.trace.TraceKeys;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.MDC;

/**
 * Consumer 侧上下文透传过滤器（自动激活）：
 * 出站调用前把当前线程的登录 token 与 traceId/spanId 写入 RPC attachment，
 * Provider 侧据此重建 IamContext 并延续日志 traceId。
 * mono 模式不加载（Dubbo 自动装配被排除，本地 Bean 同线程天然传递）。
 *
 * <p>traceId 透传的必要性：sleuth 的自动埋点覆盖 web/feign/resttemplate 等，<b>不含 Dubbo</b>；
 * 若不透传，同一次跨服务调用在两侧日志里会是两个互不相干的 traceId，链路无法串联。
 */
@Activate(group = CommonConstants.CONSUMER, order = -10000)
public class IamContextConsumerFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String traceId = MDC.get(TraceKeys.MDC_TRACE_ID);
        String spanId = MDC.get(TraceKeys.MDC_SPAN_ID);
        boolean traceAttached = traceId != null && !traceId.isEmpty();
        if (traceAttached) {
            RpcContext.getClientAttachment().setAttachment(TraceKeys.RPC_TRACE_ID, traceId);
            if (spanId != null && !spanId.isEmpty()) {
                RpcContext.getClientAttachment().setAttachment(TraceKeys.RPC_SPAN_ID, spanId);
            }
        }
        IamContext ctx = IamContextHolder.get();
        boolean tokenAttached = ctx != null && ctx.getToken() != null;
        if (tokenAttached) {
            RpcContext.getClientAttachment().setAttachment(SessionKeys.HEADER_TOKEN, ctx.getToken());
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            if (traceAttached) {
                RpcContext.getClientAttachment().removeAttachment(TraceKeys.RPC_TRACE_ID);
                RpcContext.getClientAttachment().removeAttachment(TraceKeys.RPC_SPAN_ID);
            }
            if (tokenAttached) {
                RpcContext.getClientAttachment().removeAttachment(SessionKeys.HEADER_TOKEN);
            }
        }
    }
}
