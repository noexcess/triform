package io.triform.rpc.support;

import io.triform.rpc.annotation.RemoteFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契约发现统一口径（E1，评审"二"）：Registrar 候选、InternalRpcEndpoint 白名单、
 * 本地实现识别三处原先各自实现的发现逻辑收敛到本类——单一规则源，杜绝"识别规则不一致"。
 *
 * <p>口径：
 * <ul>
 *   <li>契约接口 = 索引(FacadeIndexes) ∪ 显式 scan-packages；双空回退全 classpath 扫描
 *       （进程级缓存，Registrar 与端点共享一份，避免 fallback 模式双份数秒级开销）；</li>
 *   <li>本地实现 = 主判据 Bean 定义检查（ConfigurableBeanFactory.getType 覆盖 @Bean 工厂方法）
 *       + 副判据范围扫描（启动类包 ∪ scan-packages）；</li>
 *   <li>实例级识别（Exporter/元数据）见 {@link FacadeContracts}。</li>
 * </ul>
 */
public final class FacadeDiscovery {

    private static final Logger log = LoggerFactory.getLogger(FacadeDiscovery.class);

    /** 全扫回退的进程级缓存（键 = scan-packages 配置；配置运行期视为不可变） */
    private static final Map<String, Map<String, String>> FULL_SCAN_CACHE = new ConcurrentHashMap<>();

    private FacadeDiscovery() {
    }

    /** 契约接口发现（自动装配模式）：FQN → 来源("index"/包名)。双空时全扫回退（带 WARN）。 */
    public static Map<String, String> facadeInterfaces(Environment environment) {
        return facadeInterfaces(environment, new String[0]);
    }

    /**
     * 契约接口发现：索引 ∪ 显式包(@EnableDubboConsumers 的 basePackages) ∪ triform.rpc.scan-packages；
     * 三者皆空时全扫回退（进程级缓存，Registrar 与端点共享一份）。
     */
    public static Map<String, String> facadeInterfaces(Environment environment, String[] explicitPackages) {
        String scanPackages = scanPackagesOf(environment);
        Map<String, String> out = new LinkedHashMap<>(FacadeIndexes.read());
        if (explicitPackages != null && explicitPackages.length > 0) {
            out.putAll(scanFacadeInterfaces(explicitPackages));
        }
        if (StringUtils.hasText(scanPackages)) {
            out.putAll(scanFacadeInterfaces(scanPackages.split(",")));
        }
        if (!out.isEmpty()) {
            return out;
        }
        Map<String, String> cached = FULL_SCAN_CACHE.computeIfAbsent(scanPackages == null ? "" : scanPackages,
                k -> scanFacadeInterfaces(new String[]{""}));
        log.warn("[rpc] 未发现 {} 索引、未配 triform.rpc.scan-packages 且无显式包, 回退全 classpath 扫描"
                        + "(数秒级, 已进程级缓存)——建议为 api 模块引入 triform-indexer 注解处理器自动生成索引",
                FacadeIndexes.LOCATION);
        return cached;
    }

    /** 发现来源描述（日志用） */
    public static String describeSource(Environment environment, Map<String, String> facades, boolean explicitUsed) {
        boolean anyIndex = facades.containsValue("index");
        boolean hasScan = StringUtils.hasText(scanPackagesOf(environment));
        if (facades.isEmpty()) {
            return "full-scan";
        }
        if (!anyIndex && !hasScan && explicitUsed) {
            return "annotation";
        }
        StringBuilder sb = new StringBuilder();
        if (anyIndex) {
            sb.append("index");
        }
        if (hasScan) {
            sb.append(sb.length() > 0 ? "+scan-packages" : "scan-packages");
        }
        if (explicitUsed && sb.indexOf("annotation") < 0 && !anyIndex) {
            sb.append(sb.length() > 0 ? "+annotation" : "annotation");
        }
        return sb.length() > 0 ? sb.toString() : "index";
    }

    /** 本地实现识别（两级判据，D4 语义）：FQN → 实现类名。tier2 额外范围 = 启动类包 ∪ scan-packages。 */
    public static Map<String, String> localProviders(Collection<String> facadeNames,
                                                     BeanDefinitionRegistry registry,
                                                     Environment environment,
                                                     String[] explicitPackages) {
        Map<String, String> result = new HashMap<>();
        if (facadeNames == null || facadeNames.isEmpty()) {
            return result;
        }
        ClassLoader classLoader = FacadeDiscovery.class.getClassLoader();
        Map<Class<?>, String> interfaces = new LinkedHashMap<>();
        for (String ifaceName : facadeNames) {
            try {
                interfaces.put(ClassUtils.forName(ifaceName, classLoader), ifaceName);
            } catch (Throwable e) {
                log.warn("[rpc] 无法加载 Facade 接口，跳过本地实现自动识别: {}", ifaceName);
            }
        }
        if (interfaces.isEmpty()) {
            return result;
        }
        // 主判据: Bean 定义检查(getType 覆盖 @Bean 工厂方法)
        ConfigurableBeanFactory typedRegistry = registry instanceof ConfigurableBeanFactory
                ? (ConfigurableBeanFactory) registry : null;
        for (String bdName : registry.getBeanDefinitionNames()) {
            Class<?> cls = null;
            String implName = null;
            if (typedRegistry != null) {
                try {
                    cls = typedRegistry.getType(bdName);
                    implName = cls == null ? null : cls.getName();
                } catch (Throwable ignored) {
                    // 交由副判据兜底
                }
            }
            if (cls == null) {
                String cn = registry.getBeanDefinition(bdName).getBeanClassName();
                if (cn == null) {
                    continue;
                }
                try {
                    cls = ClassUtils.forName(cn, classLoader);
                    implName = cn;
                } catch (Throwable e) {
                    continue;
                }
            }
            if (cls.isInterface()) {
                // @Bean 工厂方法常以契约接口作为声明返回类型(getType 预测到此为止):
                // 声明类型==某候选契约 且为工厂方法定义 → 视为本进程 provider
                // (没人会把一个"消费中的契约"声明成 @Bean 返回类型; 手写远程客户端的极罕见场景可 exclude 覆盖)
                BeanDefinition bdForFactory = registry.getBeanDefinition(bdName);
                if (bdForFactory != null && bdForFactory.getFactoryMethodName() != null) {
                    for (Map.Entry<Class<?>, String> entry : interfaces.entrySet()) {
                        // 子接口/多父契约均合法(Combined extends FacadeA, FacadeB → 两个都命中)
                        if (entry.getKey().isAssignableFrom(cls)) {
                            result.putIfAbsent(entry.getValue(), bdName + "#factory-method");
                        }
                    }
                }
                continue;
            }
            for (Map.Entry<Class<?>, String> entry : interfaces.entrySet()) {
                if (cls != entry.getKey() && entry.getKey().isAssignableFrom(cls)) {
                    result.putIfAbsent(entry.getValue(), implName);
                }
            }
        }
        // 副判据: 范围扫描(类在场=本进程所有权, 误配以显式失败暴露)
        List<String> scope = new ArrayList<>();
        if (explicitPackages != null) {
            for (String p : explicitPackages) {
                if (StringUtils.hasText(p)) {
                    scope.add(p.trim());
                }
            }
        }
        scope.addAll(autoConfigurationPackages(registry));
        if (StringUtils.hasText(scanPackagesOf(environment))) {
            for (String p : scanPackagesOf(environment).split(",")) {
                if (StringUtils.hasText(p)) {
                    scope.add(p.trim());
                }
            }
        }
        if (scope.isEmpty()) {
            return result;
        }
        ClassPathScanningCandidateComponentProvider implScanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                AnnotationMetadata metadata = beanDefinition.getMetadata();
                return metadata.isIndependent() && metadata.isConcrete();
            }
        };
        for (Class<?> iface : interfaces.keySet()) {
            implScanner.addIncludeFilter(new AssignableTypeFilter(iface));
        }
        for (String basePackage : scope) {
            for (BeanDefinition candidate : implScanner.findCandidateComponents(basePackage)) {
                String implName = candidate.getBeanClassName();
                if (implName == null) {
                    continue;
                }
                Class<?> implClass;
                try {
                    implClass = ClassUtils.forName(implName, classLoader);
                } catch (Throwable e) {
                    log.debug("[rpc] 实现类不可加载，跳过: {}", implName);
                    continue;
                }
                for (Map.Entry<Class<?>, String> entry : interfaces.entrySet()) {
                    if (!implName.equals(entry.getValue()) && entry.getKey().isAssignableFrom(implClass)) {
                        result.putIfAbsent(entry.getValue(), implName);
                    }
                }
            }
        }
        return result;
    }

    static Map<String, String> scanFacadeInterfaces(String[] basePackages) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                AnnotationMetadata metadata = beanDefinition.getMetadata();
                return metadata.isInterface() || (metadata.isIndependent() && metadata.isConcrete());
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(RemoteFacade.class));
        Map<String, String> candidates = new LinkedHashMap<>();
        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                String ifaceName = candidate.getBeanClassName();
                if (ifaceName != null) {
                    candidates.put(ifaceName, basePackage);
                }
            }
        }
        return candidates;
    }

    static List<String> autoConfigurationPackages(BeanDefinitionRegistry registry) {
        if (registry instanceof BeanFactory) {
            try {
                return AutoConfigurationPackages.get((BeanFactory) registry);
            } catch (Exception e) {
                log.debug("[rpc] 无法获取 AutoConfigurationPackages, 副判据不含启动类包");
            }
        }
        return new ArrayList<>();
    }

    private static String scanPackagesOf(Environment environment) {
        return environment == null ? null : environment.getProperty("triform.rpc.scan-packages");
    }
}
