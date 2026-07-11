package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;

public final class AnimationsModule extends Module {
    public AnimationsModule() {
        super("Animations", "Custom first-person swing and block animations", Category.VISUALS);
    }

    @Override public void onEnable() {}
    @Override public void onDisable() {}

    /** OpenMyau / syuto formula: 6 + pct/100*14, pct clamped 0..100. */
    public static int armSwingAnimationEnd(int swingSpeedPct) {
        int pct = Math.max(0, Math.min(100, swingSpeedPct));
        return (int) (6.0D + (double) pct / 100.0D * 14.0D);
    }
}
