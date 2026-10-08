package io.triform.sample.api.facade;

import io.triform.core.Result;
import io.triform.rpc.annotation.RemoteFacade;

/**
 * 样例契约: 标注 @RemoteFacade 的接口即「可被远程消费」。
 * 消费方构造器注入本接口 —— mono 注入本地 Bean, micro 注入 Dubbo/HTTP 代理, 业务代码零分支。
 */
@RemoteFacade
public interface GreetingFacade {

    Result<String> greet(String name);
}
