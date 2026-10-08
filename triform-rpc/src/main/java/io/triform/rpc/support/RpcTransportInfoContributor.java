package io.triform.rpc.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把"本进程的远程调用模式"暴露到 {@code /actuator/info},解决**集群视角**问题:
 * 单看日志只能知道本进程怎么配的,排障时想知道"哪个服务是 dubbo、哪个是 http、谁开了内部端点",
 * 逐个服务打一次 {@code /actuator/info} 即可(端点已在共享配置里暴露 health/info/metrics)。
 *
 * <p>不暴露令牌本身(只暴露"是否已配置"),避免敏感值进入监控/日志采集。
 */
@Component
public class RpcTransportInfoContributor implements InfoContributor {

    private final String transport;
    private final boolean httpExpose;
    private final boolean internalTokenConfigured;
    private final String deploymentMode;

    public RpcTransportInfoContributor(@Value("${triform.rpc.transport:dubbo}") String transport,
                                       @Value("${triform.rpc.http-expose:false}") boolean httpExpose,
                                       @Value("${triform.rpc.internal-token:}") String internalToken,
                                       @Value("${deployment.mode:mono}") String deploymentMode) {
        this.transport = transport;
        this.httpExpose = httpExpose;
        this.internalTokenConfigured = internalToken != null && !internalToken.isEmpty();
        this.deploymentMode = deploymentMode;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> rpc = new LinkedHashMap<>();
        rpc.put("transport", transport);
        rpc.put("httpExpose", httpExpose);
        rpc.put("internalTokenConfigured", internalTokenConfigured);
        rpc.put("deploymentMode", deploymentMode);
        rpc.put("note", "transport=http 需目标服务开启 http-expose 且 internal-token 一致;见《11-备选传输方案》");
        builder.withDetail("rpc", rpc);
    }
}
