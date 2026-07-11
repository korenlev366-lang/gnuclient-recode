package gnu.client.module.modules.player.scaffold.technique;

import gnu.client.module.modules.player.scaffold.PlacementTarget;
import gnu.client.module.modules.player.scaffold.ScaffoldMath;
import gnu.client.module.modules.player.scaffold.ScaffoldSimulatedPlayer;
import gnu.client.module.modules.player.scaffold.ScaffoldTargetFinding;
import gnu.client.module.modules.player.scaffold.feature.ScaffoldFeatures;
import gnu.client.runtime.MoveFixUtil;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * LiquidBounce GodBridge — rotation state machine (straight weave {@code isOnRightSide},
 * pitches 75.7 / 75.6 / 75) + {@code clipLedged} ledge via {@link ScaffoldSimulatedPlayer}.
 */
public final class GodBridgeTechnique implements ScaffoldTechnique {
  public static final GodBridgeTechnique INSTANCE = new GodBridgeTechnique();

  public static final int LEDGE_JUMP = 1;
  public static final int LEDGE_SNEAK = 2;
  public static final int LEDGE_STOP = 4;
  public static final int LEDGE_BACK = 8;

  private static boolean isOnRightSide;

  private GodBridgeTechnique() {}

  @Override
  public PlacementTarget findTarget(EntityPlayer player, BlockPos intendedPlace, float baseYaw, float basePitch,
                                    TechniqueContext ctx) {
    float[] rots = getRotations(player, baseYaw, null);
    // AimModulo360: technique yaws are ±180-domain — unwrap onto base before storing/aiming.
    float yaw = ScaffoldMath.unwrapYaw(baseYaw, rots[0]);
    float pitch = rots[1];
    PlacementTarget t = ScaffoldTargetFinding.findGodBridgeTarget(player, yaw, pitch);
    if (t != null) {
      return new PlacementTarget(t.interactedBlockPos, t.placedBlockPos, t.faceOrdinal, t.hitVec,
          yaw, pitch, t.minPlacementY);
    }
    return ScaffoldTargetFinding.findTarget(player, intendedPlace, ScaffoldTargetFinding.OFFSETS_NORMAL,
        ScaffoldTargetFinding.AIM_CENTER, yaw, pitch, 0, false);
  }

  /**
   * LB {@code ScaffoldGodBridgeTechnique.getRotations}.
   *
   * @return {@code float[]{yaw, pitch}}
   */
  public static float[] getRotations(EntityPlayer player, float baseYaw, PlacementTarget target) {
    float moveInputYaw = MoveFixUtil.movementFacingYaw();
    boolean moving = MoveFixUtil.isForwardPressed()
        || (player != null && (Math.abs(player.moveForward) > 0.01f
        || Math.abs(player.moveStrafing) > 0.01f));
    if (!moving) {
      if (target != null)
        return getRotationForNoInput(target.yaw);
      return getRotationForNoInput(baseYaw);
    }
    float direction = moveInputYaw + 180.0f;
    float movingYaw = Math.round(direction / 45.0f) * 45.0f;
    float mod90 = Math.abs(movingYaw) % 90.0f;
    boolean isMovingStraight = mod90 < 1.0f || mod90 > 89.0f;
    if (isMovingStraight)
      return getRotationForStraightInput(player, movingYaw);
    return getRotationForDiagonalInput(movingYaw);
  }

  private static float[] getRotationForStraightInput(EntityPlayer player, float movingYaw) {
    if (player != null && player.onGround) {
      double rad = Math.toRadians(movingYaw);
      double cos = Math.cos(rad);
      double sin = Math.sin(rad);
      isOnRightSide = Math.floor(player.posX + cos * 0.5) != Math.floor(player.posX)
          || Math.floor(player.posZ + sin * 0.5) != Math.floor(player.posZ);

      int bx = MathHelper.floor_double(player.posX - Math.sin(rad) * 0.6);
      int bz = MathHelper.floor_double(player.posZ + Math.cos(rad) * 0.6);
      BlockPos below = new BlockPos(MathHelper.floor_double(player.posX),
          MathHelper.floor_double(player.posY) - 1,
          MathHelper.floor_double(player.posZ));
      BlockPos nextBelow = new BlockPos(bx, MathHelper.floor_double(player.posY) - 1, bz);
      boolean isLeaningOffBlock = ScaffoldMath.isReplaceable(below);
      boolean nextBlockIsAir = ScaffoldMath.isReplaceable(nextBelow);
      if (isLeaningOffBlock && nextBlockIsAir)
        isOnRightSide = !isOnRightSide;
    }
    float finalYaw = movingYaw + (isOnRightSide ? 45.0f : -45.0f);
    return new float[] {ScaffoldMath.quantize(finalYaw), ScaffoldMath.quantize(75.7f)};
  }

  private static float[] getRotationForDiagonalInput(float movingYaw) {
    return new float[] {ScaffoldMath.quantize(movingYaw), ScaffoldMath.quantize(75.6f)};
  }

  private static float[] getRotationForNoInput(float targetYaw) {
    float axisMovement = (float) Math.floor(targetYaw / 90.0f) * 90.0f;
    return new float[] {ScaffoldMath.quantize(axisMovement + 45.0f), ScaffoldMath.quantize(75.0f)};
  }

  /**
   * Apply GodBridge ledge when 1-tick sim {@code clipLedged} and current look cannot place.
   */
  public static void applyLedge(EntityPlayerSP player, World world, int modeMask, int blockCount,
                                int forceSneakBelow, boolean rotationsReady,
                                ScaffoldFeatures features) {
    if (player == null || world == null || modeMask == 0)
      return;
    if (blockCount < forceSneakBelow) {
      features.forceSneak = true;
      return;
    }
    float[] input = movementInput(player);
    ScaffoldSimulatedPlayer sim = ScaffoldSimulatedPlayer.fromClientPlayer(
        player, input[0], input[1], player.isSneaking());
    if (!sim.clipLedged)
      return;
    if (rotationsReady && blockCount > 0)
      return;
    if ((modeMask & LEDGE_SNEAK) != 0)
      features.forceSneak = true;
    if ((modeMask & LEDGE_JUMP) != 0)
      features.forceJump = true;
  }

  public static boolean wantsStopInput(int modeMask, EntityPlayerSP player, World world,
                                       boolean rotationsReady, int blockCount) {
    if ((modeMask & LEDGE_STOP) == 0 || player == null || world == null)
      return false;
    float[] input = movementInput(player);
    ScaffoldSimulatedPlayer sim = ScaffoldSimulatedPlayer.fromClientPlayer(
        player, input[0], input[1], false);
    return sim.clipLedged && (!rotationsReady || blockCount <= 0);
  }

  public static boolean wantsStepBack(int modeMask, EntityPlayerSP player, World world,
                                      boolean rotationsReady, int blockCount) {
    if ((modeMask & LEDGE_BACK) == 0 || player == null || world == null)
      return false;
    float[] input = movementInput(player);
    ScaffoldSimulatedPlayer sim = ScaffoldSimulatedPlayer.fromClientPlayer(
        player, input[0], input[1], false);
    return sim.clipLedged && (!rotationsReady || blockCount <= 0);
  }

  private static float[] movementInput(EntityPlayerSP player) {
    if (player == null || player.movementInput == null)
      return new float[] {0.0f, 0.0f};
    return new float[] {player.movementInput.moveForward, player.movementInput.moveStrafe};
  }
}
