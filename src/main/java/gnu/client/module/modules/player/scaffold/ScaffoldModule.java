package gnu.client.module.modules.player.scaffold;

import gnu.client.mixin.impl.accessors.IAccessorMinecraft;
import gnu.client.mixin.impl.accessors.IAccessorPlayerControllerMP;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.modules.player.scaffold.aim.Line3;
import gnu.client.module.modules.player.scaffold.feature.ScaffoldBlinkFeature;
import gnu.client.module.modules.player.scaffold.feature.ScaffoldFeatures;
import gnu.client.module.modules.player.scaffold.technique.BreezilyTechnique;
import gnu.client.module.modules.player.scaffold.technique.ExpandTechnique;
import gnu.client.module.modules.player.scaffold.technique.GodBridgeTechnique;
import gnu.client.module.modules.player.scaffold.technique.NormalTechnique;
import gnu.client.module.modules.player.scaffold.technique.ScaffoldTechnique;
import gnu.client.module.modules.player.scaffold.tower.ScaffoldTowers;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.PlayerUpdateHook;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.utility.IMinecraftInstance;
import gnu.client.utility.PacketUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;

import java.util.Arrays;

/**
 * LiquidBounce-parity Scaffold for Forge 1.8.9 — technique / tower / feature tree,
 * Scaffold-only MoveFix (Off/Strict/Silent/ChangeLook).
 * Aim at HEAD; C09+C08 before C03 (1.8 Grim Post). Queued place uses following C03 look.
 */
public final class ScaffoldModule extends Module implements PacketListener, IMinecraftInstance {

  public static final float ROTATION_PRIORITY = MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY;

  private static final int TECH_NORMAL = 0;
  private static final int TECH_EXPAND = 1;
  private static final int TECH_GODBRIDGE = 2;
  private static final int TECH_BREEZILY = 3;

  private static final int TIMING_NORMAL = 0;
  private static final int TIMING_ON_TICK = 1;
  private static final int TIMING_ON_TICK_SNAP = 2;

  private static volatile boolean forceSneak;
  private static volatile boolean forceJump;

  private final SliderSetting delayMin = addSetting(new SliderSetting("delay-min", 0.0f, 0.0f, 40.0f));
  private final SliderSetting delayMax = addSetting(new SliderSetting("delay-max", 0.0f, 0.0f, 40.0f));
  private final SliderSetting minDist = addSetting(new SliderSetting("min-dist", 0.0f, 0.0f, 0.25f));
  private final SliderSetting timerSpeed = addSetting(new SliderSetting("timer", 1.0f, 0.01f, 10.0f));
  private final ModeSetting technique = addSetting(new ModeSetting("technique", TECH_NORMAL,
      Arrays.asList("Normal", "Expand", "GodBridge", "Breezily")));
  private final SliderSetting expandLength = addSetting(new SliderSetting("expand-length", 4.0f, 1.0f, 10.0f));
  private final ModeSetting sameY = addSetting(new ModeSetting("same-y", ScaffoldFeatures.SAMEY_OFF,
      Arrays.asList("Off", "On", "Falling", "Hypixel")));
  private final ModeSetting tower = addSetting(new ModeSetting("tower", ScaffoldTowers.NONE,
      Arrays.asList("None", "Motion", "Pulldown", "Karhu", "Vulcan", "Hypixel")));
  private final SliderSetting towerMotion = addSetting(new SliderSetting("tower-motion", 0.42f, 0.0f, 1.0f));
  private final SliderSetting towerTriggerHeight = addSetting(new SliderSetting("tower-trigger-height", 0.78f, 0.76f, 1.0f));
  private final SliderSetting towerSlow = addSetting(new SliderSetting("tower-slow", 1.0f, 0.0f, 3.0f));
  private final SliderSetting towerPulldownTrigger = addSetting(new SliderSetting("tower-pulldown-trigger", 0.1f, 0.0f, 0.2f));
  private final SliderSetting towerKarhuTimer = addSetting(new SliderSetting("tower-karhu-timer", 5.0f, 0.1f, 10.0f));
  private final SliderSetting towerKarhuTrigger = addSetting(new SliderSetting("tower-karhu-trigger", 0.06f, 0.0f, 0.2f));
  private final BoolSetting towerKarhuPulldown = addSetting(new BoolSetting("tower-karhu-pulldown", true));

  private final ModeSetting aimMode = addSetting(new ModeSetting("aim-mode", ScaffoldTargetFinding.AIM_STABILIZED,
      Arrays.asList("Center", "Nearest", "ReverseYaw", "DiagonalYaw", "AngleYaw", "EdgePoint", "Stabilized", "Random")));
  private final ModeSetting rotationTiming = addSetting(new ModeSetting("rotation-timing", TIMING_NORMAL,
      Arrays.asList("Normal", "OnTick", "OnTickSnap")));
  /** LB {@code AngleSmooth} — replaces single rotation-speed. */
  private final ModeSetting angleSmooth = addSetting(new ModeSetting("angle-smooth", ScaffoldAngleSmooth.LINEAR,
      Arrays.asList("Linear", "Sigmoid", "Acceleration")));
  private final SliderSetting horizontalTurnSpeedMin = addSetting(new SliderSetting("horizontal-turn-speed-min", 180.0f, 0.0f, 180.0f));
  private final SliderSetting horizontalTurnSpeedMax = addSetting(new SliderSetting("horizontal-turn-speed-max", 180.0f, 0.0f, 180.0f));
  private final SliderSetting verticalTurnSpeedMin = addSetting(new SliderSetting("vertical-turn-speed-min", 180.0f, 0.0f, 180.0f));
  private final SliderSetting verticalTurnSpeedMax = addSetting(new SliderSetting("vertical-turn-speed-max", 180.0f, 0.0f, 180.0f));
  private final SliderSetting sigmoidSteepness = addSetting(new SliderSetting("sigmoid-steepness", 10.0f, 0.0f, 20.0f));
  private final SliderSetting sigmoidMidpoint = addSetting(new SliderSetting("sigmoid-midpoint", 0.3f, 0.0f, 1.0f));
  private final SliderSetting yawAccelerationMin = addSetting(new SliderSetting("yaw-acceleration-min", 20.0f, 1.0f, 180.0f));
  private final SliderSetting yawAccelerationMax = addSetting(new SliderSetting("yaw-acceleration-max", 25.0f, 1.0f, 180.0f));
  private final SliderSetting pitchAccelerationMin = addSetting(new SliderSetting("pitch-acceleration-min", 20.0f, 1.0f, 180.0f));
  private final SliderSetting pitchAccelerationMax = addSetting(new SliderSetting("pitch-acceleration-max", 25.0f, 1.0f, 180.0f));
  private final BoolSetting accelerationError = addSetting(new BoolSetting("acceleration-error", true));
  private final SliderSetting yawAccelError = addSetting(new SliderSetting("yaw-accel-error", 0.1f, 0.01f, 1.0f));
  private final SliderSetting pitchAccelError = addSetting(new SliderSetting("pitch-accel-error", 0.1f, 0.01f, 1.0f));
  private final BoolSetting constantError = addSetting(new BoolSetting("constant-error", true));
  private final SliderSetting yawConstantError = addSetting(new SliderSetting("yaw-constant-error", 0.1f, 0.01f, 1.0f));
  private final SliderSetting pitchConstantError = addSetting(new SliderSetting("pitch-constant-error", 0.1f, 0.01f, 1.0f));
  private final BoolSetting accelSigmoidDecel = addSetting(new BoolSetting("accel-sigmoid-deceleration", false));
  private final BoolSetting dynamicAccel = addSetting(new BoolSetting("dynamic-accel", false));
  private final SliderSetting coefDistance = addSetting(new SliderSetting("coef-distance", -1.393f, -2.0f, 2.0f));
  private final BoolSetting considerInventory = addSetting(new BoolSetting("consider-inventory", false));
  private final ModeSetting moveFix = addSetting(new ModeSetting("move-fix", ScaffoldMoveFix.SILENT,
      Arrays.asList("Off", "Strict", "Silent", "ChangeLook")));
  private final BoolSetting requiresSight = addSetting(new BoolSetting("requires-sight", false));
  private final BoolSetting simulatePlacement = addSetting(new BoolSetting("simulate-placement", false));
  private final BoolSetting simulateFailedOnly = addSetting(new BoolSetting("simulate-failed-only", true));
  private final SliderSetting slotResetDelay = addSetting(new SliderSetting("slot-reset-delay", 5.0f, 0.0f, 40.0f));

  private final BoolSetting safeWalk = addSetting(new BoolSetting("safe-walk", true));
  private final BoolSetting swing = addSetting(new BoolSetting("swing", true));
  /** Hidden — maxPlacements stays 1 (Grim / PacketOrder). */
  private final BoolSetting multiPlace = addSetting(new BoolSetting("multi-place", false));
  private final BoolSetting autoBlock = addSetting(new BoolSetting("auto-block", true));
  private final BoolSetting autoBlockAlways = addSetting(new BoolSetting("auto-block-always", false));
  private final SliderSetting autoBlockMinCount = addSetting(new SliderSetting("auto-block-min-count", 1.0f, 0.0f, 64.0f));
  private final BoolSetting itemSpoof = addSetting(new BoolSetting("item-spoof", false));
  private final BoolSetting prediction = addSetting(new BoolSetting("prediction", true));
  private final SliderSetting predictionCutoff = addSetting(new SliderSetting("prediction-cutoff", 0.05f, 0.0f, 0.3f));
  private final SliderSetting predictionWarmup = addSetting(new SliderSetting("prediction-warmup", 2.0f, 0.0f, 4.0f));
  private final SliderSetting predictionBootstrap = addSetting(new SliderSetting("prediction-bootstrap", 0.2f, 0.0f, 0.4f));
  private final BoolSetting ledge = addSetting(new BoolSetting("ledge", true));
  private final SliderSetting ledgeEdge = addSetting(new SliderSetting("ledge-edge", 0.3f, 0.05f, 0.5f));
  private final BoolSetting acceleration = addSetting(new BoolSetting("acceleration", false));
  private final SliderSetting accelMult = addSetting(new SliderSetting("acceleration-mult", 0.6f, 0.1f, 2.0f));
  private final BoolSetting strafe = addSetting(new BoolSetting("strafe", false));
  private final SliderSetting strafeSpeed = addSetting(new SliderSetting("strafe-speed", 0.247f, 0.1f, 0.5f));
  private final BoolSetting strafeOnJump = addSetting(new BoolSetting("strafe-on-jump", false));
  private final BoolSetting speedLimiter = addSetting(new BoolSetting("speed-limiter", false));
  private final SliderSetting speedLimit = addSetting(new SliderSetting("speed-limit", 0.11f, 0.01f, 1.0f));

  private final ModeSetting sprintClient = addSetting(new ModeSetting("sprint-client", ScaffoldFeatures.SPRINT_DO_NOT_CHANGE,
      Arrays.asList("DoNotChange", "ForceSprint", "ForceNoSprint", "NoSprintOnPlace", "NoSprintOnGround")));
  private final ModeSetting sprintServer = addSetting(new ModeSetting("sprint-server", ScaffoldFeatures.SPRINT_DO_NOT_CHANGE,
      Arrays.asList("DoNotChange", "ForceSprint", "ForceNoSprint", "NoSprintOnPlace", "NoSprintOnGround")));

  private final BoolSetting eagle = addSetting(new BoolSetting("eagle", false));
  private final SliderSetting eagleBlocksMin = addSetting(new SliderSetting("eagle-blocks-min", 0.0f, 0.0f, 10.0f));
  private final SliderSetting eagleBlocksMax = addSetting(new SliderSetting("eagle-blocks-max", 0.0f, 0.0f, 10.0f));
  private final SliderSetting eagleEdgeMin = addSetting(new SliderSetting("eagle-edge-min", 0.01f, 0.01f, 0.5f));
  private final SliderSetting eagleEdgeMax = addSetting(new SliderSetting("eagle-edge-max", 0.05f, 0.01f, 0.5f));
  private final BoolSetting eagleOnlyGround = addSetting(new BoolSetting("eagle-only-ground", true));
  private final BoolSetting telly = addSetting(new BoolSetting("telly", false));
  private final ModeSetting tellyReset = addSetting(new ModeSetting("telly-reset", ScaffoldFeatures.TELLY_RESET,
      Arrays.asList("Reset", "Reverse")));
  /** Air ticks to look straight after leaving ground (LB {@code airTicks <= straight}). */
  private final SliderSetting tellyStraight = addSetting(new SliderSetting("telly-straight", 0.0f, 0.0f, 5.0f));
  private final SliderSetting tellyJump = addSetting(new SliderSetting("telly-jump", 0.0f, 0.0f, 10.0f));
  private final BoolSetting tellyAimOnTower = addSetting(new BoolSetting("telly-aim-on-tower", true));
  private final BoolSetting down = addSetting(new BoolSetting("down", false));
  private final BoolSetting stabilizeMovement = addSetting(new BoolSetting("stabilize-movement", true));
  private final BoolSetting ceiling = addSetting(new BoolSetting("ceiling", false));
  private final BoolSetting headHitter = addSetting(new BoolSetting("head-hitter", false));
  private final BoolSetting breezilyEdgeStrafe = addSetting(new BoolSetting("breezily-edge-strafe", true));
  private final SliderSetting breezilyEdgeMin = addSetting(new SliderSetting("breezily-edge-min", 0.45f, 0.25f, 0.5f));
  private final SliderSetting breezilyEdgeMax = addSetting(new SliderSetting("breezily-edge-max", 0.5f, 0.25f, 0.5f));
  private final ModeSetting godBridgeLedge = addSetting(new ModeSetting("godbridge-ledge", 0,
      Arrays.asList("Jump", "Sneak", "StopInput", "Backwards", "Jump+Sneak")));
  private final SliderSetting godBridgeForceSneak = addSetting(new SliderSetting("godbridge-force-sneak-below", 3.0f, 0.0f, 10.0f));
  private final BoolSetting scaffoldBlink = addSetting(new BoolSetting("blink", false));
  private final SliderSetting blinkTimeMin = addSetting(new SliderSetting("blink-time-min", 50.0f, 0.0f, 3000.0f));
  private final SliderSetting blinkTimeMax = addSetting(new SliderSetting("blink-time-max", 250.0f, 0.0f, 3000.0f));
  private final BoolSetting blinkFlushPlace = addSetting(new BoolSetting("blink-flush-place", true));
  private final BoolSetting blinkFlushTower = addSetting(new BoolSetting("blink-flush-tower", true));
  private final BoolSetting blinkFlushSneak = addSetting(new BoolSetting("blink-flush-sneak", false));
  private final BoolSetting blockCounter = addSetting(new BoolSetting("block-counter", true));

  private final ScaffoldFeatures features = new ScaffoldFeatures();
  private final ScaffoldTowers towers = new ScaffoldTowers();
  private final ScaffoldMovementPlanner movementPlanner = new ScaffoldMovementPlanner();
  private final ScaffoldMovementPrediction movementPrediction = new ScaffoldMovementPrediction();
  private final ScaffoldBlinkFeature blinkFeature = new ScaffoldBlinkFeature();
  private final ScaffoldAngleSmooth angleSmoothState = new ScaffoldAngleSmooth();

  private int lastSlot = -1;
  private int blockSlot = -1;
  private int serverReportedSlot = -1;
  private int pendingServerSlot = -1;
  private int blockCount;
  private boolean sendingServerSlot;
  private int scaffoldCycleTick = -1;
  private int placeDelayLeft;
  private int placementsThisTick;
  /** True after a C08 this tick — defer further C09 (Grim PacketOrderE rightClicking). */
  private boolean placedThisTick;
  private PlacementTarget pendingTarget;
  private boolean pendingPlace;
  /** WP11: defer SimulatePlacement to beforeWalking (same C08→C03 look-lock as real place). */
  private PlacementTarget pendingSimTarget;
  private boolean pendingSim;
  private float lastSentYaw = Float.MIN_VALUE;
  private float lastSentPitch = Float.MIN_VALUE;
  /** Exact look used for a C08 this tick — must be re-asserted on C03 (Grim post-flying). */
  private float placeLockedYaw = Float.MIN_VALUE;
  private float placeLockedPitch = Float.MIN_VALUE;
  private float tickRotDeltaYaw;
  private float lastPlaceRotDeltaYaw = -1.0f;
  private float steppedServerYaw = Float.NaN;
  private float steppedServerPitch = Float.NaN;
  private boolean wasTowerJump;
  /** Sticky tower column — ignore XZ drift so we don't build a platform around the player. */
  private int towerColumnX = Integer.MIN_VALUE;
  private int towerColumnZ = Integer.MIN_VALUE;
  private boolean jumpedThisTick;
  /** Delayed hotbar restore after disable (LB slotResetDelay). */
  private static int pendingSlotRestore = -1;
  private static int pendingSlotRestoreTicks;

  public ScaffoldModule() {
    super("Scaffold", "LiquidBounce-parity scaffold", Category.PLAYER);
    multiPlace.setVisible(false);

    expandLength.visibleWhen(() -> technique.getValue() == TECH_EXPAND);
    godBridgeLedge.visibleWhen(() -> technique.getValue() == TECH_GODBRIDGE);
    godBridgeForceSneak.visibleWhen(() -> technique.getValue() == TECH_GODBRIDGE);
    breezilyEdgeStrafe.visibleWhen(() -> technique.getValue() == TECH_BREEZILY);
    breezilyEdgeMin.visibleWhen(() -> technique.getValue() == TECH_BREEZILY && breezilyEdgeStrafe.getValue());
    breezilyEdgeMax.visibleWhen(() -> technique.getValue() == TECH_BREEZILY && breezilyEdgeStrafe.getValue());

    towerMotion.visibleWhen(() -> tower.getValue() == ScaffoldTowers.MOTION);
    towerTriggerHeight.visibleWhen(() -> tower.getValue() == ScaffoldTowers.MOTION);
    towerSlow.visibleWhen(() -> tower.getValue() == ScaffoldTowers.MOTION);
    towerPulldownTrigger.visibleWhen(() -> tower.getValue() == ScaffoldTowers.PULLDOWN);
    towerKarhuTimer.visibleWhen(() -> tower.getValue() == ScaffoldTowers.KARHU);
    towerKarhuTrigger.visibleWhen(() -> tower.getValue() == ScaffoldTowers.KARHU);
    towerKarhuPulldown.visibleWhen(() -> tower.getValue() == ScaffoldTowers.KARHU);

    sigmoidSteepness.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.SIGMOID
        || (angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION && accelSigmoidDecel.getValue()));
    sigmoidMidpoint.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.SIGMOID
        || (angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION && accelSigmoidDecel.getValue()));
    yawAccelerationMin.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION);
    yawAccelerationMax.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION);
    pitchAccelerationMin.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION);
    pitchAccelerationMax.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION);
    accelerationError.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION);
    constantError.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION);
    accelSigmoidDecel.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION);
    dynamicAccel.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION);
    yawAccelError.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION
        && accelerationError.getValue());
    pitchAccelError.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION
        && accelerationError.getValue());
    yawConstantError.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION
        && constantError.getValue());
    pitchConstantError.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION
        && constantError.getValue());
    coefDistance.visibleWhen(() -> angleSmooth.getValue() == ScaffoldAngleSmooth.ACCELERATION
        && dynamicAccel.getValue());

    simulateFailedOnly.visibleWhen(() -> simulatePlacement.getValue());
    autoBlockAlways.visibleWhen(() -> autoBlock.getValue());
    autoBlockMinCount.visibleWhen(() -> autoBlock.getValue());
    predictionCutoff.visibleWhen(() -> prediction.getValue());
    predictionWarmup.visibleWhen(() -> prediction.getValue());
    predictionBootstrap.visibleWhen(() -> prediction.getValue());
    ledgeEdge.visibleWhen(() -> ledge.getValue());
    accelMult.visibleWhen(() -> acceleration.getValue());
    strafeSpeed.visibleWhen(() -> strafe.getValue());
    speedLimit.visibleWhen(() -> speedLimiter.getValue());
    eagleBlocksMin.visibleWhen(() -> eagle.getValue());
    eagleBlocksMax.visibleWhen(() -> eagle.getValue());
    eagleEdgeMin.visibleWhen(() -> eagle.getValue());
    eagleEdgeMax.visibleWhen(() -> eagle.getValue());
    eagleOnlyGround.visibleWhen(() -> eagle.getValue());
    tellyReset.visibleWhen(() -> telly.getValue());
    tellyStraight.visibleWhen(() -> telly.getValue());
    tellyJump.visibleWhen(() -> telly.getValue());
    tellyAimOnTower.visibleWhen(() -> telly.getValue());
    blinkTimeMin.visibleWhen(() -> scaffoldBlink.getValue());
    blinkTimeMax.visibleWhen(() -> scaffoldBlink.getValue());
    blinkFlushPlace.visibleWhen(() -> scaffoldBlink.getValue());
    blinkFlushTower.visibleWhen(() -> scaffoldBlink.getValue());
    blinkFlushSneak.visibleWhen(() -> scaffoldBlink.getValue());
  }

  @Override
  public void onEnable() {
    EntityPlayerSP player = mc.thePlayer;
    lastSlot = player == null ? -1 : player.inventory.currentItem;
    serverReportedSlot = lastSlot;
    blockSlot = -1;
    pendingServerSlot = -1;
    blockCount = -1;
    placeDelayLeft = 0;
    lastSentYaw = Float.MIN_VALUE;
    lastSentPitch = Float.MIN_VALUE;
    placeLockedYaw = Float.MIN_VALUE;
    placeLockedPitch = Float.MIN_VALUE;
    lastPlaceRotDeltaYaw = -1.0f;
    steppedServerYaw = Float.NaN;
    steppedServerPitch = Float.NaN;
    scaffoldCycleTick = -1;
    wasTowerJump = false;
    towerColumnX = Integer.MIN_VALUE;
    towerColumnZ = Integer.MIN_VALUE;
    clearPending();
    features.onEnable(player);
    features.refreshEagle(eagleBlocksMin.getValue().intValue(), eagleBlocksMax.getValue().intValue(),
        eagleEdgeMin.getValue(), eagleEdgeMax.getValue());
    movementPlanner.reset();
    movementPrediction.reset();
    towers.reset();
    angleSmoothState.reset();
    forceSneak = false;
    forceJump = false;
    RotationState.reset();
    if (scaffoldBlink.getValue()) {
      configureBlink();
      blinkFeature.onEnable();
    }
    PacketEvents.register(this);
  }

  @Override
  public void onDisable() {
    EntityPlayerSP player = mc.thePlayer;
    int delay = Math.max(0, slotResetDelay.getValue().intValue());
    if (player != null && lastSlot >= 0 && lastSlot <= 8) {
      if (delay <= 0) {
        restoreHotbarSlot(player, lastSlot);
      } else {
        pendingSlotRestore = lastSlot;
        pendingSlotRestoreTicks = delay;
      }
      player.setSneaking(false);
    }
    serverReportedSlot = -1;
    blockSlot = -1;
    pendingServerSlot = -1;
    forceSneak = false;
    forceJump = false;
    wasTowerJump = false;
    towerColumnX = Integer.MIN_VALUE;
    towerColumnZ = Integer.MIN_VALUE;
    clearPending();
    features.reset();
    movementPlanner.reset();
    movementPrediction.reset();
    blinkFeature.onDisable();
    towers.reset();
    setTimerSpeed(1.0f);
    RotationState.reset();
    ScaffoldTargetFinding.setEyePos(null);
    PacketEvents.unregister(this);
  }

  private void restoreHotbarSlot(EntityPlayerSP player, int slot) {
    if (player == null || slot < 0 || slot > 8)
      return;
    player.inventory.currentItem = slot;
    notifyServerSlot(slot);
    if (mc.playerController instanceof IAccessorPlayerControllerMP)
      ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(slot);
  }

  /** Tick delayed slot restore while disabled. */
  public static void tickSlotReset() {
    if (pendingSlotRestore < 0)
      return;
    Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (module != null && module.isEnabled()) {
      pendingSlotRestore = -1;
      pendingSlotRestoreTicks = 0;
      return;
    }
    if (pendingSlotRestoreTicks > 0) {
      pendingSlotRestoreTicks--;
      return;
    }
    EntityPlayerSP player = mc.thePlayer;
    int slot = pendingSlotRestore;
    pendingSlotRestore = -1;
    if (player == null || slot < 0 || slot > 8)
      return;
    player.inventory.currentItem = slot;
    PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(slot));
    if (mc.playerController instanceof IAccessorPlayerControllerMP)
      ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(slot);
  }

  @Override
  public void onTick() {}

  public static void onPreUpdate(Object player) {
    tickSlotReset();
    Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (module instanceof ScaffoldModule && module.isEnabled())
      ((ScaffoldModule) module).preUpdate();
  }

  public static void onBeforeWalkingPlace(Object player) {
    Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (module instanceof ScaffoldModule && module.isEnabled())
      ((ScaffoldModule) module).beforeWalkingSyncLook();
  }

  private void preUpdate() {
    runCycle();
  }

  /**
   * After living move, before C03: place then let walking send C03 with the same look.
   * <p>1.8 Grim {@code Post} requires C08/C09 before the flying packet. Queued places apply
   * the following C03 look for RotationPlace post-flying.
   */
  private void beforeWalkingSyncLook() {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null)
      return;
    // LB airTicks: increment after move (same as LB LocalPlayer.move RETURN).
    features.updateTellyCounters(player, telly.getValue(), wasTowerJump);
    // Any pending C09 before C08/C03 this tick (never after flying — Grim Post).
    flushPendingServerSlot(player);

    if (pendingPlace && pendingTarget != null) {
      // Telly: skip normal ground places (KEEP: no underfoot-air emergency force-place).
      boolean underAir = features.tellyUnderAir(player);
      if (telly.getValue() && player.onGround && !wasTowerJump && !underAir) {
        pendingPlace = false;
        pendingTarget = null;
      } else {
        PlacementTarget live = pendingTarget;
        PlacementTarget refreshed = findCurrentTarget(player, wasTowerJump);
        if (refreshed != null)
          live = refreshed;
        if (!ScaffoldMath.isValidSupport(live.interactedBlockPos)) {
          pendingPlace = false;
          pendingTarget = null;
        } else {
          float useYaw = lastSentYaw != Float.MIN_VALUE ? lastSentYaw : ScaffoldMath.quantize(live.yaw);
          float usePitch = lastSentPitch != Float.MIN_VALUE ? lastSentPitch : ScaffoldMath.quantize(live.pitch);
          if (ScaffoldMath.findPlacementHit(player, live.interactedBlockPos, live.faceOrdinal,
              useYaw, usePitch) == null) {
            float moveYaw = telly.getValue() && !wasTowerJump
                ? ScaffoldMath.quantize(MoveFixUtil.movementFacingYaw()) : useYaw;
            float[] pitches = {
                usePitch,
                ScaffoldMath.quantize(live.pitch),
                ScaffoldMath.quantize(MathHelper.clamp_float(live.pitch + 5.0f, -90.0f, 90.0f)),
                ScaffoldMath.quantize(MathHelper.clamp_float(live.pitch - 5.0f, -90.0f, 90.0f)),
                85.0f, 75.0f, 65.0f
            };
            float found = Float.NaN;
            float foundYaw = useYaw;
            for (float tryYaw : new float[] { moveYaw, useYaw }) {
              for (float p : pitches) {
                if (ScaffoldMath.findPlacementHit(player, live.interactedBlockPos, live.faceOrdinal,
                    tryYaw, p) != null) {
                  found = p;
                  foundYaw = tryYaw;
                  break;
                }
              }
              if (!Float.isNaN(found))
                break;
            }
            if (!Float.isNaN(found)) {
              usePitch = found;
              useYaw = foundYaw;
            } else {
              pendingPlace = false;
              pendingTarget = null;
              useYaw = Float.MIN_VALUE;
            }
          }
          if (useYaw != Float.MIN_VALUE) {
            // Telly: keep movement yaw if pitch-only still rays after move (sprint heading).
            if (telly.getValue() && !wasTowerJump) {
              float moveYaw = ScaffoldMath.quantize(MoveFixUtil.movementFacingYaw());
              if (ScaffoldMath.findPlacementHit(player, live.interactedBlockPos, live.faceOrdinal,
                  moveYaw, usePitch) != null) {
                useYaw = moveYaw;
              }
            }
            // ON_TICK / ON_TICK_SNAP: snap to place look now (no pre-aim in runCycle).
            int timing = rotationTiming.getValue();
            if (timing == TIMING_ON_TICK || timing == TIMING_ON_TICK_SNAP) {
              float[] n = ScaffoldMath.normalizeFrom(
                  PlayerUpdateHook.lastReportedYaw(player), PlayerUpdateHook.lastReportedPitch(player),
                  live.yaw, live.pitch);
              useYaw = n[0];
              usePitch = n[1];
              if (telly.getValue() && !wasTowerJump) {
                float moveYaw = ScaffoldMath.quantize(MoveFixUtil.movementFacingYaw());
                if (ScaffoldMath.findPlacementHit(player, live.interactedBlockPos, live.faceOrdinal,
                    moveYaw, usePitch) != null)
                  useYaw = moveYaw;
              }
            }
            lastSentYaw = useYaw;
            lastSentPitch = usePitch;
            tickRotDeltaYaw = Math.abs(ScaffoldMath.wrapAngle(
                useYaw - PlayerUpdateHook.lastReportedYaw(player)));
            int mf = moveFix.getValue();
            if (ScaffoldMoveFix.writesCamera(mf)) {
              player.rotationYaw = useYaw;
              player.rotationPitch = usePitch;
            } else {
              armSilentLook(player, useYaw, usePitch);
              PlayerUpdateHook.ensureRotationApplied(player);
            }
            PlacementTarget aimed = new PlacementTarget(live.interactedBlockPos, live.placedBlockPos,
                live.faceOrdinal, live.hitVec, useYaw, usePitch, live.minPlacementY);
            if (tickRotDeltaYaw > 2.0f && lastPlaceRotDeltaYaw > 2.0f
                && Math.abs(tickRotDeltaYaw - lastPlaceRotDeltaYaw) < 0.0001f
                && !features.tellyUnderAir(player)) {
              // Skip duplicate yaw step — do not neutralize (unless falling).
            } else {
              tryPlace(player, aimed, false);
            }
            // ON_TICK_SNAP: hold after place. ON_TICK: no hold (no restore C03).
            if (timing == TIMING_ON_TICK_SNAP) {
              placeLockedYaw = useYaw;
              placeLockedPitch = usePitch;
            } else if (timing == TIMING_ON_TICK) {
              placeLockedYaw = Float.MIN_VALUE;
              placeLockedPitch = Float.MIN_VALUE;
            } else {
              placeLockedYaw = useYaw;
              placeLockedPitch = usePitch;
            }
          }
          pendingPlace = false;
          pendingTarget = null;
        }
      }
    } else if (pendingPlace) {
      pendingPlace = false;
      pendingTarget = null;
    }

    // WP11: SimulatePlacement in beforeWalking — same C08→C03 look-lock as tryPlace (XOR).
    if (pendingSim && pendingSimTarget != null && !placedThisTick
        && placementsThisTick < maxPlacements()) {
      trySimulatePlacement(player, pendingSimTarget);
    }
    pendingSim = false;
    pendingSimTarget = null;

    if (placeLockedYaw != Float.MIN_VALUE && placeLockedPitch != Float.MIN_VALUE) {
      int mf = moveFix.getValue();
      if (ScaffoldMoveFix.writesCamera(mf)) {
        player.rotationYaw = placeLockedYaw;
        player.rotationPitch = placeLockedPitch;
      } else {
        armSilentLook(player, placeLockedYaw, placeLockedPitch);
        PlayerUpdateHook.ensureRotationApplied(player);
      }
    }
  }

  private void runCycle() {
    if (mc.thePlayer == null || mc.theWorld == null
        || (considerInventory.getValue() && mc.currentScreen != null)) {
      scaffoldCycleTick = -1;
      forceSneak = false;
      forceJump = false;
      clearPending();
      RotationState.applyState(false, 0, 0, 0, -1);
      return;
    }

    EntityPlayerSP player = mc.thePlayer;
    WorldClient world = mc.theWorld;
    int tick = player.ticksExisted;
    if (tick == scaffoldCycleTick)
      return;
    scaffoldCycleTick = tick;
    placementsThisTick = 0;
    placedThisTick = false;
    features.advanceSprintPlaceFlag();
    jumpedThisTick = false;
    // Per-tick actuation — do not OR with previous tick (sticky jump bug).
    forceJump = false;
    forceSneak = false;
    features.forceJump = false;
    features.forceSneak = false;
    // WP9 OnTickSnap: keep place look across ticks (LB setRotationTarget keep).
    // Do not wipe SNAP hold without re-assert. OnTick/Normal: clear same-tick lock only.
    int timingEarly = rotationTiming.getValue();
    if (timingEarly == TIMING_ON_TICK_SNAP
        && placeLockedYaw != Float.MIN_VALUE && placeLockedPitch != Float.MIN_VALUE) {
      lastSentYaw = placeLockedYaw;
      lastSentPitch = placeLockedPitch;
      int mf = moveFix.getValue();
      if (ScaffoldMoveFix.writesCamera(mf)) {
        player.rotationYaw = placeLockedYaw;
        player.rotationPitch = placeLockedPitch;
      } else {
        armSilentLook(player, placeLockedYaw, placeLockedPitch);
      }
    } else {
      placeLockedYaw = Float.MIN_VALUE;
      placeLockedPitch = Float.MIN_VALUE;
    }
    clearPending();

    if (placeDelayLeft > 0)
      placeDelayLeft--;

    updateBlockSlot(player);
    boolean towerJump = isTowerJumpActive(player);
    // LB wasTowering: keep tower underfoot targeting until we land (jump may release mid-air).
    if (player.onGround)
      wasTowerJump = false;
    if (towerJump)
      wasTowerJump = true;
    boolean towerActive = wasTowerJump;
    // Telly locks timer at 1.0 for Simulation — but not while towering (Karhu/etc. own timer).
    if (telly.getValue() && !towerActive)
      setTimerSpeed(1.0f);
    else if (!telly.getValue())
      setTimerSpeed(timerSpeed.getValue());
    if (!towerActive) {
      towerColumnX = Integer.MIN_VALUE;
      towerColumnZ = Integer.MIN_VALUE;
    } else if (towerColumnX == Integer.MIN_VALUE) {
      towerColumnX = MathHelper.floor_double(player.posX);
      towerColumnZ = MathHelper.floor_double(player.posZ);
    }

    towers.tick(player, world, tower.getValue(), towerJump, Math.max(0, blockCount),
        towerMotion.getValue(), towerTriggerHeight.getValue(), towerSlow.getValue(),
        towerPulldownTrigger.getValue(), towerKarhuTimer.getValue(), towerKarhuTrigger.getValue(),
        towerKarhuPulldown.getValue());

    // Air ticks update post-move (LB). Only clear jump flag here.
    features.forceJump = false;
    features.updateHeadHitter(player, world, headHitter.getValue());
    boolean downFalloff = features.shouldGoDown(down.getValue(), player);
    features.updateEagle(player, world, eagle.getValue(), eagleOnlyGround.getValue(), downFalloff);

    movementPlanner.updateLine(player, MoveFixUtil.movementFacingYaw());
    if (movementPlanner.hasLine()) {
      ScaffoldTargetFinding.setOptimalLine(
          new Line3(movementPlanner.lineOrigin(), movementPlanner.lineDir()));
    } else {
      ScaffoldTargetFinding.setOptimalLine(null);
    }
    Vec3 predictedForSort = movementPrediction.getPredictedPlacementPos(player, world,
        movementPlanner, prediction.getValue(), predictionBootstrap.getValue(),
        predictionCutoff.getValue(), predictionWarmup.getValue().intValue());
    ScaffoldTargetFinding.setPredictedFeet(predictedForSort);
    // WP4: predicted crouch eye for face factories when eagle would sneak at predicted feet.
    Vec3 feet = predictedForSort != null
        ? predictedForSort
        : new Vec3(player.posX, player.posY, player.posZ);
    boolean predictedCrouch = eagle.getValue()
        && features.wouldEagle(player, world, feet, eagleOnlyGround.getValue(), downFalloff);
    float eyeH = predictedCrouch
        ? ScaffoldTargetFinding.EYE_CROUCHING
        : ScaffoldTargetFinding.EYE_STANDING;
    ScaffoldTargetFinding.setEyePos(new Vec3(feet.xCoord, feet.yCoord + eyeH, feet.zCoord));

    // Keep placementY fresh even while doNotAim (SameY lock for telly).
    int sameYMode = features.effectiveSameY(sameY.getValue(), telly.getValue());
    features.sameYTarget(player, sameYMode, towerActive, false);

    boolean doNotAim = features.tellyDoNotAimCompat(player, telly.getValue(),
        tellyStraight.getValue().intValue(), tellyJump.getValue().intValue(),
        towerActive, tellyAimOnTower.getValue());
    // LB: straight only skips aim/place — do not break doNotAim for underfoot air.
    boolean lookStraight = doNotAim && !towerActive;
    boolean underfootAir = telly.getValue() && features.tellyUnderAir(player);

    // Always resolve a target (LB). Straight only changes look, not whether we know the cell.
    PlacementTarget target = findCurrentTarget(player, towerActive);

    boolean rotationsReady = false;
    int timing = rotationTiming.getValue();
    if (lookStraight) {
      applyTellyResetRotation(player);
      // Keep stepped yaw — clearing here fought the MoveFix arm above.
    } else if (target != null) {
      if (timing == TIMING_NORMAL) {
        rotationsReady = applyRotations(player, target, false);
      } else {
        // ON_TICK / ON_TICK_SNAP: no pre-aim — snap only when placing (beforeWalking).
        rotationsReady = true;
      }
      if (technique.getValue() == TECH_GODBRIDGE && !towerActive) {
        GodBridgeTechnique.applyLedge(player, world, godBridgeLedgeMask(),
            Math.max(0, blockCount), godBridgeForceSneak.getValue().intValue(),
            rotationsReady, features);
      }
    } else {
      // Keep last aim while underfoot is solid — clearing caused a visible hitch each block.
      // ON_TICK: do not hold pre-aim.
      if (timing == TIMING_NORMAL
          && lastSentYaw != Float.MIN_VALUE && lastSentPitch != Float.MIN_VALUE) {
        int mf = moveFix.getValue();
        if (ScaffoldMoveFix.writesCamera(mf)) {
          player.rotationYaw = lastSentYaw;
          player.rotationPitch = lastSentPitch;
        } else {
          armSilentLook(player, lastSentYaw, lastSentPitch);
        }
      }
    }

    // LB ledge(): near edge + not ready / no blocks → sneak (still active during telly).
    features.updateLedge(player, world, ledge.getValue(), ledgeEdge.getValue(),
        rotationsReady && !lookStraight, blockCount);

    applySafeWalk(player, world);
    applyMotionFeatures(player);

    // SafeWalk may set static forceSneak; merge with eagle/ledge (LB keeps ledge on telly).
    forceSneak = forceSneak || features.forceSneak;
    if (forceSneak)
      player.setSneaking(true);

    // WP9 requires-sight: skip pending if ray from target rotation misses support.
    if (target != null && requiresSight.getValue()) {
      if (ScaffoldMath.findPlacementHit(player, target.interactedBlockPos, target.faceOrdinal,
          target.yaw, target.pitch) == null)
        target = null;
    }

    // LB: no place while doNotAim; place only when rotations are ready (no underfoot-air force).
    if (target != null && placeDelayLeft <= 0 && !lookStraight) {
      boolean groundBlock = telly.getValue() && !towerActive && player.onGround && !underfootAir;
      if (!groundBlock && rotationsReady && ensureUniquePlaceRotation(player, target)) {
        pendingTarget = target;
        pendingPlace = true;
      }
    }

    // WP11: simulate placement XOR — defer to beforeWalking (never two C08; same look-lock).
    if (!pendingPlace && simulatePlacement.getValue() && target != null && !lookStraight
        && placeDelayLeft <= 0 && placementsThisTick < maxPlacements()) {
      pendingSimTarget = target;
      pendingSim = true;
    } else {
      pendingSim = false;
      pendingSimTarget = null;
    }

    // Jump after rotations so RESET sees cleared aim (LB MovementInput after aim state).
    boolean rotationActive = lastSentYaw != Float.MIN_VALUE || RotationState.isActived();
    features.updateTellyJump(player, telly.getValue(), tellyJump.getValue().intValue(),
        tellyStraight.getValue().intValue(), tellyReset.getValue(), towerActive,
        rotationActive, Math.max(0, blockCount), underfootAir, lookStraight);
    forceJump = features.forceJump;

    if (scaffoldBlink.getValue() && !telly.getValue()) {
      configureBlink();
      blinkFeature.tickFlushGates(towerActive, player.isSneaking() || forceSneak, player.onGround);
    }
  }

  private int godBridgeLedgeMask() {
    switch (godBridgeLedge.getValue()) {
      case 1: return GodBridgeTechnique.LEDGE_SNEAK;
      case 2: return GodBridgeTechnique.LEDGE_STOP;
      case 3: return GodBridgeTechnique.LEDGE_BACK;
      case 4: return GodBridgeTechnique.LEDGE_JUMP | GodBridgeTechnique.LEDGE_SNEAK;
      case 0:
      default: return GodBridgeTechnique.LEDGE_JUMP;
    }
  }

  private void configureBlink() {
    int mask = 0;
    if (blinkFlushPlace.getValue())
      mask |= ScaffoldBlinkFeature.FLUSH_PLACE;
    if (blinkFlushTower.getValue())
      mask |= ScaffoldBlinkFeature.FLUSH_TOWERING;
    if (blinkFlushSneak.getValue())
      mask |= ScaffoldBlinkFeature.FLUSH_SNEAKING;
    blinkFeature.configure(blinkTimeMin.getValue().intValue(), blinkTimeMax.getValue().intValue(), mask);
  }

  private PlacementTarget findCurrentTarget(EntityPlayerSP player) {
    boolean towerJump = isTowerJumpActive(player);
    if (player.onGround)
      wasTowerJump = false;
    if (towerJump)
      wasTowerJump = true;
    if (!wasTowerJump) {
      towerColumnX = Integer.MIN_VALUE;
      towerColumnZ = Integer.MIN_VALUE;
    } else if (towerColumnX == Integer.MIN_VALUE) {
      towerColumnX = MathHelper.floor_double(player.posX);
      towerColumnZ = MathHelper.floor_double(player.posZ);
    }
    return findCurrentTarget(player, wasTowerJump);
  }

  private PlacementTarget findCurrentTarget(EntityPlayerSP player, boolean towerActive) {
    WorldClient world = mc.theWorld;
    boolean goDown = !towerActive && features.shouldGoDown(down.getValue(), player);
    // LB: jump bypasses SameY only when not moving (or wall) — keeps SameY during telly.
    boolean moving = MoveFixUtil.isForwardPressed()
        || Math.abs(player.moveForward) > 0.01f
        || Math.abs(player.moveStrafing) > 0.01f;
    boolean jumpHeld = mc.gameSettings.keyBindJump.isKeyDown();
    boolean jumpBypassSameY = !towerActive && jumpHeld && (!moving || player.isCollidedHorizontally);
    int sameYMode = features.effectiveSameY(sameY.getValue(), telly.getValue());
    BlockPos intended = features.sameYTarget(player, sameYMode, towerActive, jumpBypassSameY);
    if (intended == null)
      return null;

    if (towerActive) {
      // Sticky column + underfoot Y only — never drift into side pillars.
      int tx = towerColumnX != Integer.MIN_VALUE ? towerColumnX : MathHelper.floor_double(player.posX);
      int tz = towerColumnZ != Integer.MIN_VALUE ? towerColumnZ : MathHelper.floor_double(player.posZ);
      intended = new BlockPos(tx, MathHelper.floor_double(player.posY) - 1, tz);
    } else {
      intended = features.applyDown(intended, goDown);
      intended = features.applyCeiling(player, intended, ceiling.getValue());
      if (!telly.getValue()) {
        BlockPos under = intended;
        // Diagonal: never steal underfoot air — fill feet first, then side/corner steps.
        if (features.isDiagonalMove(player)) {
          if (under != null && ScaffoldMath.isReplaceable(under)) {
            intended = under;
          } else {
            BlockPos step = features.pickDiagonalStep(player, intended);
            if (step != null && ScaffoldMath.isReplaceable(step))
              intended = step;
          }
        } else {
          // LB: findPlacementTarget(predictedPos) — SameY Y kept; XZ from predicted feet.
          Vec3 pred = movementPrediction.getPredictedPlacementPos(player, world,
              movementPlanner, prediction.getValue(), predictionBootstrap.getValue(),
              predictionCutoff.getValue(), predictionWarmup.getValue().intValue());
          if (pred != null) {
            intended = new BlockPos(MathHelper.floor_double(pred.xCoord), intended.getY(),
                MathHelper.floor_double(pred.zCoord));
          }
        }
      }
    }

    float baseYaw = rotationBaseYaw(player);
    float basePitch = rotationBasePitch(player);

    // Telly: NORMAL offsets (DOWN when down); no gap-fill toward latestPlaced.
    if (telly.getValue() && !towerActive) {
      BlockPos under = features.bridgeCellUnder(player);
      if (under == null)
        under = intended;
      else
        under = new BlockPos(under.getX(), intended.getY(), under.getZ());
      if (prediction.getValue()) {
        Vec3 pred = movementPrediction.getPredictedPlacementPos(player, world,
            movementPlanner, true, predictionBootstrap.getValue(),
            predictionCutoff.getValue(), predictionWarmup.getValue().intValue());
        if (pred != null) {
          int px = MathHelper.floor_double(pred.xCoord);
          int pz = MathHelper.floor_double(pred.zCoord);
          int bx = MathHelper.floor_double(player.posX);
          int bz = MathHelper.floor_double(player.posZ);
          // Keep telly underfoot search local (one block) so we don't aim past the gap.
          if (Math.abs(px - bx) <= 1 && Math.abs(pz - bz) <= 1)
            under = new BlockPos(px, under.getY(), pz);
        }
      }
      int offsets = goDown ? ScaffoldTargetFinding.OFFSETS_DOWN : ScaffoldTargetFinding.OFFSETS_NORMAL;
      return ScaffoldTargetFinding.findTarget(player, under,
          offsets, aimMode.getValue(), baseYaw, basePitch, 0, false);
    }

    int tech = towerActive ? TECH_NORMAL : technique.getValue();
    ScaffoldTechnique impl = techniqueOf(tech);
    boolean underfootOnly = towerActive;
    // Tower: Center aim so pitch looks straight down at UP face.
    int aim = towerActive ? ScaffoldTargetFinding.AIM_CENTER : aimMode.getValue();
    ScaffoldTechnique.TechniqueContext ctx = new ScaffoldTechnique.TechniqueContext(
        aim, expandLength.getValue().intValue(), goDown, towerActive, underfootOnly);
    return impl.findTarget(player, intended, baseYaw, basePitch, ctx);
  }

  private static ScaffoldTechnique techniqueOf(int tech) {
    switch (tech) {
      case TECH_EXPAND: return ExpandTechnique.INSTANCE;
      case TECH_GODBRIDGE: return GodBridgeTechnique.INSTANCE;
      case TECH_BREEZILY: return BreezilyTechnique.INSTANCE;
      case TECH_NORMAL:
      default: return NormalTechnique.INSTANCE;
    }
  }

  private boolean applyRotations(EntityPlayerSP player, PlacementTarget target, boolean tellyDoNotAim) {
    if (target == null)
      return false;
    float targetYaw = target.yaw;
    float targetPitch = target.pitch;
    // Tower: always look down at the underfoot UP place (readable + OpenMyau-like).
    if (wasTowerJump) {
      targetPitch = Math.max(targetPitch, 82.0f);
      if (target.faceOrdinal == ScaffoldMath.FACE_UP)
        targetPitch = Math.max(targetPitch, 85.0f);
    }

    if (tellyDoNotAim && telly.getValue()) {
      applyTellyResetRotation(player);
      return false;
    }

    float baseYaw = rotationBaseYaw(player);
    float basePitch = rotationBasePitch(player);
    float sentYaw;
    float sentPitch;
    float hMax = horizontalTurnSpeedMax.getValue();
    float vMax = verticalTurnSpeedMax.getValue();
    float hMin = horizontalTurnSpeedMin.getValue();
    float vMin = verticalTurnSpeedMin.getValue();
    // Always run AngleSmooth — do not short-circuit when max=180 (that ignored lower mins).

    ScaffoldAngleSmooth.AccelOptions accelOpts = buildAccelOptions(target);

    // Telly: prefer movement-facing yaw + place pitch so MoveFix/C03 stay on sprint heading.
    // Unwrap movementFacing (±180) onto base so we never emit a ±360 yaw snap (AimModulo360).
    float moveYaw = ScaffoldMath.normalizeFrom(baseYaw, basePitch,
        ScaffoldMath.unwrapYaw(baseYaw, MoveFixUtil.movementFacingYaw()), basePitch)[0];
    boolean tellyPitchFirst = telly.getValue() && !wasTowerJump
        && ScaffoldMath.findPlacementHit(player, target.interactedBlockPos, target.faceOrdinal,
            moveYaw, targetPitch) != null;

    if (tellyPitchFirst) {
      float[] stepped = angleSmoothState.step(angleSmooth.getValue(), baseYaw, basePitch,
          moveYaw, targetPitch,
          hMin, hMax, vMin, vMax,
          sigmoidSteepness.getValue(), sigmoidMidpoint.getValue(),
          yawAccelerationMin.getValue(), yawAccelerationMax.getValue(),
          pitchAccelerationMin.getValue(), pitchAccelerationMax.getValue(), accelOpts);
      sentYaw = moveYaw;
      sentPitch = stepped[1];
    } else {
      float[] stepped = angleSmoothState.step(angleSmooth.getValue(), baseYaw, basePitch,
          targetYaw, targetPitch,
          hMin, hMax, vMin, vMax,
          sigmoidSteepness.getValue(), sigmoidMidpoint.getValue(),
          yawAccelerationMin.getValue(), yawAccelerationMax.getValue(),
          pitchAccelerationMin.getValue(), pitchAccelerationMax.getValue(), accelOpts);
      sentYaw = stepped[0];
      sentPitch = stepped[1];
    }

    int mf = moveFix.getValue();
    if (ScaffoldMoveFix.writesCamera(mf)) {
      player.rotationYaw = sentYaw;
      player.rotationPitch = sentPitch;
      lastSentYaw = sentYaw;
      lastSentPitch = sentPitch;
      tickRotDeltaYaw = Math.abs(ScaffoldMath.wrapAngle(sentYaw - PlayerUpdateHook.lastReportedYaw(player)));
      steppedServerYaw = sentYaw;
      steppedServerPitch = sentPitch;
      RotationState.applyState(false, 0, 0, 0, -1);
      return ScaffoldMath.findPlacementHit(player, target.interactedBlockPos, target.faceOrdinal,
          sentYaw, sentPitch) != null;
    }

    armSilentLook(player, sentYaw, sentPitch);
    tickRotDeltaYaw = Math.abs(ScaffoldMath.wrapAngle(
        sentYaw - PlayerUpdateHook.lastReportedYaw(player)));
    lastSentYaw = sentYaw;
    lastSentPitch = sentPitch;
    steppedServerYaw = sentYaw;
    steppedServerPitch = sentPitch;

    // Aim ready when the sent look rays the face; PositionPlace see-check is tryPlace-only.
    return ScaffoldMath.findPlacementHit(player, target.interactedBlockPos, target.faceOrdinal,
        sentYaw, sentPitch) != null;
  }

  /**
   * Exact KillAura {@code sendSilentRotation} contract:
   * requestRotation + {@code pervYaw = sentYaw} when MoveFix on, else {@code Mc.getYaw()};
   * priority 3 when on, {@code -1} when render-only (same as KA).
   */
  private void armSilentLook(EntityPlayerSP player, float yaw, float pitch) {
    PlayerUpdateHook.requestRotation(yaw, pitch);
    int mf = moveFix.getValue();
    boolean moveFixOn = ScaffoldMoveFix.armsPhysics(mf);
    // pervYaw must match C03 yaw (Grim Simulation). Prefer keeping yaw on movement when
    // the caller already chose movement-facing yaw (telly pitch-first aim).
    float pervYaw = moveFixOn ? yaw : gnu.client.runtime.mc.Mc.getYaw();
    int priority = moveFixOn ? (int) ROTATION_PRIORITY : -1;
    RotationState.applyState(true, yaw, pitch, pervYaw, priority);
  }

  private ScaffoldAngleSmooth.AccelOptions buildAccelOptions(PlacementTarget target) {
    ScaffoldAngleSmooth.AccelOptions opts = new ScaffoldAngleSmooth.AccelOptions();
    opts.accelerationError = accelerationError.getValue();
    opts.yawAccelError = yawAccelError.getValue();
    opts.pitchAccelError = pitchAccelError.getValue();
    opts.constantError = constantError.getValue();
    opts.yawConstantError = yawConstantError.getValue();
    opts.pitchConstantError = pitchConstantError.getValue();
    opts.sigmoidDeceleration = accelSigmoidDecel.getValue();
    opts.sigmoidSteepness = sigmoidSteepness.getValue();
    opts.sigmoidMidpoint = sigmoidMidpoint.getValue();
    opts.dynamicAccel = dynamicAccel.getValue();
    opts.coefDistance = coefDistance.getValue();
    opts.crosshair = false; // scaffold: no entity crosshair
    if (target != null && mc.thePlayer != null) {
      double dx = target.hitVec.xCoord - mc.thePlayer.posX;
      double dy = target.hitVec.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
      double dz = target.hitVec.zCoord - mc.thePlayer.posZ;
      opts.distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    return opts;
  }

  /**
   * WP11 LB SimulatePlacementAttempts — XOR with real place (never two C08 same tick).
   * Failed-only: right-click when the face would not accept a block.
   * <p>Any {@code onPlayerRightClick} sends C08 — arm place-look lock like {@link #tryPlace}.
   */
  private void trySimulatePlacement(EntityPlayerSP player, PlacementTarget target) {
    if (player == null || target == null || placedThisTick || placementsThisTick >= maxPlacements())
      return;
    ItemStack stack = getPlacementStack(player);
    if (!isPlaceableStack(stack))
      return;
    WorldClient world = mc.theWorld;
    if (world == null || !ScaffoldMath.isValidSupport(target.interactedBlockPos))
      return;
    float yaw = lastSentYaw != Float.MIN_VALUE ? lastSentYaw : ScaffoldMath.quantize(target.yaw);
    float pitch = lastSentPitch != Float.MIN_VALUE ? lastSentPitch : ScaffoldMath.quantize(target.pitch);
    Vec3 hit = ScaffoldMath.findPlacementHit(player, target.interactedBlockPos, target.faceOrdinal,
        yaw, pitch);
    if (hit == null)
      return;
    EnumFacing facing = target.facing();
    if (facing == null)
      return;
    boolean canPlace = ScaffoldMath.isPlacementTargetClear(target.interactedBlockPos, target.faceOrdinal)
        && !ScaffoldMath.wouldIntersectPlayer(player, target.interactedBlockPos, target.faceOrdinal)
        && ScaffoldMath.isReplaceable(target.placedBlockPos);
    if (simulateFailedOnly.getValue()) {
      if (canPlace)
        return;
    } else if (!canPlace) {
      return;
    }
    // Arm look before C08 so walking C03 matches (Grim RotationPlace / DuplicateRot).
    lastSentYaw = yaw;
    lastSentPitch = pitch;
    placeLockedYaw = yaw;
    placeLockedPitch = pitch;
    lastPlaceRotDeltaYaw = tickRotDeltaYaw;
    int mf = moveFix.getValue();
    if (ScaffoldMoveFix.writesCamera(mf)) {
      player.rotationYaw = yaw;
      player.rotationPitch = pitch;
    } else {
      armSilentLook(player, yaw, pitch);
      PlayerUpdateHook.ensureRotationApplied(player);
    }
    ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
    ensureClientBlockSlot(player);
    boolean placed = mc.playerController != null
        && mc.playerController.onPlayerRightClick(player, world, stack, target.interactedBlockPos,
            facing, hit);
    // C08 always leaves the controller — count as this tick's placement (XOR / maxPlacements).
    placementsThisTick++;
    placedThisTick = true;
    placeDelayLeft = randomPlaceDelay();
    features.onPlaced();
    if (placed) {
      if (swing.getValue())
        player.swingItem();
      blockCount--;
      ItemStack held = getPlacementStack(player);
      blockCount = Math.min(blockCount, isPlaceableStack(held) ? held.stackSize : 0);
      features.onEagleBlockPlacement(eagle.getValue(),
          eagleBlocksMin.getValue().intValue(), eagleBlocksMax.getValue().intValue(),
          eagleEdgeMin.getValue(), eagleEdgeMax.getValue());
      Vec3 fallOff = movementPrediction.getFallOffPositionOnLine(player, world, movementPlanner);
      movementPrediction.onPlace(player, movementPlanner, fallOff);
      movementPlanner.trackPlaced(target.placedBlockPos);
      if (scaffoldBlink.getValue() && !telly.getValue())
        blinkFeature.onBlockPlacement();
      if (strafeOnJump.getValue() && !player.onGround && jumpedThisTick)
        features.applyStrafe(player, true, strafeSpeed.getValue());
    }
  }

  private void applyTellyResetRotation(EntityPlayerSP player) {
    // Always keep MoveFix armed with movement-facing look — clearing state made the next
    // place-aim tick fight vanilla motion (Simulation 0.03→0.5).
    // AimModulo360: never assign ±180-wrapped movementFacing as absolute yaw — unwrap onto
    // last reported / stepped base (vanilla yaw accumulates past ±360).
    float baseYaw = rotationBaseYaw(player);
    float basePitch = rotationBasePitch(player);
    float desired = MoveFixUtil.movementFacingYaw();
    float yaw;
    float pitch;
    if (tellyReset.getValue() == ScaffoldFeatures.TELLY_REVERSE) {
      float snapped = Math.round(desired / 45.0f) * 45.0f;
      float[] n = ScaffoldMath.normalizeFrom(baseYaw, basePitch,
          ScaffoldMath.unwrapYaw(baseYaw, snapped),
          Math.max(45.0f, basePitch));
      yaw = n[0];
      pitch = n[1];
    } else {
      float[] n = ScaffoldMath.normalizeFrom(baseYaw, basePitch,
          ScaffoldMath.unwrapYaw(baseYaw, desired),
          lastSentPitch != Float.MIN_VALUE
              ? lastSentPitch
              : MathHelper.clamp_float(basePitch, 0.0f, 60.0f));
      yaw = n[0];
      pitch = n[1];
    }
    int mf = moveFix.getValue();
    if (ScaffoldMoveFix.writesCamera(mf)) {
      player.rotationYaw = yaw;
      player.rotationPitch = pitch;
      lastSentYaw = yaw;
      lastSentPitch = pitch;
      steppedServerYaw = yaw;
      steppedServerPitch = pitch;
      return;
    }
    armSilentLook(player, yaw, pitch);
    lastSentYaw = yaw;
    lastSentPitch = pitch;
    steppedServerYaw = yaw;
    steppedServerPitch = pitch;
  }

  /**
   * Finalize look for this tick's place + following C03.
   *
   * @param applySwap if true, post-move place — never change yaw (MoveFix already ran).
   */
  private boolean preparePlaceRotation(EntityPlayerSP player, PlacementTarget target, boolean applySwap) {
    if (target == null || player == null)
      return false;
    int timing = rotationTiming.getValue();
    boolean unlimited = ScaffoldAngleSmooth.canFullSnap(
        horizontalTurnSpeedMin.getValue(), horizontalTurnSpeedMax.getValue(),
        verticalTurnSpeedMin.getValue(), verticalTurnSpeedMax.getValue());
    if (!applySwap && (timing == TIMING_ON_TICK_SNAP || (timing == TIMING_ON_TICK && unlimited))) {
      float[] n = ScaffoldMath.normalizeFrom(
          PlayerUpdateHook.lastReportedYaw(player), PlayerUpdateHook.lastReportedPitch(player),
          target.yaw, target.pitch);
      float yaw = n[0];
      float pitch = n[1];
      int mf = moveFix.getValue();
      if (ScaffoldMoveFix.writesCamera(mf)) {
        player.rotationYaw = yaw;
        player.rotationPitch = pitch;
      } else {
        armSilentLook(player, yaw, pitch);
      }
      lastSentYaw = yaw;
      lastSentPitch = pitch;
      tickRotDeltaYaw = Math.abs(ScaffoldMath.wrapAngle(yaw - PlayerUpdateHook.lastReportedYaw(player)));
      steppedServerYaw = yaw;
      steppedServerPitch = pitch;
    }
    if (applySwap) {
      // Post-move: skip place if this C03 yaw step would duplicate last place (no yaw jitter here).
      if (tickRotDeltaYaw > 2.0f && lastPlaceRotDeltaYaw > 2.0f
          && Math.abs(tickRotDeltaYaw - lastPlaceRotDeltaYaw) < 0.0001f)
        return false;
    } else if (!ensureUniquePlaceRotation(player, target)) {
      return false;
    }
    if (applySwap)
      PlayerUpdateHook.ensureRotationApplied(player);
    return true;
  }

  private boolean tryPlace(EntityPlayerSP player, PlacementTarget target, boolean uniquifyNow) {
    if (target == null || player == null || placeDelayLeft > 0)
      return false;
    // Do not flush slots here after a prior place — only ensureClientBlockSlot before C08.
    ItemStack stack = getPlacementStack(player);
    if (!isPlaceableStack(stack) || stack.stackSize < autoBlockMinCount.getValue())
      return false;
    if (placementsThisTick >= maxPlacements())
      return false;
    WorldClient world = mc.theWorld;
    if (world == null)
      return false;
    if (!ScaffoldMath.isValidSupport(target.interactedBlockPos))
      return false;
    if (!ScaffoldMath.isPlacementTargetClear(target.interactedBlockPos, target.faceOrdinal))
      return false;
    if (ScaffoldMath.wouldIntersectPlayer(player, target.interactedBlockPos, target.faceOrdinal))
      return false;
    // Tower: never place off the sticky column (defense in depth vs side fills).
    if (wasTowerJump) {
      int bx = towerColumnX != Integer.MIN_VALUE ? towerColumnX : MathHelper.floor_double(player.posX);
      int bz = towerColumnZ != Integer.MIN_VALUE ? towerColumnZ : MathHelper.floor_double(player.posZ);
      if (target.placedBlockPos.getX() != bx || target.placedBlockPos.getZ() != bz)
        return false;
      if (target.faceOrdinal != ScaffoldMath.FACE_UP)
        return false;
    }

    // MinDist: reject side hits too close on XZ
    if (minDist.getValue() > 0.0f && target.faceOrdinal >= 2) {
      double dx = Math.abs(target.hitVec.xCoord - player.posX);
      double dz = Math.abs(target.hitVec.zCoord - player.posZ);
      if (Math.min(dx, dz) < minDist.getValue())
        return false;
    }

    if (uniquifyNow && !preparePlaceRotation(player, target, false))
      return false;

    float yaw = lastSentYaw;
    float pitch = lastSentPitch;
    if (yaw == Float.MIN_VALUE || pitch == Float.MIN_VALUE) {
      yaw = ScaffoldMath.quantize(target.yaw);
      pitch = ScaffoldMath.quantize(target.pitch);
    }

    Vec3 hitVec = ScaffoldMath.findPlacementHit(player, target.interactedBlockPos, target.faceOrdinal, yaw, pitch);
    // Fail-closed: never place with targeting hitVec alone (AirLiquidPlace → GroundSpoof).
    if (hitVec == null)
      return false;

    EnumFacing facing = target.facing();
    if (facing == null)
      return false;

    ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
    // Slot C09 first, then C08, then walking C03 — never C09 after C08 (PacketOrderE / Post).
    ensureClientBlockSlot(player);

    boolean placed = mc.playerController != null
        && mc.playerController.onPlayerRightClick(player, world, stack, target.interactedBlockPos, facing, hitVec);
    if (placed && swing.getValue())
      player.swingItem();
    if (placed) {
      placementsThisTick++;
      placedThisTick = true;
      // Grim DuplicateRotPlace: delta of the C03 that follows this C08 (1.8 queue).
      lastPlaceRotDeltaYaw = tickRotDeltaYaw;
      placeLockedYaw = yaw;
      placeLockedPitch = pitch;
      lastSentYaw = yaw;
      lastSentPitch = pitch;
      armSilentLook(player, yaw, pitch);
      blockCount--;
      ItemStack held = getPlacementStack(player);
      blockCount = Math.min(blockCount, isPlaceableStack(held) ? held.stackSize : 0);
      placeDelayLeft = randomPlaceDelay();
      features.onPlaced();
      features.onEagleBlockPlacement(eagle.getValue(),
          eagleBlocksMin.getValue().intValue(), eagleBlocksMax.getValue().intValue(),
          eagleEdgeMin.getValue(), eagleEdgeMax.getValue());
      Vec3 fallOff = movementPrediction.getFallOffPositionOnLine(player, world, movementPlanner);
      movementPrediction.onPlace(player, movementPlanner, fallOff);
      movementPlanner.trackPlaced(target.placedBlockPos);
      if (scaffoldBlink.getValue() && !telly.getValue())
        blinkFeature.onBlockPlacement();
      if (strafeOnJump.getValue() && !player.onGround && jumpedThisTick)
        features.applyStrafe(player, true, strafeSpeed.getValue());
      return true;
    }
    return false;
  }

  private boolean ensureUniquePlaceRotation(EntityPlayerSP player, PlacementTarget target) {
    if (!(tickRotDeltaYaw > 2.0f && lastPlaceRotDeltaYaw > 2.0f
        && Math.abs(tickRotDeltaYaw - lastPlaceRotDeltaYaw) < 0.0001f))
      return true;
    if (jitterPlaceYaw(player, target))
      return true;
    // Do NOT neutralize to lastReported (Δ≈0). Grim keeps rotated=true until a place;
    // the next place with the same Δx>2 flags DuplicateRotPlace x=0.0.
    return false;
  }

  private boolean jitterPlaceYaw(EntityPlayerSP player, PlacementTarget target) {
    if (lastSentYaw == Float.MIN_VALUE)
      return false;
    float reportedYaw = PlayerUpdateHook.lastReportedYaw(player);
    float reportedPitch = PlayerUpdateHook.lastReportedPitch(player);
    float gcd = (float) ScaffoldMath.mouseGcd();
    float step = Math.max(gcd, 0.15f);
    float sign = ScaffoldMath.wrapAngle(lastSentYaw - reportedYaw) >= 0 ? 1.0f : -1.0f;
    float current = Math.abs(ScaffoldMath.wrapAngle(lastSentYaw - reportedYaw));
    // Build deltas that survive GCD quantize and differ from last place (Grim xDiff < 1e-4).
    for (int i = 1; i <= 32; i++) {
      for (int dir : new int[] {1, -1}) {
        float d = current + dir * i * step;
        if (d <= 2.0f || d > 35.0f)
          continue;
        if (Math.abs(d - lastPlaceRotDeltaYaw) < 0.001f)
          continue;
        float newYaw = ScaffoldMath.quantize(reportedYaw + sign * d);
        float grim = Math.abs(ScaffoldMath.wrapAngle(newYaw - reportedYaw));
        if (grim <= 2.0f)
          continue;
        if (Math.abs(grim - lastPlaceRotDeltaYaw) < 0.001f)
          continue;
        if (Math.abs(newYaw - reportedYaw) > 320.0f)
          continue;
        // Keep MoveFix close to the pre-jitter aim.
        if (Math.abs(ScaffoldMath.wrapAngle(newYaw - lastSentYaw)) > 6.0f)
          continue;
        if (ScaffoldMath.findPlacementHit(player, target.interactedBlockPos, target.faceOrdinal,
            newYaw, lastSentPitch) == null)
          continue;
        applyJittered(player, newYaw, lastSentPitch, reportedYaw, reportedPitch);
        return true;
      }
    }
    return false;
  }

  private void applyJittered(EntityPlayerSP player, float yaw, float pitch, float reportedYaw, float reportedPitch) {
    int mf = moveFix.getValue();
    if (ScaffoldMoveFix.writesCamera(mf)) {
      player.rotationYaw = yaw;
      player.rotationPitch = pitch;
    } else {
      armSilentLook(player, yaw, pitch);
    }
    tickRotDeltaYaw = Math.abs(ScaffoldMath.wrapAngle(yaw - reportedYaw));
    lastSentYaw = yaw;
    lastSentPitch = pitch;
    steppedServerYaw = yaw;
    steppedServerPitch = pitch;
  }

  private void applySafeWalk(EntityPlayerSP player, WorldClient world) {
    // Down-scaffold: allow falling off edges (LB Down feature).
    if (features.shouldGoDown(down.getValue(), player)) {
      forceSneak = false;
      features.forceSneak = false;
      player.setSneaking(mc.gameSettings.keyBindSneak.isKeyDown());
      return;
    }
    // Telly: skip SafeWalk auto-sneak only — LB ledge/eagle sneak still applies after merge.
    if (telly.getValue()) {
      player.setSneaking(forceSneak || features.forceSneak || mc.gameSettings.keyBindSneak.isKeyDown());
      return;
    }
    boolean enable = safeWalk.getValue() && player.onGround && player.motionY <= 0.0
        && ScaffoldFeatures.nearEdge(player, world, 0.05f);
    if (enable)
      forceSneak = true;
    player.setSneaking(forceSneak || mc.gameSettings.keyBindSneak.isKeyDown());
  }

  private void applyMotionFeatures(EntityPlayerSP player) {
    // Never rewrite motion during telly — Grim Simulation (sprint/friction must stay vanilla).
    if (telly.getValue())
      return;
    features.applyAcceleration(player, acceleration.getValue(), accelMult.getValue());
    features.applyStrafe(player, strafe.getValue(), strafeSpeed.getValue());
  }

  private void clearSteppedRotation() {
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

  private int maxPlacements() {
    // One C08 per C03. Diagonal/multi second places → RotationPlace → AirLiquid cascade.
    return 1;
  }

  private int randomPlaceDelay() {
    int lo = delayMin.getValue().intValue();
    int hi = delayMax.getValue().intValue();
    if (hi < lo) {
      int t = lo;
      lo = hi;
      hi = t;
    }
    return Mc.randomInt(lo, hi);
  }

  private boolean isTowerJumpActive(EntityPlayerSP player) {
    if (player == null || tower.getValue() == ScaffoldTowers.NONE)
      return false;
    if (!mc.gameSettings.keyBindJump.isKeyDown())
      return false;
    return blockCount > 0 && isHoldingPlaceable(player);
  }

  private void clearPending() {
    pendingTarget = null;
    pendingPlace = false;
    pendingSimTarget = null;
    pendingSim = false;
  }

  private void updateBlockSlot(EntityPlayerSP player) {
    if (!autoBlock.getValue() && !itemSpoof.getValue()) {
      blockSlot = player.inventory.currentItem;
      ItemStack cur = player.inventory.getCurrentItem();
      blockCount = isPlaceableStack(cur) ? cur.stackSize : 0;
      return;
    }
    int best = -1;
    ItemStack bestStack = null;
    for (int i = 0; i < 9; i++) {
      ItemStack s = player.inventory.getStackInSlot(i);
      if (!isPlaceableStack(s))
        continue;
      if (s.stackSize < autoBlockMinCount.getValue())
        continue;
      if (bestStack == null || ScaffoldBlockSelection.HOTBAR_COMPARATOR.compare(s, bestStack) > 0) {
        bestStack = s;
        best = i;
      }
    }
    // Fallback: ignore min-count if nothing qualifies.
    if (best < 0) {
      for (int i = 0; i < 9; i++) {
        ItemStack s = player.inventory.getStackInSlot(i);
        if (!isPlaceableStack(s))
          continue;
        if (bestStack == null || ScaffoldBlockSelection.HOTBAR_COMPARATOR.compare(s, bestStack) > 0) {
          bestStack = s;
          best = i;
        }
      }
    }
    int bestCount = bestStack != null ? bestStack.stackSize : 0;
    blockSlot = best;
    blockCount = bestCount;
    if (best >= 0 && (autoBlockAlways.getValue() || autoBlock.getValue())) {
      // Keep gameplay hotbar on the block stack (render spoof swaps visually).
      player.inventory.currentItem = best;
      if (shouldSyncServerSlot(best))
        pendingServerSlot = best; // flushed in beforeWalking before C08/C03
      else if (mc.playerController instanceof IAccessorPlayerControllerMP)
        ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(best);
    }
  }

  private void ensureClientBlockSlot(EntityPlayerSP player) {
    if (blockSlot < 0 || blockSlot > 8)
      return;
    // C09 must be before C08 (and before C03). Never let syncCurrentPlayItem emit C09 mid-place.
    if (shouldSyncServerSlot(blockSlot)) {
      pendingServerSlot = blockSlot;
      flushPendingServerSlot(player);
    }
    player.inventory.currentItem = blockSlot;
    if (mc.playerController instanceof IAccessorPlayerControllerMP)
      ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(blockSlot);
  }

  private ItemStack getPlacementStack(EntityPlayerSP player) {
    if (blockSlot >= 0 && blockSlot <= 8)
      return player.inventory.getStackInSlot(blockSlot);
    return player.inventory.getCurrentItem();
  }

  private boolean isHoldingPlaceable(EntityPlayerSP player) {
    return isPlaceableStack(getPlacementStack(player));
  }

  private static boolean isPlaceableStack(ItemStack stack) {
    return ScaffoldBlockSelection.isValidBlock(stack);
  }

  private boolean shouldSyncServerSlot(int slot) {
    return slot != serverReportedSlot;
  }

  private void flushPendingServerSlot(EntityPlayerSP player) {
    if (pendingServerSlot < 0)
      return;
    int slot = pendingServerSlot;
    pendingServerSlot = -1;
    notifyServerSlot(slot);
  }

  private void notifyServerSlot(int slot) {
    if (!shouldSyncServerSlot(slot))
      return;
    // C09 while Grim still has placing=true → PacketOrderE (rightClicking).
    if (placedThisTick) {
      pendingServerSlot = slot;
      return;
    }
    sendingServerSlot = true;
    try {
      PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(slot));
    } finally {
      sendingServerSlot = false;
    }
    serverReportedSlot = slot;
    if (mc.playerController instanceof IAccessorPlayerControllerMP)
      ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(slot);
  }

  private void setTimerSpeed(float speed) {
    gnu.client.runtime.mc.Mc.setTimerSpeed(speed);
  }

  public int getSpoofSlot() {
    return lastSlot;
  }

  public boolean isItemSpoofEnabled() {
    return itemSpoof.getValue();
  }

  public int getBlockCount() {
    return Math.max(0, blockCount);
  }

  public boolean isBlockCounterEnabled() {
    return blockCounter.getValue();
  }

  boolean wantsTowerJumpInput() {
    return wasTowerJump && mc.thePlayer != null && mc.thePlayer.onGround;
  }

  boolean shouldForceImmediateTellyJump(EntityPlayerSP player) {
    return features.forceJump;
  }

  private boolean shouldSuppressSprint(EntityPlayerSP player) {
    return features.shouldSuppressSprint(sprintClient.getValue(), sprintServer.getValue(), player);
  }

  public static boolean shouldSuppressSprintKey() {
    Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (!(module instanceof ScaffoldModule) || !module.isEnabled())
      return false;
    ScaffoldModule scaffold = (ScaffoldModule) module;
    // Telly: never suppress sprint (full vanilla sprint speed).
    if (scaffold.telly.getValue())
      return false;
    return scaffold.shouldSuppressSprint(mc.thePlayer);
  }

  public static void patchMovementInput(Object movInputObj) {
    if (!(movInputObj instanceof MovementInput))
      return;
    MovementInput movInput = (MovementInput) movInputObj;
    Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    ScaffoldModule scaffold = module instanceof ScaffoldModule && module.isEnabled()
        ? (ScaffoldModule) module : null;

    boolean sneak = movInput.sneak || forceSneak;
    boolean jump = movInput.jump || forceJump || shouldForceImmediateTellyJumpStatic();
    if (shouldTowerJumpInput())
      jump = true;
    float forward = movInput.moveForward;
    float strafe = movInput.moveStrafe;

    if (scaffold != null && scaffold.features.speedLimitBlocksInput(mc.thePlayer,
        scaffold.speedLimiter.getValue(), scaffold.speedLimit.getValue())) {
      forward = 0;
      strafe = 0;
    }

    if (scaffold != null && scaffold.technique.getValue() == TECH_BREEZILY
        && scaffold.breezilyEdgeStrafe.getValue() && mc.thePlayer != null) {
      float edge = BreezilyTechnique.edgeStrafe(mc.thePlayer,
          scaffold.breezilyEdgeMin.getValue(), scaffold.breezilyEdgeMax.getValue());
      if (edge != 0.0f)
        strafe = edge;
    }

    if (scaffold != null && scaffold.technique.getValue() == TECH_GODBRIDGE
        && mc.thePlayer != null && mc.theWorld != null) {
      int mask = scaffold.godBridgeLedgeMask();
      if (GodBridgeTechnique.wantsStopInput(mask, mc.thePlayer, mc.theWorld, false,
          scaffold.getBlockCount())) {
        forward = 0;
        strafe = 0;
      } else if (GodBridgeTechnique.wantsStepBack(mask, mc.thePlayer, mc.theWorld, false,
          scaffold.getBlockCount())) {
        forward = -1.0f;
      }
    }

    if (scaffold != null && scaffold.stabilizeMovement.getValue() && !scaffold.telly.getValue()
        && mc.thePlayer != null) {
      ScaffoldMovementPlanner.MovementInputMut mut =
          new ScaffoldMovementPlanner.MovementInputMut(forward, strafe);
      scaffold.movementPlanner.stabilizeInput(mut);
      forward = mut.forward;
      strafe = mut.strafe;
    }

    if (scaffold != null
        && ScaffoldMoveFix.remapsInput(scaffold.moveFix.getValue())
        && MoveFixUtil.hasMoveFixPriority((int) ROTATION_PRIORITY)
        && MoveFixUtil.isForwardPressed()) {
      // KillAura uses Mc.getYaw() — same camera source avoids Simulation drip.
      float[] fixed = MoveFixUtil.fixStrafe(
          gnu.client.runtime.mc.Mc.getYaw(), RotationState.getSmoothedYaw(), sneak);
      forward = fixed[0];
      strafe = fixed[1];
    }

    if (scaffold != null && !scaffold.telly.getValue() && mc.thePlayer != null) {
      int clientMode = scaffold.sprintClient.getValue();
      int serverMode = scaffold.sprintServer.getValue();
      // Client: setSprinting / input-side sprint.
      if (scaffold.features.shouldForceSprint(clientMode))
        mc.thePlayer.setSprinting(true);
      else if (scaffold.features.shouldSuppressSprintMode(clientMode, mc.thePlayer))
        mc.thePlayer.setSprinting(false);
      // Server: Mc.setServerSprintState only.
      if (scaffold.features.shouldForceSprint(serverMode))
        Mc.setServerSprintState(mc.thePlayer, true);
      else if (scaffold.features.shouldSuppressSprintMode(serverMode, mc.thePlayer))
        Mc.setServerSprintState(mc.thePlayer, false);
    }

    movInput.moveForward = forward;
    movInput.moveStrafe = strafe;
    movInput.sneak = sneak;
    movInput.jump = jump;
    if (jump && scaffold != null)
      scaffold.jumpedThisTick = true;
  }

  /**
   * Living-move MoveFix resync removed — committed Scaffold/KA had none; the resync
   * path caused Simulation drip and regressed KA. MovementInput fixStrafe + HEAD
   * {@link #armSilentLook} match KillAura {@code sendSilentRotation} / {@code patchMovementInput}.
   */

  private static boolean shouldTowerJumpInput() {
    Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (!(module instanceof ScaffoldModule) || !module.isEnabled())
      return false;
    return ((ScaffoldModule) module).wantsTowerJumpInput();
  }

  private static boolean shouldForceImmediateTellyJumpStatic() {
    Module module = gnu.client.module.ModuleManager.instance().getModule("Scaffold");
    if (!(module instanceof ScaffoldModule) || !module.isEnabled())
      return false;
    return ((ScaffoldModule) module).shouldForceImmediateTellyJump(mc.thePlayer);
  }

  @Override
  public int sendPriority() {
    return 50;
  }

  @Override
  public boolean onReceive(Object packet) {
    return false;
  }

  @Override
  public boolean onSend(Object packet) {
    if (!isEnabled() || packet == null || gnu.client.runtime.packet.PacketUtil.isDispatching())
      return false;
    if (handleHotbarGuard(packet))
      return true;
    if (handleVulcanTower(packet))
      return true;
    if (scaffoldBlink.getValue() && !telly.getValue()) {
      EntityPlayerSP player = mc.thePlayer;
      boolean towering = player != null && wasTowerJump;
      boolean sneaking = player != null && (player.isSneaking() || forceSneak);
      boolean onGround = player != null && player.onGround;
      return blinkFeature.onSend(packet, towering, sneaking, onGround);
    }
    return false;
  }

  private boolean handleHotbarGuard(Object packet) {
    if (!PacketHelper.isHeldItemChange(packet) || sendingServerSlot)
      return false;
    if (blockSlot < 0 || blockSlot > 8 || blockCount <= 0)
      return false;
    EntityPlayerSP player = mc.thePlayer;
    if (player == null)
      return false;
    if (itemSpoof.getValue() && player.inventory.currentItem != blockSlot)
      return true;
    return false;
  }

  private boolean handleVulcanTower(Object packet) {
    if (tower.getValue() != ScaffoldTowers.VULCAN)
      return false;
    EntityPlayerSP player = mc.thePlayer;
    if (player == null || !isTowerJumpActive(player))
      return false;
    if (!ScaffoldTowers.isBlockBelow(player))
      return false;
    if (!towers.handleVulcanPacket(packet, player, tower.getValue(), true))
      return false;
    if (!(packet instanceof C03PacketPlayer))
      return false;
    if (!PacketHelper.c03HasPosition(packet))
      return false;
    // Nudge via reflection-free: cancel and send modified — PacketHelper helpers
    double x = PacketHelper.c03PosX(packet) + 0.1;
    double y = PacketHelper.c03PosY(packet);
    double z = PacketHelper.c03PosZ(packet) + 0.1;
    boolean ground = PacketHelper.c03OnGround(packet);
    PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, ground));
    return true;
  }
}
