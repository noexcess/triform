package io.triform.core;

/**
 * 通用错误码。
 *
 * 业务错误码分段约定(新服务按段申领,防撞号):
 *   0 成功;400/401/403/404/500 通用段
 *   10xxx iam/主体/会话(既成)
 *   11xxx 机票族订单服务   12xxx 酒店订单服务   13xxx 账单服务
 *   14xxx 公共簇(消息/文件/配置/字典/政策)  15xxx 资源/网关/开放平台/报表
 *   2xxxx 预留
 */
public final class ResultCode {

    private ResultCode() {
    }

    public static final int SUCCESS = 0;
    public static final int SYSTEM_ERROR = 500;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int TOO_MANY_REQUESTS = 429;

    // IAM 业务错误码 10000+
    public static final int ACCOUNT_NOT_FOUND = 10001;
    public static final int PASSWORD_ERROR = 10002;
    public static final int ACCOUNT_DISABLED = 10003;
    public static final int TOKEN_INVALID = 10004;
    public static final int SESSION_EXPIRED = 10005;
    public static final int PRODUCT_NOT_OPENED = 10006;
    public static final int NO_MEMBERSHIP = 10007;
    public static final int SUBJECT_NOT_FOUND = 10008;
    public static final int ROLE_NOT_FOUND = 10009;
    public static final int PERMISSION_DENIED = 10010;
    public static final int LOGIN_LOCKED = 10011;
    public static final int PASSWORD_WEAK = 10012;
}
