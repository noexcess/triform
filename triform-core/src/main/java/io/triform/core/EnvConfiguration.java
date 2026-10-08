package io.triform.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 部署环境标识装配:triform.env(如 TEST/DEMO),生产留空。Result.env 随响应返回。
 */
@Configuration
public class EnvConfiguration {

    public EnvConfiguration(@Value("${triform.env:}") String env) {
        EnvHolder.init(env);
    }
}
