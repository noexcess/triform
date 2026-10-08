package io.triform.sample.allinone;

import io.triform.rpc.support.GeneralServiceBootstrap;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/** mono 单体启动类: 聚合全部服务, 跨服务调用自动落为本地 Bean 注入。 */
// 聚合壳需跨模块扫 Bean(业务服务自身启动类无需此行, 默认扫描即覆盖自己包)
@SpringBootApplication
@ComponentScan(basePackages = "io.triform")
public class SampleAllInOneApplication {

    public static void main(String[] args) {
        GeneralServiceBootstrap.run(SampleAllInOneApplication.class, args);
    }
}
