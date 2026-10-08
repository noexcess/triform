package io.triform.rpc.support;

import java.lang.reflect.InvocationHandler;

/**
 * RPC 治理装饰 SPI(T10, 设计基线/42 §7.4): HTTP 传输的远程调用链装饰点。
 *
 * <p>底座默认零治理({@code triform.rpc.resilience=none} 且无本接口 Bean);
 * 引入 triform-rpc-resilience4j 并设 {@code triform.rpc.resilience=resilience4j} 后,
 * HttpRefFactoryBean 在创建代理时用本接口包装 InvocationHandler——熔断/限流/舱壁对业务零改动。
 * 超时不在此层(TimeLimiter 需执行器), 由 OkHttp readTimeout 承担(见 HttpRefFactoryBean)。
 * Dubbo 侧治理留给 sentinel 档3(Dubbo adapter)。
 */
public interface RpcResilienceDecorator {

    /** 包装一个远程调用 handler; 返回新 handler(建议保留原引用用于 Object 方法直通) */
    InvocationHandler wrap(String facadeFqn, InvocationHandler target);
}
