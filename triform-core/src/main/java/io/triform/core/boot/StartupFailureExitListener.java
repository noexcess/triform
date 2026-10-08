package io.triform.core.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 启动失败即退出 JVM(公共模块,所有服务生效)。
 *
 * <p>背景:应用启动失败后 Spring Boot 默认<b>不结束进程</b>;而 RocketMQ 客户端、Dubbo 等组件的线程
 * 是非守护线程,会把失败的 JVM 一直吊住——表现为:IDEA 一直转圈(进程未退出)、8080/20880 被占用,
 * 导致后续启动报"Address already in use"或 topic 路由异常,排障时极易误判(实测踩到多次)。
 *
 * <p>处置:监听 {@link ApplicationFailedEvent},打印根因后 {@code System.exit(1)}。
 *
 * <p>开关:{@code ticket.boot.exit-on-failure},默认 true;单测等场景可置 false 关闭。
 */
@Component
@ConditionalOnProperty(prefix = "ticket.boot", name = "exit-on-failure", havingValue = "true", matchIfMissing = true)
public class StartupFailureExitListener implements ApplicationListener<ApplicationFailedEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupFailureExitListener.class);

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        Throwable ex = event.getException();
        Throwable root = ex;
        while (root != null && root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        log.error("[boot] 启动失败,JVM 即将退出(避免非守护线程吊住进程、占用端口;"
                        + "如需保留进程请设置 ticket.boot.exit-on-failure=false)。根因: {}: {}",
                root == null ? "unknown" : root.getClass().getSimpleName(),
                root == null ? "" : root.getMessage());
        // 必须把堆栈也打出来:ApplicationFailedEvent 早于 Spring 自己的 reportFailure 日志,
        // 本监听器随后 System.exit(1) 会**吞掉 Spring 的堆栈输出** —— 实测有过一次"只看到启动失败、看不到原因"。
        if (root != null) {
            log.error("[boot] 启动失败堆栈", root);
        }
        System.exit(1);
    }
}
