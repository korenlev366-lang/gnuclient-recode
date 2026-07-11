package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;

import java.util.Arrays;
import java.util.List;

public final class AnimationsModule extends Module {

    private static final List<String> MODES = Arrays.asList(
            "Vanilla", "Exhibition", "ETB", "Sigma", "Dortware", "Plain",
            "Spin", "Avatar", "Swong", "Swang", "Swank", "Styles",
            "Nudge", "Punch", "Jigsaw", "Slide");

    public static final int MODE_VANILLA = 0;
    public static final int MODE_EXHIBITION = 1;
    public static final int MODE_ETB = 2;
    public static final int MODE_SIGMA = 3;
    public static final int MODE_DORTWARE = 4;
    public static final int MODE_PLAIN = 5;
    public static final int MODE_SPIN = 6;
    public static final int MODE_AVATAR = 7;
    public static final int MODE_SWONG = 8;
    public static final int MODE_SWANG = 9;
    public static final int MODE_SWANK = 10;
    public static final int MODE_STYLES = 11;
    public static final int MODE_NUDGE = 12;
    public static final int MODE_PUNCH = 13;
    public static final int MODE_JIGSAW = 14;
    public static final int MODE_SLIDE = 15;

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", MODE_VANILLA, MODES));
    private final SliderSetting scale = addSetting(new SliderSetting("Scale", 100f, 50f, 150f, 1f));
    private final SliderSetting swingSpeed = addSetting(new SliderSetting("SwingSpeed", 0f, 0f, 100f, 1f));

    private static AnimationsModule INSTANCE;

    public AnimationsModule() {
        super("Animations", "Custom first-person swing and block animations", Category.VISUALS);
        INSTANCE = this;
        setEnabled(true);
    }

    public static AnimationsModule instance() {
        return INSTANCE;
    }

    public int getModeIndex() {
        return mode.getValue();
    }

    public int getScalePct() {
        return Math.round(scale.getValue());
    }

    public int getSwingSpeedPct() {
        return Math.round(swingSpeed.getValue());
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }

    /** OpenMyau / syuto formula: 6 + pct/100*14, pct clamped 0..100. */
    public static int armSwingAnimationEnd(int swingSpeedPct) {
        int pct = Math.max(0, Math.min(100, swingSpeedPct));
        return (int) (6.0D + (double) pct / 100.0D * 14.0D);
    }
}
