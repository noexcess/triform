package io.triform.rpc.support;

import feign.Headers;
import feign.Param;
import feign.RequestLine;

/**
 * HTTP 传输的 Feign 客户端接口 —— <b>Feign 注解只出现在这里,业务契约({@code @RemoteFacade} 接口)保持零传输注解</b>。
 *
 * <p>所有远程调用都走同一个通用端点:
 * <pre>
 *   POST /internal/rpc/{facadeFqn}/{methodName}
 *   Content-Type: application/json
 *   body: [arg0, arg1, ...]      // JSON 参数数组
 * </pre>
 * 返回值取响应体原文(JSON),由 {@code HttpRefFactoryBean} 按业务方法的泛型返回类型反序列化 ——
 * 因此 provider 侧只需**一个**控制器覆盖全部 Facade,新增接口零额外代码。
 */
public interface HttpRpcClient {

    @RequestLine("POST " + HttpRpcTargets.BASE_PATH + "/{facade}/{method}")
    @Headers("Content-Type: application/json")
    String invoke(@Param("facade") String facadeFqn, @Param("method") String methodName, Object[] args);
}
