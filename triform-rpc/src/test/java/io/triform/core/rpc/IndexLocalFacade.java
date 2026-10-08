package io.triform.core.rpc;

import io.triform.rpc.annotation.RemoteFacade;

@RemoteFacade
public interface IndexLocalFacade {
    String ping();
}
