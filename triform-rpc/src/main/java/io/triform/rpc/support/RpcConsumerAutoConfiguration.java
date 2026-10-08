package io.triform.rpc.support;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * T2 消费注解去灭: 引 starter(或 rpc 模块)即自动装配消费代理, 无需 @EnableDubboConsumers。
 * classpath 全扫 @RemoteFacade(本进程有实现类的接口自动跳过=本地 Bean)。
 * 开关 triform.rpc.consumer.enabled(缺省 true)。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "triform.rpc.consumer.enabled", havingValue = "true", matchIfMissing = true)
@Import(DubboConsumerRegistrar.class)
public class RpcConsumerAutoConfiguration {
}
