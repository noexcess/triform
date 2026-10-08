package io.triform.rpc.resilience4j;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 熔断行为单测: 连续失败触发开路, 后续调用直接拒绝(不触达目标)。 */
class Resilience4jRpcDecoratorTest {

    interface TestFacade {
        String call();
    }

    @Test
    void circuitOpensAfterConsecutiveFailures() throws Exception {
        Resilience4jRpcDecorator decorator = new Resilience4jRpcDecorator();
        int[] calls = {0};
        InvocationHandler failing = (p, m, a) -> {
            calls[0]++;
            throw new IllegalStateException("boom");
        };
        InvocationHandler wrapped = decorator.wrap("test.Facade", failing);
        java.lang.reflect.Method method = TestFacade.class.getMethod("call");

        for (int i = 0; i < 10; i++) {
            assertThrows(IllegalStateException.class,
                    () -> wrapped.invoke(null, method, new Object[0]));
        }
        // minimumNumberOfCalls=10 且全部失败 → 熔断开; 目标此后不再被触达
        int after = calls[0];
        assertThrows(CallNotPermittedException.class, () -> wrapped.invoke(null, method, new Object[0]));
        assertEquals(after, calls[0], "熔断打开后不应再触达目标");
    }
}
