package gnu.client.module.modules.player.scaffold;

import gnu.client.common.GnuLog;
import gnu.client.mixin.impl.accessors.IAccessorPlayerControllerMP;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.PlayerUpdateHook;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.Arrays;

/**
 * Silent scaffold bridge (KA-style rotations + MoveFix).
 *
 * <p>Item spoof (OpenMyau): gameplay {@code currentItem} stays on the block stack;
 * render shows the visual spoof slot via {@link gnu.client.runtime.ScaffoldItemSpoofHook}.
 */
public final class ScaffoldModule extends Module {

    private static final int KEEPY_OFF = 0;
    private static final int KEEPY_TELLY = 1;
    /** wsamiaw EXTRATELLY — Telly + EXTRA mid-air keep-Y clutch. */
    private static final int KEEPY_EXTRATELLY = 2;

    private final ModeSetting aim = addSetting(new ModeSetting("Aim", ScaffoldAim.AIM_BACKWARDS,
        Arrays.asList("Backwards", "GodBridge", "Nearest", "Sideways")));
    private final ModeSetting keepY = addSetting(new ModeSetting("KeepY", KEEPY_OFF,
        Arrays.asList("Off", "Telly", "ExtraTelly")));
    /** wsamiaw keep-y-on-press: KeepY only while use-item key (RMB) is held. */
    private final BoolSetting keepYOnPress = addSetting(new BoolSetting("KeepY on press", false));
    private final SliderSetting rotMin = addSetting(new SliderSetting("Rotation min", 60f, 1f, 100f, 1f));
    private final SliderSetting rotMax = addSetting(new SliderSetting("Rotation max", 80f, 1f, 100f, 1f));
    private final BoolSetting placeDebug = addSetting(new BoolSetting("Place debug", false));

    private int spoofSlot = -1;
    private int placeSlot = -1;
    /** Last hotbar index we believe the server has (skip redundant C09). */
    private int serverSlot = -1;
    private float lastSentYaw = Float.MIN_VALUE;
    private float lastSentPitch = Float.MIN_VALUE;
    /** |Δyaw| from last reported → this tick's pending C03 look. */
    private float tickRotDeltaYaw;
    /**
     * |Δyaw| of the last C03 we already sent (afterWalking). Grim DuplicateRotPlace
     * checks queued C08 against this — RotationUpdate for this tick runs after the place.
     */
    private float lastC03RotDeltaYaw = -1.0f;
    /** |Δyaw| Grim stored on the last place (previous C03 delta at place time). */
    private float lastPlaceRotDeltaYaw = -1.0f;
    private int placeYawNudgeSign = 1;
    private BlockPos lastPlacedPos;
    private boolean tellyLookForward;
    /**
     * OpenMyau {@code startY}: floor(posY) while last on ground. Telly places use
     * {@code startY - 1} so jump/sprint does not retarget a higher row (diagonal fall).
     */
    private int tellyStartY = Integer.MIN_VALUE;
    /**
     * wsamiaw {@code shouldKeepY}: ExtraTelly mid-air clutch — place at live under-feet
     * while falling back onto the keep-Y row.
     */
    private boolean shouldKeepY;
    /**
     * After Telly turn-back from look-forward, brief place delay unless look already rays.
     */
    private int tellyRotationTick;
    /** Edge-detect look-forward → turn-back so rotationTick arms once per jump. */
    private boolean tellyWasLookingForward;
    private ScaffoldTarget liveTarget;
    private boolean towering;
    /** HEAD vs beforeWalking — for place-debug lines only. */
    private String placePhase = "?";
    /** True after a successful place this client tick (OpenMyau: one PRE place window). */
    private boolean placedThisTick;

    public ScaffoldModule() {
        super("Scaffold", "Silent bridge scaffold with item spoof", Category.PLAYER);
        keepYOnPress.visibleWhen(() -> keepY.getIndex() != KEEPY_OFF);
    }

    /** Telly or ExtraTelly mode selected. */
    private boolean isTellyKeepY() {
        int i = keepY.getIndex();
        return i == KEEPY_TELLY || i == KEEPY_EXTRATELLY;
    }

    private boolean isExtraTelly() {
        return keepY.getIndex() == KEEPY_EXTRATELLY;
    }

    /**
     * wsamiaw KeepY stage gate: mode on, not holding jump, and if on-press then RMB held
     * ({@code PlayerUtil.isUsingItem} = use-key down with no screen).
     */
    private boolean isKeepYArmed() {
        if (!isTellyKeepY())
            return false;
        if (Mc.isJumpKeyHeld())
            return false;
        if (keepYOnPress.getValue()) {
            if (Mc.currentScreen() != null)
                return false;
            if (!Mc.isUseItemKeyDown())
                return false;
        }
        return true;
    }

    @Override
    public void onEnable() {
        EntityPlayerSP p = Mc.player();
        spoofSlot = p != null ? Mc.getHotbarSlot(p) : 0;
        serverSlot = spoofSlot;
        placeSlot = -1;
        lastSentYaw = lastSentPitch = Float.MIN_VALUE;
        tickRotDeltaYaw = 0.0f;
        lastC03RotDeltaYaw = -1.0f;
        lastPlaceRotDeltaYaw = -1.0f;
        placeYawNudgeSign = 1;
        lastPlacedPos = null;
        tellyLookForward = false;
        tellyStartY = Integer.MIN_VALUE;
        shouldKeepY = false;
        tellyRotationTick = 0;
        tellyWasLookingForward = false;
        liveTarget = null;
        towering = false;
        placedThisTick = false;
        // Clear sword-block / use so the first C09 is not during rightClicking.
        releaseUseIfNeeded(p);
    }

    /**
     * OpenMyau: cancel vanilla attack / RMB while Scaffold is on so KeepY-on-press can
     * read the use key without sending USE_ITEM (PacketOrderE/N).
     */
    public static boolean shouldCancelVanillaClick() {
        Module module = ModuleManager.instance().getModule("Scaffold");
        return module instanceof ScaffoldModule && module.isEnabled();
    }

    private static void releaseUseIfNeeded(EntityPlayerSP player) {
        if (player == null || !Mc.isUsingItem(player))
            return;
        Mc.stopSwordBlock(player);
    }

    /**
     * FastPlace-style: clear vanilla right-click delay early so PRE place can fire once
     * per tick after a successful place (vanilla would leave delay=4).
     */
    @Override
    public void onTickStart() {
        if (placeSlot < 0)
            return;
        Mc.clearRightClickDelay();
    }

    @Override
    public void onDisable() {
        clearRotationIfOwned();
        restoreHotbarToSpoof();
        liveTarget = null;
        placeSlot = -1;
        serverSlot = -1;
        lastPlacedPos = null;
        tellyLookForward = false;
        tellyStartY = Integer.MIN_VALUE;
        shouldKeepY = false;
        tellyRotationTick = 0;
        tellyWasLookingForward = false;
        towering = false;
    }

    public int getSpoofSlot() {
        return spoofSlot;
    }

    /** Sprint module / keybind must not keep sprint held while Scaffold bridges. */
    public static boolean shouldSuppressSprint() {
        Module module = ModuleManager.instance().getModule("Scaffold");
        if (!(module instanceof ScaffoldModule) || !module.isEnabled())
            return false;
        ScaffoldModule scaffold = (ScaffoldModule) module;
        // Telly look-forward (rise) sprints; place clutch stays walk-only.
        return !scaffold.tellyLookForward;
    }

    /**
     * Next XZ step leaves no supporting collision under the AABB (vanilla fall-off
     * prediction — used only for snap timing, never to fake sneak).
     */
    private static boolean wouldHangOverAir(EntityPlayerSP player, World world, double mx, double mz) {
        AxisAlignedBB bb = player.getEntityBoundingBox().offset(mx, -1.0, mz);
        return world.getCollidingBoundingBoxes(player, bb).isEmpty();
    }

    /** Critical place window: already over air, or this tick's motion hangs over air. */
    private static boolean isEdgeCritical(EntityPlayerSP player, World world, BlockPos underFeet) {
        if (player == null || world == null || underFeet == null)
            return false;
        if (ScaffoldPlace.isReplaceable(world, underFeet))
            return true;
        if (!player.onGround || player.motionY > 0.0)
            return false;
        return wouldHangOverAir(player, world, player.motionX, player.motionZ)
                || wouldHangOverAir(player, world, player.motionX * 1.5, player.motionZ * 1.5);
    }

    /**
     * Block under the bridge row. KeepY Telly/ExtraTelly locks to {@link #tellyStartY} - 1
     * while at/above that row (OpenMyau startY); ExtraTelly {@link #shouldKeepY} targets
     * {@code floor(posY) - 1} — one block above the keep-Y bridge (wsamiaw EXTRA clutch);
     * below keep row, clutch at live under-feet.
     */
    private BlockPos bridgeUnder(EntityPlayerSP player) {
        BlockPos live = ScaffoldPlace.underFeet(player);
        if (player == null || live == null)
            return live;
        if (!isKeepYArmed() || tellyStartY == Integer.MIN_VALUE)
            return live;
        // wsamiaw getBlockData when shouldKeepY: targetY = floor(posY) - 1 (elevated pad).
        if (shouldKeepY) {
            return new BlockPos(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY) - 1,
                MathHelper.floor_double(player.posZ));
        }
        BlockPos keep = new BlockPos(
            MathHelper.floor_double(player.posX),
            tellyStartY - 1,
            MathHelper.floor_double(player.posZ));
        if (live.getY() >= keep.getY())
            return keep;
        return live;
    }

    /**
     * wsamiaw EXTRA/EXTRATELLY clutch target — prefer UP onto the keep-Y bridge so the
     * placed block sits one above and you land higher.
     */
    private ScaffoldTarget findExtraKeepYTarget(EntityPlayerSP player, World world,
            BlockPos underFeet, float moveYaw) {
        EnumFacing moveFace = ScaffoldPlace.yawToFacing(moveYaw);
        ScaffoldTarget up = ScaffoldPlace.findNeighborTarget(
            player, world, underFeet, true, moveFace);
        if (up != null)
            return up;
        return ScaffoldPlace.findBridgeTarget(player, world, underFeet, moveYaw);
    }

    private void clearRotationIfOwned() {
        int p = (int) RotationState.getPriority();
        if (p == MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY)
            RotationState.reset();
    }

    private void restoreHotbarToSpoof() {
        EntityPlayerSP player = Mc.player();
        if (player == null || spoofSlot < 0 || spoofSlot > 8)
            return;
        selectHotbarSlot(player, spoofSlot, true);
    }

    /**
     * Keep gameplay on {@code slot}. Sync only when the controller disagrees — a redundant
     * C09 after a place/use in the same tick is Grim PacketOrderE (rightClicking=true).
     */
    private void selectHotbarSlot(EntityPlayerSP player, int slot, boolean syncServer) {
        if (player == null || slot < 0 || slot > 8)
            return;
        if (Mc.getHotbarSlot(player) != slot)
            Mc.setHotbarSlot(player, slot);
        if (!syncServer)
            return;
        PlayerControllerMP controller = Mc.controller();
        if (controller instanceof IAccessorPlayerControllerMP) {
            IAccessorPlayerControllerMP acc = (IAccessorPlayerControllerMP) controller;
            if (acc.getCurrentPlayerItem() != slot)
                acc.invokeSyncCurrentPlayItem();
        }
        serverSlot = slot;
    }

    public static void onPreUpdate(Object player) {
        Module module = ModuleManager.instance().getModule("Scaffold");
        if (module instanceof ScaffoldModule && module.isEnabled())
            ((ScaffoldModule) module).preUpdate(player);
    }

    public static void onBeforeWalkingPlace(Object player) {
        Module module = ModuleManager.instance().getModule("Scaffold");
        if (module instanceof ScaffoldModule && module.isEnabled())
            ((ScaffoldModule) module).beforeWalkingPlace(player);
    }

    public static void onAfterWalking(Object player) {
        Module module = ModuleManager.instance().getModule("Scaffold");
        if (module instanceof ScaffoldModule && module.isEnabled())
            ((ScaffoldModule) module).afterWalking(player);
    }

    public static void patchMovementInput(Object movInput) {
        Module module = ModuleManager.instance().getModule("Scaffold");
        if (module instanceof ScaffoldModule && module.isEnabled())
            ((ScaffoldModule) module).doPatchMovementInput(movInput);
    }

    private void preUpdate(Object playerObj) {
        placedThisTick = false;
        EntityPlayerSP player = resolvePlayer(playerObj);
        if (player == null) {
            clearRotationIfOwned();
            liveTarget = null;
            tellyLookForward = false;
            towering = false;
            placeSlot = -1;
            return;
        }

        placeSlot = ScaffoldBlocks.pickBestHotbarSlot(player);
        if (placeSlot < 0) {
            clearRotationIfOwned();
            liveTarget = null;
            tellyLookForward = false;
            towering = false;
            return;
        }

        // KeepY-on-press holds RMB — release any leftover use before C09/place.
        releaseUseIfNeeded(player);

        // OpenMyau: gameplay stays on the block stack; render spoof shows spoofSlot.
        selectHotbarSlot(player, placeSlot, true);

        World world = Mc.world();
        if (world == null) {
            clearRotationIfOwned();
            liveTarget = null;
            tellyLookForward = false;
            towering = false;
            return;
        }

        // Place didn't stick (server resync / ghost) — allow retry. Log for Simulation forensics.
        if (lastPlacedPos != null && ScaffoldPlace.isReplaceable(world, lastPlacedPos)) {
            placeDebugLog("lastPlacedPos cleared (dest air again) " + lastPlacedPos);
            lastPlacedPos = null;
        }

        // wsamiaw: on ground refresh startY unless EXTRA clutch kept it; clear shouldKeepY.
        if (player.onGround) {
            if (!shouldKeepY)
                tellyStartY = MathHelper.floor_double(player.posY);
            shouldKeepY = false;
        } else if (!isKeepYArmed()) {
            tellyStartY = Integer.MIN_VALUE;
        }

        BlockPos underFeet = bridgeUnder(player);
        boolean jumpHeld = Mc.isJumpKeyHeld()
                || (player.movementInput != null && player.movementInput.jump);
        float moveYaw = MoveFixUtil.movementFacingYaw();
        boolean hanging = ScaffoldPlace.isReplaceable(world, underFeet);
        boolean edgeCritical = isEdgeCritical(player, world, underFeet);
        boolean keepYArmed = isKeepYArmed();

        towering = false;
        // Telly sets movementInput.jump for auto-hop — that must NOT enter the tower branch
        // (it was clearing tellyLookForward every jump → instant turn-back).
        boolean towerJump = Mc.isJumpKeyHeld();
        if (jumpHeld && !towerJump && keepYArmed)
            jumpHeld = false; // ignore telly-injected jump for look/tower state
        if (jumpHeld || towerJump) {
            liveTarget = ScaffoldPlace.findTowerTarget(player, world);
            tellyLookForward = false;
            towering = liveTarget != null && liveTarget.face == EnumFacing.UP;
            if (liveTarget == null)
                liveTarget = ScaffoldPlace.findBridgeTarget(player, world, underFeet, moveYaw);
        } else if (keepYArmed) {
            // Telly / ExtraTelly look: forward through the rise, turn back near apex.
            if (tellyRotationTick > 0)
                tellyRotationTick--;
            boolean needsBridge = needsBridgeExtension(world, underFeet);
            boolean tellyJumpGround = player.onGround
                    && MoveFixUtil.isForwardPressed()
                    && needsBridge;
            // Vanilla jump peak is ~0.42 → 0; hold forward past apex a bit (later turn-back).
            final double turnBackMotionY = 0.02;
            if (tellyJumpGround) {
                tellyLookForward = true;
                liveTarget = null;
            } else if (!player.onGround && player.motionY > turnBackMotionY) {
                tellyLookForward = true;
                liveTarget = null;
            } else if (!player.onGround) {
                tellyLookForward = false;
                liveTarget = ScaffoldPlace.findBridgeTarget(player, world, underFeet, moveYaw);
                if (tellyWasLookingForward) {
                    tellyRotationTick = Math.max(tellyRotationTick, 2);
                    tellyWasLookingForward = false;
                }
            } else {
                tellyLookForward = false;
                liveTarget = ScaffoldPlace.findBridgeTarget(player, world, underFeet, moveYaw);
                tellyWasLookingForward = false;
            }
            if (tellyLookForward)
                tellyWasLookingForward = true;

            // wsamiaw EXTRA/EXTRATELLY: at end of jump, place one block ABOVE keep-Y
            // (UP onto the bridge) so you land higher and cover more distance.
            if (isExtraTelly() && !player.onGround && tellyStartY != Integer.MIN_VALUE) {
                int nextBlockY = MathHelper.floor_double(player.posY + player.motionY);
                if (nextBlockY <= tellyStartY && player.posY > (double) (tellyStartY + 1)) {
                    shouldKeepY = true;
                    underFeet = bridgeUnder(player);
                    hanging = ScaffoldPlace.isReplaceable(world, underFeet);
                    edgeCritical = isEdgeCritical(player, world, underFeet);
                    tellyLookForward = false;
                    tellyRotationTick = 0;
                    liveTarget = findExtraKeepYTarget(player, world, underFeet, moveYaw);
                    placeDebugLog("EXTRA clutch under=" + underFeet
                            + " startY=" + tellyStartY
                            + " tgt=" + formatTarget(liveTarget));
                }
            }
        } else {
            tellyLookForward = false;
            tellyRotationTick = 0;
            tellyWasLookingForward = false;
            liveTarget = ScaffoldPlace.findBridgeTarget(player, world, underFeet, moveYaw);
        }

        // Walk bridge / place clutch: no sprint. Telly look-forward (rise): sprint.
        if (tellyLookForward && MoveFixUtil.isForwardPressed()) {
            Mc.setSprintKeyState(true);
            player.setSprinting(true);
        } else {
            player.setSprinting(false);
            Mc.setSprintKeyState(false);
        }

        float baseYaw = lastSentYaw != Float.MIN_VALUE
            ? lastSentYaw
            : PlayerUpdateHook.lastReportedYaw(player);
        float basePitch = lastSentPitch != Float.MIN_VALUE
            ? lastSentPitch
            : PlayerUpdateHook.lastReportedPitch(player);

        // Backwards/Sideways: never accept forward hemisphere — including hang clutch
        // (disabling this made post-place looks snap forward and stick via hold-look).
        boolean hardAway = (aim.getIndex() == ScaffoldAim.AIM_BACKWARDS
                || aim.getIndex() == ScaffoldAim.AIM_SIDEWAYS)
                && !towering;

        // Aim from current eye only. Predicted-eye aim + preUpdate place caused Grim
        // RotationPlace (look hits predicted pos, post-flying checks real pos) → resync
        // ghosts → Simulation / GroundSpoof cascade.
        float[] raw;
        if (tellyLookForward) {
            raw = new float[] { moveYaw, 20f };
        } else if (liveTarget != null
                && lastSentYaw != Float.MIN_VALUE
                && ScaffoldPlace.findPlacementHit(
                    player, liveTarget.support, liveTarget.face, lastSentYaw, lastSentPitch) != null
                && (!hardAway || Math.abs(ScaffoldRotations.wrap(lastSentYaw - moveYaw)) >= 90f)) {
            raw = new float[] { lastSentYaw, lastSentPitch };
        } else if (liveTarget != null) {
            Vec3 eye = new Vec3(
                player.posX, player.posY + player.getEyeHeight(), player.posZ);
            Vec3 hitPrefer = ScaffoldAim.nearestPointOnFace(
                liveTarget.support, liveTarget.face, eye);
            if (hitPrefer == null)
                hitPrefer = ScaffoldPlace.faceCenter(liveTarget.support, liveTarget.face);
            raw = ScaffoldAim.compute(
                aim.getIndex(),
                moveYaw,
                baseYaw,
                basePitch,
                player,
                liveTarget,
                hitPrefer);
            if (towering || liveTarget.face == EnumFacing.UP) {
                float[] toFace = ScaffoldPlace.rotationsTo(hitPrefer, player, baseYaw, basePitch);
                if (ScaffoldPlace.findPlacementHit(
                        player, liveTarget.support, liveTarget.face, toFace[0], toFace[1]) != null
                        && (!hardAway || Math.abs(ScaffoldRotations.wrap(toFace[0] - moveYaw)) >= 90f))
                    raw = toFace;
                else {
                    float[] hit = ScaffoldPlace.findHittingLook(
                        player, liveTarget.support, liveTarget.face, toFace[0], toFace[1],
                        moveYaw, hardAway);
                    if (hit != null)
                        raw = hit;
                }
            }
            if (hardAway && Math.abs(ScaffoldRotations.wrap(raw[0] - moveYaw)) < 90f) {
                float[] away = ScaffoldPlace.findHittingLook(
                    player, liveTarget.support, liveTarget.face,
                    ScaffoldAim.backwardsYaw(moveYaw), raw[1], moveYaw, true);
                if (away != null)
                    raw = away;
                else
                    raw = new float[] { ScaffoldAim.backwardsYaw(moveYaw), raw[1] };
            }
        } else if (lastSentYaw != Float.MIN_VALUE
                && (!hardAway || Math.abs(ScaffoldRotations.wrap(lastSentYaw - moveYaw)) >= 90f)) {
            raw = new float[] { lastSentYaw, lastSentPitch };
        } else if (hardAway) {
            raw = new float[] { ScaffoldAim.backwardsYaw(moveYaw), 80f };
        } else {
            return;
        }

        // Keep absolute yaw continuous (KA-style). Wrapping to ±180 makes Grim see
        // ~340° snaps (AimModulo360) and mismatches DuplicateRotPlace's raw |Δyaw|.
        raw[0] = ScaffoldRotations.continuous(baseYaw, raw[0]);

        int speed = ScaffoldRotations.sampleSpeed(
            Math.round(rotMin.getValue()), Math.round(rotMax.getValue()));
        float yawNeed = Math.abs(ScaffoldRotations.wrap(raw[0] - baseYaw));
        boolean onTarget = liveTarget != null && !tellyLookForward
                && ScaffoldPlace.findPlacementHit(
                    player, liveTarget.support, liveTarget.face, raw[0], raw[1]) != null
                && yawNeed < 0.01f
                && Math.abs(raw[1] - basePitch) < 0.01f;
        // fixStrafe is 8×45° buckets: mid-turn silent yaw (~90° from move) becomes pure
        // strafe → Telly sprint drifts off the line. Snap large yaw changes; keep user
        // rot speed only for small settle / tracking.
        final float snapYawDeg = 30f;
        if (onTarget)
            speed = 100;
        else if (tellyLookForward || yawNeed > snapYawDeg)
            speed = 100;
        else if (liveTarget != null && !tellyLookForward
                && (towering || hanging || edgeCritical || liveTarget.face == EnumFacing.UP))
            speed = 100;

        float[] sent = onTarget
            ? new float[] { raw[0], raw[1] }
            : ScaffoldRotations.stepToward(baseYaw, basePitch, raw[0], raw[1], speed);

        if (!onTarget && liveTarget != null && !tellyLookForward
                && ScaffoldPlace.findPlacementHit(
                    player, liveTarget.support, liveTarget.face, sent[0], sent[1]) == null) {
            float[] hitLook = ScaffoldPlace.findHittingLook(
                player, liveTarget.support, liveTarget.face, raw[0], raw[1], moveYaw, hardAway);
            if (hitLook != null)
                sent = ScaffoldRotations.stepToward(baseYaw, basePitch, hitLook[0], hitLook[1], 100);
        }
        // If a partial step still lands in a strafe bucket, finish the yaw snap.
        if (!tellyLookForward && MoveFixUtil.isForwardPressed()
                && isFixStrafeDriftYaw(moveYaw, sent[0])
                && !isFixStrafeDriftYaw(moveYaw, raw[0])) {
            sent = ScaffoldRotations.stepToward(baseYaw, basePitch, raw[0], raw[1], 100);
        }

        float reportedYaw = PlayerUpdateHook.lastReportedYaw(player);
        lastSentYaw = sent[0];
        lastSentPitch = ScaffoldRotations.clampPitch(sent[1]);
        // Grim DuplicateRotPlace / AimModulo360 use raw yaw - lastYaw (no wrap).
        tickRotDeltaYaw = Math.abs(lastSentYaw - reportedYaw);
        // Identical yaw → no look C03 → Grim keeps the previous large Δyaw for the next
        // queued place (DuplicateRotPlace). Always emit a tiny look while scaffolding.
        // Skip during Telly look-forward — no place, and yaw jitter causes sprint drift.
        if (!tellyLookForward) {
            if (tickRotDeltaYaw < 1.0e-4f)
                forceMicroLook(player, reportedYaw);
            ensurePendingC03Unique(player);
        }
        PlayerUpdateHook.requestRotation(lastSentYaw, lastSentPitch);
        // pervYaw must equal C03/sent yaw (KA + OpenMyau). Camera-forward W comes from
        // formStrafe(camera, sentYaw) + moveFlying(sentYaw) — not from a separate move yaw.
        // Using movementFacing here while C03 looked back → Grim Simulation on forward bridge.
        RotationState.applyState(
            true, lastSentYaw, lastSentPitch, lastSentYaw, MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY);

        // OpenMyau PRE hang clutch: place at onUpdate HEAD while already over air,
        // BEFORE this tick's living move — SafeWalk not required.
        // Telly: wait rotationTick (turn-back C03s) before place — OpenMyau rotationTick<=0.
        // Never place ahead-on-solid here (predicted-eye RotationPlace cascade).
        if (!tellyLookForward && liveTarget != null && (hanging || towering)) {
            WorldClient wc = Mc.world();
            if (wc != null && Mc.controller() != null) {
                placePhase = "HEAD";
                // OpenMyau waits rotationTick before place, but with sprint we overshoot.
                // Place early once turn-back look already rays the target from current eyes.
                // ExtraTelly elevated clutch must not wait on telly turn-back.
                boolean tellyWait = isKeepYArmed() && !shouldKeepY && tellyRotationTick > 0
                        && !tellyLookReady(player, liveTarget);
                if (tellyWait) {
                    placeDebugLog("HEAD telly wait rotationTick=" + tellyRotationTick
                            + " under=" + underFeet + " tgt=" + formatTarget(liveTarget));
                } else {
                    selectHotbarSlot(player, placeSlot, true);
                    if (towering) {
                        boolean airborne = !player.onGround || hanging || player.motionY > 0.05;
                        placeDebugLog("HEAD tower try under=" + underFeet
                                + " tgt=" + formatTarget(liveTarget)
                                + " hanging=" + hanging
                                + " airborne=" + airborne
                                + " yaw=" + fmt(lastSentYaw) + " move=" + fmt(moveYaw));
                        if (airborne)
                            tryPlaceOnce(player, wc, underFeet, true, false);
                        else
                            placeDebugLog("HEAD tower skip grounded");
                    } else if (hanging) {
                        placeDebugLog("HEAD hang try under=" + underFeet
                                + " tgt=" + formatTarget(liveTarget)
                                + " yaw=" + fmt(lastSentYaw) + " move=" + fmt(moveYaw)
                                + " hardAway=" + hardAway
                                + " onGround=" + player.onGround
                                + " mot=" + fmt(player.motionX) + "," + fmt(player.motionZ));
                        tryHangPlaces(player, wc, underFeet, hardAway);
                    }
                }
            }
        } else if (placeDebug.getValue() && !tellyLookForward && (hanging || edgeCritical)) {
            placePhase = "HEAD";
            placeDebugLog("HEAD skip-place hanging=" + hanging
                    + " towering=" + towering
                    + " target=" + (liveTarget != null)
                    + " edgeCrit=" + edgeCritical
                    + " under=" + underFeet
                    + " tgt=" + formatTarget(liveTarget));
        }
    }

    /**
     * OpenMyau PRE: place after silent look is applied, before C03 (C08 then C03).
     * Hang clutch mirrors OpenMyau getBlockData + multiplace — do not wait for lastReported hit.
     */
    private void beforeWalkingPlace(Object playerObj) {
        if (tellyLookForward)
            return;
        if (placeSlot < 0)
            return;

        EntityPlayerSP player = resolvePlayer(playerObj);
        if (player == null)
            return;

        if (RotationState.isActived()
                && (int) RotationState.getPriority() != MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY)
            return;

        WorldClient world = Mc.world();
        if (world == null || Mc.controller() == null)
            return;

        BlockPos underFeet = bridgeUnder(player);
        boolean hanging = ScaffoldPlace.isReplaceable(world, underFeet);
        boolean edgeCritical = isEdgeCritical(player, world, underFeet);
        boolean hardAway = (aim.getIndex() == ScaffoldAim.AIM_BACKWARDS
                || aim.getIndex() == ScaffoldAim.AIM_SIDEWAYS)
                && !towering;

        placePhase = "WALK";

        if (isKeepYArmed() && !shouldKeepY && tellyRotationTick > 0
                && !tellyLookReady(player, liveTarget)) {
            placeDebugLog("WALK telly wait rotationTick=" + tellyRotationTick
                    + " under=" + underFeet);
            return;
        }

        // FastPlace rhythm when not clutching; hang/tower never wait on delay.
        // OpenMyau only places when under-feet is replaceable (or tower) — no ahead-on-solid.
        if (!hanging && !towering && Mc.getRightClickDelay() > 0) {
            placeDebugLog("WALK skip delay=" + Mc.getRightClickDelay()
                    + " under=" + underFeet);
            return;
        }

        PlayerUpdateHook.ensureRotationApplied(player);
        selectHotbarSlot(player, placeSlot, true);

        // Post-move re-resolve. Tower must keep UP under-feet — findBridgeTarget was
        // overwriting to horizontal ahead and tryHangPlaces rejected UP → no tower.
        float moveYaw = MoveFixUtil.movementFacingYaw();
        if (towering) {
            liveTarget = ScaffoldPlace.findTowerTarget(player, world);
            if (liveTarget == null)
                liveTarget = ScaffoldPlace.findBridgeTarget(player, world, underFeet, moveYaw);
        } else {
            liveTarget = ScaffoldPlace.findBridgeTarget(player, world, underFeet, moveYaw);
        }
        if (liveTarget == null) {
            placeDebugLog("WALK no-target hanging=" + hanging
                    + " towering=" + towering
                    + " under=" + underFeet
                    + " onGround=" + player.onGround);
            return;
        }

        if (towering) {
            // Grounded UP-into-feet clicks fail vanilla but still emit C08 → Grim spam.
            // OpenMyau-style: place tower only while airborne / already over air.
            boolean airborne = !player.onGround || hanging || player.motionY > 0.05;
            placeDebugLog("WALK tower try under=" + underFeet
                    + " tgt=" + formatTarget(liveTarget)
                    + " hanging=" + hanging
                    + " airborne=" + airborne
                    + " placedThisTick=" + placedThisTick
                    + " yaw=" + fmt(lastSentYaw)
                    + " move=" + fmt(moveYaw));
            if (!airborne)
                placeDebugLog("WALK tower skip grounded");
            else if (!placedThisTick)
                tryPlaceOnce(player, world, underFeet, true, false);
            else
                placeDebugLog("WALK tower skip already-placed");
        } else if (hanging) {
            // OpenMyau only places in UpdateEvent PRE (= HEAD). Same-tick WALK hang
            // sees lastReported still on the support top → canSeeFace side-face fail,
            // but only after reaim — that look flicker → RotationPlace. Next HEAD clutches.
            placeDebugLog("WALK hang skip (PRE-only) under=" + underFeet
                    + " placedThisTick=" + placedThisTick
                    + " tgt=" + formatTarget(liveTarget));
        } else {
            placeDebugLog("WALK skip solid (no ahead-place) under=" + underFeet
                    + " edgeCrit=" + edgeCritical
                    + " tgt=" + formatTarget(liveTarget));
        }
    }

    /**
     * OpenMyau hang place: under-feet / keep-Y path. One place per PRE — a second C08
     * same tick is Grim MultiPlace (and often RotationPlace via reaim).
     */
    private void tryHangPlaces(EntityPlayerSP player, WorldClient world, BlockPos underFeet,
            boolean hardAway) {
        underFeet = bridgeUnder(player);
        if (!ScaffoldPlace.isReplaceable(world, underFeet)) {
            placeDebugLog("hang stop solid-under under=" + underFeet);
            return;
        }
        float moveYaw = MoveFixUtil.movementFacingYaw();
        // ExtraTelly elevated pad: prefer UP onto keep-Y (one block above).
        if (shouldKeepY)
            liveTarget = findExtraKeepYTarget(player, world, underFeet, moveYaw);
        else
            liveTarget = ScaffoldPlace.findBridgeTarget(player, world, underFeet, moveYaw);
        if (liveTarget == null) {
            placeDebugLog("hang stop resolve under=" + underFeet + " tgt=null");
            return;
        }
        // Reject UP on the keep-Y row (wrong tower into jump), except ExtraTelly clutch
        // which intentionally places UP for the elevated landing pad, or void recovery.
        if (liveTarget.face == EnumFacing.UP) {
            boolean extraClutch = shouldKeepY;
            boolean belowKeep = tellyStartY != Integer.MIN_VALUE
                    && underFeet.getY() < tellyStartY - 1;
            if (!extraClutch && !belowKeep) {
                placeDebugLog("hang stop resolve under=" + underFeet
                        + " tgt=" + formatTarget(liveTarget) + " (UP)");
                return;
            }
            placeDebugLog("hang allow UP "
                    + (extraClutch ? "extra-clutch" : "recovery")
                    + " under=" + underFeet + " keepStartY=" + tellyStartY);
            // UP place is not a hard-away bridge click.
            hardAway = false;
        }
        if (!tryPlaceOnce(player, world, underFeet, true, hardAway)) {
            placeDebugLog("hang FAIL under=" + underFeet + " tgt=" + formatTarget(liveTarget));
            return;
        }
        placeDebugLog("hang ok last=" + liveTarget.placed);
    }

    /** True when silent look already rays the place face — safe to place during telly wait. */
    private boolean tellyLookReady(EntityPlayerSP player, ScaffoldTarget target) {
        if (player == null || target == null || lastSentYaw == Float.MIN_VALUE)
            return false;
        return ScaffoldPlace.findPlacementHit(
            player, target.support, target.face, lastSentYaw, lastSentPitch) != null;
    }

    private boolean tryPlaceOnce(EntityPlayerSP player, WorldClient world, BlockPos underFeet,
            boolean allowPathFill, boolean hardAway) {
        if (liveTarget == null || placeSlot < 0)
            return skipPlace("null-target-or-slot");
        if (!ScaffoldPlace.isValidSupport(world, liveTarget.support))
            return skipPlace("invalid-support " + formatTarget(liveTarget));
        if (!ScaffoldPlace.isReplaceable(world, liveTarget.placed))
            return skipPlace("dest-not-replaceable " + formatTarget(liveTarget));
        if (lastPlacedPos != null && lastPlacedPos.equals(liveTarget.placed))
            return skipPlace("lastPlacedPos-dup " + lastPlacedPos);
        if (!ScaffoldPlace.isSafePlaceTarget(player, liveTarget, underFeet, allowPathFill))
            return skipPlace("unsafe-target under=" + underFeet
                    + " " + formatTarget(liveTarget)
                    + " allowPath=" + allowPathFill);
        if (!towering && !allowPathFill && liveTarget.face != EnumFacing.UP
                && (player.motionY > 0.08 || player.motionY < -0.2))
            return skipPlace("motionY-gate motY=" + fmt(player.motionY));
        if (ScaffoldPlace.wouldIntersectPlayer(player, liveTarget.support, liveTarget.face))
            return skipPlace("wouldIntersectPlayer " + formatTarget(liveTarget));
        // Vanilla ItemBlock rejects these — calling onPlayerRightClick still emits C08 → Grim spam.
        if (ScaffoldPlace.wouldFailVanillaPlace(player, world, liveTarget.support, liveTarget.face))
            return skipPlace("vanilla-place-reject " + formatTarget(liveTarget));

        // Grim PositionPlace at lastReportedPos*.
        if (!ScaffoldPlace.canSeeFace(player, liveTarget.support, liveTarget.face))
            return skipPlace("canSeeFace-fail " + formatTarget(liveTarget)
                    + " allowPath=" + allowPathFill + " towering=" + towering);

        float moveYaw = MoveFixUtil.movementFacingYaw();
        boolean underClutch = liveTarget.placed.equals(underFeet);
        // OpenMyau hang/telly: current-eye hitVec + silent look in the same PRE.
        // lastReported RotationPlace gate caused inconsistent rotPlace-box-miss → falls.
        float yaw = lastSentYaw != Float.MIN_VALUE
            ? lastSentYaw
            : PlayerUpdateHook.lastReportedYaw(player);
        float pitch = lastSentPitch != Float.MIN_VALUE
            ? lastSentPitch
            : PlayerUpdateHook.lastReportedPitch(player);
        float awayAbs = Math.abs(ScaffoldRotations.wrap(yaw - moveYaw));

        if (hardAway && awayAbs < 90f) {
            ScaffoldPlace.FaceHit awayHit = ScaffoldPlace.findFaceHit(
                player, liveTarget.support, liveTarget.face, yaw, pitch, moveYaw, true);
            if (awayHit != null) {
                applyPlaceLook(player, awayHit.yaw, awayHit.pitch);
                yaw = lastSentYaw;
                pitch = lastSentPitch;
                awayAbs = Math.abs(ScaffoldRotations.wrap(yaw - moveYaw));
            } else if (!underClutch && !allowPathFill) {
                return skipPlace("hardAway-look awayAbs=" + fmt(awayAbs)
                        + " yaw=" + fmt(yaw) + " move=" + fmt(moveYaw)
                        + " " + formatTarget(liveTarget));
            }
        }

        Vec3 hit = ScaffoldPlace.findPlacementHit(
            player, liveTarget.support, liveTarget.face, yaw, pitch);
        if (hit == null) {
            ScaffoldPlace.FaceHit faceHit = ScaffoldPlace.findFaceHit(
                player, liveTarget.support, liveTarget.face, yaw, pitch, moveYaw, hardAway);
            if (faceHit != null) {
                applyPlaceLook(player, faceHit.yaw, faceHit.pitch);
                yaw = lastSentYaw;
                pitch = lastSentPitch;
                hit = faceHit.hit;
                placeDebugLog("reaim faceHit (OpenMyau offsets)");
            } else if (allowPathFill || underClutch) {
                hit = ScaffoldPlace.clickVec(liveTarget.support, liveTarget.face);
                if (hit == null)
                    return skipPlace("no-hitVec " + formatTarget(liveTarget));
                placeDebugLog("clickVec fallback underClutch=" + underClutch);
            } else {
                return skipPlace("ray-miss y=" + fmt(yaw) + " p=" + fmt(pitch)
                        + " " + formatTarget(liveTarget));
            }
        }
        awayAbs = Math.abs(ScaffoldRotations.wrap(yaw - moveYaw));

        // Grim applies queued C08 before this tick's RotationUpdate — place Δ is last C03.
        if (isDuplicatePlaceDelta(lastC03RotDeltaYaw, lastPlaceRotDeltaYaw))
            return skipPlace("dupRot grimΔ=" + fmt(lastC03RotDeltaYaw)
                    + " lastPlaceΔ=" + fmt(lastPlaceRotDeltaYaw)
                    + " pendingΔ=" + fmt(tickRotDeltaYaw)
                    + " " + formatTarget(liveTarget));
        yaw = lastSentYaw;
        pitch = lastSentPitch;
        awayAbs = Math.abs(ScaffoldRotations.wrap(yaw - moveYaw));

        selectHotbarSlot(player, placeSlot, true);
        ItemStack stack = player.inventory.getCurrentItem();
        if (stack == null)
            stack = Mc.getStackInSlot(player.inventory, placeSlot);
        if (stack == null)
            return skipPlace("null-stack slot=" + placeSlot);

        boolean placed = Mc.controller().onPlayerRightClick(
            player, world, stack, liveTarget.support, liveTarget.face, hit);
        if (!placed)
            return skipPlace("onPlayerRightClick-false " + formatTarget(liveTarget)
                    + " hit=" + (hit != null
                        ? (fmt(hit.xCoord) + "," + fmt(hit.yCoord) + "," + fmt(hit.zCoord))
                        : "null"));
        player.swingItem();
        Mc.setRightClickDelay(4);
        serverSlot = placeSlot;
        lastPlaceRotDeltaYaw = lastC03RotDeltaYaw;
        lastPlacedPos = liveTarget.placed;
        placedThisTick = true;
        // Next flying will check this tick's C03 Δ — uniquify pending look now.
        ensurePendingC03Unique(player);
        PlayerUpdateHook.requestRotation(lastSentYaw, lastSentPitch);
        RotationState.applyState(
            true, lastSentYaw, lastSentPitch, lastSentYaw, MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY);
        placeDebugLog("PLACE ok " + formatTarget(liveTarget)
                + " phase=" + placePhase
                + " yaw=" + fmt(yaw) + " pitch=" + fmt(pitch)
                + " away=" + fmt(awayAbs)
                + " grimΔ=" + fmt(lastPlaceRotDeltaYaw)
                + " nextΔ=" + fmt(tickRotDeltaYaw));
        return true;
    }

    private void afterWalking(Object playerObj) {
        // Commit the C03 Δ Grim will use for next tick's queued place.
        if (lastSentYaw != Float.MIN_VALUE)
            lastC03RotDeltaYaw = tickRotDeltaYaw;
    }

    private boolean skipPlace(String reason) {
        placeDebugLog("SKIP [" + placePhase + "] " + reason);
        return false;
    }

    private void placeDebugLog(String message) {
        if (!placeDebug.getValue())
            return;
        GnuLog.log("Scaffold " + message);
    }

    private static String formatTarget(ScaffoldTarget t) {
        if (t == null)
            return "null";
        return "sup=" + t.support + " face=" + t.face + " placed=" + t.placed;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    private static String fmt(float v) {
        if (v == Float.MIN_VALUE)
            return "unset";
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private void applyPlaceLook(EntityPlayerSP player, float yaw, float pitch) {
        float reported = PlayerUpdateHook.lastReportedYaw(player);
        lastSentYaw = ScaffoldRotations.continuous(reported, yaw);
        lastSentPitch = ScaffoldRotations.clampPitch(pitch);
        tickRotDeltaYaw = Math.abs(lastSentYaw - reported);
        if (tickRotDeltaYaw < 1.0e-4f)
            forceMicroLook(player, reported);
        PlayerUpdateHook.requestRotation(lastSentYaw, lastSentPitch);
        RotationState.applyState(
            true, lastSentYaw, lastSentPitch, lastSentYaw, MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY);
        PlayerUpdateHook.ensureRotationApplied(player);
    }

    /** Grim flags when consecutive places share identical raw |Δyaw| &gt; 2 within 1e-4. */
    private static boolean isDuplicatePlaceDelta(float delta, float lastPlaceDelta) {
        return delta > 2.0f && lastPlaceDelta > 2.0f
                && Math.abs(delta - lastPlaceDelta) < 0.0001f;
    }

    /**
     * Silent yaw far from move facing lands in {@link MoveFixUtil#fixStrafe} side buckets
     * (forward≠±1 or strafe≠0 for plain W) — Telly drifts sideways.
     */
    private static boolean isFixStrafeDriftYaw(float moveYaw, float sentYaw) {
        float d = Math.abs(ScaffoldRotations.wrap(moveYaw - sentYaw));
        // Safe cones: ~forward (±30) or ~backwards (≥150) for hardAway place look.
        return d > 30f && d < 150f;
    }

    /**
     * Force a look C03 (Δ ≤ 2) so Grim does not reuse a prior large Δ on the next place.
     */
    private void forceMicroLook(EntityPlayerSP player, float reportedYaw) {
        double gcd = Mc.getMouseSensitivityGcd();
        float step = (float) Math.max(gcd > 0.0 ? gcd : 0.15, 0.15);
        // Stay ≤ 2° so DuplicateRotPlace rewards instead of comparing equal large steps.
        float micro = Math.min(step, 1.5f);
        placeYawNudgeSign = -placeYawNudgeSign;
        float[] q = ScaffoldRotations.stepToward(
            reportedYaw, lastSentPitch, reportedYaw + placeYawNudgeSign * micro, lastSentPitch, 100);
        lastSentYaw = q[0];
        if (Math.abs(lastSentYaw - reportedYaw) < 1.0e-4f)
            lastSentYaw = reportedYaw + placeYawNudgeSign * micro;
        tickRotDeltaYaw = Math.abs(lastSentYaw - reportedYaw);
    }

    /**
     * Pending C03 Δ is what the next queued place will be judged on — keep it unique.
     */
    private void ensurePendingC03Unique(EntityPlayerSP player) {
        if (lastSentYaw == Float.MIN_VALUE)
            return;
        float reported = PlayerUpdateHook.lastReportedYaw(player);
        tickRotDeltaYaw = Math.abs(lastSentYaw - reported);
        if (!isDuplicatePlaceDelta(tickRotDeltaYaw, lastPlaceRotDeltaYaw))
            return;
        if (!jitterPendingYaw(player)) {
            // Fall back to a safe micro look (Δ ≤ 2) rather than sending a dupe large step.
            forceMicroLook(player, reported);
        }
    }

    private boolean jitterPendingYaw(EntityPlayerSP player) {
        if (lastSentYaw == Float.MIN_VALUE)
            return false;
        float reportedYaw = PlayerUpdateHook.lastReportedYaw(player);
        double gcd = Mc.getMouseSensitivityGcd();
        float step = (float) Math.max(gcd > 0.0 ? gcd : 0.15, 0.15);
        float signed = lastSentYaw - reportedYaw;
        float sign = signed >= 0 ? 1.0f : -1.0f;
        if (Math.abs(signed) < 1.0e-4f)
            sign = placeYawNudgeSign;
        float current = Math.abs(signed);
        float maxNudge = towering ? 14.0f : 10.0f;
        for (int i = 1; i <= 48; i++) {
            for (int dir : new int[] {1, -1}) {
                float d = current + dir * i * step;
                if (d < 1.0e-4f || d > 45.0f)
                    continue;
                // Prefer any Δ that is not a DuplicateRot match (including ≤ 2).
                if (isDuplicatePlaceDelta(d, lastPlaceRotDeltaYaw))
                    continue;
                float[] q = ScaffoldRotations.stepToward(
                    reportedYaw, lastSentPitch, reportedYaw + sign * d, lastSentPitch, 100);
                float newYaw = q[0];
                float grim = Math.abs(newYaw - reportedYaw);
                if (isDuplicatePlaceDelta(grim, lastPlaceRotDeltaYaw))
                    continue;
                if (Math.abs(newYaw - lastSentYaw) > maxNudge)
                    continue;
                if (liveTarget != null && ScaffoldPlace.findPlacementHit(
                        player, liveTarget.support, liveTarget.face, newYaw, lastSentPitch) == null)
                    continue;
                lastSentYaw = newYaw;
                tickRotDeltaYaw = grim;
                return true;
            }
        }
        return false;
    }

    private void doPatchMovementInput(Object movInput) {
        if (movInput == null)
            return;
        MovementInput in = (MovementInput) movInput;
        boolean hasPrio = MoveFixUtil.hasMoveFixPriority(MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY);
        if (hasPrio && MoveFixUtil.isForwardPressed()) {
            float[] fixed = MoveFixUtil.fixStrafe(
                PlayerUpdateHook.cameraYaw(), RotationState.getSmoothedYaw(), in.sneak);
            in.moveForward = fixed[0];
            in.moveStrafe = fixed[1];
        }
        if (shouldTellyJump(Mc.player()))
            in.jump = true;
    }

    private boolean shouldTellyJump(EntityPlayerSP player) {
        if (player == null || !isKeepYArmed())
            return false;
        // OpenMyau: only force jump while forward keys are held — never while idle.
        if (!player.onGround || placeSlot < 0 || !MoveFixUtil.isForwardPressed())
            return false;
        World world = Mc.world();
        if (world == null)
            return false;
        BlockPos underFeet = bridgeUnder(player);
        return needsBridgeExtension(world, underFeet);
    }

    private static boolean needsBridgeExtension(World world, BlockPos underFeet) {
        if (ScaffoldPlace.isReplaceable(world, underFeet))
            return true;
        float yaw = MoveFixUtil.movementFacingYaw();
        double rad = Math.toRadians(yaw);
        int ox = (int) Math.round(-Math.sin(rad));
        int oz = (int) Math.round(Math.cos(rad));
        if (ox == 0 && oz == 0)
            return false;
        return ScaffoldPlace.isReplaceable(world, underFeet.add(ox, 0, oz));
    }

    private static EntityPlayerSP resolvePlayer(Object playerObj) {
        if (playerObj instanceof EntityPlayerSP)
            return (EntityPlayerSP) playerObj;
        return Mc.player();
    }
}
