package io.triform.rpc.session;

import io.triform.core.BizException;
import io.triform.core.IamContext;
import io.triform.core.IamContextHolder;
import io.triform.core.ResultCode;
import io.triform.core.session.RequiresSession;
import io.triform.rpc.context.IamContextProviderFilter;
import io.triform.rpc.support.SessionRequiredChecker;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code @RequiresSession} 断言验收(销 需求20260915 遗留项 1):
 * 接口级/方法级标注语义、有/无上下文、报错点名方法、 Dubbo 过滤器在进入实现前拦截。
 */
class RequiresSessionTest {

    // ---- 测试契约:接口级标注 ----
    @RequiresSession
    interface CorpQueryFacade {
        String visible(String code);
    }

    // ---- 测试契约:方法级标注(系统间方法不标) ----
    interface MixedFacade {
        @RequiresSession
        String search(String kw);

        String push(String owner, String name); // 系统间调用,无会话
    }

    interface PlainFacade {
        String ping(String from);
    }

    @AfterEach
    void cleanup() {
        IamContextHolder.clear();
    }

    private static void givenContext() {
        IamContext ctx = new IamContext();
        ctx.setUserId(1L);
        ctx.setUserName("u1");
        IamContextHolder.set(ctx);
    }

    // ===================== checker 语义 =====================

    @Test
    void interfaceLevelAnnotationRequiresSession() {
        BizException e = assertThrows(BizException.class, () ->
                SessionRequiredChecker.assertSessionIfRequired(CorpQueryFacade.class, "visible", new Class<?>[]{String.class}));
        assertEquals(ResultCode.UNAUTHORIZED, e.getCode());
        assertTrue(e.getMessage().contains("CorpQueryFacade#visible"), "报错必须点名方法: " + e.getMessage());
        assertTrue(e.getMessage().contains("@RequiresSession"));
    }

    @Test
    void passesWhenContextPresent() {
        givenContext();
        SessionRequiredChecker.assertSessionIfRequired(CorpQueryFacade.class, "visible", new Class<?>[]{String.class});
        SessionRequiredChecker.assertSessionIfRequired(MixedFacade.class, "push", new Class<?>[]{String.class, String.class});
    }

    @Test
    void methodLevelAnnotationOnlyCoversAnnotatedMethod() {
        // search 标注 → 无上下文拒绝
        assertThrows(BizException.class, () ->
                SessionRequiredChecker.assertSessionIfRequired(MixedFacade.class, "search", new Class<?>[]{String.class}));
        // push 未标注(系统间) → 无上下文放行 —— OA 推单场景不能被误伤
        SessionRequiredChecker.assertSessionIfRequired(MixedFacade.class, "push", new Class<?>[]{String.class, String.class});
    }

    @Test
    void unannotatedFacadePassesWithoutContext() {
        SessionRequiredChecker.assertSessionIfRequired(PlainFacade.class, "ping", new Class<?>[]{String.class});
    }

    // ===================== Dubbo 过滤器行为 =====================

    @SuppressWarnings("unchecked")
    @Test
    void providerFilterRejectsAnnotatedMethodWithoutContextBeforeImpl() {
        IamContextHolder.clear();
        RpcContext.getServerAttachment(); // 触发初始化,无 token 附着 → 走无上下文路径
        IamContextProviderFilter filter = new IamContextProviderFilter();
        Invocation invocation = new RpcInvocation("visible", CorpQueryFacade.class.getName(), "", new Class<?>[]{String.class}, new Object[]{"corp:kinlong"});
        Invoker<CorpQueryFacade> invoker = stubInvoker(CorpQueryFacade.class);

        BizException e = assertThrows(BizException.class, () -> filter.invoke(invoker, invocation));
        assertTrue(e.getMessage().contains("CorpQueryFacade#visible"),
                "过滤器必须在进入实现前拦截并点名方法: " + e.getMessage());
    }

    @SuppressWarnings("unchecked")
    @Test
    void providerFilterProceedsWhenContextSet() {
        givenContext();
        IamContextProviderFilter filter = new IamContextProviderFilter();
        Invocation invocation = new RpcInvocation("visible", CorpQueryFacade.class.getName(), "", new Class<?>[]{String.class}, new Object[]{"corp:kinlong"});
        Invoker<CorpQueryFacade> invoker = stubInvoker(CorpQueryFacade.class);
        Result result = filter.invoke(invoker, invocation);
        assertTrue(result instanceof AppResponse);
        assertEquals("ok", result.getValue());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void providerFilterPassesSystemCallOnUnannotatedMethod() {
        IamContextHolder.clear();
        IamContextProviderFilter filter = new IamContextProviderFilter();
        Invocation invocation = new RpcInvocation("push", MixedFacade.class.getName(), "", new Class<?>[]{String.class, String.class}, new Object[]{"corp:kinlong", "hq"});
        Invoker<MixedFacade> invoker = stubInvoker(MixedFacade.class);
        Result result = filter.invoke(invoker, invocation); // 无会话系统间调用放行
        assertEquals("ok", result.getValue());
    }

    @SuppressWarnings("unchecked")
    private static <T> Invoker<T> stubInvoker(Class<T> iface) {
        return new Invoker<T>() {
            @Override
            public Class<T> getInterface() {
                return iface;
            }

            @Override
            public Result invoke(Invocation inv) {
                return new AppResponse("ok");
            }

            @Override
            public org.apache.dubbo.common.URL getUrl() {
                return org.apache.dubbo.common.URL.valueOf("dubbo://127.0.0.1:20880/" + iface.getName());
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public void destroy() {
            }
        };
    }
}
