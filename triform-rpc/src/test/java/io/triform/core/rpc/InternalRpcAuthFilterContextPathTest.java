package io.triform.core.rpc;

import io.triform.rpc.support.InternalRpcAuthFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** D1: 宿主配置 context-path 后, /app/internal/rpc/** 必须仍走令牌鉴权(修复前被 shouldNotFilter 放行)。 */
class InternalRpcAuthFilterContextPathTest {

    @Test
    void contextPathRequestStillRequiresToken() throws Exception {
        InternalRpcAuthFilter filter = new InternalRpcAuthFilter("expected-token");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/app/internal/rpc/x.Y/toString");
        request.setContextPath("/app");
        request.setRequestURI("/app/internal/rpc/x.Y/toString");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new AssertionError("未带令牌的内部端点请求不得进入后续链路(修复前 context-path 会绕过鉴权)");
        };

        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus(), "缺令牌应 403");
    }

    @Test
    void contextPathRequestWithValidTokenPasses() throws Exception {
        InternalRpcAuthFilter filter = new InternalRpcAuthFilter("expected-token");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/app/internal/rpc/x.Y/toString");
        request.setContextPath("/app");
        request.setRequestURI("/app/internal/rpc/x.Y/toString");
        request.addHeader("X-Internal-Token", "expected-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] passed = {false};
        FilterChain chain = (req, res) -> passed[0] = true;

        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertEquals(true, passed[0], "正确令牌应放行");
    }

    @Test
    void nonInternalPathSkipsFilter() throws Exception {
        InternalRpcAuthFilter filter = new InternalRpcAuthFilter("expected-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/hello");
        request.setContextPath("/app");
        request.setRequestURI("/app/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] passed = {false};
        FilterChain chain = (req, res) -> passed[0] = true;

        filter.doFilter(request, response, chain);
        assertEquals(true, passed[0], "非内部端点不应被鉴权过滤器拦截");
    }
}
