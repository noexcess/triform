package io.triform.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 上下文异步传递验证：提交捕获 -> 工作线程恢复 -> 执行后清理。
 */
class IamContextTaskDecoratorTest {

    @Test
    void contextPropagatesToWorkerThreadAndClearedAfter() throws Exception {
        IamContext ctx = new IamContext();
        ctx.setSubjectCode("tenant:gxny");
        ctx.setUserId(3L);
        ctx.setToken("tok-1");
        IamContextHolder.set(ctx);
        try {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            AtomicReference<String> seenSubject = new AtomicReference<>();
            AtomicReference<Long> seenUser = new AtomicReference<>();
            AtomicReference<IamContext> afterRun = new AtomicReference<>();
            try {
                pool.submit(IamContextTaskDecorator.wrap(() -> {
                    IamContext inWorker = IamContextHolder.get();
                    seenSubject.set(inWorker == null ? null : inWorker.getSubjectCode());
                    seenUser.set(inWorker == null ? null : inWorker.getUserId());
                    // 模拟一次业务动作后,验证 finally 前的可见性
                    afterRun.set(IamContextHolder.get());
                })).get(5, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }
            assertEquals("tenant:gxny", seenSubject.get(), "工作线程应看到提交线程的 subjectCode");
            assertEquals(Long.valueOf(3L), seenUser.get(), "工作线程应看到提交线程的 userId");
            assertEquals("tenant:gxny", afterRun.get().getSubjectCode(), "任务执行期间上下文持续可见");
        } finally {
            IamContextHolder.clear();
        }
    }

    @Test
    void workerThreadClearedAfterTaskCompletes() throws Exception {
        IamContext ctx = new IamContext();
        ctx.setSubjectCode("corp:kinlong");
        IamContextHolder.set(ctx);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<IamContext> afterTask = new AtomicReference<>();
        try {
            // 先跑一个有上下文的任务
            pool.submit(IamContextTaskDecorator.wrap(() -> { })).get(5, TimeUnit.SECONDS);
            // 再跑一个裸任务:上一个任务的 finally 应已清理该线程
            pool.submit(() -> afterTask.set(IamContextHolder.get())).get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            IamContextHolder.clear();
        }
        assertNull(afterTask.get(), "任务结束后工作线程的上下文应被清理");
    }

    @Test
    void wrapWithoutContextRunsPlain() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<IamContext> seen = new AtomicReference<>();
        try {
            pool.submit(IamContextTaskDecorator.wrap(() -> seen.set(IamContextHolder.get()))).get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertNull(seen.get(), "无上下文提交时不应在工作线程制造上下文");
    }
}
