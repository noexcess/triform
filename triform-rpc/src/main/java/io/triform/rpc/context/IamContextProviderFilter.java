package io.triform.rpc.context;

import io.triform.core.IamContext;
import io.triform.core.IamContextHolder;
import io.triform.core.session.IamContextLoader;
import io.triform.core.session.IamSpringBridge;
import io.triform.core.session.SessionKeys;
import io.triform.core.session.SessionSnapshotDto;
import io.triform.core.trace.TraceKeys;
import io.triform.rpc.support.SessionRequiredChecker;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Provider 侧上下文重建过滤器（自动激活，最外层）：
 * 从 RPC attachment 读 token，查 Redis 会话快照重建 IamContext，使数据权限/审计在下游服务
 * 与 HTTP 入口行为一致。token 缺失或会话不存在时按内部调用放行（无上下文，自动数据权限跳过）——
 * RPC 层只做传递不做鉴权（设计《上下文跨服务与异步传递设计》§1 信任边界）。
 *
 * <p>同时延续调用方的 traceId/spanId 到本线程 MDC，使下游日志与上游可被同一 ID 串联
 * （Dubbo 不在 sleuth 自动埋点范围内，故需手工接续）。仅继承标识、不新建 span。
 */
@Activate(group = CommonConstants.PROVIDER, order = -10000)
public class IamContextProviderFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(IamContextProviderFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String traceId = RpcContext.getServerAttachment().getAttachment(TraceKeys.RPC_TRACE_ID);
        String spanId = RpcContext.getServerAttachment().getAttachment(TraceKeys.RPC_SPAN_ID);
        boolean traceInjected = traceId != null && !traceId.isEmpty();
        if (traceInjected) {
            MDC.put(TraceKeys.MDC_TRACE_ID, traceId);
            if (spanId != null && !spanId.isEmpty()) {
                MDC.put(TraceKeys.MDC_SPAN_ID, spanId);
            }
        }
        try {
            return invokeWithContext(invoker, invocation);
        } finally {
            if (traceInjected) {
                MDC.remove(TraceKeys.MDC_TRACE_ID);
                MDC.remove(TraceKeys.MDC_SPAN_ID);
            }
        }
    }

    private Result invokeWithContext(Invoker<?> invoker, Invocation invocation) {
        String token = RpcContext.getServerAttachment().getAttachment(SessionKeys.HEADER_TOKEN);
        if (token == null || token.isEmpty()) {
            return proceedChecked(invoker, invocation);
        }
        StringRedisTemplate redis = IamSpringBridge.redisTemplate();
        if (redis == null) {
            log.debug("iam provider filter: no redis template, skip context rebuild");
            return proceedChecked(invoker, invocation);
        }
        String json = redis.opsForValue().get(SessionKeys.REDIS_SESSION_PREFIX + token);
        SessionSnapshotDto snapshot = IamContextLoader.parse(json);
        if (snapshot == null) {
            log.debug("iam provider filter: session missing for token, proceed as internal call");
            return proceedChecked(invoker, invocation);
        }
        IamContextHolder.set(IamContextLoader.from(token, snapshot));
        try {
            if (log.isInfoEnabled()) {
                // provider 侧访问日志:同时是"traceId 已跨服务延续"的观察点(日志列含透传来的 traceId)
                log.info("[rpc-in] {}#{}, subject={}", invoker.getInterface().getSimpleName(),
                        invocation.getMethodName(), snapshot.getSubjectCode());
            }
            return proceedChecked(invoker, invocation);
        } finally {
            IamContextHolder.clear();
        }
    }

    /**
     * 进入实现前的最后一道检查:{@code @RequiresSession} 断言。
     * 有上下文时平凡通过;无上下文路径(无 token/无 Redis/会话缺失)在此把
     * "实现体深处的 401"提前为点名方法的明确报错。
     */
    private Result proceedChecked(Invoker<?> invoker, Invocation invocation) {
        SessionRequiredChecker.assertSessionIfRequired(
                invoker.getInterface(), invocation.getMethodName(), invocation.getParameterTypes());
        return invoker.invoke(invocation);
    }
}
