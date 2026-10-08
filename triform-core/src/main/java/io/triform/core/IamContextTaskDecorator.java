package io.triform.core;

import org.springframework.core.task.TaskDecorator;

/**
 * 请求上下文异步传递装饰器：提交时捕获 IamContext，工作线程恢复并在结束后清理。
 * 用于 ThreadPoolTaskExecutor.setTaskDecorator(...) 或 wrap() 包装裸 Runnable。
 * 约定：传递的是同一请求生命周期的上下文对象引用，下游不得修改。
 */
public class IamContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return wrap(runnable);
    }

    /** 捕获当前线程上下文，返回在工作线程中恢复/清理的包装任务。 */
    public static Runnable wrap(Runnable runnable) {
        IamContext captured = IamContextHolder.get();
        return () -> {
            if (captured == null) {
                runnable.run();
                return;
            }
            IamContextHolder.set(captured);
            try {
                runnable.run();
            } finally {
                IamContextHolder.clear();
            }
        };
    }
}
