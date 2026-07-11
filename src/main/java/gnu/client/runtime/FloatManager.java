package gnu.client.runtime;

import gnu.client.event.PreUpdateEvent;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.LinkedHashMap;

/**
 * Shared float nudge ownership (OpenMyau FloatManager).
 * NO_SLOW driven by NoSlow when wired; do not activate from other modules this pass.
 */
public final class FloatManager {
    public static final FloatManager INSTANCE = new FloatManager();

    private final LinkedHashMap<FloatModules, Boolean> activeMap = new LinkedHashMap<>();
    private boolean floating;

    private FloatManager() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    /** OpenMyau FloatManager.isFalling — extracted for tests. */
    public static boolean isFallingEdge(boolean onGround, double posY, double lastTickPosY, double motionY) {
        return onGround && (posY - lastTickPosY) < 0.0 && motionY < 0.0;
    }

    public boolean isPredicted() {
        return floating;
    }

    public boolean hasActiveModule() {
        return activeMap.containsValue(Boolean.TRUE);
    }

    public void setFloatState(boolean state, FloatModules module) {
        if (module != null)
            activeMap.put(module, state);
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        EntityPlayerSP p = Mc.player();
        if (p == null) {
            floating = false;
            return;
        }
        if ((hasActiveModule() || isPredicted())
                && isFallingEdge(p.onGround, p.posY, p.lastTickPosY, p.motionY)) {
            p.setPosition(p.posX, p.posY + 0.001, p.posZ);
            floating = true;
        } else {
            floating = false;
        }
    }
}
