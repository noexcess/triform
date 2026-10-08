package io.triform.rpc.support;

import io.triform.core.BizException;
import io.triform.core.IamContextHolder;
import io.triform.core.ResultCode;
import io.triform.core.session.RequiresSession;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link RequiresSession} 的 provider 侧断言(两条传输共用):
 * <ul>
 *   <li>Dubbo:由 {@code IamContextProviderFilter} 在恢复上下文之后、进入实现之前调用;</li>
 *   <li>HTTP 备用传输:由 {@code InternalRpcEndpoint} 在反射调用之前调用。</li>
 * </ul>
 *
 * <p>语义:方法或其接口标注 {@code @RequiresSession} 且当前线程无 {@code IamContext} 时,
 * 抛 401,报错<strong>点名方法</strong>并给出处置提示 —— 把"实现体深处的 10001"提前到入口,
 * 调用方不用跨服务排查。有上下文时断言通过,实现体里的 {@code require()} 照常工作(注解不替代它)。
 *
 * <p>注解解析按 (接口, 方法名) 缓存:provider 每次调用都要判定,不能每次反射。
 */
public final class SessionRequiredChecker {

    private static final Map<String, Boolean> REQUIRED_CACHE = new ConcurrentHashMap<>();

    private SessionRequiredChecker() {
    }

    /**
     * 若目标方法要求会话而当前线程无上下文,抛 {@link BizException}(401)。
     *
     * @param facade      Facade 接口
     * @param methodName  方法名
     * @param paramTypes  参数类型(Dubbo invocation / 反射均可给到;泛化调用可能给空,退化为按名匹配)
     */
    public static void assertSessionIfRequired(Class<?> facade, String methodName, Class<?>[] paramTypes) {
        if (!requiresSession(facade, methodName, paramTypes)) {
            return;
        }
        if (IamContextHolder.get() != null) {
            return;
        }
        throw new BizException(ResultCode.UNAUTHORIZED,
                facade.getSimpleName() + "#" + methodName + " 标注 @RequiresSession,须在带会话的调用链中执行"
                        + "(当前为无会话调用:请确认调用方已传 X-Token;MQ/定时任务线程须显式带 token,"
                        + "系统间调用应改用不要求会话的接口)");
    }

    /** 方法级注解优先,其次接口级;解析结果按 接口#方法 缓存 */
    static boolean requiresSession(Class<?> facade, String methodName, Class<?>[] paramTypes) {
        String key = facade.getName() + "#" + methodName;
        return REQUIRED_CACHE.computeIfAbsent(key, k -> resolve(facade, methodName, paramTypes));
    }

    private static boolean resolve(Class<?> facade, String methodName, Class<?>[] paramTypes) {
        Method method = findMethod(facade, methodName, paramTypes);
        if (method != null && method.isAnnotationPresent(RequiresSession.class)) {
            return true;
        }
        return facade.isAnnotationPresent(RequiresSession.class);
    }

    private static Method findMethod(Class<?> facade, String methodName, Class<?>[] paramTypes) {
        try {
            return facade.getMethod(methodName, paramTypes == null ? new Class<?>[0] : paramTypes);
        } catch (NoSuchMethodException | SecurityException ignore) {
            // 泛化调用/参数类型不精确:按方法名找第一个同名方法(本项目契约禁重载)
        }
        for (Method m : facade.getMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        return null;
    }
}
