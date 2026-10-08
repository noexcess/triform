package io.triform.__NAME__.boot;

import io.triform.rpc.support.GeneralServiceBootstrap;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * __NAME__ 微服务启动类(mono 下由 all-in-one 聚合启动, 本类用于 micro 独立运行)。
 * 零接线: 消费代理/导出/底座 Bean 全部自动装配, 无需任何 triform 注解与扫描配置。
 * 注意: 启动类放模块根包(Spring 默认扫描自身包及子包)。
 */
@SpringBootApplication
public class Service__CAP__Application {
    public static void main(String[] args) {
        GeneralServiceBootstrap.run(Service__CAP__Application.class, args);
    }
}
