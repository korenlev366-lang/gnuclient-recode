package gnu.client.module.modules.player.scaffold;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.PlayerUpdateHook;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.Arrays;

/**
 * Silent scaffold bridge (KA-style rotations + MoveFix): target find, silent aim,
 * place-with-spoof switch, and Telly/tower basics.
 */
public final class ScaffoldModule extends Module {

    private static final int KEEPY_OFF = 0;
    private static final int KEEPY_TELLY = 1;

    private final ModeSetting aim = addSetting(new ModeSetting("Aim", ScaffoldAim.AIM_BACKWARDS,
        Arrays.asList("Backwards", "GodBridge", "Nearest", "Sideways")));
    private final ModeSetting keepY = addSetting(new ModeSetting("KeepY", KEEPY_OFF,
        Arrays.asList("Off", "Telly")));
    private final SliderSetting rotMin = addSetting(new SliderSetting("Rotation min", 60f, 1f, 100f, 1f));
    private final SliderSetting rotMax = addSetting(new SliderSetting("Rotation max", 80f, 1f, 100f, 1f));

    private int spoofSlot = -1;
    private int placeSlot = -1;
    private float lastSentYaw = Float.MIN_VALUE;
    private float lastSentPitch = Float.MIN_VALUE;
    private boolean tellyLookForward;
    private ScaffoldTarget liveTarget;

    public ScaffoldModule() {
        super("Scaffold", "Silent bridge scaffold with item spoof", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        EntityPlayerSP p = Mc.player();
        spoofSlot = p != null ? Mc.getHotbarSlot(p) : 0;
        placeSlot = -1;
        lastSentYaw = lastSentPitch = Float.MIN_VALUE;
        tellyLookForward = false;
        liveTarget = null;
    }

    @Override
    public void onDisable() {
        clearRotationIfOwned();
        restoreHotbarToSpoof();
        liveTarget = null;
        placeSlot = -1;
        tellyLookForward = false;
    }

    public int getSpoofSlot() {
        return spoofSlot;
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
        if (Mc.getHotbarSlot(player) != spoofSlot)
            Mc.setHotbarSlot(player, spoofSlot);
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

    public static void patchMovementInput(Object movInput) {
        Module module = ModuleManager.instance().getModule("Scaffold");
        if (module instanceof ScaffoldModule && module.isEnabled())
            ((ScaffoldModule) module).doPatchMovementInput(movInput);
    }

    private void preUpdate(Object playerObj) {
        EntityPlayerSP player = resolvePlayer(playerObj);
        if (player == null) {
            clearRotationIfOwned();
            liveTarget = null;
            tellyLookForward = false;
            placeSlot = -1;
            return;
        }

        placeSlot = ScaffoldBlocks.pickBestHotbarSlot(player);
        if (placeSlot < 0) {
            clearRotationIfOwned();
            liveTarget = null;
            tellyLookForward = false;
            return;
        }

        World world = Mc.world();
        if (world == null) {
            clearRotationIfOwned();
            liveTarget = null;
            tellyLookForward = false;
            return;
        }

        BlockPos feet = new BlockPos(player.posX, player.posY, player.posZ);
        BlockPos underFeet = feet.down();
        boolean jumpHeld = player.movementInput != null && player.movementInput.jump;

        if (jumpHeld
                && ScaffoldPlace.isReplaceable(world, feet)
                && ScaffoldPlace.isValidSupport(world, underFeet)) {
            // Tower: place UP onto the solid block under the player's feet cell.
            liveTarget = new ScaffoldTarget(underFeet, EnumFacing.UP);
            tellyLookForward = false;
        } else if (keepY.getIndex() == KEEPY_TELLY) {
            boolean needsBridge = needsBridgeExtension(world, underFeet);
            if ((player.onGround || player.motionY >= 0.0) && needsBridge) {
                tellyLookForward = true;
                liveTarget = ScaffoldPlace.findNeighborTarget(player, world, underFeet, false);
            } else if (!player.onGround && player.motionY < 0.0) {
                tellyLookForward = false;
                liveTarget = ScaffoldPlace.findNeighborTarget(player, world, underFeet, false);
            } else {
                tellyLookForward = false;
                liveTarget = needsBridge
                    ? ScaffoldPlace.findNeighborTarget(player, world, underFeet, false)
                    : null;
            }
        } else {
            tellyLookForward = false;
            liveTarget = ScaffoldPlace.isReplaceable(world, underFeet)
                ? ScaffoldPlace.findNeighborTarget(player, world, underFeet, false)
                : null;
        }

        float baseYaw = lastSentYaw != Float.MIN_VALUE
            ? lastSentYaw
            : PlayerUpdateHook.lastReportedYaw(player);
        float basePitch = lastSentPitch != Float.MIN_VALUE
            ? lastSentPitch
            : PlayerUpdateHook.lastReportedPitch(player);

        float[] raw;
        if (tellyLookForward) {
            raw = new float[] { MoveFixUtil.movementFacingYaw(), 20f };
        } else if (liveTarget != null) {
            Vec3 hitPrefer = ScaffoldPlace.faceCenter(liveTarget.support, liveTarget.face);
            raw = ScaffoldAim.compute(
                aim.getIndex(),
                MoveFixUtil.movementFacingYaw(),
                baseYaw,
                basePitch,
                player,
                liveTarget,
                hitPrefer);
        } else {
            clearRotationIfOwned();
            return;
        }

        int speed = ScaffoldRotations.sampleSpeed(
            Math.round(rotMin.getValue()), Math.round(rotMax.getValue()));
        float[] sent = ScaffoldRotations.stepToward(baseYaw, basePitch, raw[0], raw[1], speed);
        PlayerUpdateHook.requestRotation(sent[0], sent[1]);
        lastSentYaw = sent[0];
        lastSentPitch = sent[1];
        RotationState.applyState(
            true, sent[0], sent[1], sent[0], MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY);
    }

    private void beforeWalkingPlace(Object playerObj) {
        if (tellyLookForward)
            return;
        if (liveTarget == null || placeSlot < 0)
            return;

        EntityPlayerSP player = resolvePlayer(playerObj);
        if (player == null)
            return;
        WorldClient world = Mc.world();
        if (world == null || Mc.controller() == null)
            return;

        boolean switched = false;
        if (Mc.getHotbarSlot(player) != placeSlot) {
            Mc.setHotbarSlot(player, placeSlot);
            switched = true;
        }

        float yaw = lastSentYaw != Float.MIN_VALUE
            ? lastSentYaw
            : PlayerUpdateHook.lastReportedYaw(player);
        float pitch = lastSentPitch != Float.MIN_VALUE
            ? lastSentPitch
            : PlayerUpdateHook.lastReportedPitch(player);

        Vec3 hit = ScaffoldPlace.findPlacementHit(
            player, liveTarget.support, liveTarget.face, yaw, pitch);
        if (hit == null) {
            if (switched)
                restoreHotbarToSpoof();
            return;
        }

        ItemStack stack = Mc.getStackInSlot(player.inventory, placeSlot);
        if (stack != null) {
            Mc.controller().onPlayerRightClick(
                player, world, stack, liveTarget.support, liveTarget.face, hit);
            player.swingItem();
        }
        if (Mc.getHotbarSlot(player) != spoofSlot)
            Mc.setHotbarSlot(player, spoofSlot);
    }

    private void doPatchMovementInput(Object movInput) {
        if (movInput == null)
            return;
        MovementInput in = (MovementInput) movInput;
        boolean hasPrio = MoveFixUtil.hasMoveFixPriority(MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY);
        if (hasPrio && MoveFixUtil.isForwardPressed()) {
            float[] fixed = MoveFixUtil.fixStrafe(
                Mc.getYaw(), RotationState.getSmoothedYaw(), in.sneak);
            in.moveForward = fixed[0];
            in.moveStrafe = fixed[1];
        }
        if (shouldTellyJump(Mc.player()))
            in.jump = true;
    }

    private boolean shouldTellyJump(EntityPlayerSP player) {
        if (player == null || keepY.getIndex() != KEEPY_TELLY)
            return false;
        if (!player.onGround || placeSlot < 0)
            return false;
        World world = Mc.world();
        if (world == null)
            return false;
        BlockPos underFeet = new BlockPos(player.posX, player.posY, player.posZ).down();
        return needsBridgeExtension(world, underFeet);
    }

    /** Under feet replaceable — enough to need a bridge extension this tick. */
    private static boolean needsBridgeExtension(World world, BlockPos underFeet) {
        return ScaffoldPlace.isReplaceable(world, underFeet);
    }

    private static EntityPlayerSP resolvePlayer(Object playerObj) {
        if (playerObj instanceof EntityPlayerSP)
            return (EntityPlayerSP) playerObj;
        return Mc.player();
    }
}
