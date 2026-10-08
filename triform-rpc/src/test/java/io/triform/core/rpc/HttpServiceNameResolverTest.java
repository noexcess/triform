package io.triform.core.rpc;

import io.triform.rpc.support.HttpServiceNameResolver;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** C-2 三级解析: 配置 > 注册中心元数据(多命中 fail-fast) > legacy 包名约定; 正结果缓存。 */
class HttpServiceNameResolverTest {

    private static final String FQN = "lab.api.facade.TinyFacade";

    private static ServiceInstance instance(String serviceId, String facades) {
        Map<String, String> metadata = new HashMap<>();
        if (facades != null) {
            metadata.put(HttpServiceNameResolver.METADATA_KEY, facades);
        }
        return new DefaultServiceInstance(serviceId + "-1", serviceId, "127.0.0.1", 9101, false, metadata);
    }

    /** 极简假发现客户端: 服务名 -> 实例列表 */
    private static DiscoveryClient discovery(Map<String, List<ServiceInstance>> services) {
        return new DiscoveryClient() {
            @Override
            public String description() {
                return "fake";
            }

            @Override
            public List<ServiceInstance> getInstances(String serviceId) {
                return services.getOrDefault(serviceId, Collections.emptyList());
            }

            @Override
            public List<String> getServices() {
                return new java.util.ArrayList<>(services.keySet());
            }
        };
    }

    @Test
    void configTierWinsOverMetadata() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("triform.rpc.http.service-names." + FQN, "explicit-svc");
        DiscoveryClient dc = discovery(Map.of("meta-svc", List.of(instance("meta-svc", FQN))));
        assertEquals("explicit-svc", new HttpServiceNameResolver(env, dc).resolve(FQN));
    }

    @Test
    void metadataSingleHitResolvesAndCaches() {
        DiscoveryClient dc = discovery(Map.of(
                "lab-provider", List.of(instance("lab-provider", "other.F," + FQN)),
                "unrelated", List.of(instance("unrelated", "x.Y"))));
        HttpServiceNameResolver resolver = new HttpServiceNameResolver(new MockEnvironment(), dc);
        assertEquals("lab-provider", resolver.resolve(FQN));
        assertEquals("lab-provider", resolver.resolve(FQN), "正结果应缓存");
    }

    @Test
    void metadataMultiHitFailsFast() {
        DiscoveryClient dc = discovery(Map.of(
                "svc-a", List.of(instance("svc-a", FQN)),
                "svc-b", List.of(instance("svc-b", FQN))));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new HttpServiceNameResolver(new MockEnvironment(), dc).resolve(FQN));
        ex.getMessage().contains("多个服务声明");
        ex.getMessage().contains("service-names");
    }

    @Test
    void legacyFallbackForTriformPackages() {
        DiscoveryClient dc = discovery(Map.of());
        assertEquals("ticket-verify",
                new HttpServiceNameResolver(new MockEnvironment(), dc)
                        .resolve("io.triform.verify.api.facade.VerifyFacade"));
        // 历史布局(jdk8 线语义)同样兼容
        assertEquals("ticket-verify",
                new HttpServiceNameResolver(new MockEnvironment(), dc)
                        .resolve("com.p0.ticket.verify.api.facade.VerifyFacade"));
    }

    @Test
    void noTierHitsThrowsWithHint() {
        DiscoveryClient dc = discovery(Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> new HttpServiceNameResolver(new MockEnvironment(), dc).resolve("lab.api.facade.Nobody"));
    }

    @Test
    void lateRegisteredProviderUpgradesFromLegacy() {
        // 可变发现客户端: 初始无元数据, 之后 provider 注册并声明元数据
        Map<String, List<ServiceInstance>> services = new java.util.HashMap<>();
        DiscoveryClient dc = discovery(services);
        HttpServiceNameResolver resolver = new HttpServiceNameResolver(new MockEnvironment(), dc);
        String triformFqn = "io.triform.verify.api.facade.VerifyFacade";
        // 第一次: 无元数据 → legacy 推导(不缓存)
        assertEquals("ticket-verify", resolver.resolve(triformFqn));
        // provider 晚注册, 声明元数据指向 verify-service
        services.put("verify-service", List.of(instance("verify-service", triformFqn)));
        // 第二次: 自动升级为元数据命中(若 legacy 被缓存则仍是 ticket-verify, 修复前必红)
        assertEquals("verify-service", resolver.resolve(triformFqn));
    }

    @Test
    void evictForcesRediscovery() {
        Map<String, List<ServiceInstance>> services = new java.util.HashMap<>();
        DiscoveryClient dc = discovery(services);
        HttpServiceNameResolver resolver = new HttpServiceNameResolver(new MockEnvironment(), dc);
        services.put("svc-a", List.of(instance("svc-a", FQN)));
        assertEquals("svc-a", resolver.resolve(FQN));
        // 元数据变更(服务迁移): 逐出后重新解析
        services.clear();
        services.put("svc-b", List.of(instance("svc-b", FQN)));
        resolver.evict(FQN);
        assertEquals("svc-b", resolver.resolve(FQN));
    }

    @Test
    void staticResolutionSkipsDiscovery() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("triform.rpc.http.service-names." + FQN, "explicit-svc");
        // discovery 传 null 也不应抛——静态解析只走①③
        assertEquals("explicit-svc", new HttpServiceNameResolver(env, null).resolveStatic(FQN));
        assertEquals("ticket-demo", new HttpServiceNameResolver(new MockEnvironment(), null)
                .resolveStatic("io.triform.demo.api.facade.X"));
    }
}
