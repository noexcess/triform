package io.triform.rpc.context;

import io.triform.core.IamContext;
import io.triform.core.IamContextHolder;
import io.triform.core.trace.TraceKeys;
import io.triform.rpc.support.HttpRpcTargets;
import io.triform.rpc.support.HttpServiceNameResolver;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP(备选)传输的<b>出站</b>拦截器,两件事:
 * <ol>
 *   <li><b>实例解析(轻量负载均衡)</b>:Feign 的目标写的是服务名({@code http://ticket-verify}),
 *       这里按 Nacos 实例列表轮询挑一个,用 {@code template.target(...)} 改写本次请求的目标 ——
 *       因此不需要自定义 {@code feign.Client},也不经网关(内部调用不占外部入口);</li>
 *   <li><b>上下文透传</b>:把会话 token 与 traceId/spanId 写进请求头,提供侧由
 *       {@code HttpRpcContextFilter} 恢复(与 Dubbo 侧两个 Filter 的职责一一对应)。</li>
 * </ol>
 * 说明:traceId 这里**显式**透传而不是依赖 sleuth 的自动埋点 —— 原因是本项目的 Feign 客户端是手工构造的,
 * 交给框架隐式生效不可控;显式透传与 Dubbo 侧行为一致,排查时两类传输的日志都能用同一个 traceId 串起来。
 */
public class HttpRpcOutboundInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HttpRpcOutboundInterceptor.class);

    private final DiscoveryClient discoveryClient;
    private final String facadeFqn;
    private final HttpServiceNameResolver serviceNameResolver;
    private final String internalToken;
    private final AtomicInteger roundRobin = new AtomicInteger();

    /**
     * @param serviceName 目标服务名(如 {@code ticket-iam}),由代理工厂按契约包名推导后传入 ——
     *                    不能从 {@code template.feignTarget().name()} 取:Feign 的 HardCodedTarget
     *                    在 {@code target(Class, String url)} 下 name() 返回的是 **URL**,拿去查 Nacos 必然查不到。
     */
    public HttpRpcOutboundInterceptor(DiscoveryClient discoveryClient, String facadeFqn,
                                      HttpServiceNameResolver serviceNameResolver, String internalToken) {
        this.discoveryClient = discoveryClient;
        this.facadeFqn = facadeFqn;
        this.serviceNameResolver = serviceNameResolver;
        this.internalToken = internalToken;
    }

    @Override
    public void apply(RequestTemplate template) {
        resolveTarget(template);
        relayContext(template);
    }

    private void resolveTarget(RequestTemplate template) {
        if (discoveryClient == null) {
            return;     // 无服务发现时保持 Feign 目标原样(测试/特殊环境)
        }
        String serviceName = serviceNameResolver.resolve(facadeFqn);
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            serviceNameResolver.evict(facadeFqn);   // 逐出缓存, 下次调用重新三级解析(提供方可能晚注册)
            throw new IllegalStateException("HTTP 传输:服务 " + serviceName + " 无可用实例(Nacos 未注册?)");
        }
        ServiceInstance instance = instances.get(Math.abs(roundRobin.getAndIncrement()) % instances.size());
        template.target("http://" + instance.getHost() + ":" + instance.getPort());
        if (log.isDebugEnabled()) {
            log.debug("[rpc-http] {} -> {}:{} {}", serviceName, instance.getHost(), instance.getPort(), template.path());
        }
    }

    private void relayContext(RequestTemplate template) {
        // 内部令牌:服务间共享密钥(各服务配置一致)。提供侧 InternalRpcAuthFilter 校验,
        // 消费侧不发就必然 403 —— 这是"服务间调用凭证",与下面的用户会话 token 是两件事。
        if (internalToken != null && !internalToken.isEmpty()) {
            template.header(HttpRpcTargets.HEADER_INTERNAL_TOKEN, internalToken);
        }
        IamContext ctx = IamContextHolder.get();
        if (ctx != null && ctx.getToken() != null) {
            template.header(HttpRpcTargets.HEADER_TOKEN, ctx.getToken());
        }
        String traceId = MDC.get(TraceKeys.MDC_TRACE_ID);
        if (traceId != null && !traceId.isEmpty()) {
            template.header(HttpRpcTargets.HEADER_TRACE_ID, traceId);
            String spanId = MDC.get(TraceKeys.MDC_SPAN_ID);
            if (spanId != null && !spanId.isEmpty()) {
                template.header(HttpRpcTargets.HEADER_SPAN_ID, spanId);
            }
        }
    }
}
