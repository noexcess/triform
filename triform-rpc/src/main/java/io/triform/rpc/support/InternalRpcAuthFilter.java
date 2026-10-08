package io.triform.rpc.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部 RPC 端点的鉴权:必须带与 {@code triform.rpc.internal-token} 一致的令牌,否则 403。
 *
 * <p>只在 {@code triform.rpc.http-expose=true} 时生效;令牌比较用 {@link MessageDigest#isEqual}(常量时间),
 * 避免比较耗时侧信道。**注意**:这是"服务间调用凭证"而不是用户鉴权 —— 用户会话由
 * {@code HttpRpcContextFilter} 从 {@code X-Token} 恢复,两者职责不同、都要有。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(prefix = "triform.rpc", name = "http-expose", havingValue = "true")
public class InternalRpcAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalRpcAuthFilter.class);

    private final byte[] expectedToken;

    public InternalRpcAuthFilter(@Value("${triform.rpc.internal-token:}") String internalToken) {
        if (!StringUtils.hasText(internalToken)) {
            throw new IllegalStateException(
                    "已开启 triform.rpc.http-expose 但未配置 triform.rpc.internal-token —— 内部 RPC 端点无鉴权禁止开启");
        }
        this.expectedToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !InternalPaths.isInternalRpc(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HttpRpcTargets.HEADER_INTERNAL_TOKEN);
        if (provided == null
                || !MessageDigest.isEqual(expectedToken, provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[rpc-http] 内部端点鉴权失败: {} {} (来源 {})",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":10010,\"message\":\"内部 RPC 令牌无效\",\"success\":false}");
            return;
        }
        chain.doFilter(request, response);
    }
}
