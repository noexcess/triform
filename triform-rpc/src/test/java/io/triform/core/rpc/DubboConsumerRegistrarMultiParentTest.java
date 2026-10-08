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

/** GPT 七查: 工厂方法返回多父契约(Combined extends FacadeA, FacadeB)——两个都应识别为本进程 provider, 不得遗漏任何一个。 */
class DubboConsumerRegistrarMultiParentTest {

    @RemoteFacade
    public interface ContractA {
        String a();
    }

    @RemoteFacade
    public interface ContractB {
        String b();
    }

    /** 同时继承两个契约 */
    public interface Combined extends ContractA, ContractB {
    }

    static class CombinedImpl implements Combined {
        @Override public String a() { return "a"; }
        @Override public String b() { return "b"; }
    }

    static class ProviderConfig {
        public Combined combined() {
            return new CombinedImpl();
        }
    }

    static class AutoModeConfig {
    }

    @Test
    void multiParentFactoryMethodCoversBothContracts() {
        DubboConsumerRegistrar registrar = new DubboConsumerRegistrar();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("deployment.mode", "micro");
        environment.setProperty("triform.rpc.scan-packages", "io.triform.core.rpc.unused");   // 空包排除 tier2 兜底
        registrar.setEnvironment(environment);

        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("cfg",
                BeanDefinitionBuilder.genericBeanDefinition(ProviderConfig.class).getBeanDefinition());
        RootBeanDefinition factoryBean = new RootBeanDefinition();
        factoryBean.setFactoryBeanName("cfg");
        factoryBean.setFactoryMethodName("combined");
        registry.registerBeanDefinition("myCombined", factoryBean);

        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(AutoModeConfig.class), registry);

        assertFalse(registry.containsBeanDefinition(ContractA.class.getName()),
                "ContractA 应被识别为本进程 provider(多父契约不遗漏)");
        assertFalse(registry.containsBeanDefinition(ContractB.class.getName()),
                "ContractB 也应被识别为本进程 provider(修复前 break 导致只命中第一个)");
    }
}
