package io.triform.core.rpc;

import io.triform.rpc.annotation.RemoteFacade;
import io.triform.rpc.support.FacadeContracts;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GPT 八查: 提供方契约识别的继承边界——Combined extends ContractA, ContractB 时，
 * FacadeContracts 必须识别**两份父契约**(Dubbo 导出与 Nacos 元数据发布依赖此结果)。
 * Spring 的 getAllInterfacesForClassAsSet 对接口入参走 singleton 短路不递归展开，
 * 修复前返回空集。BFS 遍历后两份契约都被收集。
 */
class FacadeContractsMultiParentTest {

    @RemoteFacade
    interface ContractA {
        String a();
    }

    @RemoteFacade
    interface ContractB {
        String b();
    }

    /** 同时继承两个契约(间接继承: @RemoteFacade 在父接口上) */
    interface Combined extends ContractA, ContractB {
    }

    static class CombinedImpl implements Combined {
        @Override public String a() { return "a"; }
        @Override public String b() { return "b"; }
    }

    @Test
    void multiParentInterfacesBothRecognized() {
        Set<Class<?>> facades = FacadeContracts.ofClass(CombinedImpl.class);
        assertEquals(2, facades.size(), "两份父契约都应被识别: " + facades);
        assertTrue(facades.contains(ContractA.class));
        assertTrue(facades.contains(ContractB.class));
    }

    @Test
    void multiParentOnPlainBean() {
        Set<Class<?>> facades = FacadeContracts.of(new CombinedImpl());
        assertEquals(2, facades.size(), "实例级(Exporter/元数据)同样两份: " + facades);
    }

    @Test
    void multiParentOnCglibProxy() {
        ProxyFactory factory = new ProxyFactory(new CombinedImpl());
        factory.setProxyTargetClass(true);
        Object proxy = factory.getProxy();
        Set<Class<?>> facades = FacadeContracts.of(proxy);
        assertEquals(2, facades.size(), "CGLIB 代理 + 多父契约: " + facades);
    }

    interface Mid extends Combined {
    }

    static class MidImpl extends CombinedImpl implements Mid {
    }

    @Test
    void deepInheritanceChainRecognized() {
        Set<Class<?>> facades = FacadeContracts.ofClass(MidImpl.class);
        assertEquals(2, facades.size(), "三层继承也应识别两份契约: " + facades);
    }
}
