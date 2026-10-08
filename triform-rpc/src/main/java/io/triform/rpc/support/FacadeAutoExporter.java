package io.triform.rpc.support;

import io.triform.rpc.annotation.RemoteFacade;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 编程式 Facade 导出(消灭提供方 @DubboService 注解, 基线/42 §8.4):
 *
 * <p>micro 模式下, 扫描容器中所有"实现了 @RemoteFacade 接口的 Bean", 逐个以
 * {@link ServiceConfig#setRef(Object)} 导出为 Dubbo provider——与 @DubboService 注解等价,
 * 但接口与实现类上不再有任何传输注解, mono/micro 差异完全收敛到本类与 DubboConsumerRegistrar。
 *
 * <p>mono 模式(@ConditionalOnProperty deployment.mode!=micro)本 Bean 不装配, 天然不导出。
 *
 * <p>护栏与既有语义:
 * <ul>
 *   <li>每个接口只导出一次(按 interfaceClass 去重);</li>
 *   <li>导出前经 DubboBootstrap 统一注册(与注解扫描路径同一套注册中心配置);</li>
 *   <li>与 @DubboService 注解共存时以注解为准: 本类跳过已被注解扫描导出的接口
 *       (过渡期安全, 全部移除注解后此护栏自然沉默)。</li>
 * </ul>
 */
public class FacadeAutoExporter implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(FacadeAutoExporter.class);

    private final ApplicationContext applicationContext;

    public FacadeAutoExporter(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, ?> beans = applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Service.class);
        List<ServiceConfig<Object>> configs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, String> refNames = new LinkedHashMap<>();
        for (Object bean : beans.values()) {
            for (Class<?> it : FacadeContracts.of(bean)) {
                // 每个接口只导出一次; 出现多个实现 = 契约歧义, fail-fast(与守卫体系的"陷阱变构建失败"一致)
                if (!it.isAnnotationPresent(RemoteFacade.class)) {
                    continue;
                }
                if (!seen.add(it.getName())) {
                    throw new IllegalStateException("同一 @RemoteFacade 接口存在多个实现 Bean: " + it.getName()
                            + " (实现=" + bean.getClass().getName() + ") —— 请保留唯一实现或在消费侧显式排除");
                }
                if (alreadyExportedViaAnnotation(it)) {
                    log.info("[rpc-export] {} 已由 @DubboService 导出, 跳过编程式导出", it.getName());
                    continue;
                }
                ServiceConfig<Object> sc = new ServiceConfig<>();
                sc.setInterface(it);
                sc.setRef(bean);
                sc.setRetries(0);
                configs.add(sc);
                refNames.put(it.getName(), bean.getClass().getSimpleName());
            }
        }
        if (configs.isEmpty()) {
            return;
        }
        // 全部提交后 start() 一次: DubboBootstrap.start() 有状态机, 循环内逐个 start 只有第一个生效,
        // 其后的 service() 不会被导出(实测踩坑: 只有首个 Facade 出现在注册中心, 其余 No provider)
        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        for (ServiceConfig<Object> sc : configs) {
            String itName = sc.getInterfaceClass().getName();
            bootstrap.service(sc);
            log.info("[rpc-export] {} 提交编程式导出(ref={})", itName, refNames.get(itName));
        }
        bootstrap.start();
        log.info("[rpc-export] 编程式导出完成: {} 个 Facade", configs.size());
    }

    /** 注解扫描(@EnableDubbo)是否已把该接口作为 provider 导出——过渡期共存护栏 */
    private boolean alreadyExportedViaAnnotation(Class<?> it) {
        try {
            Map<String, Object> dubboBeans = applicationContext.getBeansWithAnnotation(
                    org.apache.dubbo.config.annotation.DubboService.class);
            for (Object b : dubboBeans.values()) {
                for (Class<?> i : FacadeContracts.of(b)) {
                    if (i.equals(it)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignore) {
            // 注解扫描不可用时按"未导出"处理
        }
        return false;
    }
}
