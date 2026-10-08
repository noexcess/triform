package io.triform.sample.echo;

import io.triform.rpc.support.GeneralServiceBootstrap;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 样例提供方启动类(3 行 main, 注解是模板的一部分)。
 * provider 导出由 FacadeAutoExporter 编程式完成, 无需 @EnableDubbo/@DubboService。
 */
@SpringBootApplication
public class SampleEchoApplication {

    public static void main(String[] args) {
        GeneralServiceBootstrap.run(SampleEchoApplication.class, args);
    }
}
