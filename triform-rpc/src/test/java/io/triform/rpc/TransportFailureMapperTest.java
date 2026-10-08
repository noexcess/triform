package io.triform.core.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.triform.core.BizException;
import io.triform.core.ResultCode;
import io.triform.rpc.support.TransportFailureMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP 传输失败映射:必须**保留提供侧业务错误码**(切换传输后 10010/10011 这类分支逻辑不能失效),
 * 解析不出时才退回通用码并给出指向根因的提示。
 */
class TransportFailureMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsProviderBusinessCode() {
        String body = "{\"code\":10010,\"message\":\"无操作权限：corp.member.manage\",\"data\":null,\"success\":false}";
        BizException e = TransportFailureMapper.map("PermissionFacade#visibleScope", 400, body, mapper);
        assertEquals(ResultCode.PERMISSION_DENIED, e.getCode(), "必须原样保留提供侧业务码");
        assertTrue(e.getMessage().contains("corp.member.manage"), "业务消息也要保留");
    }

    @Test
    void mapsForbiddenToTokenHint() {
        BizException e = TransportFailureMapper.map("X#y", 403, null, mapper);
        assertEquals(ResultCode.PERMISSION_DENIED, e.getCode());
        assertTrue(e.getMessage().contains("internal-token"), "403 应提示令牌不一致: " + e.getMessage());
    }

    @Test
    void mapsNotFoundToExposeHint() {
        BizException e = TransportFailureMapper.map("X#y", 404, null, mapper);
        assertTrue(e.getMessage().contains("http-expose"), "404 应提示未开启端点: " + e.getMessage());
    }

    @Test
    void fallsBackOnNonResultBody() {
        BizException e = TransportFailureMapper.map("X#y", 500, "<html>502 Bad Gateway</html>", mapper);
        assertEquals(ResultCode.SYSTEM_ERROR, e.getCode());
        assertTrue(e.getMessage().contains("HTTP 500"));
    }
}
