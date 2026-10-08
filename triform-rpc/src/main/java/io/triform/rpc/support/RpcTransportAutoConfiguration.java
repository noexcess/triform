package io.triform.rpc.support;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * RPC 传输自动装配(基线/42 §8.4): 提供"编程式 Facade 导出"与既有 Dubbo/HTTP 代理装配的统一入口。
 *
 * <p>- mono 模式: 本配置整体不生效(deployment.mode!=micro), FacadeAutoExporter 不装配, 天然不导出;
 * <p>- micro 模式: 装配 FacadeAutoExporter——扫描实现了 @RemoteFacade 接口的本地 Bean,
 *   编程式导出为 Dubbo provider(未标注 @DubboService 的); 已标注的由 @EnableDubbo 扫描导出(过渡期共存)。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "deployment.mode", havingValue = "micro")
public class RpcTransportAutoConfiguration {

    @Bean
    public FacadeAutoExporter facadeAutoExporter(ApplicationContext applicationContext) {
        return new FacadeAutoExporter(applicationContext);
    }

    /** E2: 启动期契约校验(重载×http fail-fast / 注解误用 / 空契约告警) */
    @Bean
    public FacadeContractValidator facadeContractValidator(org.springframework.core.env.Environment environment) {
        return new FacadeContractValidator(environment);
    }
}
