package gnu.client.module.modules.player.scaffold.feature;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.network.LagrangeModule;
import gnu.client.runtime.packet.OutboundLagQueue;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketUtil;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

/**
 * LiquidBounce {@code ScaffoldBlinkFeature} — pulse-queue outbound packets between places.
 * Scaffold-owned queue; pauses Lagrange while active. Never splits C08 from following C03
 * (place path flushes before interact).
 */
public final class ScaffoldBlinkFeature {
  public static final int FLUSH_PLACE = 1;
  public static final int FLUSH_TOWERING = 2;
  public static final int FLUSH_SNEAKING = 4;
  public static final int FLUSH_NOT_SNEAKING = 8;
  public static final int FLUSH_ON_GROUND = 16;
  public static final int FLUSH_IN_AIR = 32;

  private final OutboundLagQueue queue = new OutboundLagQueue();
  private long pulseMs = 100L;
  private long pulseStartMs;
  private boolean lagrangePaused;
  private int timeMinMs = 50;
  private int timeMaxMs = 250;
  private int flushMask = FLUSH_PLACE | FLUSH_TOWERING;

  public void configure(int timeMinMs, int timeMaxMs, int flushMask) {
    this.timeMinMs = Math.max(0, timeMinMs);
    this.timeMaxMs = Math.max(this.timeMinMs, timeMaxMs);
    this.flushMask = flushMask;
  }

  public void reset() {
    drain();
    pulseStartMs = 0L;
    lagrangePaused = false;
  }

  public void onEnable() {
    reset();
    pauseLagrange();
    queue.activate();
    rollPulse();
    pulseStartMs = System.currentTimeMillis();
  }

  public void onDisable() {
    queue.deactivate();
    drain();
    resumeLagrange();
  }

  public void onBlockPlacement() {
    rollPulse();
    pulseStartMs = System.currentTimeMillis();
  }

  /**
   * @return true if packet should be cancelled (queued).
   */
  public boolean onSend(Object packet, boolean towering, boolean sneaking, boolean onGround) {
    if (packet == null || PacketUtil.isDispatching())
      return false;
    // Never hold block interact — flush then pass (Grim C08/C03 pairing).
    if (PacketHelper.isBlockInteract(packet) || packet instanceof C08PacketPlayerBlockPlacement
        || packet instanceof C07PacketPlayerDigging) {
      drain();
      pulseStartMs = System.currentTimeMillis();
      return false;
    }
    if (PacketHelper.isBlinkOutboundExempt(packet))
      return false;
    if (shouldFlush(packet, towering, sneaking, onGround) || pulseElapsed()) {
      drain();
      pulseStartMs = System.currentTimeMillis();
      return false;
    }
    if (!queue.isActive())
      queue.activate();
    queue.offer(packet);
    return true;
  }

  public void tickFlushGates(boolean towering, boolean sneaking, boolean onGround) {
    if (shouldFlush(null, towering, sneaking, onGround) || pulseElapsed()) {
      drain();
      pulseStartMs = System.currentTimeMillis();
    }
  }

  private boolean shouldFlush(Object packet, boolean towering, boolean sneaking, boolean onGround) {
    if ((flushMask & FLUSH_PLACE) != 0 && packet instanceof C08PacketPlayerBlockPlacement)
      return true;
    if ((flushMask & FLUSH_TOWERING) != 0 && towering)
      return true;
    if ((flushMask & FLUSH_SNEAKING) != 0 && sneaking)
      return true;
    if ((flushMask & FLUSH_NOT_SNEAKING) != 0 && !sneaking)
      return true;
    if ((flushMask & FLUSH_ON_GROUND) != 0 && onGround)
      return true;
    if ((flushMask & FLUSH_IN_AIR) != 0 && !onGround)
      return true;
    return false;
  }

  private boolean pulseElapsed() {
    return System.currentTimeMillis() - pulseStartMs >= pulseMs;
  }

  private void rollPulse() {
    int span = Math.max(0, timeMaxMs - timeMinMs);
    pulseMs = timeMinMs + (span == 0 ? 0 : (int) (Math.random() * (span + 1)));
  }

  private void drain() {
    queue.deactivate();
    queue.drainAll(pkt -> {
      if (pkt == null)
        return;
      if (PacketHelper.isAttackUseEntity(pkt))
        PacketUtil.sendSwingAnimation();
      PacketUtil.sendPacketReleased(pkt);
    });
    queue.activate();
  }

  private void pauseLagrange() {
    Module lag = ModuleManager.INSTANCE.getModule("Lagrange");
    if (lag instanceof LagrangeModule && lag.isEnabled()) {
      ((LagrangeModule) lag).pauseForBlink();
      lagrangePaused = true;
    }
  }

  private void resumeLagrange() {
    lagrangePaused = false;
  }
}
