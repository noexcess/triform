package io.triform.core;

/**
 * IAM 上下文 ThreadLocal 持有器。
 */
public final class IamContextHolder {

    private static final ThreadLocal<IamContext> HOLDER = new ThreadLocal<>();

    private IamContextHolder() {
    }

    public static void set(IamContext context) {
        HOLDER.set(context);
    }

    public static IamContext get() {
        return HOLDER.get();
    }

    public static IamContext require() {
        IamContext context = HOLDER.get();
        if (context == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或会话丢失");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
