package io.triform.sample.app.web;

import io.triform.core.Result;
import io.triform.sample.api.facade.GreetingFacade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 消费点: 构造器注入对方契约 —— 与 Controller 还是 Service 无关, 容器里的类型化 Bean 而已。 */
@RestController
@RequestMapping("/app")
public class GreetController {

    private final GreetingFacade greetingFacade;

    public GreetController(GreetingFacade greetingFacade) {
        this.greetingFacade = greetingFacade;
    }

    @GetMapping("/greet")
    public Result<String> greet(@RequestParam(defaultValue = "triform") String name) {
        return greetingFacade.greet(name);
    }
}
