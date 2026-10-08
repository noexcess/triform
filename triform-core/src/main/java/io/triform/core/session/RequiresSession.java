package io.triform.core.session;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明该方法必须在<strong>带用户会话的调用链</strong>中执行。
 *
 * <p>解决"隐式上下文"的认知负担:Facade 方法不带 token 入参(会话由过滤器从 attachment 恢复进
 * {@code IamContextHolder}),签名完全看不出"必须带会话";调用方从 MQ/定时任务线程发起调用时,
 * 此前会在实现体深处以 {@code 401 未登录或会话丢失} 呈现,排查要跨服务。标注本注解后,
 * 断言在 <strong>provider 入口</strong>先行失败,报错点名方法并给出处置提示。
 *
 * <p>标注口径(《10-新增微服务与跨服务调用指南》§契约书写):
 * <ul>
 *   <li>标在<strong>接口</strong>上 = 全部方法都要求会话(如 {@code PermissionFacade});</li>
 *   <li>标在<strong>方法</strong>上 = 仅该方法要求(如 {@code CostCenterFacade} 的查询方法;
 *       而系统间调用的 {@code match}(OA 推单,无用户会话)<strong>不得标注</strong>);</li>
 *   <li>无需方法体配合 —— 实现里 {@code IamContextHolder.require()} 照写,本注解只是把失败提前到入口。</li>
 * </ul>
 *
 * <p>运行时断言覆盖跨进程到达 provider 的两条传输:Dubbo({@code IamContextProviderFilter})与
 * 备用 HTTP 内部端点({@code InternalRpcEndpoint})。mono 进程内直调不走这两处,
 * 由 HTTP 入口层(SessionTokenInterceptor)守门 —— 与既有行为一致。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresSession {
}
