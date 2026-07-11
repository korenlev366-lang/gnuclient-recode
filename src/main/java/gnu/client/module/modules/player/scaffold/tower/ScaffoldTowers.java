package gnu.client.module.modules.player.scaffold.tower;

import gnu.client.module.modules.player.scaffold.ScaffoldMath;
import gnu.client.runtime.packet.PacketHelper;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * LiquidBounce tower modes ported to 1.8.9 motion / timer / C03 nudges.
 */
public final class ScaffoldTowers {
  public static final int NONE = 0;
  public static final int MOTION = 1;
  public static final int PULLDOWN = 2;
  public static final int KARHU = 3;
  public static final int VULCAN = 4;
  public static final int HYPIXEL = 5;

  private double jumpOffY = Double.NaN;
  private boolean wasOnGround = true;
  private int airTicks;
  private boolean pulldownArmed;
  private boolean karhuTimerActive;

  public void reset() {
    jumpOffY = Double.NaN;
    wasOnGround = true;
    airTicks = 0;
    pulldownArmed = false;
    karhuTimerActive = false;
    setTimer(1.0f);
  }

  public void tick(EntityPlayerSP player, World world, int mode, boolean jumpHeld, int blockCount,
                   float motion, float triggerHeight, float slow, float pulldownTrigger,
                   float karhuTimer, float karhuTrigger, boolean karhuPulldown) {
    if (player == null || world == null || mode == NONE || !jumpHeld || blockCount <= 0) {
      if (mode == NONE || !jumpHeld)
        reset();
      return;
    }
    if (!isBlockBelow(player)) {
      jumpOffY = Double.NaN;
      wasOnGround = player.onGround;
      return;
    }

    boolean onGround = player.onGround;
    if (wasOnGround && !onGround) {
      jumpOffY = player.posY;
      pulldownArmed = mode == PULLDOWN || (mode == KARHU && karhuPulldown);
      if (mode == KARHU) {
        setTimer(karhuTimer);
        karhuTimerActive = true;
      }
    }
    wasOnGround = onGround;
    if (!onGround)
      airTicks++;
    else {
      airTicks = 0;
      if (karhuTimerActive) {
        setTimer(1.0f);
        karhuTimerActive = false;
      }
    }

    switch (mode) {
      case MOTION:
        applyMotion(player, motion, triggerHeight, slow);
        break;
      case PULLDOWN:
        applyPulldown(player, pulldownTrigger);
        break;
      case KARHU:
        if (karhuPulldown)
          applyPulldown(player, karhuTrigger);
        break;
      case VULCAN:
        applyVulcan(player);
        break;
      case HYPIXEL:
        applyHypixel(player);
        break;
      default:
        break;
    }
  }

  /** Vulcan idle C03 XZ nudge — return true to cancel and resend modified packet. */
  public boolean handleVulcanPacket(Object packet, EntityPlayerSP player, int mode, boolean jumpHeld) {
    if (mode != VULCAN || player == null || !jumpHeld)
      return false;
    if (!PacketHelper.c03HasPosition(packet))
      return false;
    if (isMoving(player))
      return false;
    if (player.ticksExisted % 2 != 0)
      return false;
    // Caller mutates via PacketHelper if available; signal handled in module.
    return true;
  }

  public BlockPos hypixelTargetBias(EntityPlayerSP player, BlockPos under) {
    if (player == null || under == null || isMoving(player))
      return under;
    // LB ScaffoldTowerHypixel: nearest side-below if that cell is NOT solid (place into it).
    BlockPos[] sides = {
        under.add(0, 0, 1), under.add(0, 0, -1), under.add(1, 0, 0), under.add(-1, 0, 0)
    };
    BlockPos nearestSide = null;
    double bestDist = Double.MAX_VALUE;
    for (BlockPos p : sides) {
      double d = distSq(player, p);
      if (d < bestDist) {
        bestDist = d;
        nearestSide = p;
      }
    }
    if (nearestSide == null)
      return under;
    BlockPos sideBelow = nearestSide.down();
    if (ScaffoldMath.isReplaceable(sideBelow))
      return sideBelow;
    return under;
  }

  private void applyMotion(EntityPlayerSP player, float motion, float triggerHeight, float slow) {
    if (Double.isNaN(jumpOffY))
      return;
    if (player.posY > jumpOffY + triggerHeight) {
      player.setPosition(player.posX, Math.floor(player.posY), player.posZ);
      player.motionY = motion;
      player.motionX *= slow;
      player.motionZ *= slow;
      jumpOffY = player.posY;
    }
  }

  private void applyPulldown(EntityPlayerSP player, float trigger) {
    if (!pulldownArmed || player.onGround)
      return;
    if (player.motionY < trigger) {
      player.motionY = -1.0;
      pulldownArmed = false;
    }
  }

  private void applyVulcan(EntityPlayerSP player) {
    if (player.ticksExisted % 2 == 0)
      player.motionY = 0.7;
    else
      player.motionY = isMoving(player) ? 0.42 : 0.6;
  }

  private void applyHypixel(EntityPlayerSP player) {
    if (player.posX % 1.0 != 0.0 && !isMoving(player)) {
      double dx = Math.round(player.posX) - player.posX;
      if (dx > 0.281)
        dx = 0.281;
      if (dx < -0.281)
        dx = -0.281;
      player.motionX = dx;
    }
    if (airTicks > 14) {
      player.motionY -= 0.09;
      player.motionX *= 0.6;
      player.motionZ *= 0.6;
      return;
    }
    switch (airTicks % 3) {
      case 0:
        player.motionY = 0.42;
        float speed = 0.247f - (float) (Math.random() / 100.0);
        strafe(player, speed);
        break;
      case 2:
        player.motionY = 1.0 - (player.posY % 1.0);
        break;
      default:
        break;
    }
  }

  private static void strafe(EntityPlayerSP player, float speed) {
    float yaw = player.rotationYaw;
    float forward = player.moveForward;
    float strafe = player.moveStrafing;
    if (forward == 0 && strafe == 0) {
      player.motionX = 0;
      player.motionZ = 0;
      return;
    }
    if (forward != 0) {
      if (strafe > 0)
        yaw += forward > 0 ? -45 : 45;
      else if (strafe < 0)
        yaw += forward > 0 ? 45 : -45;
      strafe = 0;
      forward = forward > 0 ? 1 : -1;
    }
    double rad = Math.toRadians(yaw + 90.0f);
    player.motionX = forward * speed * Math.cos(rad) + strafe * speed * Math.sin(rad);
    player.motionZ = forward * speed * Math.sin(rad) - strafe * speed * Math.cos(rad);
  }

  public static boolean isBlockBelow(EntityPlayerSP player) {
    if (player == null)
      return false;
    int bx = MathHelper.floor_double(player.posX);
    int by = MathHelper.floor_double(player.posY) - 1;
    int bz = MathHelper.floor_double(player.posZ);
    return !ScaffoldMath.isReplaceable(new BlockPos(bx, by, bz));
  }

  private static boolean isMoving(EntityPlayerSP player) {
    return player.moveForward != 0.0f || player.moveStrafing != 0.0f;
  }

  private static double distSq(EntityPlayerSP player, BlockPos p) {
    double dx = player.posX - (p.getX() + 0.5);
    double dz = player.posZ - (p.getZ() + 0.5);
    return dx * dx + dz * dz;
  }

  private static void setTimer(float speed) {
    gnu.client.runtime.mc.Mc.setTimerSpeed(speed);
  }
}
