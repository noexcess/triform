package io.triform.core.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.triform.core.IamContext;
import io.triform.core.session.SessionSnapshotDto;

/**
 * 会话快照 -> IamContext 构建工具（公共模块）。
 * HTTP 拦截器与 Dubbo Provider 过滤器共用，保证两种入口构建的上下文完全一致。
 */
public final class IamContextLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IamContextLoader() {
    }

    /** 解析 Redis 中的快照 JSON；解析失败返回 null（调用方按无会话处理）。 */
    public static SessionSnapshotDto parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, SessionSnapshotDto.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static IamContext from(String token, SessionSnapshotDto snapshot) {
        IamContext context = new IamContext();
        context.setToken(token);
        context.setUserId(snapshot.getUserId());
        context.setUserName(snapshot.getUserName());
        context.setAccountId(snapshot.getAccountId());
        context.setMembershipId(snapshot.getMembershipId());
        context.setSubjectType(snapshot.getSubjectType());
        context.setSubjectCode(snapshot.getSubjectCode());
        context.setTenantId(snapshot.getTenantId());
        context.setChannelId(snapshot.getChannelId());
        context.setCorpId(snapshot.getCorpId());
        context.setProduct(snapshot.getProduct());
        context.setSessionVersion(snapshot.getAuthzVersion());
        context.setPermissions(snapshot.getPermissions());
        context.setDataScopes(snapshot.getDataScopes());
        context.setVisibleTenantIds(snapshot.getVisibleTenantIds());
        context.setVisibleChannelIds(snapshot.getVisibleChannelIds());
        context.setVisibleCorpIds(snapshot.getVisibleCorpIds());
        context.setVisibleDeptCodes(snapshot.getVisibleDeptCodes());
        context.setSelfUserId(snapshot.getSelfUserId());
        context.setVisibleUserIds(snapshot.getVisibleUserIds());
        return context;
    }
}
