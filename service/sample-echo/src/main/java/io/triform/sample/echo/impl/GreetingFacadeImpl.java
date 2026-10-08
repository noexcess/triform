package io.triform.sample.echo.impl;

import io.triform.core.Result;
import io.triform.sample.api.facade.GreetingFacade;
import org.springframework.stereotype.Service;

/** GreetingFacade 实现 —— 只有 @Service; micro 下由 FacadeAutoExporter 导出, mono 下作为本地 Bean 被注入。 */
@Service
public class GreetingFacadeImpl implements GreetingFacade {

    @Override
    public Result<String> greet(String name) {
        return Result.ok("hello, " + name + " (from sample-echo)");
    }
}
