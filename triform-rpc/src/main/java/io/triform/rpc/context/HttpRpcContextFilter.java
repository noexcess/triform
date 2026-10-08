package io.triform.rpc.context;

import io.triform.core.IamContext;
import io.triform.core.IamContextHolder;
import io.triform.core.session.IamContextLoader;
import io.triform.core.session.SessionKeys;
import io.triform.core.session.SessionSnapshotDto;
import io.triform.core.trace.TraceKeys;
import io.triform.rpc.support.HttpRpcTargets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * HTTP(备选)传输的<b>入站</b>上下文恢复:从请求头还原 traceId(MDC) 与会话(IamContextHolder)。
 *
 * <p>与 Dubbo 侧的 {@code IamContextProviderFilter} 职责与口径完全一致:
 * <ul>
 *   <li>会话:按 {@code X-Token} 查 Redis 快照({@code iam:session:{token}})后重建上下文;
 *       token 缺失或快照不存在时**放行** —— RPC 层只负责传上下文、不做鉴权,业务方法自己 {@code require()} 兜底;</li>
 *   <li>traceId:从 {@code ticket-trace-id} 恢复(与 Dubbo attachment 同键名),便于两类传输用同一个 id 串日志;</li>
 *   <li>{@code finally} 里 clear —— 线程池复用下必须清理,否则上下文串味。</li>
 * </ul>
 * 只在 {@code triform.rpc.http-expose=true} 且路径为内部 RPC 时生效。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@ConditionalOnProperty(prefix = "triform.rpc", name = "http-expose", havingValue = "true")
public class HttpRpcContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRpcContextFilter.class);

    private final StringRedisTemplate redisTemplate;

    public HttpRpcContextFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !io.triform.rpc.support.InternalPaths.isInternalRpc(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean traceInjected = false;
        boolean contextLoaded = false;
        try {
            String traceId = request.getHeader(HttpRpcTargets.HEADER_TRACE_ID);
            if (traceId != null && !traceId.isEmpty()) {
                MDC.put(TraceKeys.MDC_TRACE_ID, traceId);
                String spanId = request.getHeader(HttpRpcTargets.HEADER_SPAN_ID);
                if (spanId != null && !spanId.isEmpty()) {
                    MDC.put(TraceKeys.MDC_SPAN_ID, spanId);
                }
                traceInjected = true;
            }
            String token = request.getHeader(HttpRpcTargets.HEADER_TOKEN);
            if (token != null && !token.isEmpty() && redisTemplate != null) {
                String json = redisTemplate.opsForValue().get(SessionKeys.REDIS_SESSION_PREFIX + token);
                SessionSnapshotDto snapshot = IamContextLoader.parse(json);
                if (snapshot != null) {
                    IamContext context = IamContextLoader.from(token, snapshot);
                    IamContextHolder.set(context);
                    contextLoaded = true;
                }
            }
            log.info("[rpc-http-in] {}, 会话={}", request.getRequestURI(), contextLoaded ? "已恢复" : "无");
            chain.doFilter(request, response);
        } finally {
            IamContextHolder.clear();
            if (traceInjected) {
                MDC.remove(TraceKeys.MDC_TRACE_ID);
                MDC.remove(TraceKeys.MDC_SPAN_ID);
            }
        }
    }
}
