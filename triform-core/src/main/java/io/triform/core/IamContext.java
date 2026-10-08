package io.triform.core;

import java.io.Serializable;

/**
 * 当前请求上下文（由网关/拦截器填充）。
 * 采用轻 token 口径：只放最小必要上下文，权限快照在 Redis 会话中。
 */
public class IamContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
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
    private Long sessionVersion;
    private java.util.List<String> permissions;
    private java.util.List<String> dataScopes;

    // ===== 数据权限可见集合（快照展开结果，@DataScope 消费）=====
    private java.util.List<Long> visibleTenantIds;
    private java.util.List<Long> visibleChannelIds;
    private java.util.List<Long> visibleCorpIds;
    private java.util.List<String> visibleDeptCodes;
    private Long selfUserId;
    /** 名单可见用户 ID 并集 */
    private java.util.List<Long> visibleUserIds;

    public java.util.List<Long> getVisibleTenantIds() {
        return visibleTenantIds;
    }

    public void setVisibleTenantIds(java.util.List<Long> visibleTenantIds) {
        this.visibleTenantIds = visibleTenantIds;
    }

    public java.util.List<Long> getVisibleChannelIds() {
        return visibleChannelIds;
    }

    public void setVisibleChannelIds(java.util.List<Long> visibleChannelIds) {
        this.visibleChannelIds = visibleChannelIds;
    }

    public java.util.List<Long> getVisibleCorpIds() {
        return visibleCorpIds;
    }

    public void setVisibleCorpIds(java.util.List<Long> visibleCorpIds) {
        this.visibleCorpIds = visibleCorpIds;
    }

    public java.util.List<String> getVisibleDeptCodes() {
        return visibleDeptCodes;
    }

    public void setVisibleDeptCodes(java.util.List<String> visibleDeptCodes) {
        this.visibleDeptCodes = visibleDeptCodes;
    }

    public Long getSelfUserId() {
        return selfUserId;
    }

    public void setSelfUserId(Long selfUserId) {
        this.selfUserId = selfUserId;
    }

    public java.util.List<Long> getVisibleUserIds() {
        return visibleUserIds;
    }

    public void setVisibleUserIds(java.util.List<Long> visibleUserIds) {
        this.visibleUserIds = visibleUserIds;
    }

    public java.util.List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(java.util.List<String> permissions) {
        this.permissions = permissions;
    }

    public java.util.List<String> getDataScopes() {
        return dataScopes;
    }

    public void setDataScopes(java.util.List<String> dataScopes) {
        this.dataScopes = dataScopes;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(Long membershipId) {
        this.membershipId = membershipId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public Long getCorpId() {
        return corpId;
    }

    public void setCorpId(Long corpId) {
        this.corpId = corpId;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public Long getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(Long sessionVersion) {
        this.sessionVersion = sessionVersion;
    }
}
