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

/** 评审#2 复查: @Bean 工厂方法以契约接口为声明返回类型 —— 必须识别为本进程 provider, 不得注册 @Primary 代理覆盖本地注入。 */
class DubboConsumerRegistrarFactoryMethodTest {

    @RemoteFacade
    public interface FactoryProvided {
        String ping();
    }

    public static class FactoryProvidedImpl implements FactoryProvided {
        @Override
        public String ping() {
            return "local";
        }
    }

    static class ProviderConfig {
        public FactoryProvided factoryProvided() {
            return new FactoryProvidedImpl();
        }
    }

    static class AutoModeConfig {
    }

    @Test
    void factoryMethodDeclaredAsFacadeIsLocalProvider() {
        DubboConsumerRegistrar registrar = new DubboConsumerRegistrar();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("deployment.mode", "micro");
        environment.setProperty("triform.rpc.scan-packages", "io.triform.core.rpc");
        registrar.setEnvironment(environment);

        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("providerConfig",
                BeanDefinitionBuilder.genericBeanDefinition(ProviderConfig.class).getBeanDefinition());
        RootBeanDefinition factoryBean = new RootBeanDefinition();
        factoryBean.setFactoryBeanName("providerConfig");
        factoryBean.setFactoryMethodName("factoryProvided");
        registry.registerBeanDefinition("myFacade", factoryBean);

        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(AutoModeConfig.class), registry);

        assertFalse(registry.containsBeanDefinition(FactoryProvided.class.getName()),
                "@Bean 工厂方法声明的契约不得再注册远程代理(修复前 @Primary 代理覆盖本地注入)");
        assertTrue(registry.containsBeanDefinition(IndexDiscoveredFacade.class.getName()),
                "其他契约照常注册(发现未被破坏)");
    }
}
