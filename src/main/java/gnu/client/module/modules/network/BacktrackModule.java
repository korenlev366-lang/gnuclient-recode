package gnu.client.module.modules.network;

import gnu.client.mixin.RealPosAccess;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.combat.RavenAntiBot;
import gnu.client.module.modules.network.KnockbackDelayModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.runtime.packet.PacketUtil;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Augustus b2.6 BackTrack — faithful port.
 *
 * <p>Holds clientbound packets in a raw list and cancels them until the geometry says
 * "now is a good moment": when the player's eyes are closer to the target's <i>server</i>
 * position ({@code realPos}) than its <i>rendered</i> position, within hit range, and
 * before the configured {@code Time} delay has elapsed. The entity is rendered at its
 * server position via {@code realPosX/Y/Z} (RealPosAccess), which are updated from
 * every {@code S14}/{@code S18} packet.
 */
public final class BacktrackModule extends Module implements PacketListener {

    private final SliderSetting hitRange = addSetting(new SliderSetting("MaxHitRange", 6.0f, 3.0f, 6.0f));
    private final SliderSetting timeDelay = addSetting(new SliderSetting("Time", 4000.0f, 0.0f, 30000.0f));
    private final BoolSetting esp = addSetting(new BoolSetting("Esp", true));
    private final BoolSetting onlyWhenNeed = addSetting(new BoolSetting("OnlyWhenNeed", true));
    private final BoolSetting player = addSetting(new BoolSetting("Player", true));
    private final BoolSetting mob = addSetting(new BoolSetting("Mob", true));
    private final BoolSetting animal = addSetting(new BoolSetting("Animal", true));
    private final BoolSetting villager = addSetting(new BoolSetting("Villager", true));
    private final BoolSetting armorStand = addSetting(new BoolSetting("ArmorStand", true));
    private final BoolSetting onlyKillAura = addSetting(new BoolSetting("OnlyKillAura", true));
    private final SliderSetting preAimRange = addSetting(new SliderSetting("PreAimRange", 4.0f, 0.0f, 15.0f));
    private final BoolSetting packetVelocity = addSetting(new BoolSetting("Velocity", true));
    private final BoolSetting packetVelocityExplosion = addSetting(new BoolSetting("ExplosionVelocity", true));
    private final BoolSetting packetTimeUpdate = addSetting(new BoolSetting("TimeUpdate", true));
    private final BoolSetting packetKeepAlive = addSetting(new BoolSetting("KeepAlive", true));

    private final List<Packet<?>> packets = new ArrayList<>();
    private EntityLivingBase entity;
    private boolean blockPackets;
    private long blockStartMs;

    /** True while inbound packets are being held (used by PingFix to keep ping in sync). */
    public boolean isLagging() {
        return isEnabled() && blockPackets && !packets.isEmpty();
    }

    public BacktrackModule() {
        super("Back Track", "Hit players at their past position", Category.COMBAT);
    }

    private static RealPosAccess realPos(EntityLivingBase entity) {
        return (RealPosAccess) (Object) entity;
    }

    @Override
    public void onEnable() {
        blockPackets = false;
        PacketEvents.register(this);
        WorldClient world = Mc.world();
        EntityPlayerSP player = Mc.player();
        if (world != null && player != null) {
            for (Entity e : world.loadedEntityList) {
                if (e instanceof EntityLivingBase) {
                    EntityLivingBase elb = (EntityLivingBase) e;
                    RealPosAccess rp = realPos(elb);
                    rp.setRealPosX(elb.serverPosX);
                    rp.setRealPosY(elb.serverPosY);
                    rp.setRealPosZ(elb.serverPosZ);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        resetPackets();
        packets.clear();
        entity = null;
        blockPackets = false;
    }

    @Override
    public void onTick() {
        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null) {
            resetPackets();
            entity = null;
            blockPackets = false;
            return;
        }

        if (KillAuraModule.getCurrentTarget() instanceof EntityLivingBase) {
            entity = (EntityLivingBase) KillAuraModule.getCurrentTarget();
        } else {
            Object[] list = world.loadedEntityList.stream()
                    .filter(this::canAttacked)
                    .sorted(Comparator.comparingDouble(player::getDistanceToEntity))
                    .toArray();
            entity = list.length > 0 ? (EntityLivingBase) list[0] : null;
            if (onlyKillAura.getValue())
                entity = null;
        }

        if (entity == null || player == null || world == null) {
            blockPackets = false;
            resetPackets();
            return;
        }

        RealPosAccess erp = realPos(entity);
        double d0 = erp.getRealPosX() / 32.0;
        double d2 = erp.getRealPosY() / 32.0;
        double d3 = erp.getRealPosZ() / 32.0;
        double d4 = entity.serverPosX / 32.0;
        double d5 = entity.serverPosY / 32.0;
        double d6 = entity.serverPosZ / 32.0;
        float f = entity.width / 2.0f;

        net.minecraft.util.AxisAlignedBB entityServerPos = new net.minecraft.util.AxisAlignedBB(
                d4 - f, d5, d6 - f, d4 + f, d5 + entity.height, d6 + f);
        Vec3 positionEyes = player.getPositionEyes(1.0f);
        double currentX = MathHelper.clamp_double(positionEyes.xCoord, entityServerPos.minX, entityServerPos.maxX);
        double currentY = MathHelper.clamp_double(positionEyes.yCoord, entityServerPos.minY, entityServerPos.maxY);
        double currentZ = MathHelper.clamp_double(positionEyes.zCoord, entityServerPos.minZ, entityServerPos.maxZ);

        net.minecraft.util.AxisAlignedBB entityPosMe = new net.minecraft.util.AxisAlignedBB(
                d0 - f, d2, d3 - f, d0 + f, d2 + entity.height, d3 + f);
        double realX = MathHelper.clamp_double(positionEyes.xCoord, entityPosMe.minX, entityPosMe.maxX);
        double realY = MathHelper.clamp_double(positionEyes.yCoord, entityPosMe.minY, entityPosMe.maxY);
        double realZ = MathHelper.clamp_double(positionEyes.zCoord, entityPosMe.minZ, entityPosMe.maxZ);

        double distance = hitRange.getValue();
        if (!player.canEntityBeSeen(entity))
            distance = distance > 3.0 ? 3.0 : distance;

        // "b" = the player's eyes are closer to the entity's server (past) position than
        // to its currently-rendered position — i.e. holding packets keeps the entity at a
        // spot you can still hit. Taking knockback (hurtTime 3..8) or OnlyWhenNeed off
        // force a release instead.
        boolean b = positionEyes.distanceTo(new Vec3(currentX, currentY, currentZ))
                > positionEyes.distanceTo(new Vec3(realX, realY, realZ));
        if (player.hurtTime < 8 && player.hurtTime > 3)
            b = false;
        if (!onlyWhenNeed.getValue())
            b = true;

        if (b
                && player.getDistanceToEntity(entity) < distance
                && System.currentTimeMillis() - blockStartMs >= timeDelay.getValue().longValue()) {
            blockPackets = true;
        } else {
            blockPackets = false;
            resetPackets();
            blockStartMs = System.currentTimeMillis();
        }
    }

    @Override
    public boolean onSend(Object packet) {
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!(packet instanceof Packet))
            return false;
        if (Mc.currentScreen() != null)
            return false;

        // Sync with KnockbackDelay: only one module may own the inbound stream at a time.
        // When KBD is holding packets, yield so the two don't fight over the same packets.
        if (KnockbackDelayModule.isOwningInboundQueue() || KnockbackDelayModule.isBlockingBacktrack()) {
            if (!packets.isEmpty())
                resetPackets();
            blockPackets = false;
            return false;
        }

        if (packet instanceof S08PacketPlayerPosLook) {
            resetPackets();
            return false;
        }

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            WorldClient world = Mc.world();
            Entity e = world != null ? p.getEntity(world) : null;
            if (e instanceof EntityLivingBase) {
                EntityLivingBase elb = (EntityLivingBase) e;
                RealPosAccess rp = realPos(elb);
                rp.setRealPosX(rp.getRealPosX() + p.func_149062_c());
                rp.setRealPosY(rp.getRealPosY() + p.func_149061_d());
                rp.setRealPosZ(rp.getRealPosZ() + p.func_149064_e());
            }
        }

        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
            Entity e = Mc.world() != null ? Mc.world().getEntityByID(p.getEntityId()) : null;
            if (e instanceof EntityLivingBase) {
                EntityLivingBase elb = (EntityLivingBase) e;
                RealPosAccess rp = realPos(elb);
                rp.setRealPosX(p.getX());
                rp.setRealPosY(p.getY());
                rp.setRealPosZ(p.getZ());
            }
        }

        if (entity == null) {
            resetPackets();
            return false;
        }

        if (delayPackets(packet)) {
            packets.add((Packet<?>) packet);
            return true;
        }
        return false;
    }

    @Override
    public void onRender(float partialTicks) {
        if (!esp.getValue() || entity == null || !blockPackets || !Mc.isInGame())
            return;

        RealPosAccess rrp = realPos(entity);
        double rx = rrp.getRealPosX() / 32.0;
        double ry = rrp.getRealPosY() / 32.0;
        double rz = rrp.getRealPosZ() / 32.0;
        float f = entity.width / 2.0f;

        double[] vp = Mc.getViewerPos(partialTicks);
        float r = 0.0f;
        float g = 1.0f;
        float bl = 0.0f;
        float lineWidth = 3.0f;
        float alpha = 0.15f;

        EntityPlayerSP player = Mc.player();
        if (player != null && player.getDistanceToEntity(entity) > 1.0f) {
            double d = 1.0f - player.getDistanceToEntity(entity) / 20.0f;
            if (d < 0.3)
                d = 0.3;
            lineWidth *= (float) d;
        }

        RenderHelper.begin();
        EspDraw.fill(
                rx - f - vp[0], ry - vp[1], rz - f - vp[2],
                rx + f - vp[0], ry + entity.height - vp[1], rz + f - vp[2],
                r, g, bl, alpha);
        RenderHelper.end();
    }

    private boolean canAttacked(Entity entity) {
        if (!(entity instanceof EntityLivingBase))
            return false;
        EntityLivingBase elb = (EntityLivingBase) entity;
        if (entity.isInvisible())
            return false;
        if (elb.deathTime > 1)
            return false;
        if (entity instanceof EntityArmorStand && !armorStand.getValue())
            return false;
        if (entity instanceof EntityAnimal && !animal.getValue())
            return false;
        if (entity instanceof EntityMob && !mob.getValue())
            return false;
        if (entity instanceof EntityPlayer && !player.getValue())
            return false;
        if (entity instanceof EntityVillager && !villager.getValue())
            return false;
        if (entity.ticksExisted < 50)
            return false;
        if (entity instanceof EntityPlayer && RavenAntiBot.isBot((EntityPlayer) entity))
            return false;
        if (entity.isDead)
            return false;
        EntityPlayerSP p = Mc.player();
        return p != null && !(entity == p) && p.getDistanceToEntity(entity) < preAimRange.getValue();
    }

    private boolean delayPackets(Object packet) {
        if (packet instanceof S03PacketTimeUpdate)
            return packetTimeUpdate.getValue();
        if (packet instanceof S00PacketKeepAlive)
            return packetKeepAlive.getValue();
        if (packet instanceof S12PacketEntityVelocity)
            return packetVelocity.getValue();
        if (packet instanceof S27PacketExplosion)
            return packetVelocityExplosion.getValue();
        if (packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
            return status.getOpCode() != 2
                    || !(Mc.world() != null
                    && Mc.world().getEntityByID(PacketHelper.entityId(packet)) instanceof EntityLivingBase);
        }
        return !(packet instanceof S06PacketUpdateHealth)
                && !(packet instanceof S29PacketSoundEffect)
                && !(packet instanceof S3EPacketTeams)
                && !(packet instanceof S0CPacketSpawnPlayer);
    }

    private void resetPackets() {
        if (packets.isEmpty())
            return;
        Object netHandler = Mc.netHandler();
        while (!packets.isEmpty()) {
            Packet<?> packet = packets.remove(0);
            if (packet == null)
                continue;
            if (netHandler != null) {
                try {
                    PacketUtil.processInbound(packet);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
