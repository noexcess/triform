package io.triform.core.rpc;

public class IndexLocalFacadeImpl implements IndexLocalFacade {
    @Override
    public String ping() {
        return "local";
    }
}
