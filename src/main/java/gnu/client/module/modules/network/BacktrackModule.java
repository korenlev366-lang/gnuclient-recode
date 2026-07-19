package gnu.client.module.modules.network;

import gnu.client.mixin.RealPosAccess;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.combat.RavenAntiBot;
import gnu.client.module.modules.network.KnockbackDelayModule;
import gnu.client.runtime.CombatAttackNotify;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.runtime.packet.InboundLagCoordinator;
import gnu.client.runtime.packet.PacketUtil;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;
import gnu.client.ui.UiFont;

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
 * gnuclient-recode BackTrack — attack-gated activation, faithful to the original.
 *
 * <p>Packets are only held within the post-attack window: BackTrack delays inbound packets
 * for {@code HurtTime} ms after the most recent hit (tracked via {@link CombatAttackNotify}),
 * and pauses while taking knockback. Simply approaching a player does nothing — it is an
 * attack aid, not a passive hold. Each hold rolls a packet delay in [MinDelay, MaxDelay]
 * shown in the ArrayList. The target is rendered at its past position via {@code realPosX/Y/Z}
 * (RealPosAccess); the ESP draws the smooth true server position Lagrange-style.
 */
public final class BacktrackModule extends Module implements PacketListener {

    private final SliderSetting hitRange = addSetting(new SliderSetting("MaxHitRange", 6.0f, 3.0f, 6.0f));
    /** BackTrack packet-delay range (ms). Each hold rolls a value in [MinDelay, MaxDelay]
     *  — e.g. 300-400 rolls 353, so inbound packets are delayed by 353ms that hold. */
    private final SliderSetting minTime = addSetting(new SliderSetting("MinDelay", 1000.0f, 0.0f, 10000.0f, 10.0f));
    private final SliderSetting maxTime = addSetting(new SliderSetting("MaxDelay", 4000.0f, 0.0f, 10000.0f, 10.0f));
    /** How long (ms) BackTrack keeps delaying packets after you hit the enemy. The hold
     *  starts on attack and flushes HurtTime ms later (mirrors the old gnuclient BackTrack
     *  post-attack window). */
    private final SliderSetting hurtTime = addSetting(new SliderSetting("HurtTime", 250.0f, 0.0f, 1000.0f, 10.0f));
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

    /** Per-hold delay, randomized between MinTime and MaxTime at the start of each hold. */
    private long currentDelay;

    /**
     * Live true server position of the target (plain double world coords), tracked for the
     * ESP from the same S14/S18 packets we intercept. Unlike {@code realPos}, this is NOT
     * frozen during a hold — it shows where the target actually is server-side right now,
     * smoothly interpolated Lagrange-style so the ghost box doesn't snap.
     */
    private double espServerX, espServerY, espServerZ;
    private boolean espServerValid;
    private double espIndFromX, espIndFromY, espIndFromZ;
    private double espIndToX, espIndToY, espIndToZ;
    private boolean espIndValid;
    private long espIndStartMs;
    private static final long ESP_INTERP_MS = 80L;
    private static final double ESP_POS_EPS = 1.0e-6;

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
        currentDelay = (long) (float) maxTime.getValue();
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
        InboundLagCoordinator.release(InboundLagCoordinator.Owner.BACKTRACK);
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

        // Never hold while blocking/using an item — Grim scrutinizes movement/slowdown then.
        if (Mc.isBlocking() || Mc.isUsingItem()) {
            blockPackets = false;
            resetPackets();
            InboundLagCoordinator.release(InboundLagCoordinator.Owner.BACKTRACK);
            entity = null;
            return;
        }

        // Target selection: KillAura's target if it has one, else the nearest attacked-able
        // entity (unless OnlyKillAura restricts us to KA's target).
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

        if (entity == null) {
            blockPackets = false;
            resetPackets();
            return;
        }

        long now = System.currentTimeMillis();
        long lastAttack = CombatAttackNotify.getLastAttackMs();

        // Old gnuclient BackTrack activation: packets are only held within the post-attack
        // window (HurtTime ms after the most recent hit). Outside that window we never hold,
        // so merely approaching a player does nothing — BackTrack is an attack aid. Taking
        // knockback (hurtTime 3..9) pauses the hold so we stop delaying the moment we're hit.
        boolean inRange = player.getDistanceToEntity(entity) < hitRange.getValue();
        boolean withinAttackWindow = now - lastAttack <= (long) (float) hurtTime.getValue();
        boolean takingKnockback = player.hurtTime >= 3 && player.hurtTime <= 9;

        boolean shouldBlock = inRange && withinAttackWindow && !takingKnockback;

        if (shouldBlock) {
            if (!blockPackets) {
                // Fresh hold: roll the randomized packet-delay value for the HUD.
                float lo = Math.min(minTime.getValue(), maxTime.getValue());
                float hi = Math.max(minTime.getValue(), maxTime.getValue());
                currentDelay = (long) (lo + Math.random() * Math.max(0.0f, hi - lo));
                InboundLagCoordinator.tryAcquire(InboundLagCoordinator.Owner.BACKTRACK);
            }
            blockPackets = true;
        } else {
            if (blockPackets) {
                blockPackets = false;
                resetPackets();
                InboundLagCoordinator.release(InboundLagCoordinator.Owner.BACKTRACK);
            }
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

        // Sync via the shared coordinator: only the highest-priority owner may hold the
        // inbound stream. Yield to KnockbackDelay (highest) and Lagrange (middle) so the
        // three lag modules never queue packets simultaneously.
        if (InboundLagCoordinator.isBlockedFor(InboundLagCoordinator.Owner.BACKTRACK)) {
            if (!packets.isEmpty())
                resetPackets();
            blockPackets = false;
            InboundLagCoordinator.release(InboundLagCoordinator.Owner.BACKTRACK);
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
                if (elb == entity)
                    trackEspServer(
                            rp.getRealPosX() / 32.0,
                            rp.getRealPosY() / 32.0,
                            rp.getRealPosZ() / 32.0);
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
                if (elb == entity)
                    trackEspServer(
                            rp.getRealPosX() / 32.0,
                            rp.getRealPosY() / 32.0,
                            rp.getRealPosZ() / 32.0);
            }
        }

        if (entity == null) {
            resetPackets();
            return false;
        }

        if (blockPackets && delayPackets(packet)) {
            packets.add((Packet<?>) packet);
            return true;
        }
        return false;
    }

    @Override
    public void onRender(float partialTicks) {
        if (!esp.getValue() || entity == null || !blockPackets || !espServerValid || !Mc.isInGame())
            return;

        double[] server = espServerPos(partialTicks);
        double rx = server[0];
        double ry = server[1];
        double rz = server[2];
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

    /** Records the latest true server position of the target (world coords). */
    private void trackEspServer(double x, double y, double z) {
        if (!espServerValid || serverPosChanged(x, y, z, espServerX, espServerY, espServerZ)) {
            espIndFromX = espServerValid ? espServerX : x;
            espIndFromY = espServerValid ? espServerY : y;
            espIndFromZ = espServerValid ? espServerZ : z;
            espIndToX = x;
            espIndToY = y;
            espIndToZ = z;
            espIndValid = true;
            espIndStartMs = System.currentTimeMillis();
        }
        espServerX = x;
        espServerY = y;
        espServerZ = z;
        espServerValid = true;
    }

    /** Returns the interpolated true server position for the ESP (Lagrange-style lerp). */
    private double[] espServerPos(float partialTicks) {
        if (!espIndValid)
            return new double[] { espServerX, espServerY, espServerZ };
        long elapsed = System.currentTimeMillis() - espIndStartMs;
        float t = elapsed >= ESP_INTERP_MS ? 1.0f : (float) elapsed / (float) ESP_INTERP_MS;
        return new double[] {
                lerp(espIndFromX, espIndToX, t),
                lerp(espIndFromY, espIndToY, t),
                lerp(espIndFromZ, espIndToZ, t)
        };
    }

    private static boolean serverPosChanged(double ax, double ay, double az,
                                            double bx, double by, double bz) {
        return Math.abs(ax - bx) > ESP_POS_EPS
                || Math.abs(ay - by) > ESP_POS_EPS
                || Math.abs(az - bz) > ESP_POS_EPS;
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
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
        // Never hold transactions — delaying S32 confirmations desyncs transaction-based
        // anti-cheats and causes skipped/flagged packets (Post/Pre attack verification).
        if (PacketHelper.isServerConfirmTransaction(packet))
            return false;
        if (packet instanceof S03PacketTimeUpdate)
            return packetTimeUpdate.getValue();
        if (packet instanceof S00PacketKeepAlive)
            return packetKeepAlive.getValue();
        if (packet instanceof S12PacketEntityVelocity)
            return packetVelocity.getValue();
        if (packet instanceof S27PacketExplosion)
            return packetVelocityExplosion.getValue();
        // Never hold swing/status-update (opcode 2) — holding it suppresses KillAura's
        // swing animation and flags the attack as Post/Pre with autoblock.
        if (packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
            return status.getOpCode() != 2;
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

    @Override
    public String[] getSuffix() {
        // Show the rolled packet delay for the current hold (e.g. 353ms), so the delay
        // BackTrack is applying is visible in the ArrayList.
        return new String[]{currentDelay + "ms"};
    }

    /**
     * Pin the ArrayList row width so the entry does not re-sort/bob as the rolled delay
     * changes length between holds. Uses the widest possible MaxDelay value as reference.
     */
    @Override
    public int getFixedSuffixWidth() {
        String widest = ((long) (float) maxTime.getValue()) + "ms";
        return (int) UiFont.width(widest);
    }
}
