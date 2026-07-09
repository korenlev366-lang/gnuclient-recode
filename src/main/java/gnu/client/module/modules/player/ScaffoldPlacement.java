package gnu.client.module.modules.player;

import gnu.client.utility.BlockUtils;
import gnu.client.utility.IMinecraftInstance;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OpenMyau-style scaffold placement — direct Forge MCP APIs (not McAccess reflection).
 * Reflection raycasts were freezing the client (~256×/tick).
 */
final class ScaffoldPlacement implements IMinecraftInstance {

  static final int FACE_DOWN = 0;
  static final int FACE_UP = 1;
  static final int FACE_NORTH = 2;
  static final int FACE_SOUTH = 3;
  static final int FACE_WEST = 4;
  static final int FACE_EAST = 5;

  private static final EnumFacing[] PLACE_FACES = {
      EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
  };

  /** Coarser than OpenMyau's 16-step grid — enough for Grim, far fewer raycasts. */
  private static final double[] PLACE_OFFSETS = {
      0.0625, 0.1875, 0.3125, 0.4375,
      0.5625, 0.6875, 0.8125, 0.9375
  };

  private ScaffoldPlacement() {}

  static final class BlockData {
    final BlockPos blockPos;
    final int faceOrdinal;

    BlockData(BlockPos blockPos, int faceOrdinal) {
      this.blockPos = blockPos;
      this.faceOrdinal = faceOrdinal;
    }

    BlockPos pos() {
      return blockPos;
    }

    EnumFacing facing() {
      return EnumFacing.values()[faceOrdinal];
    }
  }

  static final class AimData {
    final Vec3 hitVec;
    final float yaw;
    final float pitch;
    final boolean raytraceExact;

    AimData(Vec3 hitVec, float yaw, float pitch, boolean raytraceExact) {
      this.hitVec = hitVec;
      this.yaw = yaw;
      this.pitch = pitch;
      this.raytraceExact = raytraceExact;
    }
  }

  private static Minecraft mc() {
    return Minecraft.getMinecraft();
  }

  /** OpenMyau {@code BlockUtil.isReplaceable} — material.isReplaceable + snow height. */
  static boolean isReplaceable(BlockPos pos) {
    World world = mc().theWorld;
    if (world == null || pos == null)
      return false;
    Block block = world.getBlockState(pos).getBlock();
    if (!block.getMaterial().isReplaceable())
      return false;
    if (!(block instanceof BlockSnow))
      return true;
    return !(block.getBlockBoundsMaxY() > 0.125);
  }

  static boolean isInteractable(BlockPos pos) {
    World world = mc().theWorld;
    if (world == null || pos == null)
      return false;
    return BlockUtils.isInteractable(world.getBlockState(pos).getBlock());
  }

  static BlockData getBlockData(EntityPlayer player, World world, int startY, int stage, boolean shouldKeepY) {
    if (player == null || world == null)
      return null;

    int playerY = MathHelper.floor_double(player.posY);
    int targetX = MathHelper.floor_double(player.posX);
    int targetY = (stage != 0 && !shouldKeepY ? Math.min(playerY, startY) : playerY) - 1;
    int targetZ = MathHelper.floor_double(player.posZ);
    BlockPos under = new BlockPos(targetX, targetY, targetZ);
    if (!isReplaceable(under))
      return null;

    List<int[]> positions = new ArrayList<>();
    double reach = mc().playerController.getBlockReachDistance();
    for (int x = -4; x <= 4; x++) {
      for (int y = -4; y <= 0; y++) {
        for (int z = -4; z <= 4; z++) {
          int bx = targetX + x;
          int by = targetY + y;
          int bz = targetZ + z;
          BlockPos pos = new BlockPos(bx, by, bz);
          if (isReplaceable(pos))
            continue;
          if (isInteractable(pos))
            continue;
          if (player.getDistance(bx + 0.5, by + 0.5, bz + 0.5) > reach)
            continue;
          if (stage != 0 && !shouldKeepY && by >= startY)
            continue;
          for (EnumFacing face : PLACE_FACES) {
            if (isReplaceable(pos.offset(face))) {
              positions.add(new int[] {bx, by, bz});
              break;
            }
          }
        }
      }
    }

    if (positions.isEmpty())
      return null;
    positions.sort(Comparator.comparingDouble(p ->
        distSq(p[0] + 0.5, p[1] + 0.5, p[2] + 0.5,
            targetX + 0.5, targetY + 0.5, targetZ + 0.5)));

    int[] best = positions.get(0);
    EnumFacing face = bestFacing(best[0], best[1], best[2], targetX, targetY, targetZ);
    if (face == null)
      return null;
    return new BlockData(new BlockPos(best[0], best[1], best[2]), face.ordinal());
  }

  static AimData findAimData(BlockData data, float baseYaw, float basePitch) {
    if (data == null || data.blockPos == null)
      return null;
    EntityPlayer player = mc().thePlayer;
    if (player == null)
      return null;

    BlockPos pos = data.pos();
    double gcd = mouseGcd();
    float reach = mc().playerController.getBlockReachDistance();

    // Face-center first (1 raycast) — OpenMyau often lands here when bridging forward.
    Vec3 center = getClickVec(pos, data.faceOrdinal);
    if (center != null) {
      float[] centerRots = rotationsTo(center, player, baseYaw, basePitch, gcd);
      MovingObjectPosition centerMop = rayTrace(centerRots[0], centerRots[1], reach);
      if (matchesFace(centerMop, pos, data.facing()))
        return new AimData(centerMop.hitVec, centerRots[0], centerRots[1], true);
      if (matchesBlock(centerMop, pos))
        return new AimData(centerMop.hitVec, centerRots[0], centerRots[1], true);
    }

    int bx = pos.getX();
    int by = pos.getY();
    int bz = pos.getZ();

    double[] xs = PLACE_OFFSETS;
    double[] ys = PLACE_OFFSETS;
    double[] zs = PLACE_OFFSETS;
    switch (data.faceOrdinal) {
      case FACE_NORTH: zs = new double[] {0.0}; break;
      case FACE_SOUTH: zs = new double[] {1.0}; break;
      case FACE_WEST: xs = new double[] {0.0}; break;
      case FACE_EAST: xs = new double[] {1.0}; break;
      case FACE_DOWN: ys = new double[] {0.0}; break;
      case FACE_UP: ys = new double[] {1.0}; break;
      default: break;
    }

    AimData best = null;
    float bestDiff = Float.MAX_VALUE;
    search:
    for (double dx : xs) {
      for (double dy : ys) {
        for (double dz : zs) {
          Vec3 point = new Vec3(bx + dx, by + dy, bz + dz);
          float[] rotations = rotationsTo(point, player, baseYaw, basePitch, gcd);
          float aimYaw = rotations[0];
          float aimPitch = rotations[1];
          MovingObjectPosition mop = rayTrace(aimYaw, aimPitch, reach);
          if (!matchesBlock(mop, pos))
            continue;
          float diff = Math.abs(wrapAngle(aimYaw - baseYaw)) + Math.abs(aimPitch - basePitch);
          if (best == null || diff < bestDiff) {
            best = new AimData(mop.hitVec, aimYaw, aimPitch, mop.sideHit == data.facing());
            bestDiff = diff;
            if (bestDiff < 3.0f && mop.sideHit == data.facing())
              break search;
          }
        }
      }
    }
    return best;
  }

  /** Face-center aim without requiring a successful raycast (OpenMyau getClickVec path). */
  static AimData aimAtFaceCenter(BlockData data, float baseYaw, float basePitch) {
    if (data == null || data.blockPos == null)
      return null;
    EntityPlayer player = mc().thePlayer;
    if (player == null)
      return null;
    Vec3 center = getClickVec(data.blockPos, data.faceOrdinal);
    if (center == null)
      return null;
    double gcd = mouseGcd();
    float[] rots = rotationsTo(center, player, baseYaw, basePitch, gcd);
    return new AimData(center, rots[0], rots[1], false);
  }

  static AimData findAimData(BlockData data, float[] baseYaws, float basePitch) {
    if (data == null || baseYaws == null)
      return null;
    for (float baseYaw : baseYaws) {
      AimData candidate = findAimData(data, baseYaw, basePitch);
      if (candidate != null)
        return candidate;
    }
    return null;
  }

  static Vec3 hitVecForRotation(BlockPos blockPos, int faceOrdinal, float yaw, float pitch) {
    if (blockPos == null)
      return null;
    MovingObjectPosition mop = rayTrace(yaw, pitch, mc().playerController.getBlockReachDistance());
    if (!matchesFace(mop, blockPos, EnumFacing.values()[faceOrdinal]))
      return null;
    return mop.hitVec;
  }

  /**
   * Packet-look hit only. Returns null when the look misses the support block —
   * callers may fall back to aim hitVec / face-center (OpenMyau).
   */
  static Vec3 findPlacementHit(EntityPlayer player, BlockData data, float yaw, float pitch) {
    if (data == null || data.blockPos == null)
      return null;
    MovingObjectPosition mop = rayTrace(yaw, pitch, mc().playerController.getBlockReachDistance());
    if (matchesBlock(mop, data.pos()))
      return mop.hitVec;
    return null;
  }

  /** True if look from yaw/pitch intersects the block (Grim RotationPlace gate). */
  static boolean lookHitsBlock(float yaw, float pitch, BlockPos pos) {
    MovingObjectPosition mop = rayTrace(yaw, pitch, mc().playerController.getBlockReachDistance());
    return matchesBlock(mop, pos);
  }

  static boolean isFaceVisible(EntityPlayer player, BlockPos blockPos, int faceOrdinal) {
    if (player == null || blockPos == null)
      return false;
    double ex = player.posX;
    double ey = player.posY + player.getEyeHeight();
    double ez = player.posZ;
    double margin = 0.03;
    switch (faceOrdinal) {
      case FACE_NORTH: return ez < blockPos.getZ() + margin;
      case FACE_SOUTH: return ez > blockPos.getZ() + 1.0 - margin;
      case FACE_WEST: return ex < blockPos.getX() + margin;
      case FACE_EAST: return ex > blockPos.getX() + 1.0 - margin;
      case FACE_UP: return ey > blockPos.getY() + 1.0 - margin;
      case FACE_DOWN: return ey < blockPos.getY() + margin;
      default: return false;
    }
  }

  static boolean isPlacementTargetClear(BlockPos blockPos, int faceOrdinal) {
    if (blockPos == null)
      return false;
    return isReplaceable(blockPos.offset(EnumFacing.values()[faceOrdinal]));
  }

  static boolean isValidSupport(BlockPos blockPos) {
    if (blockPos == null)
      return false;
    return !isReplaceable(blockPos);
  }

  static Vec3 faceHitVec(int bx, int by, int bz, int faceOrdinal) {
    double x = bx + 0.5;
    double y = by + 0.5;
    double z = bz + 0.5;
    switch (faceOrdinal) {
      case 0: y = by; break;
      case 1: y = by + 1.0; break;
      case 2: z = bz; break;
      case 3: z = bz + 1.0; break;
      case 4: x = bx; break;
      case 5: x = bx + 1.0; break;
      default: break;
    }
    return new Vec3(x, y, z);
  }

  static Vec3 getClickVec(BlockPos blockPos, int faceOrdinal) {
    if (blockPos == null)
      return null;
    return faceHitVec(blockPos.getX(), blockPos.getY(), blockPos.getZ(), faceOrdinal);
  }

  static EnumFacing enumFacing(int ordinal) {
    EnumFacing[] values = EnumFacing.values();
    if (ordinal < 0 || ordinal >= values.length)
      return null;
    return values[ordinal];
  }

  static boolean matchesRaycast(MovingObjectPosition mop, BlockPos expectedPos, int expectedFace) {
    if (mop == null || expectedPos == null)
      return false;
    return matchesFace(mop, expectedPos, EnumFacing.values()[expectedFace]);
  }

  static boolean matchesRaycastBlock(MovingObjectPosition mop, BlockPos expectedPos) {
    if (mop == null || expectedPos == null)
      return false;
    return matchesBlock(mop, expectedPos);
  }

  static int[] offset(int x, int y, int z, int faceOrdinal) {
    EnumFacing f = EnumFacing.values()[faceOrdinal];
    return new int[] {x + f.getFrontOffsetX(), y + f.getFrontOffsetY(), z + f.getFrontOffsetZ()};
  }

  static float wrapAngle(float angle) {
    return MathHelper.wrapAngleTo180_float(angle);
  }

  static float clampPitch(float pitch) {
    return MathHelper.clamp_float(pitch, -90.0f, 90.0f);
  }

  static float quantize(float angle) {
    return quantize(angle, mouseGcd());
  }

  static float quantize(float angle, double gcd) {
    double step = gcd > 0.0 ? gcd : 0.0096;
    return (float) (angle - angle % step);
  }

  static int blockPosX(BlockPos pos) {
    return pos == null ? Integer.MIN_VALUE : pos.getX();
  }

  static int blockPosY(BlockPos pos) {
    return pos == null ? Integer.MIN_VALUE : pos.getY();
  }

  static int blockPosZ(BlockPos pos) {
    return pos == null ? Integer.MIN_VALUE : pos.getZ();
  }

  static double mouseGcd() {
    float sens = mc().gameSettings.mouseSensitivity;
    float f = sens * 0.6f + 0.2f;
    return f * f * f * 1.2f;
  }

  // ---- internals ----

  private static MovingObjectPosition rayTrace(float yaw, float pitch, float reach) {
    EntityPlayer player = mc().thePlayer;
    World world = mc().theWorld;
    if (player == null || world == null)
      return null;
    Vec3 start = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
    float yawRad = (float) Math.toRadians(yaw);
    float pitchRad = (float) Math.toRadians(pitch);
    float cosPitch = MathHelper.cos(pitchRad);
    float dx = -MathHelper.sin(yawRad) * cosPitch;
    float dy = -MathHelper.sin(pitchRad);
    float dz = MathHelper.cos(yawRad) * cosPitch;
    Vec3 end = start.addVector(dx * reach, dy * reach, dz * reach);
    return world.rayTraceBlocks(start, end, false, false, false);
  }

  private static boolean matchesFace(MovingObjectPosition mop, BlockPos pos, EnumFacing face) {
    return matchesBlock(mop, pos) && mop.sideHit == face;
  }

  private static boolean matchesBlock(MovingObjectPosition mop, BlockPos pos) {
    return mop != null
        && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
        && mop.getBlockPos() != null
        && mop.getBlockPos().equals(pos);
  }

  private static EnumFacing bestFacing(int bx, int by, int bz, int tx, int ty, int tz) {
    EnumFacing bestFace = null;
    double bestDist = Double.MAX_VALUE;
    for (EnumFacing face : PLACE_FACES) {
      BlockPos adj = new BlockPos(bx, by, bz).offset(face);
      if (adj.getY() > ty)
        continue;
      double dist = distSq(adj.getX() + 0.5, adj.getY() + 0.5, adj.getZ() + 0.5,
          tx + 0.5, ty + 0.5, tz + 0.5);
      if (bestFace == null || dist < bestDist || (dist == bestDist && face == EnumFacing.UP)) {
        bestDist = dist;
        bestFace = face;
      }
    }
    return bestFace;
  }

  private static double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
    double dx = x1 - x2;
    double dy = y1 - y2;
    double dz = z1 - z2;
    return dx * dx + dy * dy + dz * dz;
  }

  private static float[] rotationsTo(Vec3 hit, EntityPlayer player, float baseYaw, float basePitch,
                                     double gcd) {
    double eyeY = player.posY + player.getEyeHeight();
    double dx = hit.xCoord - player.posX;
    double dz = hit.zCoord - player.posZ;
    double dy = hit.yCoord - eyeY;
    double horiz = MathHelper.sqrt_double(dx * dx + dz * dz);
    float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
    float targetPitch = (float) (-(Math.atan2(dy, horiz) * 180.0 / Math.PI));
    double step = gcd > 0.0 ? gcd : 0.0096;
    float yaw = quantize(baseYaw + wrapAngle(targetYaw - baseYaw), step);
    float pitch = quantize(basePitch + wrapAngle(targetPitch - basePitch), step);
    return new float[] {yaw, clampPitch(pitch)};
  }
}
