package io.triform.rpc.support;

import io.triform.rpc.annotation.EnableDubboConsumers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 为所有标注 {@link io.triform.rpc.annotation.RemoteFacade} 的接口注册远程代理 BeanDefinition。
 *
 * <p>发现逻辑统一在 {@link FacadeDiscovery}（E1：与 InternalRpcEndpoint 白名单/本地实现识别同一口径），
 * 本类只负责：模式判定 → 候选获取 → 本地跳过 → 注册代理（dubbo/http 工厂二选一）。
 *
 * <p>注意：Spring 对 {@code @Import} 引入的 ImportBeanDefinitionRegistrar 分支不会求值
 * 类上的 @ConditionalOnProperty，因此在此处显式读取 Environment 做模式判断：
 * micro 注册远程代理（@Primary）；mono 不注册，注入本地 @Service 实现。
 */
public class DubboConsumerRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(DubboConsumerRegistrar.class);

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        if (environment == null && registry instanceof EnvironmentCapable) {
            environment = ((EnvironmentCapable) registry).getEnvironment();
        }
        String mode = environment != null ? environment.getProperty("deployment.mode") : null;
        if (!"micro".equals(mode)) {
            // mono 模式不注册远程代理，注入 @Service 本地实现
            return;
        }
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableDubboConsumers.class.getName()));
        String[] basePackages = attributes != null ? attributes.getStringArray("basePackages") : null;
        boolean annotationMode = basePackages != null && basePackages.length > 0;
        String transport = environment != null ? environment.getProperty("triform.rpc.transport", "dubbo") : "dubbo";
        boolean httpTransport = "http".equalsIgnoreCase(transport);
        if (httpTransport) {
            log.info("[rpc] 传输开关 triform.rpc.transport=http，将注册 HTTP(Feign) 远程代理"
                    + "（提供侧需同时开启 triform.rpc.http-expose=true）");
        }

        // 纯 starter(缺注册中心客户端)跑 micro 的缺件预警——不硬失败, 指路 starter-nacos
        if (!ClassUtils.isPresent("com.alibaba.nacos.api.NacosFactory", getClass().getClassLoader())) {
            String registryAddr = environment == null ? "" : environment.getProperty("dubbo.registry.address", "");
            if (httpTransport || registryAddr.startsWith("nacos://")) {
                log.warn("[rpc] micro 模式需要 Nacos 客户端但 classpath 缺失 —— 请改用 io.triform:triform-starter-nacos"
                        + "(HTTP 传输依赖 DiscoveryClient; Dubbo nacos:// 注册依赖 nacos-client)");
            }
        }

        // 手工排除清单（可选）
        Set<String> manuallyExcluded = new HashSet<>();
        if (attributes != null) {
            for (Class<?> exclude : attributes.getClassArray("exclude")) {
                manuallyExcluded.add(exclude.getName());
            }
        }

        long scanStart = System.currentTimeMillis();
        // ① 契约候选(统一口径: 索引 ∪ scan-packages, 双空全扫回退)
        Map<String, String> candidates = FacadeDiscovery.facadeInterfaces(environment,
                annotationMode ? basePackages : new String[0]);
        String discovery = FacadeDiscovery.describeSource(environment, candidates, annotationMode);
        // ② 本地实现识别(统一口径: Bean 定义检查主判据 + 范围扫描副判据)
        Map<String, String> locallyImplemented = FacadeDiscovery.localProviders(
                candidates.keySet(), registry, environment, annotationMode ? basePackages : new String[0]);
        long scanCost = System.currentTimeMillis() - scanStart;
        int autoSkipped = 0;
        int registered = 0;

        // ③ 注册远程代理
        for (String ifaceName : candidates.keySet()) {
            if (registry.containsBeanDefinition(ifaceName)) {
                continue;
            }
            if (manuallyExcluded.contains(ifaceName)) {
                log.info("[rpc] 跳过远程代理注册(手工 exclude): {}", ifaceName);
                continue;
            }
            String implClass = locallyImplemented.get(ifaceName);
            if (implClass != null) {
                autoSkipped++;
                log.info("[rpc] 跳过远程代理注册(本进程已有实现 {}): {}", implClass, ifaceName);
                continue;
            }
            Class<?> factoryBeanClass = httpTransport ? HttpRefFactoryBean.class : DubboRefFactoryBean.class;
            BeanDefinitionBuilder builder = BeanDefinitionBuilder
                    .genericBeanDefinition(factoryBeanClass)
                    .addConstructorArgValue(ifaceName)
                    .setPrimary(true)
                    .setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            registry.registerBeanDefinition(ifaceName, builder.getBeanDefinition());
            registered++;
        }
        String expose = environment != null ? environment.getProperty("triform.rpc.http-expose", "false") : "false";
        boolean tokenConfigured = environment != null
                && StringUtils.hasText(environment.getProperty("triform.rpc.internal-token"));
        log.info("[rpc] 契约装配完成: 传输={}, 发现来源={}, 提供侧端点(http-expose)={}, 内部令牌={}, 候选 {} 个, 自动跳过 {} 个(本进程有实现),"
                        + " 手工排除 {} 个, 注册远程代理 {} 个, 识别耗时 {}ms",
                transport, discovery, expose, tokenConfigured ? "已配置" : "未配置",
                candidates.size(), autoSkipped, manuallyExcluded.size(), registered, scanCost);
    }
}
