package io.triform.core.rpc;

import io.triform.rpc.annotation.RemoteFacade;

/** 索引发现用例: 仅出现在 META-INF/triform-facades, 不在任何扫描包语义内。 */
@RemoteFacade
public interface IndexDiscoveredFacade {
    String ping();
}
