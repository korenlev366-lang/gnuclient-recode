package gnu.client.module.modules.player.scaffold;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayDeque;

/**
 * LiquidBounce {@code ScaffoldMovementPrediction} — predict feet at place time via
 * optimal-line fall-off + placement-offset history (not one-tick motion).
 */
public final class ScaffoldMovementPrediction {
  private static final int MAX_PLACEMENT_OFFSETS = 4;

  private final ArrayDeque<Vec3> lastPlacementOffsets = new ArrayDeque<>(MAX_PLACEMENT_OFFSETS + 1);

  public void reset() {
    lastPlacementOffsets.clear();
  }

  /**
   * Record lateral offset of the player vs fall-off at place time (LB {@code onPlace}).
   */
  public void onPlace(EntityPlayerSP player, ScaffoldMovementPlanner planner, Vec3 fallOffPoint) {
    if (player == null || planner == null || fallOffPoint == null || !planner.hasLine())
      return;
    Vec3 dir = planner.lineDir();
    float lineDirAngle = (float) Math.atan2(dir.zCoord, dir.xCoord);
    Vec3 raw = new Vec3(player.posX - fallOffPoint.xCoord, player.posY - fallOffPoint.yCoord,
        player.posZ - fallOffPoint.zCoord);
    Vec3 unrotated = yRot(raw, lineDirAngle);
    lastPlacementOffsets.addLast(unrotated);
    while (lastPlacementOffsets.size() > MAX_PLACEMENT_OFFSETS)
      lastPlacementOffsets.removeFirst();
  }

  /**
   * @return predicted feet, or {@code null} to use current position (LB contract).
   */
  public Vec3 getPredictedPlacementPos(EntityPlayerSP player, World world,
                                       ScaffoldMovementPlanner planner,
                                       boolean enabled, float bootstrapBackoff,
                                       float predictionCutoff, int warmupPlacements) {
    if (!enabled || player == null || world == null || planner == null || !planner.hasLine())
      return null;

    if (isCloseToEdge(player, world, planner, predictionCutoff))
      return null;

    Vec3 fallOffPoint = getFallOffPositionOnLine(player, world, planner);
    if (fallOffPoint == null)
      return null;

    Vec3 playerPos = new Vec3(player.posX, player.posY, player.posZ);
    Vec3 fallOffToPlayer = new Vec3(fallOffPoint.xCoord - playerPos.xCoord,
        fallOffPoint.yCoord - playerPos.yCoord,
        fallOffPoint.zCoord - playerPos.zCoord);
    Vec3 bootstrapPos = bootstrapPlacementPos(fallOffPoint, fallOffToPlayer, bootstrapBackoff);

    Vec3 avg = averagePlacementOffset();
    if (avg == null) {
      if (planner.hasSupportOffset()) {
        return new Vec3(bootstrapPos.xCoord + planner.supportOffsetX(),
            bootstrapPos.yCoord,
            bootstrapPos.zCoord + planner.supportOffsetZ());
      }
      return bootstrapPos;
    }

    Vec3 dir = planner.lineDir();
    float lineDirAngle = (float) Math.atan2(dir.zCoord, dir.xCoord);
    Vec3 rotated = yRot(avg, -lineDirAngle);
    Vec3 predicted = new Vec3(fallOffPoint.xCoord + rotated.xCoord,
        fallOffPoint.yCoord + rotated.yCoord,
        fallOffPoint.zCoord + rotated.zCoord);

    double blend = warmupBlend(warmupPlacements);
    return lerp(bootstrapPos, predicted, blend);
  }

  public Vec3 getFallOffPositionOnLine(EntityPlayerSP player, World world,
                                       ScaffoldMovementPlanner planner) {
    if (player == null || world == null || planner == null || !planner.hasLine())
      return null;
    Vec3 nearest = planner.nearestPointOnLine(player.posX, player.posY, player.posZ);
    Vec3 dir = planner.lineDir();
    double len = Math.sqrt(dir.xCoord * dir.xCoord + dir.zCoord * dir.zCoord);
    if (len < 1.0e-6)
      return null;
    double nx = dir.xCoord / len;
    double nz = dir.zCoord / len;
    Vec3 fromLine = new Vec3(nearest.xCoord, nearest.yCoord - 0.1, nearest.zCoord);
    Vec3 toLine = new Vec3(fromLine.xCoord + nx * 3.0, fromLine.yCoord, fromLine.zCoord + nz * 3.0);
    Vec3 edge = ScaffoldEdgeCollision.findEdgeCollision(world, fromLine, toLine, 0.5f);
    if (edge == null)
      return null;
    return new Vec3(edge.xCoord, player.posY, edge.zCoord);
  }

  private boolean isCloseToEdge(EntityPlayerSP player, World world,
                                ScaffoldMovementPlanner planner, float cutoff) {
    double distance = Math.max(0.01, cutoff);
    float forward = player.moveForward;
    float strafe = player.moveStrafing;
    if (player.movementInput != null) {
      forward = player.movementInput.moveForward;
      strafe = player.movementInput.moveStrafe;
    }
    return ScaffoldSimulatedPlayer.isCloseToEdge(player, world, forward, strafe, distance);
  }

  private static Vec3 bootstrapPlacementPos(Vec3 fallOffPoint, Vec3 fallOffToPlayer,
                                            float bootstrapBackoff) {
    if (bootstrapBackoff <= 0.0f)
      return fallOffPoint;
    double len = Math.sqrt(fallOffToPlayer.xCoord * fallOffToPlayer.xCoord
        + fallOffToPlayer.yCoord * fallOffToPlayer.yCoord
        + fallOffToPlayer.zCoord * fallOffToPlayer.zCoord);
    if (len < 1.0e-6)
      return fallOffPoint;
    double s = bootstrapBackoff / len;
    return new Vec3(fallOffPoint.xCoord - fallOffToPlayer.xCoord * s,
        fallOffPoint.yCoord - fallOffToPlayer.yCoord * s,
        fallOffPoint.zCoord - fallOffToPlayer.zCoord * s);
  }

  private Vec3 averagePlacementOffset() {
    if (lastPlacementOffsets.isEmpty())
      return null;
    double sx = 0, sy = 0, sz = 0;
    int n = 0;
    for (Vec3 v : lastPlacementOffsets) {
      sx += v.xCoord;
      sy += v.yCoord;
      sz += v.zCoord;
      n++;
    }
    if (n == 0)
      return null;
    return new Vec3(sx / n, sy / n, sz / n);
  }

  private double warmupBlend(int warmupPlacements) {
    if (warmupPlacements <= 0)
      return 1.0;
    return Math.min(1.0, lastPlacementOffsets.size() / (double) warmupPlacements);
  }

  /** Mojang {@code Vec3.yRot} — yaw in radians around Y. */
  private static Vec3 yRot(Vec3 v, float yawRad) {
    float c = (float) Math.cos(yawRad);
    float s = (float) Math.sin(yawRad);
    return new Vec3(v.xCoord * c + v.zCoord * s, v.yCoord, v.zCoord * c - v.xCoord * s);
  }

  private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
    if (t <= 0.0)
      return a;
    if (t >= 1.0)
      return b;
    return new Vec3(
        a.xCoord + (b.xCoord - a.xCoord) * t,
        a.yCoord + (b.yCoord - a.yCoord) * t,
        a.zCoord + (b.zCoord - a.zCoord) * t);
  }
}
