package io.triform.rpc.resilience4j;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** T10: triform.rpc.resilience=resilience4j 时装配治理装饰器; 缺省 none 零依赖。 */
@AutoConfiguration
@ConditionalOnProperty(name = "triform.rpc.resilience", havingValue = "resilience4j")
public class Resilience4jAutoConfiguration {

    @Bean
    public Resilience4jRpcDecorator resilience4jRpcDecorator() {
        return new Resilience4jRpcDecorator();
    }
}
