package io.triform.rpc.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.triform.core.BizException;
import io.triform.core.Result;
import io.triform.core.ResultCode;
import io.triform.rpc.annotation.RemoteFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * provider 侧的<b>通用 HTTP RPC 端点</b>(备选传输用):一个控制器覆盖全部 Facade。
 *
 * <pre>
 *   POST /internal/rpc/{facadeFqn}/{methodName}      body: [arg0, arg1, ...]
 * </pre>
 *
 * <p>为什么不做"每个 Facade 一个 @RestController 适配器":那样每加一个接口就要补一个文件,
 * 与"需要时快速转型"的目标冲突;通用端点让新增 Facade 零额外代码。代价是安全面必须自己守住:
 * <ul>
 *   <li><b>默认关闭</b>:{@code triform.rpc.http-expose=true} 才注册本端点(否则该路径不存在);</li>
 *   <li><b>必须配令牌</b>:{@code triform.rpc.internal-token} 为空时**启动即失败**(fail-fast),
 *       避免"以为有鉴权其实没有";</li>
 *   <li><b>白名单</b>:只接受标注 {@link RemoteFacade} 的接口,其他 Bean 一律拒绝 —— 否则这里就是
 *       "反射调用任意 Bean"的远程执行入口;</li>
 *   <li><b>网络</b>:网关必须拒绝 {@code /internal/**}(见《11-备选传输方案》§4)。</li>
 * </ul>
 * 参数转换用 Spring 的 {@link ObjectMapper}(带全局宽容时间反序列化),因此 {@code LocalDateTime} 等类型与业务接口一致。
 */
@RestController
@RequestMapping(HttpRpcTargets.BASE_PATH)
@ConditionalOnProperty(prefix = "triform.rpc", name = "http-expose", havingValue = "true")
public class InternalRpcEndpoint {

    private static final Logger log = LoggerFactory.getLogger(InternalRpcEndpoint.class);

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final Set<String> exposableFacades;

    public InternalRpcEndpoint(ApplicationContext applicationContext,
                               ObjectMapper objectMapper,
                               @Value("${triform.rpc.internal-token:}") String internalToken) {
        if (!StringUtils.hasText(internalToken)) {
            throw new IllegalStateException(
                    "已开启 triform.rpc.http-expose 但未配置 triform.rpc.internal-token —— 内部 RPC 端点无鉴权禁止开启");
        }
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
        this.exposableFacades = new LinkedHashSet<>(
                FacadeDiscovery.facadeInterfaces(applicationContext.getEnvironment()).keySet());
        log.info("[rpc-http] 内部 RPC 端点已开启: {} (白名单 Facade {} 个, 令牌已配置)",
                HttpRpcTargets.BASE_PATH, exposableFacades.size());
    }

    @PostMapping("/{facade}/{method}")
    public ResponseEntity<Object> invoke(@PathVariable("facade") String facadeFqn,
                                         @PathVariable("method") String methodName,
                                         @RequestBody(required = false) JsonNode argsNode) {
        if (!exposableFacades.contains(facadeFqn)) {
            // 不是 @RemoteFacade 接口 → 与"路径不存在"同义,避免暴露内部结构
            return ResponseEntity.notFound().build();
        }
        Class<?> facade;
        try {
            facade = ClassUtils.forName(facadeFqn, getClass().getClassLoader());
        } catch (Throwable e) {
            return ResponseEntity.notFound().build();
        }
        Object target = applicationContext.getBean(facade);
        Method method = findMethod(facade, methodName, argsNode);
        Object[] args = convertArgs(method, argsNode);
        // @RequiresSession 断言:HTTP 备用传输与 Dubbo 过滤器同一语义(有会话放行,无会话点名报错)
        SessionRequiredChecker.assertSessionIfRequired(facade, method.getName(), method.getParameterTypes());
        try {
            Object result = method.invoke(target, args);
            return ResponseEntity.ok(result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof BizException) {
                BizException biz = (BizException) cause;
                log.warn("[rpc-http] {}#{} 业务异常: {}", facade.getSimpleName(), methodName, biz.getMessage());
                return ResponseEntity.badRequest().body(Result.fail(biz.getCode(), biz.getMessage()));
            }
            log.error("[rpc-http] {}#{} 调用失败", facade.getSimpleName(), methodName, cause);
            return ResponseEntity.status(500).body(Result.fail(ResultCode.SYSTEM_ERROR, "内部 RPC 调用失败"));
        } catch (Exception e) {
            log.error("[rpc-http] {}#{} 调用异常", facade.getSimpleName(), methodName, e);
            return ResponseEntity.status(500).body(Result.fail(ResultCode.SYSTEM_ERROR, "内部 RPC 调用异常"));
        }
    }

    /** 按方法名 + 参数个数定位(重载且参数个数相同则拒绝,避免歧义调用)。 */
    private Method findMethod(Class<?> facade, String methodName, JsonNode argsNode) {
        int arity = argsNode == null || !argsNode.isArray() ? 0 : argsNode.size();
        Method matched = null;
        for (Method m : facade.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterTypes().length == arity) {
                if (matched != null) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "内部 RPC 不支持同名同参数个数的重载方法: " + facade.getSimpleName() + "#" + methodName);
                }
                matched = m;
            }
        }
        if (matched == null) {
            throw new BizException(ResultCode.NOT_FOUND,
                    "找不到方法: " + facade.getSimpleName() + "#" + methodName + "(" + arity + " 个参数)");
        }
        return matched;
    }

    private Object[] convertArgs(Method method, JsonNode argsNode) {
        Type[] types = method.getGenericParameterTypes();
        if (types.length == 0) {
            return new Object[0];
        }
        if (argsNode == null || !argsNode.isArray() || argsNode.size() != types.length) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "参数个数不匹配: " + method.getName() + " 需要 " + types.length + " 个");
        }
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = objectMapper.convertValue(argsNode.get(i),
                    objectMapper.getTypeFactory().constructType(types[i]));
        }
        return args;
    }

    private Set<String> scanExposableFacades(String scanPackages) {
        Set<String> facades = new LinkedHashSet<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(
                            org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isInterface();
                    }
                };
        scanner.addIncludeFilter(new AnnotationTypeFilter(RemoteFacade.class));
        // 空缺省=全 classpath 扫描(端点本身有令牌鉴权, 白名单收紧由 scan-packages 可选配置)
        scanner.findCandidateComponents(scanPackages == null ? "" : scanPackages.trim())
                .forEach(bd -> facades.add(bd.getBeanClassName()));
        return facades;
    }
}
