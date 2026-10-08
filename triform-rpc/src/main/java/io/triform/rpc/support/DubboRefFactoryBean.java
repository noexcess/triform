package io.triform.rpc.support;

import org.apache.dubbo.config.ReferenceConfig;
import org.springframework.beans.factory.FactoryBean;

/**
 * Dubbo 远程引用工厂：为单个接口生成远程代理（ReferenceConfig.get()）。
 * 由 {@link DubboConsumerRegistrar} 注册为 Spring Bean，mono 模式不注册。
 */
public class DubboRefFactoryBean<T> implements FactoryBean<T> {

    private final Class<T> iface;

    public DubboRefFactoryBean(Class<T> iface) {
        this.iface = iface;
    }

    @Override
    public T getObject() {
        ReferenceConfig<T> reference = new ReferenceConfig<>();
        reference.setInterface(iface);
        reference.setCheck(false);
        reference.setRetries(0);
        reference.setTimeout(5000);
        return reference.get();
    }

    @Override
    public Class<?> getObjectType() {
        return iface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
