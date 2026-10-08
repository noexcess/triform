package io.triform.core;

/**
 * 部署环境标识(设计基线/21 附录):非生产环境配置如 TEST,生产留空。
 * 由 triform.env 配置注入;Result.env 序列化时随响应返回,便于区分问题来源环境。
 */
public final class EnvHolder {

    private static volatile String env = "";

    private EnvHolder() {
    }

    public static void init(String env) {
        EnvHolder.env = env == null ? "" : env;
    }

    public static String get() {
        return env;
    }
}
