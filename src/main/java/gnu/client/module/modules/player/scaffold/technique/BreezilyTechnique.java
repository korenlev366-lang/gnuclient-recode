package gnu.client.module.modules.player.scaffold.technique;

import gnu.client.module.modules.player.scaffold.PlacementTarget;
import gnu.client.module.modules.player.scaffold.ScaffoldMath;
import gnu.client.module.modules.player.scaffold.ScaffoldTargetFinding;
import gnu.client.runtime.MoveFixUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;

/**
 * LiquidBounce Breezily — edge-distance sideways weave + LB pitches (80 / 75.6 / 75),
 * Center placement.
 */
public final class BreezilyTechnique implements ScaffoldTechnique {
  public static final BreezilyTechnique INSTANCE = new BreezilyTechnique();

  private static float lastSideways;
  private static long lastAirMs;
  private static double currentEdgeDistance = 0.45;

  private BreezilyTechnique() {}

  @Override
  public PlacementTarget findTarget(EntityPlayer player, BlockPos intendedPlace, float baseYaw, float basePitch,
                                    TechniqueContext ctx) {
    float[] rots = getRotations(player, baseYaw, null);
    float yaw = ScaffoldMath.unwrapYaw(baseYaw, rots[0]);
    float pitch = rots[1];
    PlacementTarget t = ScaffoldTargetFinding.findBreezilyTarget(player, yaw, pitch);
    if (t != null)
      return new PlacementTarget(t.interactedBlockPos, t.placedBlockPos, t.faceOrdinal, t.hitVec,
          yaw, pitch, t.minPlacementY);
    return ScaffoldTargetFinding.findTarget(player, intendedPlace, ScaffoldTargetFinding.OFFSETS_NORMAL,
        ScaffoldTargetFinding.AIM_CENTER, yaw, pitch, 0, false);
  }

  /**
   * LB {@code ScaffoldBreezilyTechnique.getRotations}.
   *
   * @return {@code float[]{yaw, pitch}}
   */
  public static float[] getRotations(EntityPlayer player, float baseYaw, PlacementTarget target) {
    boolean moving = MoveFixUtil.isForwardPressed()
        || (player != null && (Math.abs(player.moveForward) > 0.01f
        || Math.abs(player.moveStrafing) > 0.01f));
    if (!moving) {
      float refYaw = target != null ? target.yaw : baseYaw;
      float axisMovement = (float) Math.floor(refYaw / 90.0f) * 90.0f;
      return new float[] {ScaffoldMath.quantize(axisMovement + 45.0f), ScaffoldMath.quantize(75.0f)};
    }
    float direction = MoveFixUtil.movementFacingYaw() + 180.0f;
    float movingYaw = Math.round(direction / 45.0f) * 45.0f;
    float mod90 = Math.abs(movingYaw) % 90.0f;
    boolean isMovingStraight = mod90 < 1.0f || mod90 > 89.0f;
    if (isMovingStraight)
      return new float[] {ScaffoldMath.quantize(movingYaw), ScaffoldMath.quantize(80.0f)};
    return new float[] {ScaffoldMath.quantize(movingYaw), ScaffoldMath.quantize(75.6f)};
  }

  /**
   * LB breezily sideways: based on facing + in-block offset vs random edge distance.
   */
  public static float edgeStrafe(EntityPlayer player, float edgeMin, float edgeMax) {
    if (player == null)
      return 0.0f;
    BlockPos below = new BlockPos(MathHelper.floor_double(player.posX),
        MathHelper.floor_double(player.posY) - 1,
        MathHelper.floor_double(player.posZ));
    if (ScaffoldMath.isReplaceable(below)) {
      lastAirMs = System.currentTimeMillis();
    } else if (System.currentTimeMillis() - lastAirMs > 500L) {
      return 0.0f;
    }
    if (currentEdgeDistance < edgeMin || currentEdgeDistance > edgeMax)
      currentEdgeDistance = edgeMin + Math.random() * Math.max(0.01, edgeMax - edgeMin);

    double modX = player.posX - Math.floor(player.posX);
    double modZ = player.posZ - Math.floor(player.posZ);
    double ma = 1.0 - currentEdgeDistance;
    float sideways = 0.0f;
    EnumFacing facing = yawFacing(player.rotationYaw);
    switch (facing) {
      case SOUTH:
        if (modX > ma) sideways = 1.0f;
        if (modX < currentEdgeDistance) sideways = -1.0f;
        break;
      case NORTH:
        if (modX < currentEdgeDistance) sideways = 1.0f;
        if (modX > ma) sideways = -1.0f;
        break;
      case EAST:
        if (modZ < currentEdgeDistance) sideways = 1.0f;
        if (modZ > ma) sideways = -1.0f;
        break;
      case WEST:
        if (modZ > ma) sideways = 1.0f;
        if (modZ < currentEdgeDistance) sideways = -1.0f;
        break;
      default:
        break;
    }
    if (sideways != 0.0f && sideways != lastSideways) {
      lastSideways = sideways;
      currentEdgeDistance = edgeMin + Math.random() * Math.max(0.01, edgeMax - edgeMin);
    } else if (sideways != 0.0f) {
      lastSideways = sideways;
    }
    return sideways != 0.0f ? sideways : lastSideways;
  }

  private static EnumFacing yawFacing(float yaw) {
    int i = MathHelper.floor_double((double) (yaw * 4.0f / 360.0f) + 0.5) & 3;
    switch (i) {
      case 0: return EnumFacing.SOUTH;
      case 1: return EnumFacing.WEST;
      case 2: return EnumFacing.NORTH;
      default: return EnumFacing.EAST;
    }
  }
}
