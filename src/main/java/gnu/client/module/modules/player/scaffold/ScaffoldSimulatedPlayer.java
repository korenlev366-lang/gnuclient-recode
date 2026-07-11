package gnu.client.module.modules.player.scaffold;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.List;

/**
 * LiquidBounce {@code SimulatedPlayer}-lite for scaffold edge checks — one-tick motion
 * estimate + {@code isCloseToEdge} (edge ray + fall-off AABB).
 */
public final class ScaffoldSimulatedPlayer {
  private static final double MOTION_THRESHOLD = 0.003;
  private static final float STEP_HEIGHT = 0.6f;

  public double posX;
  public double posY;
  public double posZ;
  public double motionX;
  public double motionY;
  public double motionZ;
  public boolean onGround;
  public float yaw;
  /** Set when 1-tick XZ step leaves supporting collision (GodBridge ledge approx). */
  public boolean clipLedged;

  private ScaffoldSimulatedPlayer() {}

  /**
   * Snapshot client player and advance one living-motion tick (no jump; sneak slows input).
   */
  public static ScaffoldSimulatedPlayer fromClientPlayer(EntityPlayerSP player, float forward,
                                                         float strafe, boolean sneak) {
    ScaffoldSimulatedPlayer sim = new ScaffoldSimulatedPlayer();
    if (player == null)
      return sim;
    sim.posX = player.posX;
    sim.posY = player.posY;
    sim.posZ = player.posZ;
    sim.motionX = player.motionX;
    sim.motionY = player.motionY;
    sim.motionZ = player.motionZ;
    sim.onGround = player.onGround;
    sim.yaw = player.rotationYaw;
    sim.tick(player.worldObj, forward, strafe, sneak);
    return sim;
  }

  /**
   * LB {@code LocalPlayer.isCloseToEdge}: 1-tick sim → edge ray → fall-off AABB at now and next.
   */
  public static boolean isCloseToEdge(EntityPlayerSP player, World world, float forward,
                                      float strafe, double distance) {
    return isCloseToEdge(player, world, forward, strafe, distance,
        player == null ? null : new Vec3(player.posX, player.posY, player.posZ));
  }

  public static boolean isCloseToEdge(EntityPlayerSP player, World world, float forward,
                                      float strafe, double distance, Vec3 pos) {
    if (player == null || world == null || pos == null)
      return false;
    double dist = Math.max(0.01, distance);
    // LB clears jump/sneak for the probe so edge detect is not suppressed by eagle sneak.
    ScaffoldSimulatedPlayer sim = new ScaffoldSimulatedPlayer();
    sim.posX = pos.xCoord;
    sim.posY = pos.yCoord;
    sim.posZ = pos.zCoord;
    sim.motionX = player.motionX;
    sim.motionY = player.motionY;
    sim.motionZ = player.motionZ;
    sim.onGround = player.onGround;
    sim.yaw = player.rotationYaw;
    sim.tick(world, forward, strafe, false);

    double nextMx = sim.motionX;
    double nextMz = sim.motionZ;
    double horizSq = nextMx * nextMx + nextMz * nextMz;
    double dirX;
    double dirZ;
    if (horizSq > MOTION_THRESHOLD * MOTION_THRESHOLD) {
      double inv = 1.0 / Math.sqrt(horizSq);
      dirX = nextMx * inv;
      dirZ = nextMz * inv;
    } else {
      float moveYaw = movementYaw(player.rotationYaw, forward, strafe);
      float rad = moveYaw * (float) Math.PI / 180.0f;
      dirX = -MathHelper.sin(rad);
      dirZ = MathHelper.cos(rad);
    }

    Vec3 from = new Vec3(pos.xCoord, pos.yCoord - 0.1, pos.zCoord);
    Vec3 to = new Vec3(from.xCoord + dirX * dist, from.yCoord, from.zCoord + dirZ * dist);
    if (ScaffoldEdgeCollision.findEdgeCollision(world, from, to, 0.5f) != null)
      return true;

    Vec3 nextPos = new Vec3(sim.posX + nextMx, sim.posY, sim.posZ + nextMz);
    return wouldBeCloseToFallOff(player, world, pos)
        || wouldBeCloseToFallOff(player, world, nextPos);
  }

  /**
   * LB {@code Player.wouldBeCloseToFallOff} — shrunk AABB at feet with fall/step offset has
   * no block collision (standing over air / about to drop).
   */
  public static boolean wouldBeCloseToFallOff(EntityPlayerSP player, World world, Vec3 position) {
    if (player == null || world == null || position == null)
      return false;
    float halfW = player.width * 0.5f;
    double pad = 0.05;
    double minX = position.xCoord - halfW + pad;
    double maxX = position.xCoord + halfW - pad;
    double minZ = position.zCoord - halfW + pad;
    double maxZ = position.zCoord + halfW - pad;
    double yOff = player.fallDistance - STEP_HEIGHT;
    double minY = position.yCoord + yOff;
    double maxY = minY + player.height;
    AxisAlignedBB box = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    List<AxisAlignedBB> collisions = world.getCollidingBoundingBoxes(player, box);
    return collisions == null || collisions.isEmpty();
  }

  private void tick(World world, float forward, float strafe, boolean sneak) {
    if (Math.abs(motionX) < MOTION_THRESHOLD)
      motionX = 0.0;
    if (Math.abs(motionY) < MOTION_THRESHOLD)
      motionY = 0.0;
    if (Math.abs(motionZ) < MOTION_THRESHOLD)
      motionZ = 0.0;

    float f = forward * 0.98f;
    float s = strafe * 0.98f;
    if (sneak) {
      f *= 0.3f;
      s *= 0.3f;
    }

    float slip = 0.6f;
    if (onGround && world != null) {
      BlockPos below = new BlockPos(MathHelper.floor_double(posX),
          MathHelper.floor_double(posY) - 1,
          MathHelper.floor_double(posZ));
      IBlockState state = world.getBlockState(below);
      Block block = state != null ? state.getBlock() : null;
      if (block != null)
        slip = block.slipperiness;
    }
    float friction = onGround ? slip * 0.91f : 0.91f;
    float accel;
    if (onGround) {
      float f3 = slip * 0.91f;
      accel = 0.1f * (0.16277136f / (f3 * f3 * f3));
    } else {
      accel = 0.02f;
    }
    moveFlying(s, f, accel);

    double beforeX = posX;
    double beforeZ = posZ;
    posX += motionX;
    posY += motionY;
    posZ += motionZ;

    motionX *= friction;
    motionY *= 0.9800000190734863;
    motionZ *= friction;

    if (world != null && onGround) {
      Vec3 from = new Vec3(beforeX, posY - 0.1, beforeZ);
      Vec3 to = new Vec3(posX, posY - 0.1, posZ);
      if (ScaffoldEdgeCollision.findEdgeCollision(world, from, to, 0.5f) != null)
        clipLedged = true;
    }
  }

  private void moveFlying(float strafe, float forward, float friction) {
    float lenSq = strafe * strafe + forward * forward;
    if (lenSq < 1.0e-4f)
      return;
    float len = MathHelper.sqrt_float(lenSq);
    if (len < 1.0f)
      len = 1.0f;
    len = friction / len;
    strafe *= len;
    forward *= len;
    float yawRad = yaw * (float) Math.PI / 180.0f;
    float sin = MathHelper.sin(yawRad);
    float cos = MathHelper.cos(yawRad);
    motionX += strafe * cos - forward * sin;
    motionZ += forward * cos + strafe * sin;
  }

  /** Movement-facing yaw from WASD relative to camera yaw (1.8 moveFlying basis). */
  private static float movementYaw(float cameraYaw, float forward, float strafe) {
    if (Math.abs(forward) < 1.0e-4f && Math.abs(strafe) < 1.0e-4f)
      return cameraYaw;
    return cameraYaw + (float) Math.toDegrees(Math.atan2(strafe, forward));
  }
}
