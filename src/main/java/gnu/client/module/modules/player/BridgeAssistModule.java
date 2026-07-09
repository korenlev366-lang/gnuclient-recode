package gnu.client.module.modules.player;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.utility.RotationUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge Assist — auto-sneaks at block edges while bridging and optionally
 * aims at valid block placements (pre-place rotation). Ported from raven-bS
 * {@code BridgeAssist}.
 */
public final class BridgeAssistModule extends Module implements PacketListener {

    // ── Static sneak state (read from patchMovementInput mixin hook) ────
    public static volatile boolean forceSneak = false;
    /** When true, vanilla shift sneak is cleared before forceSneak is applied. */
    public static volatile boolean suppressSneak = false;

    // ── Settings ─────────────────────────────────────────────────────────
    private final SliderSetting edgeOffset = addSetting(
            new SliderSetting("Edge Offset", 0.0f, 0.0f, 0.3f));
    private final SliderSetting unsneakDelay = addSetting(
            new SliderSetting("Unsneak Delay", 50.0f, 50.0f, 300.0f));
    private final SliderSetting sneakOnJumpMs = addSetting(
            new SliderSetting("Sneak On Jump", 0.0f, 0.0f, 500.0f));
    private final BoolSetting sneakKeyRequired = addSetting(
            new BoolSetting("Sneak Key Required", false));
    private final BoolSetting holdingBlocksOnly = addSetting(
            new BoolSetting("Holding Blocks", false));
    private final BoolSetting lookingDownOnly = addSetting(
            new BoolSetting("Looking Down", false));
    private final BoolSetting notMovingForwardOnly = addSetting(
            new BoolSetting("Not Moving Forward", false));
    private final BoolSetting prePlaceRotation = addSetting(
            new BoolSetting("Pre-place Rotation", false));

    // ── Running state ────────────────────────────────────────────────────
    private boolean sneakingFromModule;
    private boolean placed;
    private boolean forceRelease;
    private int sneakJumpStartTick = -1;
    private int sneakJumpDelayTicks = -1;
    private int unsneakStartTick = -1;
    private int unsneakDelayTicks = -1;
    private int tickCounter;

    public BridgeAssistModule() {
        super("Bridge Assist", "Auto-sneak at block edges with bridging assistance",
                Category.PLAYER);
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        resetState();
        PacketEvents.register(this);
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        resetState();
        sneakingFromModule = false;
        forceSneak = false;
        suppressSneak = false;
        Mc.setSneaking(Mc.player(), false);
    }

    // ── Tick logic ───────────────────────────────────────────────────────

    @Override
    public void onTick() {
        if (!isEnabled()) {
            forceSneak = false;
            suppressSneak = false;
            return;
        }

        tickCounter++;
        int currentTick = tickCounter;

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null) {
            forceSneak = false;
            suppressSneak = false;
            sneakingFromModule = false;
            return;
        }

        if (Mc.currentScreen() != null) {
            forceSneak = false;
            suppressSneak = false;
            sneakingFromModule = false;
            return;
        }

        if (!player.onGround) {
            clearSneakState();
            return;
        }

        // ── Condition: sneak key required ───────────────────────────────
        boolean manualSneak = isManualSneak();
        if (sneakKeyRequired.getValue()) {
            if (!manualSneak) {
                clearSneakState();
                return;
            }
            if (!isMoving(player)) {
                suppressSneak = true;
                forceSneak = false;
                sneakingFromModule = false;
                Mc.setSneaking(player, true);
                return;
            }
            suppressSneak = false;
        }

        // ── Condition: not moving forward ───────────────────────────────
        if (notMovingForwardOnly.getValue() && isMovingForward(player)) {
            clearSneakState();
            return;
        }

        // ── Condition: looking down (pitch >= 70) ───────────────────────
        float pitch = player.rotationPitch;
        if (lookingDownOnly.getValue() && pitch < 70.0f) {
            clearSneakState();
            return;
        }

        // ── Condition: holding blocks ───────────────────────────────────
        if (holdingBlocksOnly.getValue() && !isHoldingBlock(player)) {
            clearSneakState();
            return;
        }

        // ── Edge detection (predictive: uses predicted AABB one tick ahead) ─
        AABBCoords predictedAABB = computePredictedAABB(player);
        boolean nearEdge = computeNearEdge(predictedAABB, world);
        double offset = computeEdgeDistance(predictedAABB, world);

        if (Double.isNaN(offset)) {
            if (player.onGround) {
                pressSneak(player, true);
            } else if (sneakingFromModule) {
                releaseSneak(player, true);
            }
            return;
        }

        // ── Sneak on jump ───────────────────────────────────────────────
        boolean jumpHeld = isJumpHeld();
        if (sneakOnJumpMs.getValue() > 0.0f && jumpHeld && (nearEdge || offset > edgeOffset.getValue())) {
            if (!sneakingFromModule || forceRelease) {
                sneakJumpStartTick = currentTick;
                double raw = sneakOnJumpMs.getValue() / 50.0;
                int base = (int) raw;
                sneakJumpDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
                pressSneak(player, true);
                return;
            }
        }

        // ── Unsneak delay management ────────────────────────────────────
        if (sneakingFromModule) {
            boolean shouldRelease = !nearEdge && offset <= edgeOffset.getValue();

            if (shouldRelease) {
                if (unsneakStartTick == -1 && sneakJumpStartTick == -1) {
                    unsneakStartTick = currentTick;
                    double raw = (unsneakDelay.getValue() - 50) / 50.0;
                    int base = (int) raw;
                    unsneakDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
                }

                if (sneakJumpStartTick != -1
                        && currentTick - sneakJumpStartTick < sneakJumpDelayTicks) {
                    pressSneak(player, false);
                    return;
                }

                if (unsneakStartTick != -1
                        && currentTick - unsneakStartTick < unsneakDelayTicks) {
                    pressSneak(player, false);
                    return;
                }

                releaseSneak(player, true);
                return;
            }

            unsneakStartTick = -1;
            sneakJumpStartTick = -1;
            pressSneak(player, true);
            return;
        }

        // ── Edge-triggered sneak start ──────────────────────────────────
        if (nearEdge || offset > edgeOffset.getValue()) {
            pressSneak(player, true);
        }

        // ── Pre-place rotation ──────────────────────────────────────────
        if (prePlaceRotation.getValue() && sneakingFromModule
                && isHoldingBlock(player) && pitch >= 60.0f) {
            applyPrePlaceRotation(player, world);
        }
    }

    // ── Rotation (pre-place aiming) ──────────────────────────────────────

    private void applyPrePlaceRotation(EntityPlayerSP player, World world) {
        double reach = getBlockReachDistance();
        float currentPitch = player.rotationPitch;
        float currentYaw = player.rotationYaw;

        List<FaceTarget> targets = findFaceTargets(player, world);
        if (targets.isEmpty())
            return;

        float bestPitch = Float.NaN;
        float bestDelta = Float.MAX_VALUE;
        float randScale = 0.2f;

        for (float scanPitch = 60f; scanPitch <= 90f; ) {
            float step = 1.0f + (float) (Math.random() * 2 - 1) * (0.3f + randScale * 0.4f);
            if (step < 0.4f) step = 0.4f;
            if (step > 1.8f) step = 1.8f;
            scanPitch += step;
            float samplePitch = Math.min(scanPitch, 90f);

            MovingObjectPosition hitResult = RotationUtils.rayTraceCustom(reach, currentYaw, samplePitch);
            if (hitResult == null || hitResult.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
                continue;

            EnumFacing sideHit = hitResult.sideHit;
            if (sideHit == null)
                continue;
            int sideOrdinal = sideHit.ordinal();
            if (sideOrdinal == 0 || sideOrdinal == 1)
                continue;

            BlockPos hitBlockPos = hitResult.getBlockPos();
            if (hitBlockPos == null)
                continue;

            for (FaceTarget ft : targets) {
                if (hitBlockPos.equals(ft.block) && sideOrdinal == ft.face) {
                    float delta = Math.abs(samplePitch - currentPitch);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestPitch = samplePitch;
                    }
                    break;
                }
            }
            if (scanPitch >= 90f) break;
        }

        if (Float.isNaN(bestPitch))
            return;

        float smoothedPitch = smoothRotation(currentPitch, bestPitch, 15f, 20f);
        player.rotationPitch = smoothedPitch;
    }

    // ── PacketListener ──────────────────────────────────────────────────

    @Override
    public boolean onSend(Object packet) {
        if (packet != null && PacketHelper.isBlockPlacement(packet)) {
            if (sneakingFromModule && sneakKeyRequired.getValue()) {
                placed = true;
            }
        }
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    // ── Static patchMovementInput (called from MovementInputHook) ───────

    public static void patchMovementInput(Object movInput) {
        if (!(movInput instanceof MovementInput))
            return;

        MovementInput input = (MovementInput) movInput;
        boolean vanillaSneak = input.sneak;
        float moveForward = input.moveForward;
        float moveStrafe = input.moveStrafe;

        boolean finalSneak = vanillaSneak;
        if (suppressSneak)
            finalSneak = false;
        if (forceSneak)
            finalSneak = true;

        if (!vanillaSneak && finalSneak) {
            moveForward *= 0.3F;
            moveStrafe *= 0.3F;
        } else if (vanillaSneak && !finalSneak) {
            if (moveForward != 0f)
                moveForward /= 0.3F;
            if (moveStrafe != 0f)
                moveStrafe /= 0.3F;
        }

        input.sneak = finalSneak;
        input.moveForward = moveForward;
        input.moveStrafe = moveStrafe;
    }

    // ── Sneak control ────────────────────────────────────────────────────

    private void pressSneak(EntityPlayerSP player, boolean resetDelay) {
        forceSneak = true;
        sneakingFromModule = true;
        if (resetDelay) {
            unsneakStartTick = -1;
            sneakJumpStartTick = -1;
        }
        Mc.setSneaking(player, true);
    }

    private void releaseSneak(EntityPlayerSP player, boolean resetDelay) {
        EntityPlayerSP local = Mc.player();
        if (sneakKeyRequired.getValue() && sneakingFromModule
                && isManualSneak() && local != null && (placed || !local.onGround)) {
            Mc.setKeyBindState(Mc.settings().keyBindSneak, false);
            forceRelease = true;
        } else if (forceRelease) {
            // Keep released
        }

        forceSneak = false;
        sneakingFromModule = false;
        placed = false;
        if (resetDelay) resetUnsneakTimers();
        Mc.setSneaking(player, false);
    }

    private void repressSneak(EntityPlayerSP player) {
        if (forceRelease && isManualSneak()) {
            Mc.setKeyBindState(Mc.settings().keyBindSneak, true);
            forceSneak = true;
            sneakingFromModule = true;
            Mc.setSneaking(player, true);
        }
        forceRelease = false;
    }

    private void clearSneak() {
        sneakingFromModule = false;
        resetUnsneakTimers();
        forceSneak = false;
        suppressSneak = false;
        repressSneak(Mc.player());
    }

    private void clearSneakState() {
        sneakingFromModule = false;
        forceSneak = false;
        suppressSneak = false;
        resetUnsneakTimers();
        Mc.setSneaking(Mc.player(), false);
    }

    // ── Edge detection ───────────────────────────────────────────────────

    private boolean computeNearEdge(AABBCoords coords, World world) {
        if (coords == null) return false;

        int by = (int) Math.floor(coords.minY - 0.01);
        double cx = (coords.minX + coords.maxX) * 0.5;
        double cz = (coords.minZ + coords.maxZ) * 0.5;

        int bxLo = (int) Math.floor(coords.minX);
        int bxHi = (int) Math.floor(coords.maxX);
        int bzLo = (int) Math.floor(coords.minZ);
        int bzHi = (int) Math.floor(coords.maxZ);

        boolean hasGround = false;
        double minDist = Double.POSITIVE_INFINITY;

        for (int bx = bxLo; bx <= bxHi; bx++) {
            for (int bz = bzLo; bz <= bzHi; bz++) {
                if (isAirBlock(world, bx, by, bz))
                    continue;
                hasGround = true;
                double closestX = clamp(cx, bx, bx + 1.0);
                double closestZ = clamp(cz, bz, bz + 1.0);
                double dist = Math.max(Math.abs(cx - closestX), Math.abs(cz - closestZ));
                if (dist < minDist)
                    minDist = dist;
            }
        }

        return hasGround && minDist > edgeOffset.getValue();
    }

    private double computeEdgeDistance(AABBCoords coords, World world) {
        if (coords == null) return Double.NaN;

        int by = (int) Math.floor(coords.minY - 0.01);
        double cx = (coords.minX + coords.maxX) * 0.5;
        double cz = (coords.minZ + coords.maxZ) * 0.5;

        int bxLo = (int) Math.floor(coords.minX);
        int bxHi = (int) Math.floor(coords.maxX);
        int bzLo = (int) Math.floor(coords.minZ);
        int bzHi = (int) Math.floor(coords.maxZ);

        double minDist = Double.POSITIVE_INFINITY;
        for (int bx = bxLo; bx <= bxHi; bx++) {
            for (int bz = bzLo; bz <= bzHi; bz++) {
                if (isAirBlock(world, bx, by, bz))
                    continue;
                double closestX = clamp(cx, bx, bx + 1.0);
                double closestZ = clamp(cz, bz, bz + 1.0);
                double dist = Math.max(Math.abs(cx - closestX), Math.abs(cz - closestZ));
                if (dist < minDist) minDist = dist;
            }
        }

        return minDist == Double.POSITIVE_INFINITY ? Double.NaN : minDist;
    }

    // ── Face targeting for pre-place rotation ────────────────────────────

    private static final int[] SIDE_ORDINALS = {2, 3, 4, 5}; // NORTH, SOUTH, EAST, WEST

    private List<FaceTarget> findFaceTargets(EntityPlayerSP player, World world) {
        List<FaceTarget> targets = new ArrayList<>();
        AxisAlignedBB aabb = player.getEntityBoundingBox();
        if (aabb == null)
            return targets;

        double minX = aabb.minX;
        double maxX = aabb.maxX;
        double minY = aabb.minY;
        double minZ = aabb.minZ;
        double maxZ = aabb.maxZ;

        int standY = (int) Math.floor(minY) - 1;
        int bxLo = (int) Math.floor(minX);
        int bxHi = (int) Math.floor(maxX);
        int bzLo = (int) Math.floor(minZ);
        int bzHi = (int) Math.floor(maxZ);

        for (int x = bxLo; x <= bxHi; x++) {
            for (int z = bzLo; z <= bzHi; z++) {
                if (isAirBlock(world, x, standY, z))
                    continue;
                for (int face : SIDE_ORDINALS) {
                    int nx = x + (face == 4 ? -1 : face == 5 ? 1 : 0);
                    int nz = z + (face == 2 ? -1 : face == 3 ? 1 : 0);
                    if (isAirBlock(world, nx, standY, nz)) {
                        targets.add(new FaceTarget(new BlockPos(x, standY, z), face));
                    }
                }
            }
        }
        return targets;
    }

    // ── MC helpers ───────────────────────────────────────────────────────

    private static boolean isAirBlock(World world, int x, int y, int z) {
        return world.isAirBlock(new BlockPos(x, y, z));
    }

    private boolean isHoldingBlock(EntityPlayerSP player) {
        if (player == null)
            return false;
        ItemStack stack = player.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemBlock;
    }

    private boolean isManualSneak() {
        return Mc.isSneakKeyHeld();
    }

    private boolean isJumpHeld() {
        return Mc.isJumpKeyHeld();
    }

    private boolean isMovingForward(EntityPlayerSP player) {
        if (player == null || player.movementInput == null)
            return false;
        return player.movementInput.moveForward > 0.0f;
    }

    private boolean isMoving(EntityPlayerSP player) {
        if (player == null || player.movementInput == null)
            return false;
        MovementInput input = player.movementInput;
        return input.moveForward != 0.0f || input.moveStrafe != 0.0f;
    }

    private double getBlockReachDistance() {
        PlayerControllerMP controller = Mc.controller();
        return controller != null ? controller.getBlockReachDistance() : 4.5;
    }

    private float smoothRotation(float current, float target, float maxStep, float smoothFactor) {
        float delta = target - current;
        if (Math.abs(delta) > maxStep) {
            delta = Math.signum(delta) * maxStep;
        }
        float alpha = smoothFactor / 100.0f;
        if (alpha > 1.0f) alpha = 1.0f;
        return current + delta * alpha;
    }

    // ── Utility ──────────────────────────────────────────────────────────

    private void resetUnsneakTimers() {
        unsneakStartTick = -1;
        sneakJumpStartTick = -1;
        sneakJumpDelayTicks = -1;
        unsneakDelayTicks = -1;
    }

    private void resetState() {
        resetUnsneakTimers();
        sneakingFromModule = false;
        placed = false;
        forceRelease = false;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    // ── Inner types ──────────────────────────────────────────────────────

    private static class FaceTarget {
        final BlockPos block;
        final int face;

        FaceTarget(BlockPos block, int face) {
            this.block = block;
            this.face = face;
        }
    }

    private static final class AABBCoords {
        final double minX, maxX, minY, minZ, maxZ;

        AABBCoords(double minX, double maxX, double minY, double minZ, double maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }

    private AABBCoords computePredictedAABB(EntityPlayerSP player) {
        AxisAlignedBB aabb = player.getEntityBoundingBox();
        if (aabb == null)
            return null;

        double minX = aabb.minX;
        double maxX = aabb.maxX;
        double minY = aabb.minY;
        double minZ = aabb.minZ;
        double maxZ = aabb.maxZ;

        double motionX = player.motionX;
        double motionZ = player.motionZ;

        if (forceSneak) {
            motionX /= 0.3;
            motionZ /= 0.3;
        }

        if (motionX == 0.0 && motionZ == 0.0) {
            return new AABBCoords(minX, maxX, minY, minZ, maxZ);
        }

        MovementInput movInput = player.movementInput;
        if (movInput != null) {
            float forward = movInput.moveForward;
            float strafe = movInput.moveStrafe;
            if (forward != 0.0f || strafe != 0.0f) {
                float yawRad = (float) Math.toRadians(player.rotationYaw);
                float f = forward * 0.98f;
                float s = strafe * 0.98f;
                double accelX = (s * MathHelper.cos(yawRad)
                        - f * MathHelper.sin(yawRad)) * 0.02;
                double accelZ = (f * MathHelper.cos(yawRad)
                        + s * MathHelper.sin(yawRad)) * 0.02;
                motionX = motionX * 0.91 + accelX;
                motionZ = motionZ * 0.91 + accelZ;
            }
        }

        return new AABBCoords(
                minX + motionX, maxX + motionX, minY,
                minZ + motionZ, maxZ + motionZ
        );
    }
}
