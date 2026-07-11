package gnu.client.module.modules.player.scaffold;

import gnu.client.runtime.MoveFixUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * LiquidBounce {@code ScaffoldMovementPlanner} — optimal bridge line from support + recent places.
 */
public final class ScaffoldMovementPlanner {
  private static final int MAX_LAST = 4;
  private static final float DIR_HYSTERESIS = 30.0f;
  private static final double SUPPORT_SURFACE_EPSILON = 1.0e-3;
  private static final double SUPPORT_OVERLAP_HYSTERESIS = 0.02;
  private static final double[] OFFSETS_TO_TRY = {0.301, 0.0, -0.301};

  private final ArrayDeque<BlockPos> lastPlaced = new ArrayDeque<>(MAX_LAST);
  private float lastDirectionAngle = Float.NaN;
  private Vec3 currentLineOrigin;
  private Vec3 currentLineDir;
  private double supportOffsetX;
  private double supportOffsetZ;
  private boolean hasSupportOffset;
  private SupportReference lastSupportReference;
  private BlockPos lastSupportPosition;

  public static final class SupportReference {
    public final BlockPos blockPos;
    public final double offsetX;
    public final double offsetZ;

    public SupportReference(BlockPos blockPos, double offsetX, double offsetZ) {
      this.blockPos = blockPos;
      this.offsetX = offsetX;
      this.offsetZ = offsetZ;
    }
  }

  private static final class SupportCandidate implements Comparable<SupportCandidate> {
    final BlockPos blockPos;
    final double overlapArea;
    final double surfaceDelta;
    final double horizontalDistanceToPlayerSqr;

    SupportCandidate(BlockPos blockPos, double overlapArea, double surfaceDelta,
                     double horizontalDistanceToPlayerSqr) {
      this.blockPos = blockPos;
      this.overlapArea = overlapArea;
      this.surfaceDelta = surfaceDelta;
      this.horizontalDistanceToPlayerSqr = horizontalDistanceToPlayerSqr;
    }

    @Override
    public int compareTo(SupportCandidate other) {
      if (surfaceDelta + SUPPORT_SURFACE_EPSILON < other.surfaceDelta)
        return -1;
      if (other.surfaceDelta + SUPPORT_SURFACE_EPSILON < surfaceDelta)
        return 1;
      if (overlapArea > other.overlapArea + SUPPORT_OVERLAP_HYSTERESIS)
        return -1;
      if (overlapArea + SUPPORT_OVERLAP_HYSTERESIS < other.overlapArea)
        return 1;
      return Double.compare(horizontalDistanceToPlayerSqr, other.horizontalDistanceToPlayerSqr);
    }

    boolean isStableComparedTo(SupportCandidate best) {
      if (surfaceDelta > best.surfaceDelta + SUPPORT_SURFACE_EPSILON)
        return false;
      if (overlapArea + SUPPORT_OVERLAP_HYSTERESIS < best.overlapArea)
        return false;
      return true;
    }
  }

  public void reset() {
    lastPlaced.clear();
    lastDirectionAngle = Float.NaN;
    currentLineOrigin = null;
    currentLineDir = null;
    supportOffsetX = 0.0;
    supportOffsetZ = 0.0;
    hasSupportOffset = false;
    lastSupportReference = null;
    lastSupportPosition = null;
  }

  public void trackPlaced(BlockPos placed) {
    if (placed == null)
      return;
    lastPlaced.addLast(placed);
    while (lastPlaced.size() > MAX_LAST)
      lastPlaced.removeFirst();
  }

  /** Drop fill anchor when stale (caller should stop preferring it). */
  public void clearLatestPlaced() {
    lastPlaced.clear();
  }

  /** Most recent scaffold place (telly gap-fill anchor), or null. */
  public BlockPos latestPlaced() {
    return lastPlaced.isEmpty() ? null : lastPlaced.peekLast();
  }

  public SupportReference getCurrentSupportReference() {
    return lastSupportReference;
  }

  public boolean updateLine(EntityPlayerSP player, float moveYawDeg) {
    if (player == null)
      return false;
    float dir = chooseDirection(moveYawDeg);
    double rad = Math.toRadians(dir);
    double dx = -Math.sin(rad);
    double dz = Math.cos(rad);

    SupportReference supportReference = findSupportReferenceUnderPlayer(player);
    if (supportReference == null) {
      currentLineOrigin = null;
      currentLineDir = null;
      hasSupportOffset = false;
      lastSupportReference = null;
      return false;
    }
    lastSupportReference = supportReference;
    supportOffsetX = supportReference.offsetX;
    supportOffsetZ = supportReference.offsetZ;
    hasSupportOffset = true;

    double ox = supportReference.blockPos.getX() + 0.5 + supportReference.offsetX;
    double oz = supportReference.blockPos.getZ() + 0.5 + supportReference.offsetZ;
    // Prefer recent place history when it aligns with movement; else support-anchored offset.
    if (lastPlaced.size() >= 2) {
      BlockPos a = null;
      BlockPos b = null;
      int i = 0;
      for (BlockPos p : lastPlaced) {
        if (i == lastPlaced.size() - 2)
          a = p;
        if (i == lastPlaced.size() - 1)
          b = p;
        i++;
      }
      if (a != null && b != null && !a.equals(b)) {
        double lx = b.getX() - a.getX();
        double lz = b.getZ() - a.getZ();
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len > 0.01) {
          double ndx = lx / len;
          double ndz = lz / len;
          // cos(60°) = 0.5 — LB divergesTooMuchFromDirection
          if (ndx * dx + ndz * dz >= 0.5) {
            // Nearest point on history line to player (XZ).
            double ax = a.getX() + 0.5;
            double az = a.getZ() + 0.5;
            double t = ((player.posX - ax) * ndx + (player.posZ - az) * ndz);
            ox = ax + ndx * t;
            oz = az + ndz * t;
            dx = ndx;
            dz = ndz;
          }
        }
      }
    }

    currentLineOrigin = new Vec3(ox, player.posY, oz);
    currentLineDir = new Vec3(dx, 0, dz);
    return true;
  }

  private SupportReference findSupportReferenceUnderPlayer(EntityPlayerSP player) {
    Map<BlockPos, SupportCandidate> candidates = collectSupportCandidates(player);
    if (candidates.isEmpty()) {
      lastSupportReference = null;
      lastSupportPosition = null;
      return null;
    }
    SupportCandidate best = null;
    for (SupportCandidate c : candidates.values()) {
      if (best == null || c.compareTo(best) < 0)
        best = c;
    }
    if (best == null)
      return null;
    SupportCandidate chosen = chooseStableSupportCandidate(candidates, best);
    lastSupportPosition = chosen.blockPos;
    return new SupportReference(
        chosen.blockPos,
        player.posX - (chosen.blockPos.getX() + 0.5),
        player.posZ - (chosen.blockPos.getZ() + 0.5));
  }

  private Map<BlockPos, SupportCandidate> collectSupportCandidates(EntityPlayerSP player) {
    HashMap<BlockPos, SupportCandidate> candidates = new HashMap<>(8);
    for (double xOffset : OFFSETS_TO_TRY) {
      for (double zOffset : OFFSETS_TO_TRY) {
        BlockPos blockPos = new BlockPos(
            MathHelper.floor_double(player.posX + xOffset),
            MathHelper.floor_double(player.posY) - 1,
            MathHelper.floor_double(player.posZ + zOffset));
        if (candidates.containsKey(blockPos))
          continue;
        if (!ScaffoldMath.isValidSupport(blockPos))
          continue;
        candidates.put(blockPos, createSupportCandidate(player, blockPos));
      }
    }
    return candidates;
  }

  private SupportCandidate chooseStableSupportCandidate(Map<BlockPos, SupportCandidate> candidates,
                                                        SupportCandidate bestCandidate) {
    BlockPos lastPlacedBlock = lastPlaced.isEmpty() ? null : lastPlaced.peekLast();
    SupportCandidate preferredLastPlaced = lastPlacedBlock != null ? candidates.get(lastPlacedBlock) : null;
    SupportCandidate preferredLastPosition =
        lastSupportPosition != null ? candidates.get(lastSupportPosition) : null;
    if (preferredLastPlaced != null && preferredLastPlaced.isStableComparedTo(bestCandidate))
      return preferredLastPlaced;
    if (preferredLastPosition != null && preferredLastPosition.isStableComparedTo(bestCandidate))
      return preferredLastPosition;
    return bestCandidate;
  }

  private SupportCandidate createSupportCandidate(EntityPlayerSP player, BlockPos blockPos) {
    World world = Minecraft.getMinecraft().theWorld;
    AxisAlignedBB playerBox = player.getEntityBoundingBox();
    double bestSurfaceDelta = Double.POSITIVE_INFINITY;
    double overlapAreaOnBestSurface = 0.0;

    AxisAlignedBB blockBox = null;
    if (world != null) {
      Block block = world.getBlockState(blockPos).getBlock();
      if (block != null) {
        block.setBlockBoundsBasedOnState(world, blockPos);
        blockBox = block.getCollisionBoundingBox(world, blockPos, world.getBlockState(blockPos));
      }
    }
    if (blockBox == null) {
      blockBox = new AxisAlignedBB(
          blockPos.getX(), blockPos.getY(), blockPos.getZ(),
          blockPos.getX() + 1.0, blockPos.getY() + 1.0, blockPos.getZ() + 1.0);
    }

    if (playerBox != null) {
      double overlapX = Math.min(playerBox.maxX, blockBox.maxX) - Math.max(playerBox.minX, blockBox.minX);
      double overlapZ = Math.min(playerBox.maxZ, blockBox.maxZ) - Math.max(playerBox.minZ, blockBox.minZ);
      if (overlapX > 0.0 && overlapZ > 0.0) {
        double surfaceDelta = Math.abs(playerBox.minY - blockBox.maxY);
        bestSurfaceDelta = surfaceDelta;
        overlapAreaOnBestSurface = overlapX * overlapZ;
      }
    }

    double hx = player.posX - (blockPos.getX() + 0.5);
    double hz = player.posZ - (blockPos.getZ() + 0.5);
    return new SupportCandidate(blockPos, overlapAreaOnBestSurface, bestSurfaceDelta, hx * hx + hz * hz);
  }

  public boolean hasLine() {
    return currentLineOrigin != null && currentLineDir != null;
  }

  public Vec3 lineOrigin() {
    return currentLineOrigin;
  }

  public Vec3 lineDir() {
    return currentLineDir;
  }

  public boolean hasSupportOffset() {
    return hasSupportOffset;
  }

  public double supportOffsetX() {
    return supportOffsetX;
  }

  public double supportOffsetZ() {
    return supportOffsetZ;
  }

  public Vec3 nearestPointOnLine(double px, double py, double pz) {
    if (!hasLine())
      return new Vec3(px, py, pz);
    double ox = currentLineOrigin.xCoord;
    double oy = currentLineOrigin.yCoord;
    double oz = currentLineOrigin.zCoord;
    double dx = currentLineDir.xCoord;
    double dy = currentLineDir.yCoord;
    double dz = currentLineDir.zCoord;
    double lenSq = dx * dx + dy * dy + dz * dz;
    if (lenSq < 1.0e-12)
      return new Vec3(ox, oy, oz);
    double t = ((px - ox) * dx + (py - oy) * dy + (pz - oz) * dz) / lenSq;
    return new Vec3(ox + dx * t, oy + dy * t, oz + dz * t);
  }

  public float lineYaw(float fallback) {
    if (currentLineDir == null)
      return fallback;
    return (float) Math.toDegrees(Math.atan2(-currentLineDir.xCoord, currentLineDir.zCoord));
  }

  public void stabilizeInput(MovementInputMut mut) {
    if (currentLineDir == null || mut == null)
      return;
    float lineYaw = lineYaw(0.0f);
    float moveYaw = MoveFixUtil.movementFacingYaw();
    float diff = Math.abs(ScaffoldMath.wrapAngle(moveYaw - lineYaw));
    if (diff < 25.0f) {
      if (Math.abs(mut.forward) > Math.abs(mut.strafe))
        mut.strafe *= 0.35f;
      else if (Math.abs(mut.strafe) > Math.abs(mut.forward))
        mut.forward *= 0.35f;
    }
  }

  private float chooseDirection(float moveYaw) {
    float yaw = MathHelper.wrapAngleTo180_float(moveYaw);
    if (!Float.isNaN(lastDirectionAngle)
        && Math.abs(ScaffoldMath.wrapAngle(yaw - lastDirectionAngle)) < DIR_HYSTERESIS)
      return lastDirectionAngle;
    float snapped = Math.round(yaw / 45.0f) * 45.0f;
    lastDirectionAngle = snapped;
    return snapped;
  }

  public static final class MovementInputMut {
    public float forward;
    public float strafe;

    public MovementInputMut(float forward, float strafe) {
      this.forward = forward;
      this.strafe = strafe;
    }
  }
}
