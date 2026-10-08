package io.triform.core.datascope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据范围注入注解：打在 Mapper 方法（或 Mapper 接口）上，
 * 由 DataScopeHandler 按当前会话快照的可见集合生成 WHERE 条件追加。
 * 判定规则见《底座权限系统开发落地设计》§3.4.2：维度即 OR 分支，无分支 1=0，平台旁路，未注解不改写。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * 显式豁免：true 时该语句不参与自动数据权限（自动模式下用于排除个别语句）。
     */
    boolean disabled() default false;

    /**
     * 消费的维度，空 = 全部可用维度。
     */
    Dimension[] dimensions() default {};

    /** 租户列名 */
    String tenantColumn() default "tenant_id";

    /** 渠道列名 */
    String channelColumn() default "channel_id";

    /** 企业列名 */
    String corpColumn() default "corp_id";

    /** 部门列名 */
    String deptColumn() default "dept_code";

    /** 用户列名 */
    String userColumn() default "user_id";

    enum Dimension {
        TENANT, CHANNEL, CORP, DEPT, USER
    }
}
