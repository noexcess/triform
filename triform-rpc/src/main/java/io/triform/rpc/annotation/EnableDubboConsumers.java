package io.triform.rpc.annotation;

import io.triform.rpc.support.DubboConsumerRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 开启"注解驱动 Dubbo 消费"：在 consumer 服务启动类上标注。
 * <p>
 * - micro 模式：为 {@link RemoteFacade} 接口注册 Dubbo 远程代理 Bean（@Primary），业务直接 @Autowired 注入；
 * - mono 模式：Registrar 内部检测 deployment.mode 非 micro 时直接跳过，注入本地实现。
 * - micro 模式：exclude 用于排掉**本服务自己导出的接口**。漏排的后果不是"走网络"（Dubbo 3 对同 JVM 已导出的
 *   服务有 injvm 本地兜底），而是 provider 过滤器会在调用方线程上执行并清空 IamContextHolder —— 见
 *   ServiceIamApplication 的 exclude 注释与《10-新增微服务与跨服务调用指南》。
 *
 * @param basePackages 扫描包，应覆盖所有被消费接口所在包（如 io.triform）
 * @param exclude      不注册远程代理的接口；用于服务自身实现了某 Facade（即该接口的 provider）时，
 *                     避免在 provider 进程内注册 @Primary 代理，使内部注入退化为走网络的自我调用
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(DubboConsumerRegistrar.class)
public @interface EnableDubboConsumers {

    String[] basePackages() default {};

    Class<?>[] exclude() default {};
}
