package io.triform.rpc.nacos;

import com.alibaba.cloud.nacos.registry.NacosRegistration;
import com.alibaba.cloud.nacos.registry.NacosRegistrationCustomizer;
import io.triform.rpc.annotation.RemoteFacade;
import io.triform.rpc.support.FacadeContracts;
import io.triform.rpc.support.HttpServiceNameResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * C-2 提供侧元数据(基线/45): 经官方扩展点 NacosRegistrationCustomizer 把本进程实现的
 * Facade FQN 列表写入 Nacos 实例元数据(triform-facades, 逗号分隔)——消费方 HTTP 传输
 * 据此三级解析目标服务名, 消灭手工 service-names 映射。mono 下注册自动关闭, 本装配不被触发。
 */
@AutoConfiguration
public class TriformFacadeMetadataAutoConfiguration {

    @Bean
    public NacosRegistrationCustomizer triformFacadeMetadataCustomizer(ApplicationContext applicationContext) {
        return (NacosRegistration registration) -> {
            Set<String> facades = new LinkedHashSet<>();
            for (Object bean : applicationContext.getBeansWithAnnotation(
                    org.springframework.stereotype.Service.class).values()) {
                for (Class<?> iface : FacadeContracts.of(bean)) {
                    facades.add(iface.getName());
                }
            }
            if (!facades.isEmpty()) {
                registration.getMetadata().put(HttpServiceNameResolver.METADATA_KEY, String.join(",", facades));
            }
        };
    }
}
