package io.triform.core.rpc;

import io.triform.rpc.annotation.RemoteFacade;
import io.triform.rpc.support.FacadeContractValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E2: 启动期契约校验 —— 重载×http 点名 fail-fast; 无重载/dubbo 传输放行。 */
class FacadeContractValidatorTest {

    @RemoteFacade
    interface Overloaded {
        String ping(String a);

        String ping(String a, String b);
    }

    @RemoteFacade
    interface Fine {
        String ping(String a);

        String pong();
    }

    private MockEnvironment env(String transport) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("deployment.mode", "micro");
        environment.setProperty("triform.rpc.transport", transport);
        environment.setProperty("triform.rpc.scan-packages", "io.triform.core.rpc");   // 并集发现本包契约
        return environment;
    }

    @Test
    void overloadedFacadeFailsFastOnHttpTransport() {
        // 前提: Overloaded 在测试索引中可见? 不在——经全扫回退发现(测试类路径小, 秒内)
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new FacadeContractValidator(env("http")).afterSingletonsInstantiated());
        assertTrue(ex.getMessage().contains("ping"), "错误应点名重载方法: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Overloaded"), "错误应点名契约: " + ex.getMessage());
    }

    @Test
    void overloadedFacadeAllowedOnDubboTransport() {
        assertDoesNotThrow(() -> new FacadeContractValidator(env("dubbo")).afterSingletonsInstantiated());
    }
}
