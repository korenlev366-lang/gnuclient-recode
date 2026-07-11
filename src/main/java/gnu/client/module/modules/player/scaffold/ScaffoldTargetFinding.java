package gnu.client.module.modules.player.scaffold;

import gnu.client.module.modules.player.scaffold.aim.AlignedFace;
import gnu.client.module.modules.player.scaffold.aim.FaceAimModes;
import gnu.client.module.modules.player.scaffold.aim.FaceTargetPositionFactory;
import gnu.client.module.modules.player.scaffold.aim.Line3;
import gnu.client.module.modules.player.scaffold.aim.PositionFactoryConfiguration;
import gnu.client.module.modules.player.scaffold.aim.ScaffoldGeometry;
import gnu.client.runtime.MoveFixUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * LiquidBounce {@code findBestBlockPlacementTarget} offset-grid port for 1.8.9.
 * Offsets NORMAL/DOWN/FULL match LB {@code BlockPosOffsets}; aim points stay ray-validated.
 */
public final class ScaffoldTargetFinding {
  public static final int AIM_CENTER = 0;
  public static final int AIM_NEAREST = 1;
  public static final int AIM_REVERSE_YAW = 2;
  public static final int AIM_DIAGONAL_YAW = 3;
  public static final int AIM_ANGLE_YAW = 4;
  public static final int AIM_EDGE = 5;
  public static final int AIM_STABILIZED = 6;
  public static final int AIM_RANDOM = 7;

  public static final int OFFSETS_NORMAL = 0;
  public static final int OFFSETS_DOWN = 1;
  public static final int OFFSETS_FULL = 2;
  public static final int OFFSETS_EXPAND = 3;

  private static final double[] FACE_OFFSETS = {
      0.125, 0.3125, 0.5, 0.6875, 0.875
  };

  private static final List<BlockPos> OFFSETS_LIST_NORMAL = generateScaffoldOffsets(0, -1, 1);
  private static final List<BlockPos> OFFSETS_LIST_DOWN = generateScaffoldOffsets(0, -1, 1, -2, 2);
  private static final List<BlockPos> OFFSETS_LIST_FULL =
      generateScaffoldOffsets(0, -1, 1, -2, 2, -3, 3, -4, 4);
  private static final List<BlockPos> OFFSETS_LIST_ZERO =
      Collections.singletonList(new BlockPos(0, 0, 0));

  /** Standing / crouching eye heights (1.8.9 {@code EntityPlayer.getEyeHeight}). */
  public static final float EYE_STANDING = 1.62f;
  public static final float EYE_CROUCHING = 1.54f;

  /** LB Stabilized: yaw of optimal movement line (set each tick by ScaffoldModule). */
  private static volatile float optimalLineYaw = Float.NaN;
  private static volatile Line3 optimalLine;
  private static volatile Vec3 predictedFeet;
  /** Override eye for face factories (predicted feet + crouch/stand height). */
  private static volatile Vec3 overrideEyePos;

  private ScaffoldTargetFinding() {}

  public static void setOptimalLineYaw(float yawDeg) {
    optimalLineYaw = yawDeg;
  }

  /** Full optimal line from planner (also updates {@link #optimalLineYaw}). */
  public static void setOptimalLine(Line3 line) {
    optimalLine = line;
    if (line != null && line.direction != null) {
      optimalLineYaw = (float) Math.toDegrees(
          Math.atan2(-line.direction.xCoord, line.direction.zCoord));
    } else {
      optimalLineYaw = Float.NaN;
    }
  }

  public static Line3 getOptimalLine() {
    return optimalLine;
  }

  /** Predicted feet for offset sort; null → use player position. */
  public static void setPredictedFeet(Vec3 feet) {
    predictedFeet = feet;
  }

  public static Vec3 getPredictedFeet() {
    return predictedFeet;
  }

  /**
   * LB {@code PositionFactoryConfiguration.eyePos} — predicted feet + pose eye height.
   * Null clears override (use live {@code player.getEyeHeight()}).
   */
  public static void setEyePos(Vec3 eye) {
    overrideEyePos = eye;
  }

  public static Vec3 getEyePos() {
    return overrideEyePos;
  }

  private static Vec3 eyeOf(EntityPlayer player) {
    if (overrideEyePos != null)
      return overrideEyePos;
    return new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
  }

  private static Minecraft mc() {
    return Minecraft.getMinecraft();
  }

  /** LB {@code generateScaffoldOffsets}: all (x,0|−1,z) for x,z in values, sorted by length². */
  private static List<BlockPos> generateScaffoldOffsets(int... xzValues) {
    ArrayList<BlockPos> list = new ArrayList<>(xzValues.length * xzValues.length * 2);
    for (int x : xzValues) {
      for (int z : xzValues) {
        list.add(new BlockPos(x, 0, z));
        list.add(new BlockPos(x, -1, z));
      }
    }
    list.sort(Comparator
        .comparingLong((BlockPos p) -> (long) p.getX() * p.getX() + (long) p.getY() * p.getY()
            + (long) p.getZ() * p.getZ())
        .thenComparingInt(BlockPos::getY)
        .thenComparingInt(BlockPos::getX)
        .thenComparingInt(BlockPos::getZ));
    return Collections.unmodifiableList(list);
  }

  private static List<BlockPos> offsetsFor(int offsetMode, boolean underfootOnly) {
    if (underfootOnly)
      return OFFSETS_LIST_ZERO;
    switch (offsetMode) {
      case OFFSETS_DOWN:
        return OFFSETS_LIST_DOWN;
      case OFFSETS_FULL:
      case OFFSETS_EXPAND:
        return OFFSETS_LIST_FULL;
      case OFFSETS_NORMAL:
      default:
        return OFFSETS_LIST_NORMAL;
    }
  }

  /**
   * LB {@code findBestBlockPlacementTarget} against {@code intendedPlace} (+ offsets).
   *
   * @param considerFacingAway LB expand/down — allow faces pointing away from the eye
   * @param underfootOnly tower — only the intended cell
   * @param upFaceOnly tower — only UP face of the block below
   */
  public static PlacementTarget findTarget(EntityPlayer player, BlockPos intendedPlace,
                                           int offsetMode, int aimMode, float baseYaw, float basePitch,
                                           int expandLength, boolean considerFacingAway,
                                           boolean underfootOnly, boolean upFaceOnly) {
    if (player == null || intendedPlace == null || mc().theWorld == null)
      return null;

    // LB: cannot place when the base targeted cell is already solid.
    if (!ScaffoldMath.isReplaceable(intendedPlace))
      return null;

    float reach = mc().playerController != null
        ? mc().playerController.getBlockReachDistance() : 4.5f;
    float preferYaw = preferredYaw(player, aimMode, baseYaw);
    Vec3 eye = eyeOf(player);

    List<BlockPos> offsets = new ArrayList<>(offsetsFor(offsetMode, underfootOnly));
    // LB: leastBlockDistanceToLine when line exists, else leastBlockDistanceToPos(predicted/player).
    final BlockPos origin = intendedPlace;
    final Line3 line = optimalLine;
    final Vec3 sortPos = predictedFeet != null
        ? predictedFeet
        : new Vec3(player.posX, player.posY, player.posZ);
    final Comparator<BlockPos> worldPriority = line != null
        ? ScaffoldGeometry.leastBlockDistanceToLine(line)
        : ScaffoldGeometry.leastBlockDistanceToPos(sortPos);
    offsets.sort((a, b) -> {
      BlockPos pa = origin.add(a.getX(), a.getY(), a.getZ());
      BlockPos pb = origin.add(b.getX(), b.getY(), b.getZ());
      return worldPriority.compare(pa, pb);
    });

    for (BlockPos off : offsets) {
      BlockPos placePos = intendedPlace.add(off.getX(), off.getY(), off.getZ());
      if (!ScaffoldMath.isReplaceable(placePos))
        continue;

      PlacementTarget best = null;
      float bestAngle = Float.MAX_VALUE;

      for (EnumFacing face : EnumFacing.values()) {
        if (upFaceOnly && face != EnumFacing.UP)
          continue;
        // PLACE_AT_NEIGHBOR: interact with neighbor opposite to face, click `face` into placePos.
        BlockPos support = placePos.offset(face.getOpposite());
        if (!ScaffoldMath.isValidSupport(support))
          continue;
        if (!ScaffoldMath.isPlacementTargetClear(support, face.ordinal()))
          continue;
        if (ScaffoldMath.wouldIntersectPlayer(player, support, face.ordinal()))
          continue;

        Vec3 faceCenter = ScaffoldMath.faceHitVec(support, face.ordinal());
        if (faceCenter == null)
          continue;
        if (!considerFacingAway && angleToEyeCosine(eye, faceCenter, face) < 0.0)
          continue;

        PlacementTarget aim = aimWithFallbacks(player, support, face, placePos,
            preferYaw, baseYaw, basePitch, reach, aimMode);
        if (aim == null)
          continue;
        float ang = Math.abs(ScaffoldMath.wrapAngle(aim.yaw - baseYaw))
            + Math.abs(aim.pitch - basePitch);
        if (ang < bestAngle) {
          bestAngle = ang;
          best = aim;
        }
      }
      if (best != null)
        return best;
    }
    return null;
  }

  public static PlacementTarget findTarget(EntityPlayer player, BlockPos intendedPlace,
                                           int offsetMode, int aimMode, float baseYaw, float basePitch,
                                           int expandLength, boolean considerFacingAway) {
    return findTarget(player, intendedPlace, offsetMode, aimMode, baseYaw, basePitch,
        expandLength, considerFacingAway, false, false);
  }

  public static PlacementTarget findTarget(EntityPlayer player, BlockPos intendedPlace,
                                           int offsetMode, int aimMode, float baseYaw, float basePitch,
                                           int expandLength, boolean considerFacingAway,
                                           boolean underfootOnly) {
    return findTarget(player, intendedPlace, offsetMode, aimMode, baseYaw, basePitch,
        expandLength, considerFacingAway, underfootOnly, underfootOnly);
  }

  /**
   * Re-aim an existing support/face after the eye moved (post-move place).
   */
  public static PlacementTarget retargetFace(EntityPlayer player, BlockPos support, int faceOrdinal,
                                             BlockPos placePos, float baseYaw, float basePitch) {
    if (player == null || support == null || placePos == null)
      return null;
    EnumFacing face = ScaffoldMath.enumFacing(faceOrdinal);
    if (face == null)
      return null;
    float reach = mc().playerController != null
        ? mc().playerController.getBlockReachDistance() : 4.5f;
    return aimWithFallbacks(player, support, face, placePos, baseYaw, baseYaw, basePitch, reach, AIM_CENTER);
  }

  private static double angleToEyeCosine(Vec3 eye, Vec3 faceCenter, EnumFacing face) {
    double dx = eye.xCoord - faceCenter.xCoord;
    double dy = eye.yCoord - faceCenter.yCoord;
    double dz = eye.zCoord - faceCenter.zCoord;
    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (len < 1.0e-6)
      return 1.0;
    double nx = face.getFrontOffsetX();
    double ny = face.getFrontOffsetY();
    double nz = face.getFrontOffsetZ();
    return (dx * nx + dy * ny + dz * nz) / len;
  }

  private static PlacementTarget aimWithFallbacks(EntityPlayer player, BlockPos support, EnumFacing face,
                                                  BlockPos placePos, float preferYaw, float baseYaw,
                                                  float basePitch, float reach, int aimMode) {
    PlacementTarget aim = aimAtFace(player, support, face, placePos,
        preferYaw, baseYaw, basePitch, reach, aimMode);
    if (aim == null && Math.abs(ScaffoldMath.wrapAngle(preferYaw - baseYaw)) > 0.5f)
      aim = aimAtFace(player, support, face, placePos, baseYaw, baseYaw, basePitch, reach, AIM_CENTER);
    if (aim == null)
      aim = aimAtFace(player, support, face, placePos, preferYaw, baseYaw, 85.0f, reach, AIM_CENTER);
    return aim;
  }

  private static PlacementTarget aimAtFace(EntityPlayer player, BlockPos support, EnumFacing facing,
                                           BlockPos placePos, float preferYaw, float baseYaw, float basePitch,
                                           float reach, int aimMode) {
    // Primary: LB face factories (point relative to support origin → world → ray validate).
    AlignedFace localFace = ScaffoldGeometry.fromBlockFace(support, facing);
    if (localFace != null) {
      // LB: prefer upper portion when face top ≥ 0.9
      AlignedFace searchFace = localFace;
      if (searchFace.to.yCoord >= 0.9) {
        AlignedFace truncated = searchFace.truncateY(0.6).requireNonEmpty();
        if (truncated != null)
          searchFace = truncated;
      }
      Vec3 eye = eyeOf(player);
      PositionFactoryConfiguration config = new PositionFactoryConfiguration(eye, 0.0);
      FaceTargetPositionFactory factory = FaceAimModes.resolve(aimMode, config, optimalLine);
      Vec3 rel = factory.producePositionOnFace(searchFace, support);
      if (rel != null) {
        Vec3 world = new Vec3(
            support.getX() + rel.xCoord,
            support.getY() + rel.yCoord,
            support.getZ() + rel.zCoord);
        PlacementTarget factoryHit = tryAimPoint(player, support, facing, placePos, world,
            baseYaw, basePitch, reach);
        if (factoryHit != null)
          return factoryHit;
      }
    }

    // Fallback: FACE_OFFSETS grid (kept for ray-miss / null factory).
    return aimAtFaceOffsets(player, support, facing, placePos, preferYaw, baseYaw, basePitch, reach, aimMode);
  }

  private static PlacementTarget aimAtFaceOffsets(EntityPlayer player, BlockPos support, EnumFacing facing,
                                                  BlockPos placePos, float preferYaw, float baseYaw,
                                                  float basePitch, float reach, int aimMode) {
    int faceOrd = facing.ordinal();
    double[] xs = FACE_OFFSETS;
    double[] ys = FACE_OFFSETS;
    double[] zs = FACE_OFFSETS;
    switch (facing) {
      case NORTH: zs = new double[] {0.0}; break;
      case SOUTH: zs = new double[] {1.0}; break;
      case WEST: xs = new double[] {0.0}; break;
      case EAST: xs = new double[] {1.0}; break;
      case DOWN: ys = new double[] {0.0}; break;
      case UP: ys = new double[] {1.0}; break;
      default: break;
    }

    PlacementTarget best = null;
    float bestDiff = Float.MAX_VALUE;

    Vec3 center = ScaffoldMath.faceHitVec(support, faceOrd);
    if (center != null) {
      PlacementTarget c = tryAimPoint(player, support, facing, placePos, center,
          baseYaw, basePitch, reach);
      if (c != null) {
        best = c;
        bestDiff = Math.abs(ScaffoldMath.wrapAngle(c.yaw - preferYaw)) + Math.abs(c.pitch - basePitch);
        if (aimMode == AIM_CENTER)
          return best;
      }
    }

    for (double dx : xs) {
      for (double dy : ys) {
        for (double dz : zs) {
          Vec3 point = new Vec3(support.getX() + dx, support.getY() + dy, support.getZ() + dz);
          PlacementTarget cand = tryAimPoint(player, support, facing, placePos, point,
              baseYaw, basePitch, reach);
          if (cand == null)
            continue;
          float diff = Math.abs(ScaffoldMath.wrapAngle(cand.yaw - preferYaw))
              + Math.abs(cand.pitch - basePitch);
          if (best == null || diff < bestDiff) {
            best = cand;
            bestDiff = diff;
          }
        }
      }
    }
    return best;
  }

  private static PlacementTarget tryAimPoint(EntityPlayer player, BlockPos support, EnumFacing facing,
                                             BlockPos placePos, Vec3 point,
                                             float baseYaw, float basePitch, float reach) {
    // GCD-normalize from real look base so PlacementTarget.yaw stays unwrapped (AimModulo360).
    float[] rots = ScaffoldMath.rotationsTo(point, player, baseYaw, basePitch);
    MovingObjectPosition mop = ScaffoldMath.rayTrace(rots[0], rots[1], reach);
    if (!ScaffoldMath.matchesFace(mop, support, facing))
      return null;
    return new PlacementTarget(support, placePos, facing.ordinal(), mop.hitVec,
        rots[0], rots[1], placePos.getY());
  }

  private static float preferredYaw(EntityPlayer player, int aimMode, float baseYaw) {
    float moveYaw = MoveFixUtil.movementFacingYaw();
    float lineYaw = Float.isNaN(optimalLineYaw) ? moveYaw : optimalLineYaw;
    switch (aimMode) {
      case AIM_REVERSE_YAW:
        return ScaffoldMath.quantize(ScaffoldMath.unwrapYaw(baseYaw, moveYaw - 180.0f));
      case AIM_DIAGONAL_YAW: {
        float back = ScaffoldMath.unwrapYaw(baseYaw, moveYaw - 180.0f);
        if (isDiagonal(moveYaw))
          return ScaffoldMath.quantize(back);
        float side = ((moveYaw + 180.0f) % 90.0f) < 45.0f ? 1.0f : -1.0f;
        return ScaffoldMath.quantize(ScaffoldMath.unwrapYaw(baseYaw, moveYaw - 135.0f * side));
      }
      case AIM_ANGLE_YAW:
        return ScaffoldMath.quantize(ScaffoldMath.unwrapYaw(baseYaw, Math.round(moveYaw / 45.0f) * 45.0f));
      case AIM_STABILIZED:
        return ScaffoldMath.quantize(ScaffoldMath.unwrapYaw(baseYaw, lineYaw - 180.0f));
      case AIM_RANDOM:
        return ScaffoldMath.quantize(baseYaw + (float) ((Math.random() * 2.0 - 1.0) * 8.0));
      case AIM_EDGE:
        return ScaffoldMath.quantize(ScaffoldMath.unwrapYaw(baseYaw, moveYaw - 180.0f));
      case AIM_NEAREST:
      case AIM_CENTER:
      default:
        return baseYaw;
    }
  }

  private static boolean isDiagonal(float yaw) {
    float a = Math.abs(MathHelper.wrapAngleTo180_float(yaw)) % 90.0f;
    return a > 20.0f && a < 70.0f;
  }

  /**
   * LB Expand: search along yaw from predicted feet (Center aim, facing-away allowed).
   * Still ray-validates via {@link #findTarget}.
   */
  public static PlacementTarget findExpandTarget(EntityPlayer player, float yaw, int length,
                                                 float basePitch, int aimMode) {
    if (player == null || length < 1)
      return null;
    Vec3 origin = predictedFeet != null
        ? predictedFeet
        : new Vec3(player.posX, player.posY, player.posZ);
    int by = MathHelper.floor_double(origin.yCoord) - 1;
    double rad = Math.toRadians(yaw);
    double fx = -Math.sin(rad);
    double fz = Math.cos(rad);
    int aim = AIM_CENTER; // LB Expand always CenterTargetPositionFactory
    for (int i = 0; i <= length; i++) {
      int bx = MathHelper.floor_double(origin.xCoord + fx * i);
      int bz = MathHelper.floor_double(origin.zCoord + fz * i);
      BlockPos place = new BlockPos(bx, by, bz);
      PlacementTarget t = findTarget(player, place, OFFSETS_NORMAL, aim, yaw, basePitch, 0, true);
      if (t != null)
        return t;
    }
    return null;
  }

  public static PlacementTarget findGodBridgeTarget(EntityPlayer player, float yaw, float pitch) {
    Vec3 origin = predictedFeet != null
        ? predictedFeet
        : new Vec3(player.posX, player.posY, player.posZ);
    int bx = MathHelper.floor_double(origin.xCoord);
    int by = MathHelper.floor_double(origin.yCoord) - 1;
    int bz = MathHelper.floor_double(origin.zCoord);
    return findTarget(player, new BlockPos(bx, by, bz), OFFSETS_NORMAL, AIM_CENTER, yaw, pitch, 0, false);
  }

  /** LB Breezily: Center placement (not EdgePoint). */
  public static PlacementTarget findBreezilyTarget(EntityPlayer player, float yaw, float pitch) {
    Vec3 origin = predictedFeet != null
        ? predictedFeet
        : new Vec3(player.posX, player.posY, player.posZ);
    int bx = MathHelper.floor_double(origin.xCoord);
    int by = MathHelper.floor_double(origin.yCoord) - 1;
    int bz = MathHelper.floor_double(origin.zCoord);
    return findTarget(player, new BlockPos(bx, by, bz), OFFSETS_NORMAL, AIM_CENTER, yaw, pitch, 0, false);
  }
}
