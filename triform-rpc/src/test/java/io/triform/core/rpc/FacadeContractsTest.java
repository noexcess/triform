package io.triform.core.rpc;

import io.triform.rpc.annotation.RemoteFacade;
import io.triform.rpc.support.FacadeContracts;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** D2: CGLIB 类代理(@事务/@切面)下契约识别不得漏 —— 修复前 getInterfaces() 拿不到业务接口。 */
class FacadeContractsTest {

    @RemoteFacade
    interface Proxied {
    }

    static class ProxiedImpl implements Proxied {
    }

    @Test
    void cglibClassProxyStillRecognized() {
        // setProxyTargetClass(true) 模拟 @Transactional 无接口代理场景
        ProxyFactory factory = new ProxyFactory(new ProxiedImpl());
        factory.setProxyTargetClass(true);
        Object proxy = factory.getProxy();

        assertTrue(proxy.getClass() != ProxiedImpl.class, "前提: 确为代理类");
        assertTrue(FacadeContracts.of(proxy).contains(Proxied.class),
                "CGLIB 代理 Bean 的 @RemoteFacade 接口必须被识别(修复前 getInterfaces 漏识别)");
    }
}
