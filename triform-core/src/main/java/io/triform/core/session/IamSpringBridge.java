package io.triform.core.session;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Spring 静态桥：供非 Spring 管理的基础设施组件（如 Dubbo SPI 过滤器）获取容器 Bean。
 * 仅限平台底座基础设施使用，业务代码禁止依赖此桥（应走正常注入）。
 */
@Component
public class IamSpringBridge implements ApplicationContextAware {

    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static StringRedisTemplate redisTemplate() {
        if (context == null) {
            return null;
        }
        try {
            return context.getBean(StringRedisTemplate.class);
        } catch (Exception e) {
            return null;
        }
    }
}
