package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.utility.PacketUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.AxisAlignedBB;

import java.util.Comparator;

public final class GrimReduceVelocity extends VelocityMode {

    public GrimReduceVelocity(VelocityModule parent) {
        super("GrimReduce", parent);
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (!(packet instanceof S12PacketEntityVelocity))
            return false;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() != player.getEntityId())
            return false;

        Entity target = getClosestEntity();
        if (target != null) {
            PacketUtils.sendPacketNoEvent(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
        }
        return false;
    }

    @Override
    public void onUpdate(boolean pre) {
        if (!pre)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null || player.ticksExisted <= 20)
            return;

        if (player.hurtTime > 0) {
            if (isNearBlock()) {
                player.motionX *= 0.02;
                player.motionZ *= 0.02;
            }

            if (player.hurtTime == 9 && player.getHeldItem() != null
                    && player.getHeldItem().getItem() instanceof ItemSword) {
                PacketUtils.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(player.getHeldItem()));
            }
        }
    }

    private Entity getClosestEntity() {
        EntityPlayerSP player = Mc.player();
        if (player == null || mc.theWorld == null)
            return null;

        return mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityLivingBase && e != player && player.getDistanceToEntity(e) <= 6.0f)
                .min(Comparator.comparingDouble(player::getDistanceToEntity))
                .orElse(null);
    }

    private boolean isNearBlock() {
        EntityPlayerSP player = Mc.player();
        if (player == null || mc.theWorld == null)
            return false;
        AxisAlignedBB bb = player.getEntityBoundingBox().expand(0.5, 0.0, 0.5);
        return !mc.theWorld.getCollidingBoundingBoxes(player, bb).isEmpty();
    }
}
