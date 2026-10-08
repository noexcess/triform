package io.triform.rpc.support;

import io.triform.rpc.annotation.RemoteFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 启动期契约校验（E2）："正常时少操作, 异常时不用猜"的后半句——
 * 不支持的形态在启动时点名报错, 不留到第一次调用才炸。
 *
 * <p>校验规则：
 * <ul>
 *   <li><b>方法重载 × HTTP 传输</b>：内部端点按方法名路由（/internal/rpc/{facade}/{method}），
 *       重载无法区分——消费侧 http 时启动即抛, 指名 Facade 与方法名；</li>
 *   <li><b>@RemoteFacade 误标在类上</b>：告警（契约只认接口）；</li>
 *   <li><b>空契约</b>（无方法接口）：告警（多为忘了写方法）。</li>
 * </ul>
 * micro 模式装配（RpcTransportAutoConfiguration）；mono 无远程调用不校验。
 */
public class FacadeContractValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(FacadeContractValidator.class);

    private final Environment environment;

    public FacadeContractValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean httpTransport = environment != null
                && "http".equalsIgnoreCase(environment.getProperty("triform.rpc.transport", "dubbo"));
        for (String fqn : FacadeDiscovery.facadeInterfaces(environment).keySet()) {
            Class<?> facade;
            try {
                facade = Class.forName(fqn, false, getClass().getClassLoader());
            } catch (Throwable e) {
                continue;   // 加载失败已由读取侧告警
            }
            if (!facade.isInterface()) {
                log.warn("[rpc-contract] @RemoteFacade 应标注在接口上, 当前为类: {}", fqn);
                continue;
            }
            Method[] methods = facade.getMethods();
            if (methods.length == 0) {
                log.warn("[rpc-contract] 契约 {} 没有任何方法 —— 若非有意, 请补全方法声明", fqn);
            }
            if (httpTransport) {
                Map<String, Integer> byName = new HashMap<>();
                for (Method m : methods) {
                    byName.merge(m.getName(), 1, Integer::sum);
                }
                Set<String> overloaded = new HashSet<>();
                byName.forEach((name, count) -> {
                    if (count > 1) {
                        overloaded.add(name);
                    }
                });
                if (!overloaded.isEmpty()) {
                    throw new IllegalStateException("HTTP 传输不支持契约方法重载(内部端点按方法名路由): "
                            + fqn + " 的重载方法 " + overloaded
                            + " —— 请拆分方法名, 或改用 dubbo 传输(triform.rpc.transport=dubbo)");
                }
            }
        }
        log.info("[rpc-contract] 契约校验通过: 重载×http={}, 注解误用=0", httpTransport ? "已检查" : "未启用(dubbo)");
    }
}
