package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;

public final class GrimVelocity extends VelocityMode {

    private boolean realVelocity;
    private boolean velocity;

    public GrimVelocity(VelocityModule parent) {
        super("Grim", parent);
    }

    @Override
    public void onUpdate(boolean pre) {
        if (!pre || !velocity)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        C07PacketPlayerDigging.Action action = C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK;
        if (mc.objectMouseOver != null && player.isSwingInProgress
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            action = C07PacketPlayerDigging.Action.START_DESTROY_BLOCK;
        }
        Mc.addToSendQueue(new C07PacketPlayerDigging(action, new BlockPos(player), EnumFacing.UP));
        velocity = false;
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null || mc.theWorld == null)
            return false;

        if (packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
            if (status.getEntity(mc.theWorld) == player && status.getOpCode() == 2) {
                realVelocity = true;
            }
        }

        if (packet instanceof S12PacketEntityVelocity && realVelocity) {
            S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
            if (vel.getEntityID() == player.getEntityId()) {
                realVelocity = false;
                velocity = true;
                return true;
            }
        }
        return false;
    }
}
