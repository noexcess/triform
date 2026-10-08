package io.triform.core.session;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 会话权限快照（Redis 中存储）。
 * 平台级会话契约：所有服务（iam / verify / order ...）共用同一结构，
 * 保证任意服务都能解析同一份 Redis 会话实现共享鉴权。
 */
@Data
public class SessionSnapshotDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long accountId;
    private Long membershipId;
    /** 用户姓名（审计 operator_name 来源） */
    private String userName;
    private String subjectType;
    private String subjectCode;
    private Long tenantId;
    private Long channelId;
    private Long corpId;
    private String product;
    /** 任职所属部门（下单默认成本中心、按部门的数据范围都用它；见《03》§5.3） */
    private String deptCode;
    /** 任职职级（差标/审批按职级，后续模块消费） */
    private String jobGrade;
    private List<String> roles;
    private List<String> permissions;
    private List<String> dataScopes;
    private Long authzVersion;
    private Long expireAt;

    // ===== 数据权限可见集合（登录侧按主体树展开，请求侧 @DataScope 直接消费）=====
    /** TENANT 维度分支：可见租户 ID */
    private List<Long> visibleTenantIds;
    /** CHANNEL 维度分支：可见渠道 ID */
    private List<Long> visibleChannelIds;
    /** CORP 维度分支：可见企业 ID */
    private List<Long> visibleCorpIds;
    /** DEPT 维度分支：可见部门编码 */
    private List<String> visibleDeptCodes;
    /** USER 维度分支：本人用户 ID（USER:SELF） */
    private Long selfUserId;
    /** 名单可见用户 ID 并集(USER:SELF 恒含自身;名单行追加) */
    private java.util.List<Long> visibleUserIds;

    /** 代操作（impersonation）来源操作者 userId；非代操作会话为 null */
    private Long impersonatorUserId;

    /** 代操作来源操作者登录名；非代操作会话为 null */
    private String impersonatorLoginName;
}
