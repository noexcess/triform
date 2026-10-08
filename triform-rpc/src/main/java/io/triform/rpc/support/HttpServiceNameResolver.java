package io.triform.rpc.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 传输的目标服务名三级解析（C-2，基线/45）：
 * <ol>
 *   <li><b>显式配置</b> {@code triform.rpc.http.service-names.<facadeFqn>} —— 人工覆盖一切；</li>
 *   <li><b>注册中心元数据反查</b>：提供方经 NacosRegistrationCustomizer 把本进程 Facade 列表写入实例元数据
 *       {@link #METADATA_KEY}，消费方按元数据命中反推 serviceId —— <b>多服务命中即 fail-fast</b>
 *       （契约归属歧义须显式配置，与 Exporter 多实现 fail-fast 对称）；</li>
 *   <li><b>legacy 包名约定</b>：io.triform.&lt;svc&gt;.api... → ticket-&lt;svc&gt;（旧版本混布兜底）。</li>
 * </ol>
 * 正结果永久缓存；负结果与元数据未命中不缓存（提供方可能晚注册）。
 */
public final class HttpServiceNameResolver {

    public static final String METADATA_KEY = "triform-facades";

    private static final Logger log = LoggerFactory.getLogger(HttpServiceNameResolver.class);

    private final Environment environment;
    private final DiscoveryClient discoveryClient;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public HttpServiceNameResolver(Environment environment, DiscoveryClient discoveryClient) {
        this.environment = environment;
        this.discoveryClient = discoveryClient;
    }

    /** 请求期三级解析（①配置与②元数据命中缓存; ③legacy 推导不缓存——提供方晚注册后可自动升级为元数据命中）。 */
    public String resolve(String facadeFqn) {
        String cached = cache.get(facadeFqn);
        if (cached != null) {
            return cached;
        }
        return resolveUncached(facadeFqn);
    }

    /** 调用失败(如无实例)时逐出缓存, 下次调用重新走三级解析(元数据可能已就绪)。 */
    public void evict(String facadeFqn) {
        cache.remove(facadeFqn);
    }

    private String resolveUncached(String facadeFqn) {
        // ① 显式配置(最高优先级, 结果可信, 缓存)
        String configured = configured(environment, facadeFqn);
        if (configured != null) {
            cache.put(facadeFqn, configured);
            return configured;
        }
        // ② 注册中心元数据反查(提供方亲口声明, 结果可信, 缓存)
        if (discoveryClient != null) {
            Set<String> hits = new LinkedHashSet<>();
            try {
                for (String serviceId : discoveryClient.getServices()) {
                    for (ServiceInstance instance : discoveryClient.getInstances(serviceId)) {
                        if (declaresFacade(instance, facadeFqn)) {
                            hits.add(serviceId);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("[rpc-http] 元数据反查失败, 降级下一级解析: {}", e.getMessage());
            }
            if (hits.size() == 1) {
                String resolved = hits.iterator().next();
                log.info("[rpc-http] {} 经注册中心元数据解析 -> {}", facadeFqn, resolved);
                cache.put(facadeFqn, resolved);
                return resolved;
            }
            if (hits.size() > 1) {
                throw new IllegalStateException("HTTP 传输: Facade " + facadeFqn + " 被多个服务声明(" + hits
                        + ") —— 契约归属歧义, 请显式配置 triform.rpc.http.service-names." + facadeFqn);
            }
        }
        // ③ legacy 包名约定(未经验证的推导, 不缓存: 提供方注册元数据后下次解析自动升级)
        return HttpRpcTargets.legacyServiceNameOf(facadeFqn);
    }

    /**
     * 启动期静态名（仅①③，不碰注册中心）：给 Feign target 一个占位 URL 用；
     * 真正的目标解析发生在每次请求的 {@link #resolve(String)}。
     */
    public String resolveStatic(String facadeFqn) {
        String configured = configured(environment, facadeFqn);
        if (configured != null) {
            return configured;
        }
        try {
            return HttpRpcTargets.legacyServiceNameOf(facadeFqn);
        } catch (Exception e) {
            return facadeFqn.substring(facadeFqn.lastIndexOf('.') + 1);
        }
    }

    private static String configured(Environment environment, String facadeFqn) {
        if (environment == null) {
            return null;
        }
        String v = environment.getProperty("triform.rpc.http.service-names." + facadeFqn);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static boolean declaresFacade(ServiceInstance instance, String facadeFqn) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata == null) {
            return false;
        }
        String declared = metadata.get(METADATA_KEY);
        if (declared == null || declared.isEmpty()) {
            return false;
        }
        return Arrays.stream(declared.split(",")).map(String::trim).anyMatch(facadeFqn::equals);
    }
}
