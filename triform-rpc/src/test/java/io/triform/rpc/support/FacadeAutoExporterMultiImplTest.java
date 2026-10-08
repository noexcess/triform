package io.triform.rpc.support;

import io.triform.rpc.annotation.RemoteFacade;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 多实现契约歧义必须 fail-fast(评审 B1): 静默选首个是最难排的坑。 */
class FacadeAutoExporterMultiImplTest {

    @RemoteFacade
    interface Dup {
    }

    @org.springframework.stereotype.Service
    static class DupA implements Dup {
    }

    @org.springframework.stereotype.Service
    static class DupB implements Dup {
    }

    @Test
    void duplicateImplementationsFailFast() {
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.registerBean("a", DupA.class);
        ctx.registerBean("b", DupB.class);
        ctx.refresh();
        FacadeAutoExporter exporter = new FacadeAutoExporter(ctx);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                exporter::afterSingletonsInstantiated);
        assertTrue(ex.getMessage().contains("Dup"), "错误信息应指名冲突接口: " + ex.getMessage());
    }
}
