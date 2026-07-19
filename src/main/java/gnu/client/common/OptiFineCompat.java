package gnu.client.common;

/**
 * OptiFine coexistence helper.
 *
 * <p>OptiFine 1.8.9 ships as a Forge coremod that reworks the same vanilla classes our
 * performance mixins touch (RenderManager entity distance, EntityRenderer weather, and
 * the ambient-occlusion / smooth-lighting pipeline). We cannot compile against OptiFine
 * (it has no public Maven coordinate and its license forbids redistribution), and
 * compile-time type-checking would not prevent runtime transform collisions anyway.
 *
 * <p>Instead we detect OptiFine on the classpath at runtime and degrade the settings that
 * are known to conflict with its transformed bytecode, so the client loads cleanly
 * alongside OptiFine rather than crashing or producing visual artifacts.
 */
public final class OptiFineCompat {

    private static final boolean PRESENT = detect();

    private OptiFineCompat() {}

    /** True if OptiFine's coremod classes are present on the classpath. */
    public static boolean isPresent() {
        return PRESENT;
    }

    /**
     * The smooth-lighting toggle forces {@code GameSettings.ambientOcclusion = 0} every
     * tick, which fights OptiFine's own lighting pipeline and can cause flicker. When
     * OptiFine is present this toggle is unsafe and should stay off.
     */
    public static boolean smoothLightingToggleAllowed() {
        return !PRESENT;
    }

    /**
     * OptiFine's "Fast Render" replaces vanilla's VBO / display-list render path with its
     * own. Any render-path optimization we apply (VBO forcing, chunk-update throttling,
     * display-list reuse) is only safe when Fast Render is OFF — with it on, OptiFine
     * owns the path and our changes would conflict or be ignored.
     */
    public static boolean renderPathTweaksAllowed() {
        return !fastRenderActive();
    }

    /** True if OptiFine is loaded AND its Fast Render option is enabled. */
    public static boolean fastRenderActive() {
        if (!PRESENT)
            return false;
        try {
            Class<?> cfg = Class.forName("net.optifine.Config");
            // Prefer the static accessor; fall back to the field.
            try {
                java.lang.reflect.Method m = cfg.getMethod("isFastRender");
                return Boolean.TRUE.equals(m.invoke(null));
            } catch (NoSuchMethodException ignored) {
                java.lang.reflect.Field f = cfg.getDeclaredField("fastRender");
                f.setAccessible(true);
                return Boolean.TRUE.equals(f.get(null));
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean detect() {
        // Either of these is present whenever OptiFine is loaded as a mod.
        return classExists("net.optifine.Config")
                || classExists("net.optifine.OptiFineClassTransformer");
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, OptiFineCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
