package gnu.client.module.modules.movement;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

/**
 * raven-bS {@code Stasis} — zero local motion/input and block position {@code C03} packets
 * (look-only {@code C05PacketPlayerLook} still sent).
 */
public final class StasisModule extends Module implements PacketListener {

    public StasisModule() {
        super("Stasis", "Freeze movement", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        PacketEvents.register(this);
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
    }

    /** raven {@code PreUpdateEvent} — {@code EntityPlayerSP.onUpdate} HEAD via {@code PlayerUpdateHook}. */
    public static void onPreUpdate(Object player) {
        Module module = gnu.client.module.ModuleManager.INSTANCE.getModule("Stasis");
        if (!(module instanceof StasisModule) || !module.isEnabled())
            return;
        if (!(player instanceof EntityPlayerSP) || Mc.player() == null)
            return;

        EntityPlayerSP sp = (EntityPlayerSP) player;
        Mc.setEntityMotion(sp, 0.0, 0.0, 0.0);
    }

    /** raven {@code PrePlayerInputEvent} — {@code MovementInputHook.afterUpdatePlayerMoveState}. */
    public static void patchPlayerInput(Object movementInput) {
        Module module = gnu.client.module.ModuleManager.INSTANCE.getModule("Stasis");
        if (!(module instanceof StasisModule) || !module.isEnabled())
            return;
        if (!(movementInput instanceof MovementInput))
            return;

        MovementInput input = (MovementInput) movementInput;
        input.moveForward = 0.0f;
        input.moveStrafe = 0.0f;
    }

    @Override
    public boolean onSend(Object packet) {
        if (!isEnabled())
            return false;
        if (!PacketHelper.isPlayerMovement(packet))
            return false;
        return !PacketHelper.isC05PacketPlayerLook(packet);
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }
}
