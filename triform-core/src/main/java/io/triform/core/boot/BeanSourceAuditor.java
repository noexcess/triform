package io.triform.core.boot;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 启动期 Bean 来源审计(混装检测)。
 *
 * <p>背景:mono 聚合部署时若本地仓库残留旧版 io.triform 构件,旧 jar 可能与 reactor 构件
 * 同时进入 classpath——同 FQN 类存在两个来源。此时启动不报错,运行期行为取决于类加载
 * 顺序,极难排查。
 *
 * <p>原理:应用就绪后,对容器内全部 io.triform 业务 Bean 类逐个调用
 * {@link ClassLoader#getResources(String)} 枚举该 FQN 在 classpath 上的全部来源;
 * 命中超过 1 个即判定混装,启动日志输出类与来源清单。
 * 构建期兜底由 maven-enforcer banDuplicateClasses 负责。
 *
 * <p>实现注记:BeanDefinition 经 {@link BeanDefinitionRegistry} 读取(Boot 应用上下文
 * 均实现该接口),不依赖 ConfigurableListableBeanFactory。
 *
 * <p>开关:iam.bean-audit.enabled,缺省开启。
 */
@Component
@ConditionalOnProperty(prefix = "iam.bean-audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BeanSourceAuditor implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(BeanSourceAuditor.class);

    /** 只审计业务类;第三方 jar 的重复由构建期 enforcer 兜底,启动期不做全量扫描。 */
    private static final String BUSINESS_PACKAGE_PREFIX = "io.triform";

    /** 单类冲突明细最多打印的来源数,防止极端情况下刷屏。 */
    private static final int MAX_SOURCES_PER_CLASS = 5;

    /** 混装类清单最多完整打印的类数,其余计数省略。 */
    private static final int MAX_CLASSES_PRINTED = 30;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            audit(event.getApplicationContext());
        } catch (Exception e) {
            // 审计是旁路观测,任何异常不得影响应用
            log.warn("[bean-audit] 审计执行失败,已跳过: {}", e.getMessage());
        }
    }

    private void audit(ApplicationContext context) {
        if (!(context instanceof BeanDefinitionRegistry)) {
            log.info("[bean-audit] 上下文非 BeanDefinitionRegistry,跳过审计");
            return;
        }
        long start = System.currentTimeMillis();
        ClassLoader cl = context.getClassLoader() != null
                ? context.getClassLoader() : Thread.currentThread().getContextClassLoader();
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context;
        List<String> classNames = collectBusinessBeanClassNames(registry);
        Map<String, List<String>> conflicts = findConflicts(cl, classNames);

        long cost = System.currentTimeMillis() - start;
        if (conflicts.isEmpty()) {
            log.info("[bean-audit] OK: 审计 {} 个业务 Bean 类,classpath 无重复来源 ({} ms)", classNames.size(), cost);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[bean-audit] 检测到 ").append(conflicts.size()).append(" 个业务类存在多个 classpath 来源")
          .append("(陈旧构件混装/重复依赖),运行行为取决于类加载顺序,须尽快清理:\n");
        int printed = 0;
        for (Map.Entry<String, List<String>> entry : conflicts.entrySet()) {
            if (printed++ >= MAX_CLASSES_PRINTED) {
                sb.append("  ... 其余 ").append(conflicts.size() - MAX_CLASSES_PRINTED).append(" 个类省略\n");
                break;
            }
            sb.append("  ").append(entry.getKey()).append('\n');
            int s = 0;
            for (String source : entry.getValue()) {
                if (s++ >= MAX_SOURCES_PER_CLASS) {
                    sb.append("    ... 其余 ").append(entry.getValue().size() - MAX_SOURCES_PER_CLASS).append(" 个来源省略\n");
                    break;
                }
                sb.append("    -> ").append(source).append('\n');
            }
        }
        sb.append("  共涉及 ").append(conflicts.size()).append(" 个类 / ")
          .append(sumSources(conflicts)).append(" 个来源;处理:清理本地仓库旧构件(删除 .m2repo 对应目录)后重新打包");
        log.error(sb.toString());
    }

    /** 从 BeanDefinitionRegistry 收集业务包下的 Bean 类名(不含第三方/内部工厂类)。 */
    static List<String> collectBusinessBeanClassNames(BeanDefinitionRegistry registry) {
        List<String> names = new ArrayList<>();
        for (String beanName : registry.getBeanDefinitionNames()) {
            String className;
            try {
                BeanDefinition definition = registry.getBeanDefinition(beanName);
                className = definition.getBeanClassName();
            } catch (Exception e) {
                continue;
            }
            if (className != null && className.startsWith(BUSINESS_PACKAGE_PREFIX)) {
                names.add(className);
            }
        }
        Collections.sort(names);
        return names;
    }

    /**
     * 逐类枚举 classpath 来源;同 FQN 命中多个不同位置即冲突。
     * 返回 fqn -> 来源 URL 列表(已去重、按发现顺序)。
     */
    public static Map<String, List<String>> findConflicts(ClassLoader classLoader, Collection<String> classNames) {
        Map<String, List<String>> conflicts = new LinkedHashMap<>();
        if (classLoader == null || classNames == null) {
            return conflicts;
        }
        for (String className : classNames) {
            Set<String> sources = sourcesOf(classLoader, className);
            if (sources.size() > 1) {
                conflicts.put(className, new ArrayList<>(sources));
            }
        }
        return conflicts;
    }

    private static Set<String> sourcesOf(ClassLoader classLoader, String className) {
        Set<String> sources = new LinkedHashSet<>();
        String resourcePath = className.replace('.', '/') + ".class";
        try {
            Enumeration<URL> found = classLoader.getResources(resourcePath);
            while (found.hasMoreElements()) {
                sources.add(String.valueOf(found.nextElement()));
            }
        } catch (IOException e) {
            // 单类枚举失败按无来源处理,不中断整体审计
        }
        return sources;
    }

    private static int sumSources(Map<String, List<String>> conflicts) {
        int total = 0;
        for (List<String> sources : conflicts.values()) {
            total += sources.size();
        }
        return total;
    }
}
