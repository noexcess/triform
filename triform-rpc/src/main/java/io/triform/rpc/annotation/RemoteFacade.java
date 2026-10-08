package io.triform.rpc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记"可被远程消费"的服务接口（标在 service-xxx-api 的 Facade 接口上）。
 * <p>
 * - micro 模式：consumer 通过 {@link io.triform.rpc.annotation.EnableDubboConsumers} 扫描本注解，
 *   为接口自动注册 Dubbo 远程代理（ReferenceConfig）；
 * - mono 模式：无处理器生效，注入同 JVM 内 {@code @Service} 本地实现。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RemoteFacade {
}
