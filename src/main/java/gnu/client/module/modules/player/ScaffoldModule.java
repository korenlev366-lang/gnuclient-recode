package gnu.client.module.modules.player;

import gnu.client.mixin.impl.accessors.IAccessorMinecraft;
import gnu.client.mixin.impl.accessors.IAccessorTimer;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.PlayerUpdateHook;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.ScaffoldItemSpoofHook;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.utility.IMinecraftInstance;
import gnu.client.utility.PacketUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OpenMyau-style 1.8.9 scaffold — direct Forge MCP APIs (no McAccess reflection).
 *
 * <p>Place contract: getBlockData → aim → snapMovementFacingYaw → applyRotation →
 * place when {@code blockData != null && rotationTick <= 0} only if the
 * <b>sent</b> look ray hits the support (Grim RotationPlace). Deferred place via
 * {@link #beforeWalkingPlace} keeps C08 before C03.
 */
public final class ScaffoldModule extends Module implements PacketListener, IMinecraftInstance {

  private static final int ROT_NONE = 0;
  private static final int ROT_DEFAULT = 1;
  private static final int ROT_BACKWARDS = 2;
  private static final int ROT_SIDEWAYS = 3;
  private static final int ROT_GODBRIDGE = 4;
  private static final int ROT_SMOOTH = 5;

  private static final int MOVEFIX_NONE = 0;
  private static final int MOVEFIX_SILENT = 1;

  private static final int TELLY_RESET = 0;
  private static final int TELLY_REVERSE = 1;

  private static final int SPRINT_NONE = 0;
  private static final int SPRINT_VANILLA = 1;

  private static final int TOWER_NONE = 0;
  private static final int TOWER_MOTION = 1;
  private static final int TOWER_PULLDOWN = 2;
  private static final int TOWER_KARHU = 3;
  private static final int TOWER_VULCAN = 4;
  private static final int TOWER_HYPIXEL = 5;

  private static final int KEEPY_NONE = 0;
  private static final int KEEPY_VANILLA = 1;
  private static final int KEEPY_EXTRA = 2;
  private static final int KEEPY_TELLY = 3;

  private static final float ROTATION_PRIORITY = 3.0f;

  private static volatile boolean forceSneak;
  private static volatile boolean forceJump;

  private final ModeSetting rotationMode = addSetting(new ModeSetting("rotations", ROT_BACKWARDS,
      Arrays.asList("NONE", "DEFAULT", "BACKWARDS", "SIDEWAYS", "GODBRIDGE", "SMOOTH")));
  // Default 180 = instant snap (OpenMyau-like). Lower values step and delay place via rotationTick.
  private final SliderSetting rotationSpeed = addSetting(new SliderSetting("rotation-speed", 180.0f, 1.0f, 180.0f));
  private final ModeSetting moveFix = addSetting(new ModeSetting("move-fix", MOVEFIX_SILENT,
      Arrays.asList("NONE", "SILENT")));
  private final ModeSetting sprintMode = addSetting(new ModeSetting("sprint", SPRINT_NONE,
      Arrays.asList("NONE", "VANILLA")));
  private final SliderSetting groundMotion = addSetting(new SliderSetting("ground-motion", 100.0f, 0.0f, 100.0f));
  private final SliderSetting airMotion = addSetting(new SliderSetting("air-motion", 100.0f, 0.0f, 100.0f));
  private final SliderSetting speedMotion = addSetting(new SliderSetting("speed-motion", 100.0f, 0.0f, 100.0f));
  private final BoolSetting telly = addSetting(new BoolSetting("telly", false));
  private final ModeSetting tellyResetMode = addSetting(new ModeSetting("telly-reset", TELLY_RESET,
      Arrays.asList("RESET", "REVERSE")));
  private final SliderSetting tellyStraightTicks = addSetting(new SliderSetting("telly-straight", 3.0f, 0.0f, 5.0f));
  private final SliderSetting tellyJumpTicks = addSetting(new SliderSetting("telly-jump", 0.0f, 0.0f, 10.0f));
  private final ModeSetting tower = addSetting(new ModeSetting("tower", TOWER_NONE,
      Arrays.asList("NONE", "MOTION", "PULLDOWN", "KARHU", "VULCAN", "HYPIXEL")));
  private final SliderSetting towerMotion = addSetting(new SliderSetting("tower-motion", 0.42f, 0.0f, 1.0f));
  private final SliderSetting towerTriggerHeight = addSetting(new SliderSetting("tower-trigger-height", 0.78f, 0.76f, 1.0f));
  private final SliderSetting towerSlow = addSetting(new SliderSetting("tower-slow", 1.0f, 0.0f, 3.0f));
  private final SliderSetting towerPulldownTrigger = addSetting(new SliderSetting("tower-pulldown-trigger", 0.1f, 0.0f, 0.2f));
  private final SliderSetting towerKarhuTimer = addSetting(new SliderSetting("tower-karhu-timer", 5.0f, 0.1f, 10.0f));
  private final SliderSetting towerKarhuTrigger = addSetting(new SliderSetting("tower-karhu-trigger", 0.06f, 0.0f, 0.2f));
  private final BoolSetting towerKarhuPulldown = addSetting(new BoolSetting("tower-karhu-pulldown", true));
  private final ModeSetting keepY = addSetting(new ModeSetting("keep-y", KEEPY_NONE,
      Arrays.asList("NONE", "VANILLA", "EXTRA", "TELLY")));
  private final BoolSetting keepYOnPress = addSetting(new BoolSetting("keep-y-on-press", false));
  private final BoolSetting multiPlace = addSetting(new BoolSetting("multi-place", false));
  private final BoolSetting safeWalk = addSetting(new BoolSetting("safe-walk", true));
  private final BoolSetting swing = addSetting(new BoolSetting("swing", false));
  private final BoolSetting itemSpoof = addSetting(new BoolSetting("item-spoof", false));
  private final BoolSetting blockCounter = addSetting(new BoolSetting("block-counter", true));

  private int rotationTick;
  private int lastSlot = -1;
  /** Hotbar slot scaffold places blocks from (may differ from client slot when item-spoof is on). */
  private int blockSlot = -1;
  /** Last hotbar index we told the server via C09 (dedupes BadPackets duplicate slot). */
  private int serverReportedSlot = -1;
  /** Client slot switch queued for C09 immediately before the next placement packet. */
  private int pendingServerSlot = -1;
  /** True while scaffold is sending its own C09 (do not treat as user hotbar scroll). */
  private boolean sendingServerSlot;
  private int blockCount = -1;
  private float yaw = -180.0f;
  private float pitch = 0.0f;
  private boolean canRotate;
  private boolean towering;
  private boolean towerWasOnGround = true;
  private double towerJumpOffY = Double.NaN;
  private int towerAirTicks;
  private boolean towerKarhuTimerActive;
  private boolean towerPulldownPending;
  private boolean wasTowerJump;
  private int stage;
  private int startY = 256;
  private boolean shouldKeepY;
  private int targetFacing = -1;
  private int scaffoldCycleTick = -1;
  private int tellyTicksUntilJump;
  private int tellyAirTicks;
  private boolean tellyStraightJumpActive;
  private boolean tellyAwaitingPlacement;
  private boolean tellyWasOnGround;
  private boolean tellyDoNotAimActive;
  private boolean tellyDoNotAimWasActive;
  private boolean tellyRotTransition;
  private World cycleWorld;
  private boolean cyclePrioritizePlacement;
  private boolean cycleAllowTellyAirPlacement;
  private float lastSentYaw = Float.MIN_VALUE;
  /** Last silent yaw/pitch actually sent — used as smooth-rotation base (not lastReportedYaw). */
  private float steppedServerYaw = Float.NaN;
  private float steppedServerPitch = Float.NaN;
  private ScaffoldPlacement.BlockData pendingBlockData;
  private Vec3 pendingHit;
  private boolean pendingPlace;
  /** Yaw/pitch used when {@link #pendingHit} was queued (must match place-time lastSent). */
  private float pendingHitYaw = Float.MIN_VALUE;
  private float pendingHitPitch;
  private float lastSentPitch;
  private float prevSentYaw = Float.MIN_VALUE;
  private float prevSentPitch;
  private float tickRotDeltaYaw;
  private float tickRotDeltaPitch;
  private float lastPlaceRotDeltaYaw = -1.0f;
  private float lastPlaceRotDeltaPitch = -1.0f;
  private float rotationTargetYaw;
  private float rotationTargetPitch;
  private int placementsThisTick;
  private boolean hasExactBlockAim;

  public ScaffoldModule() {
    super("Scaffold", "Automatically place blocks under you", Category.PLAYER);
  }

  @Override
  public void onEnable() {
    EntityPlayerSP player = mc.thePlayer;
    lastSlot = player == null ? -1 : player.inventory.currentItem;
    serverReportedSlot = lastSlot;
    blockSlot = -1;
    pendingServerSlot = -1;
    blockCount = -1;
    rotationTick = 0;
    yaw = -180.0f;
    pitch = 0.0f;
    canRotate = false;
    resetTowerState();
    stage = 0;
    startY = player == null ? 256 : MathHelper.floor_double(player.posY);
    shouldKeepY = false;
    towering = false;
    targetFacing = -1;
    forceSneak = false;
    forceJump = false;
    tellyDoNotAimActive = false;
    tellyDoNotAimWasActive = false;
    tellyRotTransition = false;
    prevSentYaw = Float.MIN_VALUE;
    lastPlaceRotDeltaYaw = -1.0f;
    lastPlaceRotDeltaPitch = -1.0f;
    RotationState.reset();
    tellyTicksUntilJump = 0;
    tellyAirTicks = 0;
    tellyStraightJumpActive = false;
    tellyAwaitingPlacement = false;
    tellyWasOnGround = true;
    scaffoldCycleTick = -1;
    steppedServerYaw = Float.NaN;
    steppedServerPitch = Float.NaN;
    clearPendingPlacement();
    PacketEvents.register(this);
  }

  @Override
  public void onDisable() {
    EntityPlayerSP player = mc.thePlayer;
    if (player != null && lastSlot >= 0 && lastSlot <= 8) {
      player.inventory.currentItem = lastSlot;
      notifyServerSlot(lastSlot);
      player.setSneaking(false);
    }
    serverReportedSlot = -1;
    blockSlot = -1;
    pendingServerSlot = -1;
    forceSneak = false;
    forceJump = false;
    tellyDoNotAimActive = false;
    tellyDoNotAimWasActive = false;
    tellyRotTransition = false;
    prevSentYaw = Float.MIN_VALUE;
    lastPlaceRotDeltaYaw = -1.0f;
    lastPlaceRotDeltaPitch = -1.0f;
    tellyStraightJumpActive = false;
    tellyAwaitingPlacement = false;
    tellyWasOnGround = true;
    scaffoldCycleTick = -1;
    steppedServerYaw = Float.NaN;
    steppedServerPitch = Float.NaN;
    RotationState.reset();
    clearPendingPlacement();
    resetTowerState();
    setTimerSpeed(1.0f);
    PacketEvents.unregister(this);
  }

  @Override
  public void onTick() {
    // Scaffold runs from PlayerUpdateHook.onPreUpdate (before walking packets).
  }

  private void runScaffoldCycle(boolean deferPlacement) {
    if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
      scaffoldCycleTick = -1;
      forceSneak = false;
      forceJump = false;
      clearPendingPlacement();
      clearRotationState();
      resetSteppedRotation();
      RotationState.applyState(false, 0.0f, 0.0f, 0.0f, -1);
      return;
    }

    EntityPlayerSP player = mc.thePlayer;
    WorldClient world = mc.theWorld;

    int tick = player.ticksExisted;
    if (tick == scaffoldCycleTick)
      return;
    scaffoldCycleTick = tick;
    placementsThisTick = 0;
    hasExactBlockAim = false;

    cycleWorld = world;
    clearPendingPlacement();
    clearRotationState();

    if (rotationTick > 0)
      rotationTick--;

    updateKeepY(player);
    guardClientHotbarSlot(player);
    updateBlockSlot(player);
    updateTelly(player);
    applyTower(player, world);
    boolean towerJump = isTowerJumpActive(player);
    boolean tellyDoNotAim = !towerJump && isTellyDoNotAim(player);
    boolean prioritizePlacement = shouldPrioritizePlacement(player, tellyDoNotAim);
    cyclePrioritizePlacement = prioritizePlacement;
    boolean allowTellyAirPlacement = shouldAllowTellyAirPlacement(player, prioritizePlacement, tellyDoNotAim);
    cycleAllowTellyAirPlacement = allowTellyAirPlacement;
    if (telly.getValue() && tellyDoNotAim != tellyDoNotAimWasActive) {
      tellyRotTransition = true;
      if (tellyDoNotAim)
        rotationTick = Math.max(rotationTick, 1);
      else if (!shouldSmoothRotation())
        rotationTick = 0;
    }
    tellyDoNotAimWasActive = tellyDoNotAim;
    tellyDoNotAimActive = tellyDoNotAim;
    if (tellyDoNotAim && !allowTellyAirPlacement)
      prepareTellyRotation(player);
    else
      prepareBaseRotation(player);

    ScaffoldPlacement.BlockData blockData =
        ScaffoldPlacement.getBlockData(player, world, startY, stage, shouldKeepY);
    ScaffoldPlacement.AimData aimData = null;
    if (blockData != null && (!tellyDoNotAim || allowTellyAirPlacement)) {
      aimData = resolveBlockAim(player, blockData, pitch);
      if (aimData != null) {
        yaw = aimData.yaw;
        pitch = aimData.pitch;
        hasExactBlockAim = true;
        if (rotationMode.getValue() != ROT_NONE)
          canRotate = true;
      } else if (rotationMode.getValue() != ROT_NONE && canRotate) {
        // Aim grid missed (common while walking forward after snap) — still aim at
        // face-center so we have a place hitVec like OpenMyau getClickVec fallback.
        aimData = ScaffoldPlacement.aimAtFaceCenter(blockData, yaw, pitch);
        if (aimData != null) {
          yaw = aimData.yaw;
          pitch = aimData.pitch;
          hasExactBlockAim = false;
        }
      }
    }

    // OpenMyau parity: snap after block aim so movement-facing yaw wins over precise aim.
    if (!towerJump && (!tellyDoNotAim || allowTellyAirPlacement))
      snapMovementFacingYaw(player);

    applyRotation(player);
    applySafeWalk(player, world);

    queuePlacement(player, blockData, towerJump, aimData, deferPlacement, prioritizePlacement,
        allowTellyAirPlacement);

    applySprint(player);

    if (targetFacing >= 0 && rotationTick <= 0 && placementsThisTick < maxPlacementsPerTick()) {
      int bx = MathHelper.floor_double(player.posX);
      int by = MathHelper.floor_double(player.posY) - 1;
      int bz = MathHelper.floor_double(player.posZ);
      BlockPos pos = new BlockPos(bx, by, bz);
      Vec3 hit = resolveHitForPlace(pos, targetFacing, null);
      if (hit != null)
        executePlacement(player, pos, targetFacing, hit, deferPlacement);
      targetFacing = -1;
    }
  }

  private void queuePlacement(EntityPlayerSP player, ScaffoldPlacement.BlockData blockData,
                              boolean towerJump, ScaffoldPlacement.AimData aimData,
                              boolean deferPlacement, boolean prioritizePlacement,
                              boolean allowTellyAirPlacement) {
    // OpenMyau: place when rotationTick <= 0 (not the stricter rotationSyncActive).
    if (blockData == null || !canPlaceThisTick(player, towerJump, allowTellyAirPlacement)
        || (rotationTick > 0 && !towerJump)
        || !isHoldingPlaceable(player))
      return;

    Vec3 hit = resolvePlacementHit(blockData, aimData);
    if (hit == null)
      return;
    executePlacement(player, blockData.blockPos, blockData.faceOrdinal, hit, deferPlacement);
  }

  private void executePlacement(EntityPlayerSP player, BlockPos blockPos, int faceOrdinal,
                                Vec3 hit, boolean deferPlacement) {
    if (deferPlacement) {
      pendingBlockData = new ScaffoldPlacement.BlockData(blockPos, faceOrdinal);
      pendingHit = hit;
      pendingHitYaw = lastSentYaw;
      pendingHitPitch = lastSentPitch;
      pendingPlace = true;
      return;
    }
    tryPlace(player, blockPos, faceOrdinal, hit);
  }

  private void clearPendingPlacement() {
    pendingBlockData = null;
    pendingHit = null;
    pendingHitYaw = Float.MIN_VALUE;
    pendingPlace = false;
  }

  /** Kept for PlayerUpdateHook compatibility. */
  public static void onPreUpdate(Object player) {
    gnu.client.module.Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (module instanceof ScaffoldModule && module.isEnabled())
      ((ScaffoldModule) module).preUpdate();
  }

  private void preUpdate() {
    runScaffoldCycle(true);
  }

  /**
   * OpenMyau PRE parity: place after silent rotation is applied to the entity but
   * before {@code onUpdateWalkingPlayer} sends the flying packet (C08 then C03).
   */
  public static void onBeforeWalkingPlace(Object player) {
    gnu.client.module.Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (module instanceof ScaffoldModule && module.isEnabled())
      ((ScaffoldModule) module).beforeWalkingPlace();
  }

  private void beforeWalkingPlace() {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null)
      return;
    flushPendingServerSlot(player);
    if (pendingPlace && pendingBlockData != null) {
      int faceOrdinal = pendingBlockData.faceOrdinal;
      BlockPos blockPos = pendingBlockData.blockPos;
      Vec3 hit = pendingHit;
      clearPendingPlacement();
      if (isHoldingPlaceable(player))
        tryPlace(player, blockPos, faceOrdinal, hit);
    }
    if ((!multiPlace.getValue()) || !isHoldingPlaceable(player) || blockCount <= 0)
      return;
    for (int i = 0; i < 2; i++) {
      if (placementsThisTick >= maxPlacementsPerTick())
        break;
      ScaffoldPlacement.BlockData blockData = ScaffoldPlacement.getBlockData(
          player, cycleWorld, startY, stage, shouldKeepY);
      if (blockData == null)
        break;
      Vec3 hit = resolvePlacementHit(blockData, null);
      if (hit == null)
        break;
      tryPlace(player, blockData.blockPos, blockData.faceOrdinal, hit);
    }
  }

  private int maxPlacementsPerTick() {
    return multiPlace.getValue() ? 3 : 1;
  }

  private void updateKeepY(EntityPlayerSP player) {
    if (player.onGround) {
      boolean keepYAllowed = keepY.getValue() != KEEPY_NONE
          && (!keepYOnPress.getValue() || isUseItemKeyDown())
          && !mc.gameSettings.keyBindJump.isKeyDown();
      boolean preserveTellyKeepY = telly.getValue() && isTellyMoving() && keepYAllowed && stage > 0;

      if (stage > 0 && !preserveTellyKeepY)
        stage--;
      else if (stage < 0)
        stage++;

      boolean canStartKeepY = stage == 0 && keepYAllowed;
      if (canStartKeepY)
        stage = 1;

      if (!shouldKeepY && !preserveTellyKeepY)
        startY = MathHelper.floor_double(player.posY);
      shouldKeepY = false;
      towering = false;
    }
  }

  private void updateTelly(EntityPlayerSP player) {
    if (!telly.getValue()) {
      forceJump = false;
      tellyTicksUntilJump = 0;
      tellyAirTicks = 0;
      tellyStraightJumpActive = false;
      return;
    }

    boolean onGround = player.onGround;
    if (onGround) {
      tellyWasOnGround = true;
      tellyAirTicks = 0;
      tellyTicksUntilJump++;
    } else {
      tellyWasOnGround = false;
      tellyAirTicks++;
      if (tellyStraightJumpActive && tellyAirTicks > tellyStraightTicks())
        tellyAwaitingPlacement = true;
      forceJump = false;
    }

    if (!isTellyMoving() || !hasTellyBlocks(player) || isTowering(player)) {
      forceJump = false;
      return;
    }

    if (onGround && tellyTicksUntilJump >= tellyJumpTicks()) {
      forceJump = true;
      tellyTicksUntilJump = 0;
      tellyStraightJumpActive = true;
      tellyAwaitingPlacement = false;
    } else if (onGround) {
      forceJump = false;
    }
  }

  /**
   * LiquidBounce {@code ScaffoldTellyFeature.doNotAim}: forward look once jump delay is met,
   * then keep it through {@code telly-straight} air ticks after a telly jump.
   */
  private boolean isTellyDoNotAim(EntityPlayerSP player) {
    if (!telly.getValue() || !isTellyMoving() || !hasTellyBlocks(player) || isTowering(player))
      return false;
    if (!tellyStraightJumpActive)
      return false;
    if (player.onGround) {
      if (tellyAwaitingPlacement)
        return false;
      return tellyTicksUntilJump >= tellyJumpTicks();
    }
    return tellyAirTicks <= tellyStraightTicks();
  }

  private void prepareTellyRotation(EntityPlayerSP player) {
    if (tellyResetMode.getValue() == TELLY_REVERSE) {
      yaw = ScaffoldPlacement.quantize(Math.round(player.rotationYaw / 45.0f) * 45.0f);
      pitch = ScaffoldPlacement.quantize(Math.max(45.0f, player.rotationPitch));
      canRotate = true;
    } else {
      yaw = ScaffoldPlacement.quantize(currentMoveYaw(player));
      pitch = ScaffoldPlacement.quantize(0.0f);
      canRotate = true;
    }
  }

  private void updateBlockSlot(EntityPlayerSP player) {
    if (player == null)
      return;

    int searchSlot = blockSlot >= 0 && blockSlot <= 8
        ? blockSlot
        : player.inventory.currentItem;
    ItemStack stack = player.inventory.getStackInSlot(searchSlot);
    int count = isPlaceableStack(stack) ? stack.stackSize : 0;
    // -1 means "uninitialized" (OpenMyau); don't Math.min it into a permanent -1.
    if (blockCount < 0)
      blockCount = count;
    else
      blockCount = Math.min(blockCount, count);
    if (blockCount > 0) {
      selectBlockSlot(player, searchSlot);
      return;
    }

    int slot = searchSlot;
    if (blockCount == 0)
      slot--;
    for (int i = slot; i > slot - 9; i--) {
      int hotbarSlot = (i % 9 + 9) % 9;
      ItemStack candidate = player.inventory.getStackInSlot(hotbarSlot);
      if (!isPlaceableStack(candidate))
        continue;
      blockCount = candidate.stackSize;
      selectBlockSlot(player, hotbarSlot);
      return;
    }
    blockSlot = -1;
    blockCount = 0;
  }

  /** OpenMyau parity: client/server hotbar uses the block stack; item-spoof is render-only. */
  private void selectBlockSlot(EntityPlayerSP player, int hotbarSlot) {
    if (hotbarSlot < 0 || hotbarSlot > 8)
      return;
    blockSlot = hotbarSlot;
    if (hotbarSlot != player.inventory.currentItem) {
      player.inventory.currentItem = hotbarSlot;
      if (shouldSyncServerSlot(hotbarSlot))
        pendingServerSlot = hotbarSlot;
    }
  }

  /**
   * Revert accidental hotbar scroll/number-key switches while scaffold owns a block slot.
   */
  private void guardClientHotbarSlot(EntityPlayerSP player) {
    if (player == null || blockSlot < 0 || blockSlot > 8 || blockCount <= 0)
      return;
    if (ScaffoldItemSpoofHook.isRenderSpoofActive())
      return;
    if (player.inventory.currentItem == blockSlot)
      return;
    player.inventory.currentItem = blockSlot;
    if (shouldSyncServerSlot(blockSlot))
      pendingServerSlot = blockSlot;
  }

  private boolean shouldSyncServerSlot(int slot) {
    if (slot < 0 || slot > 8 || slot == serverReportedSlot)
      return false;
    if (itemSpoof.getValue() && slot == lastSlot && slot == blockSlot)
      return false;
    return true;
  }

  /** Send C09 right before C08 so the server uses the same block slot as the client. */
  private void flushPendingServerSlot(EntityPlayerSP player) {
    if (player == null) {
      pendingServerSlot = -1;
      return;
    }
    if (pendingServerSlot >= 0) {
      if (shouldSyncServerSlot(pendingServerSlot))
        notifyServerSlot(pendingServerSlot);
      pendingServerSlot = -1;
      return;
    }
    int syncSlot = blockSlot >= 0 && blockSlot <= 8
        ? blockSlot
        : player.inventory.currentItem;
    if (shouldSyncServerSlot(syncSlot))
      notifyServerSlot(syncSlot);
  }

  private ItemStack getPlacementStack(EntityPlayerSP player) {
    if (player == null)
      return null;
    if (blockSlot >= 0 && blockSlot <= 8) {
      ItemStack stack = player.inventory.getStackInSlot(blockSlot);
      if (isPlaceableStack(stack))
        return stack;
    }
    return player.getHeldItem();
  }

  private void ensureClientBlockSlot(EntityPlayerSP player) {
    if (player == null || blockSlot < 0 || blockSlot > 8)
      return;
    if (player.inventory.currentItem != blockSlot)
      player.inventory.currentItem = blockSlot;
  }

  private void notifyServerSlot(int slot) {
    if (!shouldSyncServerSlot(slot))
      return;
    sendingServerSlot = true;
    try {
      PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(slot));
    } finally {
      sendingServerSlot = false;
    }
    serverReportedSlot = slot;
  }

  /** Client hotbar index to show when item-spoof render hooks are active (OpenMyau {@code getSlot}). */
  public int getSpoofSlot() {
    return lastSlot;
  }

  public boolean isItemSpoofEnabled() {
    return itemSpoof.getValue();
  }

  private void prepareBaseRotation(EntityPlayerSP player) {
    float currentYaw = currentMoveYaw(player);
    float reportedYaw = PlayerUpdateHook.lastReportedYaw(player);
    float yawDiffTo180 = wrapTo(currentYaw - 180.0f, reportedYaw);
    float diagonalYaw = isDiagonal(currentYaw)
        ? yawDiffTo180
        : wrapTo(currentYaw - 135.0f * (((currentYaw + 180.0f) % 90.0f) < 45.0f ? 1.0f : -1.0f),
            reportedYaw);

    if (canRotate)
      return;

    switch (rotationMode.getValue()) {
      case ROT_DEFAULT:
        yaw = ScaffoldPlacement.quantize(diagonalYaw);
        pitch = ScaffoldPlacement.quantize(85.0f);
        canRotate = true;
        break;
      case ROT_BACKWARDS:
        yaw = ScaffoldPlacement.quantize(yawDiffTo180);
        pitch = ScaffoldPlacement.quantize(85.0f);
        canRotate = true;
        break;
      case ROT_SIDEWAYS:
        yaw = ScaffoldPlacement.quantize(diagonalYaw);
        pitch = ScaffoldPlacement.quantize(85.0f);
        canRotate = true;
        break;
      case ROT_GODBRIDGE:
        yaw = ScaffoldPlacement.quantize(Math.round(currentYaw / 45.0f) * 45.0f);
        pitch = ScaffoldPlacement.quantize(79.3f);
        canRotate = true;
        break;
      case ROT_SMOOTH:
        yaw = ScaffoldPlacement.quantize(isDiagonal(currentYaw) ? diagonalYaw : yawDiffTo180);
        pitch = ScaffoldPlacement.quantize(85.0f);
        canRotate = true;
        break;
      default:
        yaw = player.rotationYaw;
        pitch = player.rotationPitch;
        canRotate = false;
        break;
    }
  }

  private float yawBaseForAim(EntityPlayerSP player) {
    float reportedYaw = PlayerUpdateHook.lastReportedYaw(player);
    return yaw == -180.0f && pitch == 0.0f ? reportedYaw : wrapTo(yaw, reportedYaw);
  }

  private ScaffoldPlacement.AimData resolveBlockAim(EntityPlayerSP player,
                                                    ScaffoldPlacement.BlockData blockData,
                                                    float basePitch) {
    if (player == null || blockData == null)
      return null;
    // OpenMyau uses one yaw base. Try sequentially and stop on first hit —
    // scanning all four bases × 256 reflected raycasts freezes the client.
    float presetYaw = yawBaseForAim(player);
    ScaffoldPlacement.AimData hit = ScaffoldPlacement.findAimData(blockData, presetYaw, basePitch);
    if (hit != null)
      return hit;
    float moveYaw = currentMoveYaw(player);
    if (Math.abs(ScaffoldPlacement.wrapAngle(moveYaw - presetYaw)) > 0.5f) {
      hit = ScaffoldPlacement.findAimData(blockData, moveYaw, basePitch);
      if (hit != null)
        return hit;
    }
    float reportedYaw = PlayerUpdateHook.lastReportedYaw(player);
    if (Math.abs(ScaffoldPlacement.wrapAngle(reportedYaw - presetYaw)) > 0.5f
        && Math.abs(ScaffoldPlacement.wrapAngle(reportedYaw - moveYaw)) > 0.5f) {
      hit = ScaffoldPlacement.findAimData(blockData, reportedYaw, basePitch);
      if (hit != null)
        return hit;
    }
    float cameraYaw = player.rotationYaw;
    if (Math.abs(ScaffoldPlacement.wrapAngle(cameraYaw - presetYaw)) > 0.5f
        && Math.abs(ScaffoldPlacement.wrapAngle(cameraYaw - moveYaw)) > 0.5f
        && Math.abs(ScaffoldPlacement.wrapAngle(cameraYaw - reportedYaw)) > 0.5f) {
      return ScaffoldPlacement.findAimData(blockData, cameraYaw, basePitch);
    }
    return null;
  }

  private void clearRotationState() {
    canRotate = false;
    lastSentYaw = Float.MIN_VALUE;
  }

  private void resetSteppedRotation() {
    steppedServerYaw = Float.NaN;
    steppedServerPitch = Float.NaN;
  }

  private float rotationBaseYaw(EntityPlayerSP player) {
    if (!Float.isNaN(steppedServerYaw))
      return steppedServerYaw;
    return PlayerUpdateHook.lastReportedYaw(player);
  }

  private float rotationBasePitch(EntityPlayerSP player) {
    if (!Float.isNaN(steppedServerPitch))
      return steppedServerPitch;
    return PlayerUpdateHook.lastReportedPitch(player);
  }

  /**
   * OpenMyau Scaffold 424–431: snap backwards/sideways preset yaw into {@code this.yaw}
   * when block aim is within 90° of the movement-facing preset.
   */
  private void snapMovementFacingYaw(EntityPlayerSP player) {
    if (player == null || !MoveFixUtil.isForwardPressed() || !canRotate)
      return;
    if (isTowerJumpActive(player))
      return;
    if (tellyDoNotAimActive && telly.getValue())
      return;

    int mode = rotationMode.getValue();
    if (mode != ROT_BACKWARDS && mode != ROT_SIDEWAYS)
      return;

    float currentYaw = currentMoveYaw(player);
    float cameraYaw = player.rotationYaw;
    float yawDiffTo180 = wrapTo(currentYaw - 180.0f, cameraYaw);
    if (Math.abs(ScaffoldPlacement.wrapAngle(yawDiffTo180 - yaw)) >= 90.0f)
      return;

    float diagonalYaw = isDiagonal(currentYaw)
        ? yawDiffTo180
        : wrapTo(currentYaw - 135.0f * (((currentYaw + 180.0f) % 90.0f) < 45.0f ? 1.0f : -1.0f),
            cameraYaw);
    if (mode == ROT_BACKWARDS)
      yaw = ScaffoldPlacement.quantize(yawDiffTo180);
    else
      yaw = ScaffoldPlacement.quantize(diagonalYaw);
  }

  private void applyRotation(EntityPlayerSP player) {
    if (rotationMode.getValue() == ROT_NONE) {
      resetSteppedRotation();
      RotationState.applyState(false, 0.0f, 0.0f, 0.0f, -1);
      lastSentYaw = Float.MIN_VALUE;
      return;
    }

    if (tellyDoNotAimActive && telly.getValue()) {
      sendTellyTransitionRotation(player, yaw, pitch);
      return;
    }

    if (isTowerJumpActive(player)) {
      applyTowerRotation(player);
      return;
    }
    towering = false;
    applyNormalRotation(player);
  }

  private void applyNormalRotation(EntityPlayerSP player) {
    float baseYaw = rotationBaseYaw(player);
    float basePitch = rotationBasePitch(player);
    float targetYaw = yaw;
    float targetPitch = pitch;

    if (cyclePrioritizePlacement || cycleAllowTellyAirPlacement) {
      sendTargetRotation(baseYaw, basePitch, targetYaw, targetPitch);
    } else if (useTellyTransitionRotation()) {
      sendTellyTransitionRotation(player, targetYaw, targetPitch);
      return;
    } else {
      sendTargetRotation(baseYaw, basePitch, targetYaw, targetPitch);
    }
    updateTellyRotTransition(targetYaw, targetPitch);
  }

  private void applyTowerRotation(EntityPlayerSP player) {
    float targetYaw = canRotate ? yaw : ScaffoldPlacement.quantize(currentMoveYaw(player));
    float targetPitch = canRotate ? pitch : 85.0f;
    if (targetPitch < 75.0f)
      targetPitch = 85.0f;
    targetYaw = ScaffoldPlacement.quantize(targetYaw);
    targetPitch = ScaffoldPlacement.quantize(targetPitch);

    sendSilentRotation(targetYaw, targetPitch, targetYaw, targetPitch);
    towering = true;
  }

  private boolean useTellyTransitionRotation() {
    return telly.getValue() && tellyDoNotAimActive;
  }

  private void sendTellyTransitionRotation(EntityPlayerSP player, float targetYaw, float targetPitch) {
    sendTargetRotation(rotationBaseYaw(player), rotationBasePitch(player), targetYaw, targetPitch);
    updateTellyRotTransition(targetYaw, targetPitch);
  }

  private void updateTellyRotTransition(float targetYaw, float targetPitch) {
    if (!telly.getValue()) {
      tellyRotTransition = false;
      return;
    }
    tellyRotTransition = tellyDoNotAimActive
        && rotationIncomplete(lastSentYaw, lastSentPitch, targetYaw, targetPitch);
  }

  private void sendTargetRotation(float baseYaw, float basePitch,
                                  float targetYaw, float targetPitch) {
    if (shouldSmoothRotation()) {
      sendSilentRotation(
          smoothYaw(baseYaw, targetYaw),
          smoothPitch(basePitch, targetPitch),
          targetYaw,
          targetPitch);
    } else {
      sendSilentRotation(targetYaw, targetPitch, targetYaw, targetPitch);
    }
  }

  private void sendSilentRotation(float sentYaw, float sentPitch, float targetYaw, float targetPitch) {
    PlayerUpdateHook.requestRotation(sentYaw, sentPitch);
    if (prevSentYaw != Float.MIN_VALUE) {
      tickRotDeltaYaw = Math.abs(ScaffoldPlacement.wrapAngle(sentYaw - prevSentYaw));
      tickRotDeltaPitch = Math.abs(sentPitch - prevSentPitch);
    } else {
      tickRotDeltaYaw = 0.0f;
      tickRotDeltaPitch = 0.0f;
    }
    prevSentYaw = sentYaw;
    prevSentPitch = sentPitch;
    rotationTargetYaw = targetYaw;
    rotationTargetPitch = targetPitch;
    lastSentYaw = sentYaw;
    lastSentPitch = sentPitch;
    steppedServerYaw = sentYaw;
    steppedServerPitch = sentPitch;
    // Always feed RotationState for F5/FreeLook. MoveFix priority only when SILENT
    // MoveFix is on (MoveFixHook requires priority 1/3 for moveFlying).
    // pervYaw MUST match sentYaw while stepping — targetYaw here causes Simulation
    // (moveFlying at full snap while C03 is still mid-step).
    boolean moveFixEnabled = moveFix.getValue() == MOVEFIX_SILENT;
    float pervYaw = moveFixEnabled ? sentYaw : mc.thePlayer.rotationYaw;
    // MoveFix on → priority 3; render-only → -3 (KA uses -1) so clears do not cross-wipe.
    int priority = moveFixEnabled ? (int) ROTATION_PRIORITY : -3;
    RotationState.applyState(true, sentYaw, sentPitch, pervYaw, priority);
    updateRotationDelay(sentYaw, sentPitch, targetYaw, targetPitch);
  }

  /** Hold placement until stepped rotation reaches the target (Grim RotationPlace). */
  private void updateRotationDelay(float sentYaw, float sentPitch, float targetYaw, float targetPitch) {
    if (!shouldSmoothRotation())
      return;
    if (rotationIncomplete(sentYaw, sentPitch, targetYaw, targetPitch))
      rotationTick = Math.max(rotationTick, 1);
  }

  /**
   * Grim RotationPlace: hit must lie on the ray from the <b>sent</b> C03 look.
   * Aim/face-center hitVec is only a fallback when the look already intersects
   * the support (same block); never place on a miss.
   */
  private Vec3 resolvePlacementHit(ScaffoldPlacement.BlockData blockData,
                                   ScaffoldPlacement.AimData aimData) {
    if (blockData == null || lastSentYaw == Float.MIN_VALUE)
      return null;
    Vec3 lookHit = ScaffoldPlacement.findPlacementHit(
        mc.thePlayer, blockData, lastSentYaw, lastSentPitch);
    if (lookHit != null)
      return lookHit;
    // Look misses support — do not place (RotationPlace). Aim hitVec alone is unsafe
    // while rotation-speed < 180 or after movement-facing snap changes yaw.
    return null;
  }

  private Vec3 resolveHitForPlace(BlockPos blockPos, int faceOrdinal,
                                  ScaffoldPlacement.AimData aimData) {
    if (blockPos == null || lastSentYaw == Float.MIN_VALUE)
      return null;
    return ScaffoldPlacement.findPlacementHit(
        mc.thePlayer,
        new ScaffoldPlacement.BlockData(blockPos, faceOrdinal),
        lastSentYaw,
        lastSentPitch);
  }

  private boolean canPlaceThisTick(EntityPlayerSP player, boolean towerJump,
                                   boolean allowTellyAirPlacement) {
    if (towerJump)
      return true;
    if (allowTellyAirPlacement)
      return true;
    if (!telly.getValue() || !isTellyMoving() || isTowering(player))
      return true;
    return !isTellyDoNotAim(player);
  }

  private boolean tryPlace(EntityPlayerSP player, BlockPos blockPos, int faceOrdinal, Vec3 preferredHit) {
    if (blockPos == null || player == null)
      return false;
    if (shouldBlockDuplicateRotPlace())
      return false;
    flushPendingServerSlot(player);
    ItemStack stack = getPlacementStack(player);
    if (!isPlaceableStack(stack) || stack.stackSize < 1)
      return false;
    if (placementsThisTick >= maxPlacementsPerTick())
      return false;
    WorldClient world = mc.theWorld;
    if (world == null)
      return false;
    if (!ScaffoldPlacement.isValidSupport(blockPos))
      return false;
    if (!ScaffoldPlacement.isPlacementTargetClear(blockPos, faceOrdinal))
      return false;

    // Grim RotationPlace: never invent a hitVec the sent look did not produce.
    if (preferredHit == null)
      return false;
    Vec3 hitVec = preferredHit;

    EnumFacing facing = ScaffoldPlacement.enumFacing(faceOrdinal);
    if (facing == null)
      return false;

    ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
    ensureClientBlockSlot(player);

    boolean placed = false;
    if (mc.playerController != null) {
      placed = mc.playerController.onPlayerRightClick(
          player, world, stack, blockPos, facing, hitVec);
    }
    if (placed && swing.getValue())
      player.swingItem();
    if (placed) {
      placementsThisTick++;
      lastPlaceRotDeltaYaw = tickRotDeltaYaw;
      lastPlaceRotDeltaPitch = tickRotDeltaPitch;
      blockCount--;
      ItemStack held = getPlacementStack(player);
      int heldCount = isPlaceableStack(held) ? held.stackSize : 0;
      blockCount = Math.min(blockCount, heldCount);
      if (telly.getValue())
        tellyAwaitingPlacement = false;
      return true;
    }
    return false;
  }

  private void applySafeWalk(EntityPlayerSP player, World world) {
    boolean enable = safeWalk.getValue() && player.onGround
        && player.motionY <= 0.0 && nearEdge(player, world);
    boolean sneak = enable || mc.gameSettings.keyBindSneak.isKeyDown();
    forceSneak = enable;
    player.setSneaking(sneak);
  }

  private boolean nearEdge(EntityPlayerSP player, World world) {
    if (player == null || world == null)
      return false;
    AxisAlignedBB aabb = player.getEntityBoundingBox();
    if (aabb == null)
      return false;
    int by = MathHelper.floor_double(aabb.minY - 0.01);
    int minX = MathHelper.floor_double(aabb.minX + player.motionX);
    int maxX = MathHelper.floor_double(aabb.maxX + player.motionX);
    int minZ = MathHelper.floor_double(aabb.minZ + player.motionZ);
    int maxZ = MathHelper.floor_double(aabb.maxZ + player.motionZ);
    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        if (!ScaffoldPlacement.isReplaceable(new BlockPos(x, by, z)))
          return false;
      }
    }
    return isMovementKeyHeld();
  }

  private void applyTower(EntityPlayerSP player, World world) {
    boolean onGround = player.onGround;

    if (!isTowerJumpActive(player)) {
      resetTowerState();
      towerWasOnGround = onGround;
      return;
    }

    if (!hasTowerSupport(player, world)) {
      towerJumpOffY = Double.NaN;
      towerWasOnGround = onGround;
      return;
    }

    if (towerWasOnGround && !onGround)
      onTowerJump(player);

    towerWasOnGround = onGround;
    if (!onGround)
      towerAirTicks++;
    else
      towerAirTicks = 0;

    switch (tower.getValue()) {
      case TOWER_MOTION:
        applyTowerMotion(player);
        break;
      case TOWER_PULLDOWN:
        applyTowerPulldown(player, world);
        break;
      case TOWER_KARHU:
        applyTowerKarhu(player, world);
        break;
      case TOWER_VULCAN:
        applyTowerVulcan(player);
        break;
      case TOWER_HYPIXEL:
        applyTowerHypixel(player);
        break;
      default:
        break;
    }
  }

  private void onTowerJump(EntityPlayerSP player) {
    towerJumpOffY = player.posY;
    int mode = tower.getValue();
    if (mode == TOWER_PULLDOWN)
      towerPulldownPending = true;
    if (mode == TOWER_KARHU) {
      towerKarhuTimerActive = true;
      setTimerSpeed(towerKarhuTimer.getValue());
      if (towerKarhuPulldown.getValue())
        towerPulldownPending = true;
    }
  }

  private void resetTowerState() {
    if (towerKarhuTimerActive)
      setTimerSpeed(1.0f);
    towerJumpOffY = Double.NaN;
    towerAirTicks = 0;
    towerKarhuTimerActive = false;
    towerPulldownPending = false;
    wasTowerJump = false;
  }

  private void applyTowerMotion(EntityPlayerSP player) {
    if (Double.isNaN(towerJumpOffY))
      return;
    double y = player.posY;
    if (y <= towerJumpOffY + towerTriggerHeight.getValue())
      return;

    double truncated = Math.floor(y);
    player.setPosition(player.posX, truncated, player.posZ);

    float slow = towerSlow.getValue();
    player.motionX *= slow;
    player.motionY = towerMotion.getValue();
    player.motionZ *= slow;
    towerJumpOffY = truncated;
  }

  private void applyTowerPulldown(EntityPlayerSP player, World world) {
    if (!towerPulldownPending || player.onGround)
      return;
    if (player.motionY >= towerPulldownTrigger.getValue())
      return;
    if (!hasTowerSupport(player, world)) {
      towerPulldownPending = false;
      return;
    }
    player.motionY = -1.0;
    towerPulldownPending = false;
  }

  private void applyTowerKarhu(EntityPlayerSP player, World world) {
    if (!towerKarhuPulldown.getValue() || !towerPulldownPending || player.onGround)
      return;
    if (player.motionY >= towerKarhuTrigger.getValue())
      return;
    if (!hasTowerSupport(player, world)) {
      towerPulldownPending = false;
      return;
    }
    player.motionY -= 1.0;
    towerPulldownPending = false;
  }

  private void applyTowerVulcan(EntityPlayerSP player) {
    int tick = player.ticksExisted;
    boolean moving = isMovementKeyHeld();
    if (tick % 2 == 0) {
      player.motionY = 0.7;
    } else {
      player.motionY = moving ? 0.42 : 0.6;
    }
  }

  private void applyTowerHypixel(EntityPlayerSP player) {
    if (!isMovementKeyHeld()) {
      double px = player.posX;
      if (px % 1.0 != 0.0) {
        double snap = Math.round(px) - px;
        if (snap > 0.281)
          snap = 0.281;
        player.motionX = snap;
      }
    }

    if (towerAirTicks > 14) {
      player.motionX *= 0.6;
      player.motionY -= 0.09;
      player.motionZ *= 0.6;
      return;
    }

    switch (towerAirTicks % 3) {
      case 0: {
        double speed = 0.247 - ThreadLocalRandom.current().nextFloat() / 100.0;
        double yawRad = Math.toRadians(player.rotationYaw);
        player.motionX = -Math.sin(yawRad) * speed;
        player.motionY = 0.42;
        player.motionZ = Math.cos(yawRad) * speed;
        break;
      }
      case 2:
        player.motionY = 1.0 - (player.posY % 1.0);
        break;
      default:
        break;
    }
  }

  private boolean hasTowerSupport(EntityPlayerSP player, World world) {
    if (player == null || world == null)
      return false;
    AxisAlignedBB aabb = player.getEntityBoundingBox();
    if (aabb == null)
      return isBlockBelow(player);
    int minX = MathHelper.floor_double(aabb.minX - 0.5);
    int maxX = MathHelper.floor_double(aabb.maxX + 0.5);
    int minZ = MathHelper.floor_double(aabb.minZ - 0.5);
    int maxZ = MathHelper.floor_double(aabb.maxZ + 0.5);
    int minY = MathHelper.floor_double(aabb.minY - 1.05);
    int maxY = MathHelper.floor_double(aabb.minY - 0.01);
    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        for (int y = minY; y <= maxY; y++) {
          if (!ScaffoldPlacement.isReplaceable(new BlockPos(x, y, z)))
            return true;
        }
      }
    }
    return false;
  }

  private boolean isBlockBelow(EntityPlayerSP player) {
    if (player == null)
      return false;
    int bx = MathHelper.floor_double(player.posX);
    int by = MathHelper.floor_double(player.posY) - 1;
    int bz = MathHelper.floor_double(player.posZ);
    return !ScaffoldPlacement.isReplaceable(new BlockPos(bx, by, bz));
  }

  private boolean isTowerJumpActive(EntityPlayerSP player) {
    if (player == null || tower.getValue() == TOWER_NONE) {
      wasTowerJump = false;
      return false;
    }
    if (!mc.gameSettings.keyBindJump.isKeyDown()) {
      wasTowerJump = false;
      return false;
    }
    if (blockCount <= 0 || !isHoldingPlaceable(player)) {
      wasTowerJump = false;
      return false;
    }
    wasTowerJump = true;
    return true;
  }

  boolean wantsTowerJumpInput() {
    EntityPlayerSP player = mc.thePlayer;
    return wasTowerJump && player != null && player.onGround;
  }

  private boolean isTowering(EntityPlayerSP player) {
    if (!isTowerJumpActive(player))
      return false;
    return keepY.getValue() == KEEPY_TELLY && stage > 0 && !telly.getValue()
        || mc.gameSettings.keyBindJump.isKeyDown();
  }

  @Override
  public int sendPriority() {
    return 50;
  }

  @Override
  public boolean onSend(Object packet) {
    if (!isEnabled() || packet == null || gnu.client.runtime.packet.PacketUtil.isDispatching())
      return false;
    if (handleHotbarGuard(packet))
      return true;
    return handleVulcanTower(packet);
  }

  private boolean handleHotbarGuard(Object packet) {
    if (!PacketHelper.isHeldItemChange(packet) || sendingServerSlot)
      return false;
    if (blockSlot < 0 || blockSlot > 8 || blockCount <= 0)
      return false;
    EntityPlayerSP player = mc.thePlayer;
    if (player == null)
      return false;
    guardClientHotbarSlot(player);
    return true;
  }

  private boolean handleVulcanTower(Object packet) {
    if (tower.getValue() != TOWER_VULCAN)
      return false;
    EntityPlayerSP player = mc.thePlayer;
    WorldClient world = mc.theWorld;
    if (player == null || world == null || !isTowerJumpActive(player))
      return false;
    if (!hasTowerSupport(player, world))
      return false;
    if (isMovementKeyHeld() || !PacketHelper.c03HasPosition(packet))
      return false;
    int tick = player.ticksExisted;
    if (tick % 2 != 0)
      return false;
    double x = PacketHelper.c03PosX(packet);
    double z = PacketHelper.c03PosZ(packet);
    if (Double.isNaN(x) || Double.isNaN(z))
      return false;
    PacketHelper.c03SetPosition(packet, x + 0.1, PacketHelper.c03PosY(packet), z + 0.1);
    return false;
  }

  @Override
  public boolean onReceive(Object packet) {
    return false;
  }

  private float[] applyMotionScale(float forward, float strafe) {
    EntityPlayerSP player = mc.thePlayer;
    float speed = (player != null && player.onGround)
        ? groundMotion.getValue() / 100.0f
        : airMotion.getValue() / 100.0f;
    if (Math.abs(speed - 1.0f) < 0.001f)
      return new float[] {forward, strafe};
    return new float[] {forward * speed, strafe * speed};
  }

  private void applySprint(EntityPlayerSP player) {
    if (shouldEnableTellySprint(player)) {
      setSprintKeyState(true);
      player.setSprinting(true);
      return;
    }
    if (shouldSuppressSprint(player)) {
      setSprintKeyState(false);
      player.setSprinting(false);
    }
  }

  private boolean shouldEnableTellySprint(EntityPlayerSP player) {
    if (!shouldUseTellySprint() || player == null || !telly.getValue())
      return false;
    if (tellyResetMode.getValue() != TELLY_RESET)
      return false;
    if (!isTellyMoving() || !hasTellyBlocks(player) || isTowering(player))
      return false;
    if (!isTellyDoNotAim(player))
      return false;
    if (forceSneak)
      return false;
    if (safeWalk.getValue() && player.onGround && cycleWorld != null && nearEdge(player, cycleWorld))
      return false;
    return isServerForwardMoving();
  }

  private boolean shouldPrioritizePlacement(EntityPlayerSP player, boolean tellyDoNotAim) {
    if (player == null || cycleWorld == null)
      return false;
    if (!telly.getValue() || !tellyStraightJumpActive || tellyDoNotAim)
      return false;
    if (tellyAwaitingPlacement)
      return true;
    return player.onGround;
  }

  private boolean shouldAllowTellyAirPlacement(EntityPlayerSP player, boolean prioritizePlacement,
                                               boolean tellyDoNotAim) {
    if (prioritizePlacement)
      return true;
    if (!telly.getValue() || player == null || cycleWorld == null)
      return false;
    if (!isTellyMoving() || isTowering(player))
      return false;
    if (!tellyStraightJumpActive)
      return true;
    return !tellyDoNotAim;
  }

  private boolean isServerForwardMoving() {
    return MoveFixUtil.isForwardPressed();
  }

  public static boolean shouldSuppressSprintKey() {
    gnu.client.module.Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (!(module instanceof ScaffoldModule) || !module.isEnabled())
      return false;
    return ((ScaffoldModule) module).shouldSuppressSprint(mc.thePlayer);
  }

  private boolean shouldSuppressSprint(EntityPlayerSP player) {
    if (isTellyResetSprintWindow(player))
      return false;
    if (isTowering(player))
      return false;
    return sprintMode.getValue() == SPRINT_NONE;
  }

  private boolean isTellyResetSprintWindow(EntityPlayerSP player) {
    if (!telly.getValue() || tellyResetMode.getValue() != TELLY_RESET || player == null)
      return false;
    if (!isTellyMoving() || !hasTellyBlocks(player) || isTowering(player))
      return false;
    return isTellyDoNotAim(player);
  }

  public static void patchMovementInput(Object movInputObj) {
    if (!(movInputObj instanceof MovementInput))
      return;
    MovementInput movInput = (MovementInput) movInputObj;
    gnu.client.module.Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    ScaffoldModule scaffold = module instanceof ScaffoldModule && module.isEnabled()
        ? (ScaffoldModule) module : null;

    boolean sneak = movInput.sneak || forceSneak;
    boolean jump = movInput.jump || forceJump || shouldForceImmediateTellyJump();
    if (shouldTowerJumpInput())
      jump = true;
    float forward = movInput.moveForward;
    float strafe = movInput.moveStrafe;
    if (scaffold != null
        && scaffold.moveFix.getValue() == MOVEFIX_SILENT
        && MoveFixUtil.hasMoveFixPriority((int) ROTATION_PRIORITY)
        && MoveFixUtil.isForwardPressed()) {
      EntityPlayerSP player = mc.thePlayer;
      float cameraYaw = player != null ? player.rotationYaw : 0.0f;
      float[] fixed = MoveFixUtil.fixStrafe(cameraYaw, RotationState.getSmoothedYaw(), sneak);
      forward = fixed[0];
      strafe = fixed[1];
    }
    if (scaffold != null) {
      float[] scaled = scaffold.applyMotionScale(forward, strafe);
      forward = scaled[0];
      strafe = scaled[1];
    }
    movInput.sneak = sneak;
    movInput.jump = jump;
    movInput.moveForward = forward;
    movInput.moveStrafe = strafe;
  }

  private static boolean shouldTowerJumpInput() {
    gnu.client.module.Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (!(module instanceof ScaffoldModule) || !module.isEnabled())
      return false;
    return ((ScaffoldModule) module).wantsTowerJumpInput();
  }

  private static boolean shouldForceImmediateTellyJump() {
    gnu.client.module.Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (!(module instanceof ScaffoldModule) || !module.isEnabled())
      return false;
    return ((ScaffoldModule) module).shouldForceImmediateTellyJump(mc.thePlayer);
  }

  private boolean shouldForceImmediateTellyJump(EntityPlayerSP player) {
    if (!telly.getValue() || tellyJumpTicks() != 0 || player == null)
      return false;
    if (!player.onGround || !isTellyMoving() || isTowering(player))
      return false;
    if (!hasTellyBlocks(player))
      return false;

    forceJump = true;
    tellyTicksUntilJump = 0;
    tellyStraightJumpActive = true;
    tellyAwaitingPlacement = false;
    return true;
  }

  private boolean shouldUseTellySprint() {
    if (sprintMode.getValue() == SPRINT_VANILLA)
      return true;
    Module sprint = gnu.client.module.ModuleManager.instance().getModule("Sprint");
    return sprint != null && sprint.isEnabled();
  }

  private boolean isHoldingPlaceable(EntityPlayerSP player) {
    return isPlaceableStack(getPlacementStack(player));
  }

  private boolean hasTellyBlocks(EntityPlayerSP player) {
    return blockCount > 0 || isHoldingPlaceable(player);
  }

  private boolean isPlaceableStack(ItemStack stack) {
    return stack != null && stack.stackSize >= 1 && stack.getItem() instanceof ItemBlock;
  }

  private float currentMoveYaw(EntityPlayerSP player) {
    return MoveFixUtil.adjustYaw(player.rotationYaw, forwardValue(), leftValue());
  }

  private int forwardValue() {
    int value = 0;
    if (mc.gameSettings.keyBindForward.isKeyDown())
      value++;
    if (mc.gameSettings.keyBindBack.isKeyDown())
      value--;
    return value;
  }

  private int leftValue() {
    int value = 0;
    if (mc.gameSettings.keyBindLeft.isKeyDown())
      value++;
    if (mc.gameSettings.keyBindRight.isKeyDown())
      value--;
    return value;
  }

  private boolean isTellyMoving() {
    return forwardValue() != 0 || leftValue() != 0;
  }

  private boolean isMovementKeyHeld() {
    return mc.gameSettings.keyBindForward.isKeyDown()
        || mc.gameSettings.keyBindBack.isKeyDown()
        || mc.gameSettings.keyBindLeft.isKeyDown()
        || mc.gameSettings.keyBindRight.isKeyDown();
  }

  private boolean isUseItemKeyDown() {
    return mc.gameSettings.keyBindUseItem.isKeyDown();
  }

  private void setSprintKeyState(boolean pressed) {
    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), pressed);
  }

  private void setTimerSpeed(float speed) {
    ((IAccessorTimer) ((IAccessorMinecraft) mc).getTimer()).setTimerSpeed(speed);
  }

  private int tellyStraightTicks() {
    return Math.max(0, Math.round(tellyStraightTicks.getValue()));
  }

  private int tellyJumpTicks() {
    return Math.max(0, Math.round(tellyJumpTicks.getValue()));
  }

  private boolean shouldSmoothRotation() {
    return rotationMode.getValue() != ROT_NONE && rotationSpeed.getValue() < 179.5f;
  }

  private boolean rotationIncomplete(float sentYaw, float sentPitch,
                                     float targetYaw, float targetPitch) {
    if (sentYaw == Float.MIN_VALUE)
      return true;
    float yawRemaining = Math.abs(ScaffoldPlacement.wrapAngle(targetYaw - sentYaw));
    float pitchRemaining = Math.abs(targetPitch - sentPitch);
    return yawRemaining > 1.0f || pitchRemaining > 1.0f;
  }

  public boolean isRotationSyncing() {
    return rotationSyncActive();
  }

  private boolean shouldBlockDuplicateRotPlace() {
    if (tickRotDeltaYaw > 2.0f && lastPlaceRotDeltaYaw > 2.0f
        && Math.abs(tickRotDeltaYaw - lastPlaceRotDeltaYaw) < 0.0001f)
      return true;
    if (tickRotDeltaPitch > 2.0f && lastPlaceRotDeltaPitch > 2.0f
        && Math.abs(tickRotDeltaPitch - lastPlaceRotDeltaPitch) < 0.0001f)
      return true;
    return false;
  }

  private boolean rotationSyncActive() {
    if (!shouldSmoothRotation())
      return false;
    if (lastSentYaw == Float.MIN_VALUE)
      return false;
    if (rotationTick > 0)
      return true;
    if (tellyRotTransition)
      return true;
    return rotationIncomplete(lastSentYaw, lastSentPitch, rotationTargetYaw, rotationTargetPitch);
  }

  private float rotationStepDegrees() {
    return Math.max(0.05f, rotationSpeed.getValue());
  }

  private float smoothYaw(float current, float target) {
    return ScaffoldPlacement.quantize(current + cappedDelta(ScaffoldPlacement.wrapAngle(target - current)));
  }

  private float smoothPitch(float current, float target) {
    float pitch = current + cappedDelta(target - current);
    return ScaffoldPlacement.quantize(ScaffoldPlacement.clampPitch(pitch));
  }

  private float cappedDelta(float delta) {
    float maxStep = rotationStepDegrees();
    if (Math.abs(delta) <= maxStep)
      return delta;
    return Math.signum(delta) * maxStep;
  }

  private boolean isDiagonal(float yaw) {
    float absYaw = Math.abs(yaw % 90.0f);
    return absYaw > 20.0f && absYaw < 70.0f;
  }

  private static float wrapTo(float angle, float target) {
    return target + ScaffoldPlacement.wrapAngle(angle - target);
  }
}
