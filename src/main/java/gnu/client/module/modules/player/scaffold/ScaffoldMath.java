package gnu.client.module.modules.player.scaffold;

import gnu.client.utility.IMinecraftInstance;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Shared 1.8.9 math / world helpers for Scaffold. */
public final class ScaffoldMath implements IMinecraftInstance {
  public static final int FACE_DOWN = 0;
  public static final int FACE_UP = 1;
  public static final int FACE_NORTH = 2;
  public static final int FACE_SOUTH = 3;
  public static final int FACE_WEST = 4;
  public static final int FACE_EAST = 5;

  private ScaffoldMath() {}

  private static Minecraft mc() {
    return Minecraft.getMinecraft();
  }

  public static boolean isReplaceable(BlockPos pos) {
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

  public static boolean isValidSupport(BlockPos blockPos) {
    return blockPos != null && !isReplaceable(blockPos);
  }

  public static boolean isPlacementTargetClear(BlockPos support, int faceOrdinal) {
    EnumFacing face = enumFacing(faceOrdinal);
    return face != null && isReplaceable(support.offset(face));
  }

  /**
   * True if placing against {@code support} on {@code faceOrdinal} would put a full-cube
   * block inside the player's body.
   * <p>Underfoot / tower UP into the feet cell is always allowed — the player AABB overlaps
   * that cell while standing/jumping on it; rejecting it stopped tower from placing up.
   */
  public static boolean wouldIntersectPlayer(EntityPlayer player, BlockPos support, int faceOrdinal) {
    if (player == null || support == null)
      return false;
    EnumFacing facing = enumFacing(faceOrdinal);
    if (facing == null)
      return false;
    BlockPos placed = support.offset(facing);
    // Floor / tower UP at or below the player's foot block — always legal.
    if (facing == EnumFacing.UP) {
      int footY = MathHelper.floor_double(player.posY);
      if (placed.getY() <= footY)
        return false;
    }
    AxisAlignedBB placedBox = new AxisAlignedBB(
        placed.getX(), placed.getY(), placed.getZ(),
        placed.getX() + 1.0, placed.getY() + 1.0, placed.getZ() + 1.0);
    AxisAlignedBB playerBox = player.getEntityBoundingBox();
    if (playerBox == null)
      return false;
    if (placedBox.maxY <= playerBox.minY + 0.05)
      return false;
    playerBox = playerBox.expand(0.02, 0.0, 0.02);
    return playerBox.intersectsWith(placedBox);
  }

  /**
   * Grim {@code PositionPlace} — eye must be on the outside of the clicked face
   * (not placing against a hidden/back face).
   * <p>UP into the underfoot column is always treated as visible (tower / scaffold floor).
   */
  public static boolean canSeePlaceFace(EntityPlayer player, BlockPos support, int faceOrdinal) {
    if (player == null || support == null)
      return false;
    EnumFacing face = enumFacing(faceOrdinal);
    if (face == null)
      return false;
    // Tower/scaffold floor: standing/jumping on the column — always "see" UP.
    if (face == EnumFacing.UP) {
      int bx = MathHelper.floor_double(player.posX);
      int bz = MathHelper.floor_double(player.posZ);
      BlockPos placed = support.offset(EnumFacing.UP);
      if (placed.getX() == bx && placed.getZ() == bz)
        return true;
    }
    double eyeY = player.posY + player.getEyeHeight();
    double ex = player.posX;
    double ez = player.posZ;
    double minX = support.getX();
    double minY = support.getY();
    double minZ = support.getZ();
    double maxX = minX + 1.0;
    double maxY = minY + 1.0;
    double maxZ = minZ + 1.0;
    if (ex >= minX && ex <= maxX && eyeY >= minY && eyeY <= maxY && ez >= minZ && ez <= maxZ)
      return true;
    switch (face) {
      case NORTH:
        return ez <= minZ + 0.25;
      case SOUTH:
        return ez >= maxZ - 0.25;
      case WEST:
        return ex <= minX + 0.25;
      case EAST:
        return ex >= maxX - 0.25;
      case UP:
        return eyeY >= maxY - 0.25;
      case DOWN:
        return eyeY <= minY + 0.25;
      default:
        return false;
    }
  }

  public static EnumFacing enumFacing(int ordinal) {
    EnumFacing[] values = EnumFacing.values();
    if (ordinal < 0 || ordinal >= values.length)
      return null;
    return values[ordinal];
  }

  public static Vec3 faceHitVec(BlockPos pos, int faceOrdinal) {
    if (pos == null)
      return null;
    double x = pos.getX() + 0.5;
    double y = pos.getY() + 0.5;
    double z = pos.getZ() + 0.5;
    switch (faceOrdinal) {
      case FACE_DOWN: y = pos.getY(); break;
      case FACE_UP: y = pos.getY() + 1.0; break;
      case FACE_NORTH: z = pos.getZ(); break;
      case FACE_SOUTH: z = pos.getZ() + 1.0; break;
      case FACE_WEST: x = pos.getX(); break;
      case FACE_EAST: x = pos.getX() + 1.0; break;
      default: break;
    }
    return new Vec3(x, y, z);
  }

  public static float wrapAngle(float angle) {
    return MathHelper.wrapAngleTo180_float(angle);
  }

  /**
   * Map a desired yaw (often ±180-wrapped from atan2 / movementFacing) onto {@code baseYaw}
   * without collapsing the absolute yaw into ±180. Prevents Grim AimModulo360
   * ({@code |Δyaw| > 320} while {@code |yaw| < 360} after a small previous delta).
   */
  public static float unwrapYaw(float baseYaw, float desiredYaw) {
    return baseYaw + wrapAngle(desiredYaw - baseYaw);
  }

  public static float clampPitch(float pitch) {
    return MathHelper.clamp_float(pitch, -90.0f, 90.0f);
  }

  public static double mouseGcd() {
    float sens = mc().gameSettings.mouseSensitivity;
    float f = sens * 0.6f + 0.2f;
    return f * f * f * 1.2f;
  }

  public static float quantize(float angle) {
    double gcd = mouseGcd();
    double step = gcd > 0.0 ? gcd : 0.0096;
    return (float) (angle - angle % step);
  }

  /**
   * LiquidBounce {@code Rotation.normalize}: round Δyaw/Δpitch to mouse GCD from a base look.
   */
  public static float[] normalizeFrom(float baseYaw, float basePitch, float yaw, float pitch) {
    double gcd = mouseGcd();
    double step = gcd > 0.0 ? gcd : 0.0096;
    float dYaw = wrapAngle(yaw - baseYaw);
    float dPitch = pitch - basePitch;
    float g1 = (float) (Math.round(dYaw / step) * step);
    float g2 = (float) (Math.round(dPitch / step) * step);
    return new float[] {baseYaw + g1, clampPitch(basePitch + g2)};
  }

  /**
   * Eye used for aim/ray validation — prefers {@link ScaffoldTargetFinding#getEyePos()}
   * (predicted crouch/stand) when set, else live standing eye.
   */
  public static Vec3 eyePos(EntityPlayer player) {
    Vec3 override = ScaffoldTargetFinding.getEyePos();
    if (override != null)
      return override;
    if (player == null)
      return new Vec3(0.0, 0.0, 0.0);
    return new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
  }

  public static MovingObjectPosition rayTrace(float yaw, float pitch, float reach) {
    EntityPlayer player = mc().thePlayer;
    World world = mc().theWorld;
    if (player == null || world == null)
      return null;
    Vec3 start = eyePos(player);
    float yawRad = (float) Math.toRadians(yaw);
    float pitchRad = (float) Math.toRadians(pitch);
    float cosPitch = MathHelper.cos(pitchRad);
    float dx = -MathHelper.sin(yawRad) * cosPitch;
    float dy = -MathHelper.sin(pitchRad);
    float dz = MathHelper.cos(yawRad) * cosPitch;
    Vec3 end = start.addVector(dx * reach, dy * reach, dz * reach);
    return world.rayTraceBlocks(start, end, false, false, false);
  }

  public static float[] rotationsTo(Vec3 point, EntityPlayer player, float baseYaw, float basePitch) {
    Vec3 eye = eyePos(player);
    double dx = point.xCoord - eye.xCoord;
    double dy = point.yCoord - eye.yCoord;
    double dz = point.zCoord - eye.zCoord;
    double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
    float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
    float pitch = (float) (-(Math.atan2(dy, dist) * 180.0 / Math.PI));
    yaw = quantize(baseYaw + wrapAngle(yaw - baseYaw));
    pitch = quantize(clampPitch(basePitch + wrapAngle(pitch - basePitch)));
    return new float[] {yaw, pitch};
  }

  public static boolean matchesBlock(MovingObjectPosition mop, BlockPos pos) {
    return mop != null
        && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
        && mop.getBlockPos() != null
        && mop.getBlockPos().equals(pos);
  }

  public static boolean matchesFace(MovingObjectPosition mop, BlockPos pos, EnumFacing face) {
    return matchesBlock(mop, pos) && mop.sideHit == face;
  }

  public static Vec3 findPlacementHit(EntityPlayer player, BlockPos support, int faceOrdinal,
                                       float yaw, float pitch) {
    if (support == null || player == null)
      return null;
    EnumFacing face = enumFacing(faceOrdinal);
    if (face == null)
      return null;
    float reach = mc().playerController != null
        ? mc().playerController.getBlockReachDistance() : 4.5f;
    MovingObjectPosition mop = rayTrace(yaw, pitch, reach);
    // Must hit the intended face — any-face match places client-side then Grim rejects (ghost).
    if (matchesFace(mop, support, face))
      return mop.hitVec;
    return null;
  }
}
