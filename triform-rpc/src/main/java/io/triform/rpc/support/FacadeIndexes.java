package io.triform.rpc.support;

import io.triform.rpc.annotation.RemoteFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ClassUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Facade 索引文件读取(D3, 基线/45): {@code classpath*:META-INF/triform-facades}（每行一个 FQN, # 注释）。
 * Registrar 候选发现与 InternalRpcEndpoint 白名单共用本读取器——只读资源不解析字节码, 毫秒级。
 * 索引项做加载与 @RemoteFacade 校验, 防陈旧索引。
 */
public final class FacadeIndexes {

    public static final String LOCATION = "classpath*:META-INF/triform-facades";

    private static final Logger log = LoggerFactory.getLogger(FacadeIndexes.class);

    private FacadeIndexes() {
    }

    /** @return FQN → 来源(固定 "index"), 保持文件内声明顺序; 无索引返回空 Map */
    public static Map<String, String> read() {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver(FacadeIndexes.class.getClassLoader())
                    .getResources(LOCATION);
            for (Resource resource : resources) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        try {
                            Class<?> iface = ClassUtils.forName(line, FacadeIndexes.class.getClassLoader());
                            if (iface.isAnnotationPresent(RemoteFacade.class)) {
                                out.putIfAbsent(line, "index");
                            } else {
                                log.warn("[rpc] 索引项未标 @RemoteFacade, 忽略: {}", line);
                            }
                        } catch (Throwable e) {
                            log.warn("[rpc] 索引项不可加载(陈旧索引?), 忽略: {}", line);
                        }
                    }
                } catch (Exception e) {
                    log.debug("[rpc] 索引文件读取失败: {}", resource, e);
                }
            }
        } catch (Exception e) {
            log.debug("[rpc] 索引资源枚举失败", e);
        }
        return out;
    }
}
