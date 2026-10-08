package io.triform.rpc.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 通用服务启动基座(基线/42 §8.4 #4): 让每个微服务的 main 缩到 3 行。
 *
 * <p>微服务启动类只需(脚手架生成, 注解是模板的一部分、每服务仅差名字):
 * <pre>
 * &#64;SpringBootApplication
 * (无需任何 triform 注解: 消费代理由 RpcConsumerAutoConfiguration 自动装配)
 * public class ServiceXxxApplication {
 *     public static void main(String[] args) {
 *         GeneralServiceBootstrap.run(ServiceXxxApplication.class, args);
 *     }
 * }
 * </pre>
 *
 * <p>提供 @RemoteFacade 实现的类上不需要 @DubboService——micro 下由 RpcTransportAutoConfiguration
 * 的 FacadeAutoExporter 编程式导出(设计基线/42 §8.4)。
 */
public final class GeneralServiceBootstrap {

    private GeneralServiceBootstrap() {
    }

    /** 与 SpringApplication.run 等价; 保留显式入口以便后续统一收敛启动逻辑 */
    public static ConfigurableApplicationContext run(Class<?> mainClass, String[] args) {
        return SpringApplication.run(mainClass, args);
    }
}
