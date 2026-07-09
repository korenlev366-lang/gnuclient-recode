package gnu.client.runtime.mc;

/** Forge-only stub — always Forge 1.8. */
public final class ClientDetector {

    private ClientDetector() {}

    public static ClientProfile detect(ClassLoader[] loaders) {
        return ClientProfile.FORGE_18;
    }
}
