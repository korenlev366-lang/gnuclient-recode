package gnu.client.module.modules.network;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.InboundLagCoordinator;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.runtime.packet.OutboundLagQueue;
import gnu.client.runtime.packet.PacketUtil;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

/**
 * raven-bS {@code KnockbackDelay} — uniform inbound freeze ({@code UnifiedLagHandler} semantics).
 *
 * <p>While a session is active, <em>all</em> inbound packets are queued (including self
 * {@code S12} and target {@code S14/S18}). Aged packets batch-release each tick via
 * {@link OutboundLagQueue#releaseExpired}. Session ends when raven conditions fail or on
 * {@code S08}. No OpenMyau-style selective pass-through.
 */
public final class KnockbackDelayModule extends Module implements PacketListener {

    private final SliderSetting distanceToTarget = addSetting(new SliderSetting("Distance", 6.0f, 3.0f, 12.0f));
    private final SliderSetting chance = addSetting(new SliderSetting("Chance", 100.0f, 0.0f, 100.0f));
    private final SliderSetting maximumDelay = addSetting(new SliderSetting("Maximum delay", 200.0f, 50.0f, 1000.0f));
    private final BoolSetting inAir = addSetting(new BoolSetting("In air", true));
    private final BoolSetting lookingAtPlayer = addSetting(new BoolSetting("Looking at player", false));
    private final BoolSetting requireLeftMouse = addSetting(new BoolSetting("Require LMB", false));
    private final BoolSetting flushLagrangeOnKb = addSetting(new BoolSetting("Flush Lagrange on KB", false));

    private final OutboundLagQueue inbound = new OutboundLagQueue();
    private boolean sessionActive;

    public KnockbackDelayModule() {
        super("KnockbackDelay", "Delay knockback packets", Category.COMBAT);
    }

    public static boolean isOwningInboundQueue() {
        return InboundLagCoordinator.knockbackDelayOwns();
    }

    public static boolean isBlockingBacktrack() {
        KnockbackDelayModule kd = activeInstance();
        return kd != null && kd.sessionActive;
    }

    private static KnockbackDelayModule activeInstance() {
        Module mod = ModuleManager.INSTANCE.getModule("KnockbackDelay");
        if (mod instanceof KnockbackDelayModule && mod.isEnabled())
            return (KnockbackDelayModule) mod;
        return null;
    }

    @Override
    public void onEnable() {
        inbound.clear();
        inbound.deactivate();
        sessionActive = false;
        InboundLagCoordinator.forceReleaseAll();
        flushLagrangeIfEnabled();
        PacketEvents.register(this);
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        flushInboundAndClear();
    }

    @Override
    public void onTick() {
        if (!isEnabled())
            return;

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null || player.isDead) {
            flushInboundAndClear();
            return;
        }

        if (!sessionActive)
            return;

        if (conditionsFailureReason(player, world) != null) {
            flushInboundAndClear();
            return;
        }

        inbound.releaseExpired(activeDelayMs(), PacketUtil::processInbound);
    }

    @Override
    public boolean onSend(Object packet) {
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!isEnabled() || PacketUtil.isDispatching())
            return false;

        if (PacketHelper.isPlayerPosLook(packet)) {
            flushInboundAndClear();
            return false;
        }

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();

        if (PacketHelper.isEntityVelocity(packet) && player != null
                && PacketHelper.velocityEntityId(packet) == Mc.entityId(player)) {
            flushLagrangeIfEnabled();
            if (!sessionActive) {
                if (conditionsFailureReason(player, world) != null)
                    return false;
                if (chance.getValue() < 100.0f && Math.random() * 100.0 >= chance.getValue())
                    return false;
                BacktrackModule.abortActiveLag();
                startSession();
            }
        }

        if (!sessionActive)
            return false;

        inbound.offer(packet);
        return true;
    }

    private void startSession() {
        inbound.activate();
        sessionActive = true;
        InboundLagCoordinator.tryAcquire(InboundLagCoordinator.Owner.KNOCKBACK_DELAY);
    }

    private String conditionsFailureReason(EntityPlayerSP player, WorldClient world) {
        if (player == null || world == null)
            return "null";

        double maxSq = distanceToTarget.getValue() * distanceToTarget.getValue();
        if (findTargetInRange(player, world, maxSq) == null)
            return "no target";

        if (inAir.getValue() && player.onGround)
            return "on ground";

        if (lookingAtPlayer.getValue() && getMouseOverTarget(player, world, maxSq) == null)
            return "not looking at player";

        if (requireLeftMouse.getValue() && !ClientBootstrap.isLeftMouseDown())
            return "lmb";

        return null;
    }

    private long activeDelayMs() {
        return maximumDelay.getValue().longValue();
    }

    private void flushInboundAndClear() {
        inbound.deactivate();
        sessionActive = false;
        InboundLagCoordinator.release(InboundLagCoordinator.Owner.KNOCKBACK_DELAY);
        inbound.drainAll(PacketUtil::processInbound);
    }

    private void flushLagrangeIfEnabled() {
        if (!flushLagrangeOnKb.getValue())
            return;
        Module lag = ModuleManager.INSTANCE.getModule("Lagrange");
        if (lag instanceof LagrangeModule && lag.isEnabled())
            ((LagrangeModule) lag).flushQueueNow();
    }

    private EntityPlayer getMouseOverTarget(EntityPlayerSP player, WorldClient world, double maxRangeSq) {
        MovingObjectPosition mop = Mc.objectMouseOver();
        if (mop == null)
            return null;
        Entity hit = mop.entityHit;
        if (hit == null || hit == player || !(hit instanceof EntityPlayer))
            return null;
        if (hit instanceof EntityLivingBase && ((EntityLivingBase) hit).hurtTime > 0)
            return null;
        return eyeToAabbDistSq(player, hit) <= maxRangeSq ? (EntityPlayer) hit : null;
    }

    private EntityPlayer findTargetInRange(EntityPlayerSP player, WorldClient world, double maxRangeSq) {
        EntityPlayer mouseTarget = getMouseOverTarget(player, world, maxRangeSq);
        if (mouseTarget != null)
            return mouseTarget;

        EntityPlayer best = null;
        double bestSq = Double.MAX_VALUE;
        for (EntityPlayer entity : world.playerEntities) {
            if (entity == null || entity == player)
                continue;
            if (entity.hurtTime > 0)
                continue;
            double distSq = eyeToAabbDistSq(player, entity);
            if (distSq > maxRangeSq || distSq >= bestSq)
                continue;
            best = entity;
            bestSq = distSq;
        }
        return best;
    }

    private static double eyeToAabbDistSq(Entity player, Entity entity) {
        double px = player.posX;
        double py = player.posY + player.height * 0.85;
        double pz = player.posZ;
        double ex = entity.posX;
        double ey = entity.posY;
        double ez = entity.posZ;
        double halfW = entity.width * 0.5f;
        double h = entity.height;
        double cx = clamp(px, ex - halfW, ex + halfW);
        double cy = clamp(py, ey, ey + h);
        double cz = clamp(pz, ez - halfW, ez + halfW);
        double dx = cx - px;
        double dy = cy - py;
        double dz = cz - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
