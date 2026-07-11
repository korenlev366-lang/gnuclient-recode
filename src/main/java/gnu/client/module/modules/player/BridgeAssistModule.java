package gnu.client.module.modules.player;

import gnu.client.event.ClientRotationEvent;
import gnu.client.event.PrePlayerInputEvent;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.utility.BlockUtils;
import gnu.client.utility.IMinecraftInstance;
import gnu.client.utility.RotationUtils;
import gnu.client.utility.Utils;
import gnu.client.utility.sim.SimulatedPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge Assist — auto-sneaks at block edges while bridging and optionally
 * aims at valid block placements (pre-place rotation). Ported from raven-bS
 * {@code BridgeAssist}.
 */
public final class BridgeAssistModule extends Module implements PacketListener, IMinecraftInstance {

    private static final EnumFacing[] SIDES = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };

    private final BoolSetting prePlace = addSetting(new BoolSetting("Pre place", false));
    private final SliderSetting edgeOffset = addSetting(
            new SliderSetting("Edge offset", 0.0f, 0.0f, 0.3f, 0.01f));
    private final SliderSetting unsneakDelay = addSetting(
            new SliderSetting("Unsneak delay", 50.0f, 50.0f, 300.0f, 5.0f));
    private final SliderSetting sneakOnJump = addSetting(
            new SliderSetting("Sneak on jump", 0.0f, 0.0f, 500.0f, 5.0f));
    private final BoolSetting sneakKeyPressed = addSetting(new BoolSetting("Sneak key pressed", false));
    private final BoolSetting holdingBlocks = addSetting(new BoolSetting("Holding blocks", false));
    private final BoolSetting lookingDown = addSetting(new BoolSetting("Looking down", false));
    private final BoolSetting notMovingForward = addSetting(new BoolSetting("Not moving forward", false));

    private boolean sneakingFromModule;
    private boolean placed;
    private boolean forceRelease;
    private int sneakJumpDelayTicks = -1;
    private int sneakJumpStartTick = -1;
    private int unsneakDelayTicks = -1;
    private int unsneakStartTick = -1;

    public BridgeAssistModule() {
        super("Bridge Assist", "Auto-sneak at block edges with bridging assistance",
                Category.PLAYER);
    }

    @Override
    public void onEnable() {
        resetState();
        MinecraftForge.EVENT_BUS.register(this);
        PacketEvents.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        PacketEvents.unregister(this);
        sneakingFromModule = false;
        resetUnsneak();
    }

    @Override
    public String[] getSuffix() {
        float v = edgeOffset.getValue();
        String s = v == Math.rint(v) ? Integer.toString((int) v) : String.format("%.2f", v);
        return new String[] { s };
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!Utils.nullCheck() || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying)
            return;

        boolean manualSneak = isManualSneak();
        boolean requireSneak = sneakKeyPressed.getValue();

        if (manualSneak && !requireSneak) {
            resetUnsneak();
            return;
        }

        if (requireSneak && (!manualSneak || (e.getForward() == 0 && e.getStrafe() == 0))) {
            if (!manualSneak)
                resetUnsneak();
            repressSneak(e);
            return;
        }

        if (notMovingForward.getValue() && e.getForward() > 0) {
            clearSneak(e);
            return;
        }
        if (lookingDown.getValue() && mc.thePlayer.rotationPitch < 70) {
            clearSneak(e);
            return;
        }
        if (holdingBlocks.getValue()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemBlock)) {
                clearSneak(e);
                return;
            }
        }

        if (e.isJump() && mc.thePlayer.onGround && (e.getForward() != 0 || e.getStrafe() != 0)
                && sneakOnJump.getValue() > 0) {
            if (!requireSneak || forceRelease) {
                sneakJumpStartTick = mc.thePlayer.ticksExisted;
                double raw = sneakOnJump.getValue() / 50.0;
                int base = (int) raw;
                sneakJumpDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
                pressSneak(e, true);
                return;
            }
        }

        SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);
        sim.movementInput.sneak = false;
        sim.tick();

        double offset = computeEdgeOffset(sim.getEntityBoundingBox());

        if (Double.isNaN(offset)) {
            if (e.isJump() && (sneakOnJump.getValue() <= 0 || (e.getForward() == 0 && e.getStrafe() == 0))) {
                if (sneakingFromModule)
                    tryReleaseSneak(e, true);
            } else if (mc.thePlayer.onGround) {
                pressSneak(e, true);
            } else if (sneakingFromModule) {
                tryReleaseSneak(e, true);
            }
            return;
        }

        if (offset > edgeOffset.getValue()) {
            pressSneak(e, true);
        } else if (sneakingFromModule) {
            tryReleaseSneak(e, true);
        }
    }

    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (!prePlace.getValue())
            return;
        if (!Utils.nullCheck() || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying)
            return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock))
            return;
        if (lookingDown.getValue() && mc.thePlayer.rotationPitch < 70f)
            return;
        if (notMovingForward.getValue() && mc.thePlayer.movementInput.moveForward > 0f)
            return;

        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
        double reach = mc.playerController.getBlockReachDistance();

        TargetResult target = findTarget(basePitch, reach);
        if (target == null)
            return;

        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float[] sm = RotationUtils.smoothRotation(baseYaw, basePitch, target.yaw, target.pitch, 15, 20f);

        e.setYaw(sm[0]);
        e.setPitch(sm[1]);
    }

    @Override
    public boolean onSend(Object packet) {
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement c08 = (C08PacketPlayerBlockPlacement) packet;
            if (c08.getPlacedBlockDirection() != 255 && sneakingFromModule && sneakKeyPressed.getValue())
                placed = true;
        }
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    private void pressSneak(PrePlayerInputEvent e, boolean resetDelay) {
        e.setSneak(true);
        sneakingFromModule = true;
        if (resetDelay)
            unsneakStartTick = -1;
        repressSneak(e);
    }

    private void tryReleaseSneak(PrePlayerInputEvent e, boolean resetDelay) {
        int existed = mc.thePlayer.ticksExisted;
        if (unsneakStartTick == -1 && sneakJumpStartTick == -1) {
            unsneakStartTick = existed;
            double raw = (unsneakDelay.getValue() - 50) / 50.0;
            int base = (int) raw;
            unsneakDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
        }

        if (sneakJumpStartTick != -1 && existed - sneakJumpStartTick < sneakJumpDelayTicks) {
            pressSneak(e, false);
            return;
        }
        if (unsneakStartTick != -1 && existed - unsneakStartTick < unsneakDelayTicks) {
            pressSneak(e, false);
            return;
        }

        releaseSneak(e, resetDelay);
    }

    private void releaseSneak(PrePlayerInputEvent e, boolean resetDelay) {
        if (!sneakKeyPressed.getValue()) {
            e.setSneak(false);
        } else if (sneakingFromModule && isManualSneak() && (placed || !mc.thePlayer.onGround)) {
            Mc.setKeyBindState(Mc.settings().keyBindSneak, false);
            e.setSneak(false);
            forceRelease = true;
        } else if (forceRelease) {
            e.setSneak(false);
        }

        sneakingFromModule = false;
        placed = false;
        if (resetDelay)
            resetUnsneak();
    }

    private void repressSneak(PrePlayerInputEvent e) {
        if (forceRelease && isManualSneak()) {
            Mc.setKeyBindState(Mc.settings().keyBindSneak, true);
            e.setSneak(true);
        }
        forceRelease = false;
    }

    private void clearSneak(PrePlayerInputEvent e) {
        sneakingFromModule = false;
        resetUnsneak();
        if (sneakKeyPressed.getValue())
            repressSneak(e);
    }

    private void resetUnsneak() {
        unsneakStartTick = -1;
        sneakJumpStartTick = -1;
        sneakJumpDelayTicks = -1;
        unsneakDelayTicks = -1;
    }

    private void resetState() {
        resetUnsneak();
        sneakingFromModule = false;
        placed = false;
        forceRelease = false;
    }

    private boolean isManualSneak() {
        return Mc.isSneakKeyHeld();
    }

    private double computeEdgeOffset(AxisAlignedBB simBox) {
        AxisAlignedBB groundCheck = new AxisAlignedBB(
                simBox.minX, simBox.minY - 0.01, simBox.minZ,
                simBox.maxX, simBox.minY, simBox.maxZ);

        List<AxisAlignedBB> groundBoxes = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, groundCheck);
        if (groundBoxes.isEmpty())
            return Double.NaN;

        double feetX = (simBox.minX + simBox.maxX) / 2.0;
        double feetZ = (simBox.minZ + simBox.maxZ) / 2.0;

        double minDist = Double.MAX_VALUE;
        for (AxisAlignedBB box : groundBoxes) {
            double closestX = Math.max(box.minX, Math.min(feetX, box.maxX));
            double closestZ = Math.max(box.minZ, Math.min(feetZ, box.maxZ));
            double dx = Math.abs(feetX - closestX);
            double dz = Math.abs(feetZ - closestZ);
            double dist = Math.max(dx, dz);
            minDist = Math.min(minDist, dist);
        }

        return minDist;
    }

    private TargetResult findTarget(float currentPitch, double reach) {
        float yaw = mc.thePlayer.rotationYaw;

        AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox();
        int standY = MathHelper.floor_double(bbox.minY) - 1;
        int minX = MathHelper.floor_double(bbox.minX);
        int maxX = MathHelper.floor_double(bbox.maxX);
        int minZ = MathHelper.floor_double(bbox.minZ);
        int maxZ = MathHelper.floor_double(bbox.maxZ);

        ArrayList<FaceTarget> targets = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos standBlock = new BlockPos(x, standY, z);
                if (BlockUtils.replaceable(standBlock))
                    continue;
                for (EnumFacing face : SIDES) {
                    BlockPos placed = standBlock.offset(face);
                    if (!BlockUtils.replaceable(placed))
                        continue;
                    targets.add(new FaceTarget(standBlock, face));
                }
            }
        }
        if (targets.isEmpty())
            return null;

        float bestDelta = Float.MAX_VALUE;
        float bestPitch = Float.NaN;
        BlockPos bestSupport = null;
        EnumFacing bestFace = null;
        float randScale = 0.2f;

        for (float pitch = 60f; pitch <= 90f; ) {
            float step = 1.0f + (float) (Math.random() * 2 - 1) * (0.3f + randScale * 0.4f);
            if (step < 0.4f)
                step = 0.4f;
            if (step > 1.8f)
                step = 1.8f;
            pitch += step;
            float samplePitch = Math.min(pitch, 90f);
            MovingObjectPosition mop = RotationUtils.rayCastBlock(reach, yaw, samplePitch);
            if (mop == null)
                continue;
            EnumFacing hitFace = mop.sideHit;
            if (hitFace == EnumFacing.UP || hitFace == EnumFacing.DOWN)
                continue;

            BlockPos hitBlock = mop.getBlockPos();
            for (FaceTarget t : targets) {
                if (hitBlock.equals(t.block) && hitFace == t.face) {
                    float delta = Math.abs(samplePitch - currentPitch);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestPitch = samplePitch;
                        bestSupport = t.block;
                        bestFace = t.face;
                    }
                    break;
                }
            }
            if (pitch >= 90f)
                break;
        }

        if (bestSupport == null || bestFace == null || Float.isNaN(bestPitch))
            return null;
        return new TargetResult(yaw, bestPitch, bestSupport, bestFace);
    }

    private static final class FaceTarget {
        final BlockPos block;
        final EnumFacing face;

        FaceTarget(BlockPos block, EnumFacing face) {
            this.block = block;
            this.face = face;
        }
    }

    private static final class TargetResult {
        final float yaw;
        final float pitch;
        final BlockPos support;
        final EnumFacing face;

        TargetResult(float yaw, float pitch, BlockPos support, EnumFacing face) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.support = support;
            this.face = face;
        }
    }
}
