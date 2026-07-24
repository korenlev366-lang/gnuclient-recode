package gnu.client.module.modules.combat.velocity;

import gnu.client.mixin.impl.accessors.IAccessorS12PacketEntityVelocity;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketHelper;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

/**
 * wsamiaw / Tenacity {@code ATTACK_REDUCE}: scale horizontal S12 to 5%, keep vertical.
 */
public final class AttackReduceVelocity extends VelocityMode {

    private static final double AR_FRACTION = 0.05;

    private int arTicks;

    public AttackReduceVelocity(VelocityModule parent) {
        super("AttackReduce", parent);
    }

    @Override
    public void onEnable() {
        arTicks = 0;
    }

    @Override
    public void onDisable() {
        arTicks = 0;
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (parent.onSwing.getValue() && !player.isSwingInProgress)
            return false;
        if (!PacketHelper.isEntityVelocity(packet))
            return false;
        if (!(packet instanceof S12PacketEntityVelocity))
            return false;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() != player.getEntityId())
            return false;

        boolean moving = player.moveForward != 0.0f || player.moveStrafing != 0.0f;
        if (parent.onlyWhileMoving.getValue() && !moving)
            return false;

        int roll = (int) (Math.random() * 101.0);
        if (roll > Math.round(parent.chance.getValue()))
            return false;

        int mx = vel.getMotionX();
        int mz = vel.getMotionZ();
        arTicks = calculateAttackReduceTicks(mx, mz);

        IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) vel;
        accessor.setMotionX((int) Math.round(mx * AR_FRACTION));
        accessor.setMotionZ((int) Math.round(mz * AR_FRACTION));
        // vertical untouched
        return false;
    }

    @Override
    public void onUpdate(boolean pre) {
        if (pre)
            return;
        if (arTicks > 0)
            arTicks--;
    }

    /** Tenacity / wsamiaw tick estimate from horizontal knockback magnitude. */
    static int calculateAttackReduceTicks(int motionX, int motionZ) {
        double dist = Math.hypot(motionX, motionZ);
        long ticks = Math.round(6.43153527E-4 * dist + 2.9419087136);
        return (int) Math.max(0, Math.min(70, ticks));
    }
}
