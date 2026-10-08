package io.triform.sample.app;

import io.triform.rpc.support.GeneralServiceBootstrap;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 样例消费方启动类。本进程没有 GreetingFacade 的实现类, Registrar 自动注册远程代理(micro)。 */
@SpringBootApplication
public class SampleAppApplication {

    public static void main(String[] args) {
        GeneralServiceBootstrap.run(SampleAppApplication.class, args);
    }
}
