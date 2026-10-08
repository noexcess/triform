package io.triform.core.rpc;

import io.triform.rpc.annotation.EnableDubboConsumers;
import io.triform.rpc.annotation.RemoteFacade;
import io.triform.rpc.support.DubboConsumerRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 消费方远程代理注册：
 * 1) micro 注册（@Primary 远程代理）、mono 不注册；
 * 2) exclude 手工排除的接口即使标注 @RemoteFacade 也不注册；
 * 3) **自动兜底**：本进程 classpath 上已有实现类的接口不注册（无需人工维护 exclude 清单）。
 */
class DubboConsumerRegistrarTest {

    private static final String PACKAGE = "io.triform.core.rpc";

    @RemoteFacade
    interface IncludedProbeFacade {
        String ping();
    }

    @RemoteFacade
    interface ExcludedProbeFacade {
        String ping();
    }

    /** 自动兜底用例：本进程已有实现类 → 不应注册代理 */
    @RemoteFacade
    interface LocallyProvidedProbeFacade {
        String ping();
    }

    static class LocallyProvidedProbeFacadeImpl implements LocallyProvidedProbeFacade {
        @Override
        public String ping() {
            return "local";
        }
    }

    @EnableDubboConsumers(basePackages = PACKAGE, exclude = ExcludedProbeFacade.class)
    static class ProbeConfig {
    }

    private DefaultListableBeanFactory register(String mode) {
        DubboConsumerRegistrar registrar = new DubboConsumerRegistrar();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("deployment.mode", mode);
        registrar.setEnvironment(environment);
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(ProbeConfig.class), registry);
        return registry;
    }

    @Test
    void microRegistersProxyForConsumerInterface() {
        DefaultListableBeanFactory registry = register("micro");
        assertTrue(registry.containsBeanDefinition(IncludedProbeFacade.class.getName()),
                "未 exclude 的 @RemoteFacade 接口应注册远程代理");
    }

    @Test
    void microSkipsExcludedProviderInterface() {
        DefaultListableBeanFactory registry = register("micro");
        assertFalse(registry.containsBeanDefinition(ExcludedProbeFacade.class.getName()),
                "exclude 的接口(本服务即 provider)不得注册远程代理");
    }

    @Test
    void microAutoSkipsInterfaceImplementedLocally() {
        DefaultListableBeanFactory registry = register("micro");
        assertFalse(registry.containsBeanDefinition(LocallyProvidedProbeFacade.class.getName()),
                "本进程已有实现类的 @RemoteFacade 接口应被自动跳过(无需写在 exclude 里)");
    }

    @Test
    void monoRegistersNothing() {
        DefaultListableBeanFactory registry = register("mono");
        assertFalse(registry.containsBeanDefinition(IncludedProbeFacade.class.getName()));
        assertFalse(registry.containsBeanDefinition(ExcludedProbeFacade.class.getName()));
        assertFalse(registry.containsBeanDefinition(LocallyProvidedProbeFacade.class.getName()));
    }
}
