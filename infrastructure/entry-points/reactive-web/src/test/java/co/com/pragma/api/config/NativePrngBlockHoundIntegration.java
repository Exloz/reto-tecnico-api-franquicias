package co.com.pragma.api.config;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

public final class NativePrngBlockHoundIntegration implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        builder.allowBlockingCallsInside("sun.security.provider.NativePRNG$RandomIO", "readFully");
    }
}
