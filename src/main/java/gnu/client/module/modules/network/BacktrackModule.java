package gnu.client.module.modules.network;

import gnu.client.mixin.RealPosAccess;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
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

import java.util.ArrayList;
import java.util.List;

/**
 * gnuclient-recode BackTrack — attack-driven, players-only.
 *
 * <p>While inside the post-attack window ({@code HurtTime} ms), the whole inbound stream
 * (minus hard exempts) is delayed by {@code currentDelay} ms rolled from
 * {@code MinDelay}–{@code MaxDelay}. Packets are timestamped and released FIFO when aged —
 * not held until the window ends. Delaying the full stream keeps hitboxes/metadata in sync
 * (target-only position delay flags). {@code HurtTime} only controls how long after a hit we
 * keep applying that delay; 0ms Min/Max is passthrough. Optional {@code Smart Flush} dumps the
 * queue when true server pos is closer than the lagged entity (old gnu Advanced).
 */
public final class BacktrackModule extends Module implements PacketListener {

    private final SliderSetting hitRange = addSetting(new SliderSetting("MaxHitRange", 6.0f, 3.0f, 6.0f));
    /** Packet age before release (ms). Rolled once per attack window in [MinDelay, MaxDelay]. */
    private final SliderSetting minTime = addSetting(new SliderSetting("MinDelay", 1000.0f, 0.0f, 10000.0f, 10.0f));
    private final SliderSetting maxTime = addSetting(new SliderSetting("MaxDelay", 4000.0f, 0.0f, 10000.0f, 10.0f));
    /** How long after the initiating hit we keep applying Min/Max delay (not the delay itself). */
    private final SliderSetting hurtTime = addSetting(new SliderSetting("HurtTime", 250.0f, 0.0f, 1000.0f, 10.0f));
    private final BoolSetting esp = addSetting(new BoolSetting("Esp", true));
    /** Flush when true server pos is closer than the lagged entity (old gnu Advanced smart flush). */
    private final BoolSetting smartFlush = addSetting(new BoolSetting("Smart Flush", true));
    private final BoolSetting packetVelocity = addSetting(new BoolSetting("Velocity", true));
    private final BoolSetting packetVelocityExplosion = addSetting(new BoolSetting("ExplosionVelocity", true));
    private final BoolSetting packetTimeUpdate = addSetting(new BoolSetting("TimeUpdate", true));
    private final BoolSetting packetKeepAlive = addSetting(new BoolSetting("KeepAlive", true));

    private final List<QueuedPacket> packets = new ArrayList<>();
    /** The entity you last attacked — BackTrack only ever tracks this (players only),
     *  mirroring the original gnuclient BackTrack's attack-packet-driven target. */
    private EntityLivingBase entity;
    private int targetEntityId = -1;
    private boolean blockPackets;

    /** Per-window delay, randomized between MinDelay and MaxDelay when a window opens. */
    private long currentDelay;

    /**
     * Timestamp of the initiating hit for the current window. Set only when we are NOT already
     * queueing, so the accept-window stays bounded to {@code HurtTime} after the first hit.
     * Uses a BackTrack-private clock rather than shared attack notify — KillAura would otherwise
     * keep extending the window forever.
     */
    private long lastOwnAttackMs = 0L;

    private static final class QueuedPacket {
        final Packet<?> packet;
        final long queuedAtMs;

        QueuedPacket(Packet<?> packet, long queuedAtMs) {
            this.packet = packet;
            this.queuedAtMs = queuedAtMs;
        }
    }

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

    /** True while inbound packets are delayed (used by PingFix to keep ping in sync). */
    public boolean isLagging() {
        return isEnabled() && !packets.isEmpty();
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
        targetEntityId = -1;
        blockPackets = false;
        lastOwnAttackMs = 0L;
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


        // Target = the entity you last attacked (set from the outgoing C02 ATTACK packet in
        // onSend), resolved against the current world. BackTrack tracks players only and
        // never pulls KillAura's target — this matches the original gnuclient behavior and
        // avoids holding packets for anything you aren't actually hitting.
        if (entity == null || entity.isDead || entity.getEntityId() != targetEntityId
                || Mc.world().getEntityByID(targetEntityId) != entity) {
            entity = targetEntityId >= 0
                    ? (EntityLivingBase) Mc.world().getEntityByID(targetEntityId)
                    : null;
            if (entity != null && !(entity instanceof EntityPlayer))
                entity = null;
        }

        if (entity == null) {
            blockPackets = false;
            resetPackets();
            lastOwnAttackMs = 0L;
            return;
        }

        long now = System.currentTimeMillis();

        // HurtTime = how long after the initiating hit we keep delaying target packets.
        // MinDelay/MaxDelay = how long each queued packet waits before release (the actual
        // backtrack depth). Taking knockback pauses new queueing.
        boolean inRange = player.getDistanceToEntity(entity) < hitRange.getValue();
        boolean withinAttackWindow = now - lastOwnAttackMs <= (long) (float) hurtTime.getValue();
        boolean takingKnockback = player.hurtTime >= 3 && player.hurtTime <= 9;

        boolean shouldAccept = inRange && withinAttackWindow && !takingKnockback;

        float delayHi = Math.max(minTime.getValue(), maxTime.getValue());
        if (delayHi <= 0.0f) {
            currentDelay = 0L;
        } else if (shouldAccept && !blockPackets) {
            currentDelay = rollDelayMs();
        } else if (currentDelay > (long) delayHi) {
            currentDelay = (long) delayHi;
        }

        if (shouldAccept) {
            if (!blockPackets)
                InboundLagCoordinator.tryAcquire(InboundLagCoordinator.Owner.BACKTRACK);
            blockPackets = true;
        } else {
            blockPackets = false;
        }

        // 0ms delay: never keep a queue — flush leftovers and stay passthrough.
        if (currentDelay <= 0L) {
            resetPackets();
            if (!blockPackets)
                InboundLagCoordinator.release(InboundLagCoordinator.Owner.BACKTRACK);
            return;
        }

        releaseExpiredPackets();
        if (!blockPackets && packets.isEmpty())
            InboundLagCoordinator.release(InboundLagCoordinator.Owner.BACKTRACK);
    }

    @Override
    public boolean onSend(Object packet) {
        if (!(packet instanceof C02PacketUseEntity))
            return false;
        C02PacketUseEntity use = (C02PacketUseEntity) packet;
        if (use.getAction() != C02PacketUseEntity.Action.ATTACK)
            return false;
        // Stamp initiating-hit clock so the accept window opens. Only stamp when not already
        // accepting, so KillAura/repeated clicks can't extend the window forever.
        Entity attacked = use.getEntityFromWorld(Mc.world());
        if (attacked instanceof EntityPlayer) {
            entity = (EntityLivingBase) attacked;
            targetEntityId = attacked.getEntityId();
            if (!blockPackets)
                lastOwnAttackMs = System.currentTimeMillis();
        }
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

        // Drain aged packets every receive so delay tracks wall-clock, not just ticks.
        if (currentDelay > 0L)
            releaseExpiredPackets();

        // Old gnu Advanced smart flush: if true server hitbox is closer than the lagged
        // rendered entity, dump the queue and let this packet through live.
        if (blockPackets && currentDelay > 0L && smartFlush.getValue()
                && shouldQueue(packet) && shouldSmartFlush(Mc.player())) {
            resetPackets();
            return false;
        }

        if (blockPackets && currentDelay > 0L && shouldQueue(packet)) {
            packets.add(new QueuedPacket((Packet<?>) packet, System.currentTimeMillis()));
            return true;
        }
        return false;
    }

    @Override
    public void onRender(float partialTicks) {
        if (!esp.getValue() || entity == null || packets.isEmpty() || !espServerValid || !Mc.isInGame())
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
        float alpha = 0.15f;

        RenderHelper.begin();
        EspDraw.fillWithGlow(
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

    /**
     * Old gnu Advanced smart flush: true server AABB (realPos) closer to us than the
     * lagged rendered entity → flush so we don't keep a worse hitbox.
     */
    private boolean shouldSmartFlush(EntityPlayerSP player) {
        if (player == null || entity == null)
            return false;
        RealPosAccess rp = realPos(entity);
        double sx = rp.getRealPosX() / 32.0;
        double sy = rp.getRealPosY() / 32.0;
        double sz = rp.getRealPosZ() / 32.0;
        if (sx == 0.0 && sy == 0.0 && sz == 0.0 && !espServerValid)
            return false;
        if (espServerValid) {
            sx = espServerX;
            sy = espServerY;
            sz = espServerZ;
        }
        double ghostDist = squaredBoxDistance(player, sx, sy, sz, entity.width, entity.height);
        double visibleDist = squaredBoxDistance(player, entity.posX, entity.posY, entity.posZ,
                entity.width, entity.height);
        return ghostDist + 0.01 < visibleDist;
    }

    private static double squaredBoxDistance(EntityPlayerSP player,
                                             double ex, double ey, double ez,
                                             float width, float height) {
        double px = player.posX;
        double py = player.posY;
        double pz = player.posZ;
        double halfW = width * 0.5f;
        double cx = clamp(px, ex - halfW, ex + halfW);
        double cy = clamp(py, ey, ey + height);
        double cz = clamp(pz, ez - halfW, ez + halfW);
        double dx = cx - px;
        double dy = cy - py;
        double dz = cz - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /**
     * Whole-inbound delay (keeps hitboxes/metadata aligned with lagged positions).
     * Hard exempts only: transactions, setback, disconnect, chat, health, hurt-status.
     * Optional toggles can leave time/keepalive/explosion/velocity live.
     */
    private boolean shouldQueue(Object packet) {
        if (PacketHelper.isServerConfirmTransaction(packet)
                || PacketHelper.isClientConfirmTransaction(packet))
            return false;
        if (PacketHelper.isPlayerPosLook(packet) || PacketHelper.isDisconnect(packet))
            return false;
        if (PacketHelper.isChat(packet) || PacketHelper.isUpdateHealth(packet))
            return false;
        // Hurt status must stay live or KillAura swing/Post flags break.
        if (packet instanceof S19PacketEntityStatus
                && ((S19PacketEntityStatus) packet).getOpCode() == 2)
            return false;

        if (packet instanceof S03PacketTimeUpdate)
            return packetTimeUpdate.getValue();
        if (packet instanceof S00PacketKeepAlive)
            return packetKeepAlive.getValue();
        if (packet instanceof S27PacketExplosion)
            return packetVelocityExplosion.getValue();
        if (packet instanceof S12PacketEntityVelocity)
            return packetVelocity.getValue();

        // Everything else in the inbound stream (all entities' move/meta/spawn/etc.).
        return !(packet instanceof S06PacketUpdateHealth)
                && !(packet instanceof S29PacketSoundEffect)
                && !(packet instanceof S3EPacketTeams)
                && !(packet instanceof S0CPacketSpawnPlayer);
    }

    /** Live MinDelay–MaxDelay roll (0 when either end is 0 and both ≤ 0). */
    private long rollDelayMs() {
        float lo = Math.min(minTime.getValue(), maxTime.getValue());
        float hi = Math.max(minTime.getValue(), maxTime.getValue());
        if (hi <= 0.0f)
            return 0L;
        if (lo >= hi)
            return (long) hi;
        return (long) (lo + Math.random() * (hi - lo));
    }

    /** Flush every queued packet immediately (disable / setback / target lost / 0ms). */
    private void resetPackets() {
        if (packets.isEmpty())
            return;
        Object netHandler = Mc.netHandler();
        while (!packets.isEmpty()) {
            QueuedPacket queued = packets.remove(0);
            if (queued == null || queued.packet == null)
                continue;
            if (netHandler != null) {
                try {
                    PacketUtil.processInbound(queued.packet);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Release packets older than {@link #currentDelay} (FIFO). */
    private void releaseExpiredPackets() {
        if (packets.isEmpty())
            return;
        long now = System.currentTimeMillis();
        long delayMs = Math.max(0L, currentDelay);
        Object netHandler = Mc.netHandler();
        while (!packets.isEmpty()) {
            QueuedPacket queued = packets.get(0);
            if (now - queued.queuedAtMs < delayMs)
                break;
            packets.remove(0);
            if (queued.packet == null)
                continue;
            if (netHandler != null) {
                try {
                    PacketUtil.processInbound(queued.packet);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public String[] getSuffix() {
        // Show the rolled packet delay for the current window (e.g. 353ms).
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
