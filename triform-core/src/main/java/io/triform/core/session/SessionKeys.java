package io.triform.core.session;

/**
 * 平台会话契约常量：token 头与 Redis 会话 key 前缀。
 * 所有服务必须使用同一套常量，才能实现"同一 token 跨服务共享鉴权"。
 */
public final class SessionKeys {

    private SessionKeys() {
    }

    /**
     * 请求头 token 名。
     */
    public static final String HEADER_TOKEN = "X-Token";

    /**
     * Redis 会话快照 key 前缀（完整 key = 前缀 + token）。
     */
    public static final String REDIS_SESSION_PREFIX = "iam:session:";

    /**
     * Redis 会话反向索引 key 前缀（完整 key = 前缀 + membershipId，set 成员为 token）。
     * 用于权限/账号变更时按 membership 批量失效会话。
     */
    public static final String REDIS_SESSION_INDEX_PREFIX = "iam:session-mindex:";
}
