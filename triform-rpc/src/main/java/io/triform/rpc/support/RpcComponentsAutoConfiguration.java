package io.triform.rpc.support;

import io.triform.rpc.context.HttpRpcContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/** T3: rpc 组件自动装配(过滤器类自带 @ConditionalOnProperty 条件, import 时仍生效)。 */
@AutoConfiguration
@Import({HttpRpcContextFilter.class, RpcTransportInfoContributor.class,
        InternalRpcEndpoint.class, InternalRpcAuthFilter.class})
public class RpcComponentsAutoConfiguration {
}
