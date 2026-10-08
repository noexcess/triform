package io.triform.core.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 部署形态的环境级默认值: mono(缺省) 自动关闭服务注册, 兑现"mono 0 项配置"。
 *
 * <p>starter 携带 nacos-discovery(micro 需要); mono 下若不关, 会向默认 Nacos 地址发起注册。
 * 本处理器在自动装配之前以**最低优先级**属性源注入关闭项:
 * <ul>
 *   <li>deployment.mode != micro 时生效(mono 及未配置);</li>
 *   <li>宿主 yml/环境变量里的显式配置优先级更高, 可随时覆盖打开。</li>
 * </ul>
 */
public class TriformDeploymentEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String mode = environment.getProperty("deployment.mode", "mono");
        if ("micro".equals(mode)) {
            return;
        }
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.cloud.nacos.discovery.enabled", "false");
        defaults.put("spring.cloud.service-registry.auto-registration.enabled", "false");
        environment.getPropertySources().addLast(new MapPropertySource("triformMonoDefaults", defaults));
    }
}
