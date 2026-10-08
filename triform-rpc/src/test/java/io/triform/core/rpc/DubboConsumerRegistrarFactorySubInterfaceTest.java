package io.triform.core.rpc;

import io.triform.rpc.annotation.RemoteFacade;
import io.triform.rpc.support.DubboConsumerRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GPT 六查#2: 工厂方法识别不依赖实现类扫描兜底的独立用例。
 * scan-packages 指向 io.triform.core.rpc.unused(空包, 不含任何实现类),
 * 工厂方法返回子接口 —— 仅靠 tier1 的工厂方法识别才能避免代理覆盖本地注入。
 */
class DubboConsumerRegistrarFactorySubInterfaceTest {

    @RemoteFacade
    public interface Parent {
        String ping();
    }

    /** 子接口: @Bean 方法返回它而非直接返回 Parent(继承场景) */
    public interface Child extends Parent {
    }

    static class ChildImpl implements Child {
        @Override
        public String ping() {
            return "local";
        }
    }

    static class ProviderConfig {
        public Child parent() {
            return new ChildImpl();   // 返回子接口
        }
    }

    static class AutoModeConfig {
    }

    @Test
    void subInterfaceFactoryMethodIsLocalProvider() {
        DubboConsumerRegistrar registrar = new DubboConsumerRegistrar();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("deployment.mode", "micro");
        // 扫描包指向空包(实现类不在此范围, 排除 tier2 兜底)
        environment.setProperty("triform.rpc.scan-packages", "io.triform.core.rpc.unused");
        registrar.setEnvironment(environment);

        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("cfg",
                BeanDefinitionBuilder.genericBeanDefinition(ProviderConfig.class).getBeanDefinition());
        RootBeanDefinition factoryBean = new RootBeanDefinition();
        factoryBean.setFactoryBeanName("cfg");
        factoryBean.setFactoryMethodName("parent");
        registry.registerBeanDefinition("myParent", factoryBean);

        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(AutoModeConfig.class), registry);

        // Parent 契约不应被注册代理(工厂方法返回 Child extends Parent, isAssignableFrom 应命中)
        assertFalse(registry.containsBeanDefinition(Parent.class.getName()),
                "子接口工厂方法应被识别为本进程 provider, 不得注册 @Primary 代理覆盖本地注入");
    }
}
