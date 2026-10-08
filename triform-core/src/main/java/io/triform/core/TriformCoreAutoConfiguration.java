package io.triform.core;

import io.triform.core.boot.BeanSourceAuditor;
import io.triform.core.boot.StartupFailureExitListener;
import io.triform.core.EnvConfiguration;
import io.triform.core.EnvHolder;
import io.triform.core.json.LenientTimeJacksonConfiguration;
import io.triform.core.session.IamSpringBridge;
import io.triform.core.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * T3 零扫描: 引入 core 即自动装配全部底座 Bean, 业务启动类无需 @ComponentScan 补包。
 */
@AutoConfiguration
@Import({EnvConfiguration.class, EnvHolder.class, GlobalExceptionHandler.class,
        LenientTimeJacksonConfiguration.class, IamSpringBridge.class,
        BeanSourceAuditor.class, StartupFailureExitListener.class})
public class TriformCoreAutoConfiguration {
}
