package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.visual.FreeLookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

/**
 * Mixin bridge for {@link FreeLookModule} — camera redirects and mouse capture
 * (raven Freelook.overrideMouse parity).
 */
public final class FreeLookHook {

    private FreeLookHook() {}

    private static FreeLookModule module() {
        Module m = ModuleManager.INSTANCE.getModule("FreeLook");
        return m instanceof FreeLookModule ? (FreeLookModule) m : null;
    }

    public static boolean isActive() {
        FreeLookModule m = module();
        return m != null && m.isEnabled() && m.isPerspectiveActive();
    }

    public static float redirectYaw(Object entity) {
        FreeLookModule m = module();
        if (m != null && m.isEnabled() && m.isPerspectiveActive()) {
            return m.getCameraYaw();
        }
        return entity instanceof Entity ? ((Entity) entity).rotationYaw : 0f;
    }

    public static float redirectPitch(Object entity) {
        FreeLookModule m = module();
        if (m != null && m.isEnabled() && m.isPerspectiveActive()) {
            return m.getCameraPitch();
        }
        return entity instanceof Entity ? ((Entity) entity).rotationPitch : 0f;
    }

    /**
     * Replaces {@code Minecraft.inGameHasFocus} during {@code updateCameraAndRender}.
     * When freelook is active, consumes mouse deltas into the freelook camera and
     * returns false so vanilla does not rotate the player body.
     */
    public static boolean overrideMouse(Minecraft mc) {
        if (!mc.inGameHasFocus) {
            return false;
        }
        FreeLookModule m = module();
        if (m == null || !m.isEnabled() || !m.isPerspectiveActive()) {
            return true;
        }

        mc.mouseHelper.mouseXYChange();
        float sens = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float mult = sens * sens * sens * 8.0f;
        float dYaw = mc.mouseHelper.deltaX * mult;
        float dPitch = mc.mouseHelper.deltaY * mult;
        // applyCameraDelta applies invertPitch and vanilla setAngles math.
        m.applyCameraDelta(dYaw, dPitch);
        return false;
    }
}
