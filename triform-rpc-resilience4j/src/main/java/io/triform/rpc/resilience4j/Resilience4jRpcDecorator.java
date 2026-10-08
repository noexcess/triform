package io.triform.rpc.resilience4j;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.triform.rpc.support.RpcResilienceDecorator;

import java.lang.reflect.InvocationHandler;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T10 治理装饰(resilience4j): 每 facadeFqn 一套 CircuitBreaker/Bulkhead/RateLimiter。
 * 调用链 RateLimiter → Bulkhead → CircuitBreaker → 目标(resilience4j 推荐次序);
 * 超时由 OkHttp readTimeout 承担(不引入 TimeLimiter 执行器, 见 RpcResilienceDecorator)。
 * 默认值贴基线/42 口径: 熔断 10 次起判/50% 失败率/开 10s; 舱壁 50 并发; 限流 200 QPS。
 */
public class Resilience4jRpcDecorator implements RpcResilienceDecorator {

    private final CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                    .slidingWindowSize(20).minimumNumberOfCalls(10).failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .permittedNumberOfCallsInHalfOpenState(3).build());
    private final BulkheadRegistry bhRegistry = BulkheadRegistry.of(
            io.github.resilience4j.bulkhead.BulkheadConfig.custom().maxConcurrentCalls(50).build());
    private final RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(
            io.github.resilience4j.ratelimiter.RateLimiterConfig.custom().limitForPeriod(200)
                    .limitRefreshPeriod(Duration.ofSeconds(1)).build());

    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    @Override
    public InvocationHandler wrap(String facadeFqn, InvocationHandler target) {
        CircuitBreaker cb = breakers.computeIfAbsent(facadeFqn, k -> cbRegistry.circuitBreaker(k));
        Bulkhead bh = bulkheads.computeIfAbsent(facadeFqn, k -> bhRegistry.bulkhead(k));
        RateLimiter rl = limiters.computeIfAbsent(facadeFqn, k -> rlRegistry.rateLimiter(k));
        return (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return target.invoke(p, method, args);
            }
            try {
                return rl.executeSupplier(() -> bh.executeSupplier(() -> cb.executeSupplier(() -> {
                    try {
                        return target.invoke(p, method, args);
                    } catch (Throwable t) {
                        throw sneak(t);   // 原样重抛(含受检异常), 熔断按失败记录
                    }
                })));
            } catch (Throwable t) {
                throw sneak(t);   // 受检异常兜底
            }
        };
    }

    /** 原样重抛受检异常(泛型擦除), 保持目标方法异常语义不变 */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneak(Throwable t) throws E {
        throw (E) t;
    }
}
