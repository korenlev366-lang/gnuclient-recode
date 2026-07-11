package gnu.client.module.modules.player.scaffold.feature;

import gnu.client.module.modules.player.scaffold.ScaffoldMath;
import gnu.client.module.modules.player.scaffold.ScaffoldSimulatedPlayer;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.world.World;

/**
 * LiquidBounce scaffold feature state — Telly, Eagle, Down, SprintControl, Ledge, etc.
 */
public final class ScaffoldFeatures {
  public static final int TELLY_RESET = 0;
  public static final int TELLY_REVERSE = 1;

  public static final int SPRINT_DO_NOT_CHANGE = 0;
  public static final int SPRINT_FORCE = 1;
  public static final int SPRINT_FORCE_NO = 2;
  public static final int SPRINT_NO_ON_PLACE = 3;
  public static final int SPRINT_NO_ON_GROUND = 4;

  public static final int SAMEY_OFF = 0;
  public static final int SAMEY_ON = 1;
  public static final int SAMEY_FALLING = 2;
  public static final int SAMEY_HYPIXEL = 3;

  // Telly (LiquidBounce ScaffoldTellyFeature)
  public int tellyTicksUntilJump;
  public int tellyAirTicks;
  public boolean forceJump;

  // Eagle / ledge / down (LB ScaffoldEagleFeature: placedBlocks cycle)
  public boolean forceSneak;
  /** Blocks placed since last eagle reset — eagle only when == 0. */
  public int placedBlocks;
  /** Current rolled edge distance (LB refreshable float range). */
  public float eagleEdgeDistance = 0.05f;
  /** Current rolled blocks-to-eagle threshold. */
  public int eagleBlocksToEagle;
  public int sameYJumps;
  public int startY = 256;
  public int placementY = 256;

  // Sprint
  public boolean placedThisTick;
  /** Previous-tick place flag for NoSprintOnPlace (LB wasPlaced). */
  public boolean wasPlaced;

  public void reset() {
    tellyTicksUntilJump = 0;
    tellyAirTicks = 0;
    forceJump = false;
    forceSneak = false;
    placedBlocks = 0;
    eagleEdgeDistance = 0.05f;
    eagleBlocksToEagle = 0;
    sameYJumps = 0;
    placedThisTick = false;
    wasPlaced = false;
  }

  public void onEnable(EntityPlayerSP player) {
    reset();
    if (player != null) {
      startY = MathHelper.floor_double(player.posY);
      // LB: placementY = block below player
      placementY = startY - 1;
    }
  }

  /**
   * Ground/air counters — call after living {@code move} (LB {@code airTicks} on move RETURN).
   * {@code onGround → 0}, else {@code ++} (first airborne tick is 1, not 0).
   */
  public void updateTellyCounters(EntityPlayerSP player, boolean tellyEnabled, boolean towering) {
    if (!tellyEnabled || player == null || towering)
      return;
    if (player.onGround) {
      tellyAirTicks = 0;
      tellyTicksUntilJump++;
    } else {
      tellyAirTicks++;
    }
  }

  /**
   * Telly needs a fixed bridge Y. If SameY is Off, treat as On (LB telly + SameY).
   */
  public int effectiveSameY(int sameYMode, boolean tellyEnabled) {
    if (tellyEnabled && sameYMode == SAMEY_OFF)
      return SAMEY_ON;
    return sameYMode;
  }

  /** Block under the player at the locked bridge Y (SameY / telly). */
  public BlockPos bridgeCellUnder(EntityPlayerSP player) {
    if (player == null)
      return null;
    int bx = MathHelper.floor_double(player.posX);
    int bz = MathHelper.floor_double(player.posZ);
    return new BlockPos(bx, placementY, bz);
  }

  /**
   * Move-direction signs from motion, else from camera forward (MoveFix-safe enough for gaps).
   * Returns {@code int[]{sx, sz}} each in {-1,0,1}.
   */
  public int[] bridgeMoveSigns(EntityPlayerSP player) {
    if (player == null)
      return new int[] {0, 0};
    double mx = player.motionX;
    double mz = player.motionZ;
    if (Math.abs(mx) < 0.02 && Math.abs(mz) < 0.02) {
      float yaw = player.rotationYaw * (float) Math.PI / 180.0f;
      mx = -MathHelper.sin(yaw);
      mz = MathHelper.cos(yaw);
    }
    int sx = mx > 0.02 ? 1 : (mx < -0.02 ? -1 : 0);
    int sz = mz > 0.02 ? 1 : (mz < -0.02 ? -1 : 0);
    // Strafe-only / tiny axis: still cover the pressed axis via moveStrafing when available.
    if (sx == 0 && Math.abs(player.moveStrafing) > 0.01f) {
      float yaw = player.rotationYaw * (float) Math.PI / 180.0f;
      // strafe is along +yaw right: (cos, sin) in MC xz
      double smx = MathHelper.cos(yaw) * player.moveStrafing;
      sx = smx > 0.01 ? 1 : (smx < -0.01 ? -1 : 0);
    }
    if (sz == 0 && Math.abs(player.moveStrafing) > 0.01f && sx != 0) {
      // already handled via motion usually
    }
    return new int[] {sx, sz};
  }

  /**
   * Bridge cells for forward / diagonal: under, +X, +Z, and corner — in fill order
   * (cardinals before corner so searchInto can support the diagonal).
   */
  public java.util.List<BlockPos> bridgeCellsForMove(EntityPlayerSP player) {
    java.util.ArrayList<BlockPos> cells = new java.util.ArrayList<>(4);
    if (player == null)
      return cells;
    int bx = MathHelper.floor_double(player.posX);
    int bz = MathHelper.floor_double(player.posZ);
    int[] s = bridgeMoveSigns(player);
    int sx = s[0];
    int sz = s[1];
    cells.add(new BlockPos(bx, placementY, bz));
    if (sx != 0)
      cells.add(new BlockPos(bx + sx, placementY, bz));
    if (sz != 0)
      cells.add(new BlockPos(bx, placementY, bz + sz));
    if (sx != 0 && sz != 0)
      cells.add(new BlockPos(bx + sx, placementY, bz + sz));
    return cells;
  }

  /** Next bridge cell in move direction (sprint look-ahead) — may be the diagonal corner. */
  public BlockPos bridgeCellAhead(EntityPlayerSP player) {
    java.util.List<BlockPos> cells = bridgeCellsForMove(player);
    if (cells.isEmpty())
      return null;
    return cells.get(cells.size() - 1);
  }

  /** True if the bridge cell under the player is air (fall-through risk). */
  public boolean tellyUnderAir(EntityPlayerSP player) {
    BlockPos under = bridgeCellUnder(player);
    return under != null && ScaffoldMath.isReplaceable(under);
  }

  /**
   * Hold jump only when underfoot bridge is missing — never because ahead is empty
   * (telly is supposed to jump over ahead gaps).
   */
  public boolean tellyShouldHoldJump(EntityPlayerSP player, World world) {
    if (player == null || !player.onGround)
      return false;
    return tellyUnderAir(player);
  }

  /** True when moving on both axes (diagonal bridge / turn). */
  public boolean isDiagonalMove(EntityPlayerSP player) {
    int[] s = bridgeMoveSigns(player);
    return s[0] != 0 && s[1] != 0;
  }

  /**
   * Next diagonal fill cell only: side X / side Z / corner (not under).
   * Prefer a cell that already has cardinal support.
   */
  public BlockPos pickDiagonalStep(EntityPlayerSP player, BlockPos fallbackY) {
    if (player == null || !isDiagonalMove(player))
      return fallbackY;
    int y = fallbackY != null ? fallbackY.getY() : placementY;
    int bx = MathHelper.floor_double(player.posX);
    int bz = MathHelper.floor_double(player.posZ);
    int[] s = bridgeMoveSigns(player);
    BlockPos[] order = {
        new BlockPos(bx + s[0], y, bz),
        new BlockPos(bx, y, bz + s[1]),
        new BlockPos(bx + s[0], y, bz + s[1])
    };
    BlockPos bestUnsupported = null;
    for (BlockPos cell : order) {
      if (!ScaffoldMath.isReplaceable(cell))
        continue;
      if (hasCardinalSupport(cell))
        return cell;
      if (bestUnsupported == null)
        bestUnsupported = cell;
    }
    return bestUnsupported != null ? bestUnsupported : fallbackY;
  }

  /**
   * @deprecated use {@link #pickDiagonalStep} / underfoot sameY
   */
  public BlockPos pickBridgePlaceCell(EntityPlayerSP player, BlockPos fallbackY) {
    if (tellyUnderAir(player)) {
      BlockPos under = bridgeCellUnder(player);
      return under != null ? new BlockPos(under.getX(),
          fallbackY != null ? fallbackY.getY() : placementY, under.getZ()) : fallbackY;
    }
    if (isDiagonalMove(player))
      return pickDiagonalStep(player, fallbackY);
    return fallbackY;
  }

  private static boolean hasCardinalSupport(BlockPos place) {
    for (EnumFacing f : new EnumFacing[] {
        EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP, EnumFacing.DOWN}) {
      if (ScaffoldMath.isValidSupport(place.offset(f)))
        return true;
    }
    return false;
  }

  /**
   * LB {@code ScaffoldTellyFeature} jump: REVERSE always; RESET when looking
   * straight ({@code currentRotation == null || straightTicks == 0}) and jump delay met.
   * Landing while still in straight ({@code lookingStraight}) jumps immediately.
   */
  public void updateTellyJump(EntityPlayerSP player, boolean tellyEnabled, int jumpTicks,
                              int straightTicks, int resetMode, boolean towering,
                              boolean rotationActive, int blockCount, boolean underfootAir,
                              boolean lookingStraight) {
    if (!tellyEnabled || player == null || towering || forceJump)
      return;
    if (blockCount <= 0 || !player.onGround)
      return;
    if (!gnu.client.runtime.MoveFixUtil.isForwardPressed())
      return;
    if (underfootAir) {
      forceJump = true;
      tellyTicksUntilJump = 0;
      return;
    }
    if (resetMode == TELLY_REVERSE) {
      forceJump = true;
      tellyTicksUntilJump = 0;
      return;
    }
    boolean isStraight = lookingStraight || !rotationActive || Math.max(0, straightTicks) == 0;
    if (!isStraight)
      return;
    if (lookingStraight || tellyTicksUntilJump >= Math.max(0, jumpTicks)) {
      forceJump = true;
      tellyTicksUntilJump = 0;
    }
  }

  /**
   * LB {@code ScaffoldTellyFeature.doNotAim}:
   * {@code airTicks <= straight && ticksUntilJump >= jumpTicks && !(towering && aimOnTower)}.
   */
  public boolean tellyDoNotAimCompat(EntityPlayerSP player, boolean tellyEnabled, int straightTicks,
                                     int jumpTicks, boolean towering, boolean aimOnTower) {
    if (!tellyEnabled || player == null)
      return false;
    if (towering && aimOnTower)
      return false;
    return tellyAirTicks <= Math.max(0, straightTicks)
        && tellyTicksUntilJump >= Math.max(0, jumpTicks);
  }

  /**
   * LB {@code ScaffoldEagleFeature.shouldEagle}: sneak when {@code placedBlocks == 0}
   * and close to edge (not the old “N places then eagle” model).
   */
  public void updateEagle(EntityPlayerSP player, World world, boolean eagle, boolean onlyOnGround,
                          boolean downFalloff) {
    if (!eagle || player == null || world == null)
      return;
    if (wouldEagle(player, world, null, onlyOnGround, downFalloff))
      forceSneak = true;
  }

  /**
   * Whether eagle would sneak at {@code feet} (null = live player pos). Used for predicted crouch eye.
   */
  public boolean wouldEagle(EntityPlayerSP player, World world, net.minecraft.util.Vec3 feet,
                            boolean onlyOnGround, boolean downFalloff) {
    if (player == null || world == null)
      return false;
    if (downFalloff)
      return false;
    if (onlyOnGround && !player.onGround)
      return false;
    if (player.capabilities != null && player.capabilities.isFlying)
      return false;
    if (placedBlocks != 0)
      return false;
    float[] input = movementInput(player);
    return ScaffoldSimulatedPlayer.isCloseToEdge(player, world, input[0], input[1], eagleEdgeDistance,
        feet);
  }

  /**
   * Roll current eagle thresholds from min/max ranges (LB {@code asRefreshable}).
   */
  public void refreshEagle(int blocksMin, int blocksMax, float edgeMin, float edgeMax) {
    int bLo = Math.min(blocksMin, blocksMax);
    int bHi = Math.max(blocksMin, blocksMax);
    eagleBlocksToEagle = Mc.randomInt(bLo, bHi);
    float eLo = Math.min(edgeMin, edgeMax);
    float eHi = Math.max(edgeMin, edgeMax);
    eagleEdgeDistance = (float) Mc.randomDouble(eLo, eHi);
  }

  public void updateLedge(EntityPlayerSP player, World world, boolean ledge, float edgeDist,
                          boolean rotationsReady, int blockCount) {
    if (!ledge || player == null || world == null)
      return;
    // LiquidBounce ledge(): near edge + not ready / no blocks → sneak, never auto-jump.
    if (!nearEdge(player, world, edgeDist))
      return;
    if (!rotationsReady || blockCount <= 0)
      forceSneak = true;
  }

  public BlockPos sameYTarget(EntityPlayerSP player, int sameYMode, boolean towering,
                              boolean jumpBypassSameY) {
    if (player == null)
      return null;
    int bx = MathHelper.floor_double(player.posX);
    int bz = MathHelper.floor_double(player.posZ);
    // Prefer AABB feet for underfoot cell while falling / on edge.
    double feetY = player.getEntityBoundingBox() != null
        ? player.getEntityBoundingBox().minY - 0.01
        : player.posY - 1.0;
    int py = MathHelper.floor_double(player.posY);
    int underY = MathHelper.floor_double(feetY);
    if (player.onGround) {
      // LB: placementY = block below player
      placementY = py - 1;
      if (sameYMode == SAMEY_HYPIXEL)
        sameYJumps++;
    }
    int y;
    switch (sameYMode) {
      case SAMEY_ON:
        y = placementY;
        break;
      case SAMEY_FALLING:
        y = player.motionY < 0.2 ? placementY : underY;
        break;
      case SAMEY_HYPIXEL:
        if (Math.abs(player.motionY - (-0.15233518685055708)) < 1e-6 && sameYJumps >= 2) {
          sameYJumps = 0;
          y = startY;
        } else {
          y = startY - 1;
        }
        break;
      case SAMEY_OFF:
      default:
        y = underY;
        break;
    }
    // LB: jump bypasses SameY only when not moving (or wall) — tower, not telly.
    if (towering || jumpBypassSameY)
      y = underY;
    return new BlockPos(bx, y, bz);
  }

  public boolean shouldGoDown(boolean downEnabled, EntityPlayerSP player) {
    return downEnabled && player != null && player.isSneaking();
  }

  public BlockPos applyDown(BlockPos under, boolean down) {
    if (!down || under == null)
      return under;
    return under.down();
  }

  public BlockPos applyCeiling(EntityPlayerSP player, BlockPos under, boolean ceiling) {
    if (!ceiling || player == null || under == null)
      return under;
    BlockPos feet = new BlockPos(MathHelper.floor_double(player.posX),
        MathHelper.floor_double(player.posY) - 1,
        MathHelper.floor_double(player.posZ));
    if (!ScaffoldMath.isReplaceable(feet))
      return new BlockPos(under.getX(), under.getY() + 3, under.getZ());
    return under;
  }

  public void updateHeadHitter(EntityPlayerSP player, World world, boolean headHitter) {
    if (!headHitter || player == null || world == null || !player.onGround)
      return;
    BlockPos above = new BlockPos(MathHelper.floor_double(player.posX),
        MathHelper.floor_double(player.posY) + 2,
        MathHelper.floor_double(player.posZ));
    if (!ScaffoldMath.isReplaceable(above)
        && (Math.abs(player.moveForward) > 0.01f || Math.abs(player.moveStrafing) > 0.01f))
      forceJump = true;
  }

  public boolean shouldSuppressSprint(int clientMode, int serverMode, EntityPlayerSP player) {
    return shouldSuppressSprintMode(serverMode != SPRINT_DO_NOT_CHANGE ? serverMode : clientMode, player);
  }

  /** Client or server mode alone (WP10 split). Uses previous-tick {@link #wasPlaced}. */
  public boolean shouldSuppressSprintMode(int mode, EntityPlayerSP player) {
    switch (mode) {
      case SPRINT_FORCE_NO:
        return true;
      case SPRINT_NO_ON_PLACE:
        return wasPlaced;
      case SPRINT_NO_ON_GROUND:
        return player != null && player.onGround;
      case SPRINT_FORCE:
      case SPRINT_DO_NOT_CHANGE:
      default:
        return false;
    }
  }

  public boolean shouldForceSprint(int clientMode) {
    return clientMode == SPRINT_FORCE;
  }

  /** Call at tick start: promote placedThisTick → wasPlaced for next-tick NoSprintOnPlace. */
  public void advanceSprintPlaceFlag() {
    wasPlaced = placedThisTick;
    placedThisTick = false;
  }

  public void applyAcceleration(EntityPlayerSP player, boolean enabled, float multiplier) {
    if (!enabled || player == null)
      return;
    player.motionX *= multiplier;
    player.motionZ *= multiplier;
  }

  public void applyStrafe(EntityPlayerSP player, boolean enabled, float speed) {
    if (!enabled || player == null)
      return;
    float yaw = player.rotationYaw;
    double rad = Math.toRadians(yaw + 90.0f);
    float f = player.moveForward;
    float s = player.moveStrafing;
    if (f == 0 && s == 0)
      return;
    player.motionX = f * speed * Math.cos(rad) + s * speed * Math.sin(rad);
    player.motionZ = f * speed * Math.sin(rad) - s * speed * Math.cos(rad);
  }

  public boolean speedLimitBlocksInput(EntityPlayerSP player, boolean enabled, float limit) {
    if (!enabled || player == null)
      return false;
    double hs = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
    return hs > limit;
  }

  /**
   * After a successful place — sets {@code placedThisTick}. Eagle counter is
   * {@link #onEagleBlockPlacement}.
   */
  public void onPlaced() {
    placedThisTick = true;
  }

  /**
   * LB {@code ScaffoldEagleFeature.onBlockPlacement}: increment; when past threshold
   * reset to 0 and re-roll blocks/edge ranges.
   */
  public void onEagleBlockPlacement(boolean eagleEnabled, int blocksMin, int blocksMax,
                                    float edgeMin, float edgeMax) {
    if (!eagleEnabled)
      return;
    placedBlocks++;
    if (placedBlocks > eagleBlocksToEagle) {
      placedBlocks = 0;
      refreshEagle(blocksMin, blocksMax, edgeMin, edgeMax);
    }
  }

  public static boolean nearEdge(EntityPlayerSP player, World world, float edgeDist) {
    if (player == null || world == null)
      return false;
    float[] input = movementInput(player);
    return ScaffoldSimulatedPlayer.isCloseToEdge(player, world, input[0], input[1], edgeDist);
  }

  private static float[] movementInput(EntityPlayerSP player) {
    MovementInput mi = player.movementInput;
    if (mi != null)
      return new float[] {mi.moveForward, mi.moveStrafe};
    return new float[] {player.moveForward, player.moveStrafing};
  }
}
