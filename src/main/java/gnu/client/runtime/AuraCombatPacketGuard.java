package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;

/**
 * Grim BadPacketsX parity: at most one sprint and one sneak C0B between C03 packets.
 * Also blocks START_SPRINTING on the aura attack tick (vanilla attack slow window).
 *
 * <p>Registered while KillAura is enabled. {@link gnu.client.runtime.mc.Mc#sendSprintActionPacket}
 * peeks via {@link #shouldCancelEntityAction} (read-only); only {@link #onSend} commits the
 * one-per-move slot so a pre-check cannot mark the slot and then cancel the real send.
 */
public final class AuraCombatPacketGuard implements PacketListener {

    private static final AuraCombatPacketGuard INSTANCE = new AuraCombatPacketGuard();

    private boolean sprintActionSinceMove;
    private boolean sneakActionSinceMove;

    private AuraCombatPacketGuard() {}

    public static void register() {
        gnu.client.runtime.packet.PacketEvents.register(INSTANCE);
    }

    public static void unregister() {
        gnu.client.runtime.packet.PacketEvents.unregister(INSTANCE);
        INSTANCE.sprintActionSinceMove = false;
        INSTANCE.sneakActionSinceMove = false;
    }

    /**
     * Read-only peek for {@code Mc.send*ActionPacket} — must not mutate slot flags.
     * Commit happens only in {@link #onSend}.
     */
    public static boolean shouldCancelEntityAction(Object packet) {
        if (!isGuardActive())
            return false;
        return INSTANCE.wouldCancel(packet);
    }

    @Override
    public int sendPriority() {
        return 900;
    }

    @Override
    public boolean onSend(Object packet) {
        if (!isGuardActive())
            return false;
        if (PacketHelper.isPlayerMovement(packet)) {
            sprintActionSinceMove = false;
            sneakActionSinceMove = false;
            return false;
        }
        return checkEntityAction(packet);
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    private boolean wouldCancel(Object packet) {
        if (PacketHelper.isSprintEntityAction(packet))
            return wouldCancelSprint(packet);
        if (PacketHelper.isSneakEntityAction(packet))
            return sneakActionSinceMove;
        return false;
    }

    private boolean wouldCancelSprint(Object packet) {
        // One sprint C0B per move (BadPacketsX). No post-hit START suppress —
        // OpenMyau lets living re-sprint after attack slow.
        return sprintActionSinceMove;
    }

    private boolean checkEntityAction(Object packet) {
        if (PacketHelper.isSprintEntityAction(packet))
            return checkSprintAction(packet);
        if (PacketHelper.isSneakEntityAction(packet))
            return checkSneakAction();
        return false;
    }

    private boolean checkSprintAction(Object packet) {
        if (wouldCancelSprint(packet))
            return true;
        sprintActionSinceMove = true;
        return false;
    }

    private boolean checkSneakAction() {
        if (sneakActionSinceMove)
            return true;
        sneakActionSinceMove = true;
        return false;
    }

    private static boolean isGuardActive() {
        Module module = ModuleManager.instance().getModule("KillAura");
        return module instanceof KillAuraModule && module.isEnabled();
    }
}
