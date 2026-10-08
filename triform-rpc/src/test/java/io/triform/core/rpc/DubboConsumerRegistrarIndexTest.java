package io.triform.core.rpc;

import io.triform.rpc.support.DubboConsumerRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-3 索引路径: 自动装配模式经 META-INF/triform-facades 发现候选(毫秒级);
 * 本地实现识别主判据 = Bean 定义检查(与包路径无关)。
 */
class DubboConsumerRegistrarIndexTest {

    static class AutoModeConfig {
        // 无 @EnableDubboConsumers → 自动装配模式
    }

    /** 仅存在于包内、不在索引中的 Facade —— 经 scan-packages 发现 */
    @io.triform.rpc.annotation.RemoteFacade
    interface ScanOnlyFacade {
        String ping();
    }

    private DefaultListableBeanFactory register() {
        DubboConsumerRegistrar registrar = new DubboConsumerRegistrar();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("deployment.mode", "micro");
        registrar.setEnvironment(environment);
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        // 主判据用例: 实现类以 Bean 定义形式在场(非 classpath 扫描发现)
        registry.registerBeanDefinition(IndexLocalFacadeImpl.class.getName(),
                BeanDefinitionBuilder.genericBeanDefinition(IndexLocalFacadeImpl.class).getBeanDefinition());
        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(AutoModeConfig.class), registry);
        return registry;
    }

    @Test
    void indexDiscoveredFacadeGetsProxy() {
        assertTrue(register().containsBeanDefinition(IndexDiscoveredFacade.class.getName()),
                "索引声明的 Facade 应注册远程代理");
    }

    @Test
    void indexUnionsWithConfiguredScanPackages() {
        // D3(评审#3): 索引存在时不得吞掉显式 scan-packages 的发现——混用场景旧模块仍可被发现
        DubboConsumerRegistrar registrar = new DubboConsumerRegistrar();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("deployment.mode", "micro");
        environment.setProperty("triform.rpc.scan-packages", "io.triform.core.rpc");
        registrar.setEnvironment(environment);
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(AutoModeConfig.class), registry);
        assertTrue(registry.containsBeanDefinition(IndexDiscoveredFacade.class.getName()),
                "索引项应注册代理");
        assertTrue(registry.containsBeanDefinition(ScanOnlyFacade.class.getName()),
                "scan-packages 发现的非索引 Facade 也应注册代理(修复前被索引短路吞掉)");
    }

    @Test
    void beanDefinedLocalImplSkipsProxy() {
        assertFalse(register().containsBeanDefinition(IndexLocalFacade.class.getName()),
                "Bean 定义在场的实现应被主判据识别为本进程服务");
    }
}
