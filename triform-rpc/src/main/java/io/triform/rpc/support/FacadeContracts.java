package io.triform.rpc.support;

import io.triform.rpc.annotation.RemoteFacade;
import org.springframework.aop.support.AopUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bean 的 @RemoteFacade 契约接口识别(D2): 经 {@link AopUtils#getTargetClass()} 取目标类后递归收集接口 ——
 * CGLIB 类代理(@事务/@切面等)不直接声明父类接口, {@code bean.getClass().getInterfaces()} 会漏识别
 * (漏导出 provider / 漏发布元数据)。调用方仍应持有**代理 Bean** 本身, 不得绕开切面。
 */
public final class FacadeContracts {

    private FacadeContracts() {
    }

    /** Bean(可能是代理)实现的全部 @RemoteFacade 接口, 声明顺序稳定 */
    public static Set<Class<?>> of(Object bean) {
        return ofClass(AopUtils.getTargetClass(bean));
    }

    /** 类(通常为 BeanDefinition 声明的目标类)实现的全部 @RemoteFacade 接口 */
    public static Set<Class<?>> ofClass(Class<?> clazz) {
        return collectFacades(clazz);
    }

    /**
     * BFS 遍历类/接口继承图, 收集全部 @RemoteFacade 契约(含间接继承)。
     * 不用 Spring 的 getAllInterfacesForClassAsSet: 其对"入参本身是接口"走 singleton 短路,
     * 不递归展开父接口(Combined extends ContractA, ContractB 只会返回 Combined 本身)。
     */
    private static Set<Class<?>> collectFacades(Class<?> root) {
        Set<Class<?>> facades = new LinkedHashSet<>();
        Set<Class<?>> visited = new LinkedHashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            if (current == null || !visited.add(current)) {
                continue;
            }
            if (current.isInterface() && current.isAnnotationPresent(RemoteFacade.class)) {
                facades.add(current);
            }
            for (Class<?> iface : current.getInterfaces()) {
                queue.add(iface);
            }
            if (!current.isInterface()) {
                Class<?> superclass = current.getSuperclass();
                if (superclass != null && superclass != Object.class) {
                    queue.add(superclass);
                }
            }
        }
        return facades;
    }
}
