package gnu.client.module.modules.combat;

import gnu.client.event.StrafeEvent;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.BlinkManager;
import gnu.client.runtime.BlinkModules;
import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.PlayerUpdateHook;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.utility.Utils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Displace — push targets toward void while attacking (OpenMiau port).
 *
 * <p>Silent movefix is OpenMyau-Plus style: {@link gnu.client.runtime.MoveFixHook}
 * swaps {@code moveFlying} to displace yaw at priority {@link #ROTATION_PRIORITY}
 * (priority 2), plus {@link #patchStrafe} {@code forward=1} / compensate strafe.
 * Do <b>not</b> use FixMovement or {@link MoveFixUtil#fixStrafe} — remapping WASD
 * then forcing forward=1 leaves leftover strafe and causes Grim Simulation micro-offsets.
 */
public final class DisplaceModule extends Module implements PacketListener {

    public static final int ROTATION_PRIORITY = MoveFixUtil.DISPLACE_MOVE_FIX_PRIORITY;

    private static final int DISPLACE_WINDOW_TICKS = 10;
    private static final int VOID_SCAN_DIRECTIONS = 32;
    private static final int VOID_SCAN_RINGS = 12;
    private static final int VOID_SCAN_DEPTH = 10;
    private static final double VOID_SCAN_STEP = 0.5D;
    private static final double DYNAMIC_SCAN_STEP = 0.5D;
    private static final double DYNAMIC_SCAN_DISTANCE = 6.0D;
    private static final double DYNAMIC_SCAN_SIDE_STEP = 0.45D;
    private static final double DYNAMIC_WALL_CHECK_STEP = 0.25D;
    private static final double DYNAMIC_COLLISION_INSET = 0.03D;
    private static final long ARROW_FADE_MS = 250L;
    private static final double ARROW_FORWARD_GAP = 0.24D;
    private static final double ARROW_BODY_LENGTH = 0.74D;
    private static final double ARROW_BODY_HALF_HEIGHT = 0.08D;
    private static final double ARROW_HEAD_BACKSET = 0.18D;
    private static final double ARROW_HEAD_LENGTH = 0.52D;
    private static final double ARROW_HEAD_HALF_HEIGHT = 0.30D;
    private static final double[] VOID_SCAN_X = new double[VOID_SCAN_DIRECTIONS];
    private static final double[] VOID_SCAN_Z = new double[VOID_SCAN_DIRECTIONS];

    static {
        for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
            double angle = Math.PI * 2.0D * (double) i / (double) VOID_SCAN_DIRECTIONS;
            VOID_SCAN_X[i] = Math.cos(angle);
            VOID_SCAN_Z[i] = Math.sin(angle);
        }
    }

    private static final List<String> DYNAMIC_ANGLE_MODES =
            Arrays.asList("STATIC", "DYNAMIC");
    private static final List<String> DIRECTION_MODES =
            Arrays.asList("LEFT", "RIGHT");

    private final ModeSetting dynamicAngle = addSetting(
            new ModeSetting("Dynamic Angle", 0, DYNAMIC_ANGLE_MODES));
    private final SliderSetting yawOffset = addSetting(
            new SliderSetting("Yaw Offset", 90f, 0f, 180f, 1f));
    private final SliderSetting delayMs = addSetting(
            new SliderSetting("Delay ms", 0f, 0f, 500f, 1f));
    private final ModeSetting direction = addSetting(
            new ModeSetting("Direction", 0, DIRECTION_MODES));
    private final BoolSetting showDirection = addSetting(
            new BoolSetting("Show Direction", true));
    private final BoolSetting findVoid = addSetting(
            new BoolSetting("Find Void", false));
    private final BoolSetting blink = addSetting(
            new BoolSetting("Blink", false));
    private final BoolSetting ignoreTeammates = addSetting(
            new BoolSetting("Ignore Teammates", true));
    private final BoolSetting hasKnockback = addSetting(
            new BoolSetting("Has Knockback", false));

    private boolean displaceThisTick;
    private boolean active;
    private boolean hasKB;
    private boolean compensateNextTick;
    private boolean displaceLeft;
    private boolean wasDisplacingLastTick;
    private boolean releaseBlinkNextTick;
    private Float renderDisplaceYaw;
    private EntityPlayer renderTarget;
    private Float fadingDisplaceYaw;
    private EntityPlayer fadingTarget;
    private long arrowFadeStartMs;
    private Float lastRenderedDisplaceYaw;
    private EntityPlayer lastRenderedTarget;
    private long lastRenderedArrowMs;
    private int tickCounter;
    private final Map<Integer, Integer> targetWindowStartTicks = new HashMap<>();

    public DisplaceModule() {
        super("Displace", "Displace targets toward void while attacking", Category.COMBAT);
        yawOffset.visibleWhen(() -> dynamicAngle.getIndex() == 0);
        direction.visibleWhen(() -> dynamicAngle.getIndex() == 0);
        findVoid.visibleWhen(() -> dynamicAngle.getIndex() == 0);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{Math.round(delayMs.getValue()) + "ms"};
    }

    @Override
    public void onEnable() {
        resetState();
        PacketEvents.register(this);
    }

    @Override
    public void onDisable() {
        resetState();
        releaseBlink();
        PacketEvents.unregister(this);
        clearRotationIfOwned();
    }

    @Override
    public void onTickStart() {
        releaseBlinkIfScheduled();

        if (!isEnabled()) {
            clearActiveState();
            clearRotationIfOwned();
            return;
        }

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null) {
            clearActiveState();
            clearRotationIfOwned();
            return;
        }

        tickCounter++;
        pruneTargetDelayStates();

        if (hasKnockback.getValue() && EnchantmentHelper.getKnockbackModifier(player) <= 0) {
            clearActiveState();
            clearRotationIfOwned();
            return;
        }

        boolean attacking = Mc.isAttackKeyDown();
        EntityPlayer target = attacking ? findClosestTarget(9.0D) : null;
        boolean hasKBEnchant = EnchantmentHelper.getKnockbackModifier(player) > 0;
        active = target != null && (hasKBEnchant || anyMovementKey());
        if (!active) {
            clearActiveState();
            clearRotationIfOwned();
            return;
        }

        Float dynamicVoidYaw = isDynamicAngle()
                ? findDynamicVoidYaw(target)
                : findVoid.getValue() ? findStaticVoidYaw(target) : null;
        if (dynamicVoidYaw == null && !isDynamicAngle()) {
            displaceLeft = direction.getIndex() == 0;
        }
        renderDisplaceYaw = dynamicVoidYaw != null ? dynamicVoidYaw : isDynamicAngle() ? null : getFixedDisplaceYaw();
        renderTarget = renderDisplaceYaw != null ? target : null;
        if (renderDisplaceYaw == null) {
            clearActiveState();
            clearRotationIfOwned();
            return;
        }

        hasKB = hasKBEnchant;
        displaceThisTick = !displaceThisTick;
        if (displaceThisTick && !shouldDisplaceInCurrentWindow(target, tickCounter)) {
            clearActiveState();
            clearRotationIfOwned();
            return;
        }

        if (!displaceThisTick && wasDisplacingLastTick
                && Mc.settings().keyBindAttack.getKeyCode() != 0) {
            Mc.pressAttackKeyOnce();
        }
        wasDisplacingLastTick = displaceThisTick;

        if (displaceThisTick) {
            RotationState.applyState(
                    true, renderDisplaceYaw, player.rotationPitch, renderDisplaceYaw, ROTATION_PRIORITY);
        } else {
            clearRotationIfOwned();
        }
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onRender(float partialTicks) {
        if (!isEnabled() || !showDirection.getValue()) {
            clearArrowState();
            return;
        }
        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null) {
            clearArrowState();
            return;
        }

        long nowMs = System.currentTimeMillis();
        boolean activeArrow = active && renderDisplaceYaw != null && renderTarget != null && !renderTarget.isDead;
        Float arrowYaw = renderDisplaceYaw;
        EntityPlayer arrowTarget = renderTarget;
        float alpha = 1.0F;
        if (!activeArrow) {
            if (fadingDisplaceYaw == null || fadingTarget == null || fadingTarget.isDead) {
                return;
            }
            long fadeElapsed = nowMs - arrowFadeStartMs;
            if (fadeElapsed >= ARROW_FADE_MS) {
                clearArrowState();
                return;
            }
            arrowYaw = fadingDisplaceYaw;
            arrowTarget = fadingTarget;
            alpha = 1.0F - (float) fadeElapsed / (float) ARROW_FADE_MS;
        }
        renderArrow(arrowTarget, arrowYaw, alpha, partialTicks);
        if (activeArrow) {
            lastRenderedDisplaceYaw = arrowYaw;
            lastRenderedTarget = arrowTarget;
            lastRenderedArrowMs = nowMs;
        }
    }

    @Override
    public boolean onSend(Object packet) {
        if (!isEnabled() || !blink.getValue() || !active || !displaceThisTick || releaseBlinkNextTick) {
            return false;
        }
        if (packet instanceof C03PacketPlayer) {
            BlinkManager.INSTANCE.setBlinkState(true, BlinkModules.DISPLACE);
            releaseBlinkNextTick = true;
        }
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    public static void onPreUpdate(Object player) {
        Module mod = ModuleManager.INSTANCE.getModule("Displace");
        if (!(mod instanceof DisplaceModule) || !mod.isEnabled()) {
            return;
        }
        DisplaceModule dm = (DisplaceModule) mod;
        if (dm.displaceThisTick && dm.active && dm.renderDisplaceYaw != null) {
            EntityPlayerSP local = Mc.player();
            float pitch = local != null ? local.rotationPitch : 0.0F;
            PlayerUpdateHook.requestRotation(dm.renderDisplaceYaw, pitch);
        }
    }

    public static void patchStrafe(StrafeEvent event) {
        Module mod = ModuleManager.INSTANCE.getModule("Displace");
        if (!(mod instanceof DisplaceModule) || !mod.isEnabled() || event == null) {
            return;
        }
        DisplaceModule dm = (DisplaceModule) mod;
        if (!dm.active) {
            dm.compensateNextTick = false;
            return;
        }
        if (dm.compensateNextTick && !dm.displaceThisTick) {
            dm.compensateNextTick = false;
            event.setStrafe(dm.displaceLeft ? -1.0F : 1.0F);
            return;
        }
        if (!dm.displaceThisTick || dm.hasKB || !dm.anyMovementKey()) {
            return;
        }
        event.setForward(1.0F);
        dm.compensateNextTick = true;
    }

    private void releaseBlinkIfScheduled() {
        if (releaseBlinkNextTick) {
            releaseBlink();
            releaseBlinkNextTick = false;
        }
    }

    private void resetState() {
        displaceThisTick = false;
        active = false;
        hasKB = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        releaseBlinkNextTick = false;
        renderDisplaceYaw = null;
        renderTarget = null;
        clearArrowState();
        tickCounter = 0;
        targetWindowStartTicks.clear();
    }

    private void releaseBlink() {
        if (BlinkManager.INSTANCE.getBlinkingModule() == BlinkModules.DISPLACE) {
            BlinkManager.INSTANCE.setBlinkState(false, BlinkModules.DISPLACE);
        }
    }

    private void clearRotationIfOwned() {
        if ((int) RotationState.getPriority() == ROTATION_PRIORITY) {
            RotationState.reset();
        }
    }

    private boolean anyMovementKey() {
        return Mc.isMovementKeyHeld();
    }

    private boolean isDynamicAngle() {
        return dynamicAngle.getIndex() == 1;
    }

    private static int msToTicks(double ms) {
        return ms <= 0.0D ? 0 : (int) Math.ceil(ms / 50.0D);
    }

    private EntityPlayer findClosestTarget(double range) {
        EntityPlayerSP self = Mc.player();
        WorldClient world = Mc.world();
        if (self == null || world == null) {
            return null;
        }
        EntityPlayer best = null;
        double bestDist = range * range;
        for (EntityPlayer player : world.playerEntities) {
            if (player == null || player == self || player.isDead || player.deathTime != 0) {
                continue;
            }
            if (ignoreTeammates.getValue() && Utils.isTeammate(player)) {
                continue;
            }
            if (RavenAntiBot.isBot(player) || Utils.isFriended(player)) {
                continue;
            }
            double dist = self.getDistanceSqToEntity(player);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private Float findStaticVoidYaw(EntityPlayer target) {
        EntityPlayerSP self = Mc.player();
        if (self == null) {
            return null;
        }
        double bestX = 0.0D;
        double bestZ = 0.0D;
        double bestScore = Double.MAX_VALUE;
        for (int ring = 1; ring <= VOID_SCAN_RINGS; ring++) {
            double radius = (double) ring * VOID_SCAN_STEP;
            boolean foundInRing = false;
            for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
                double x = target.posX + VOID_SCAN_X[i] * radius;
                double z = target.posZ + VOID_SCAN_Z[i] * radius;
                if (!isVoidColumn(x, target.posY, z)) {
                    continue;
                }
                double dx = x - self.posX;
                double dz = z - self.posZ;
                double score = radius * radius * 1000.0D + dx * dx + dz * dz;
                if (score < bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestZ = z;
                    foundInRing = true;
                }
            }
            if (foundInRing) {
                break;
            }
        }
        if (bestScore == Double.MAX_VALUE) {
            return null;
        }
        updateDisplaceSide(target, bestX, bestZ);
        return yawTo(target.posX, target.posY + target.getEyeHeight() * 0.5D, target.posZ,
                bestX, target.posY + target.getEyeHeight() * 0.5D, bestZ);
    }

    private Float findDynamicVoidYaw(EntityPlayer target) {
        double bestForwardX = 0.0D;
        double bestForwardZ = 0.0D;
        double bestScore = 0.0D;
        for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
            double forwardX = VOID_SCAN_X[i];
            double forwardZ = VOID_SCAN_Z[i];
            double score = scoreVoidPath(target, forwardX, forwardZ);
            if (score > bestScore) {
                bestScore = score;
                bestForwardX = forwardX;
                bestForwardZ = forwardZ;
            }
        }
        if (bestScore <= 0.0D) {
            return null;
        }
        updateDisplaceSide(target, target.posX + bestForwardX, target.posZ + bestForwardZ);
        return (float) (Math.toDegrees(Math.atan2(bestForwardZ, bestForwardX)) - 90.0D);
    }

    private float yawTo(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
    }

    private double scoreVoidPath(EntityPlayer target, double forwardX, double forwardZ) {
        double sideX = -forwardZ;
        double sideZ = forwardX;
        double score = 0.0D;
        double checkedForward = 0.0D;
        int consecutiveCenterVoid = 0;
        AxisAlignedBB baseBox = target.getEntityBoundingBox().contract(DYNAMIC_COLLISION_INSET, 0.0D, DYNAMIC_COLLISION_INSET);
        for (int step = 1; step <= (int) (DYNAMIC_SCAN_DISTANCE / DYNAMIC_SCAN_STEP); step++) {
            double forward = (double) step * DYNAMIC_SCAN_STEP;
            if (!isDynamicPathClear(target, baseBox, forwardX, forwardZ, checkedForward, forward)) {
                break;
            }
            checkedForward = forward;
            boolean centerVoid = false;
            for (int side = -1; side <= 1; side++) {
                double sideOffset = (double) side * DYNAMIC_SCAN_SIDE_STEP;
                double x = target.posX + forwardX * forward + sideX * sideOffset;
                double z = target.posZ + forwardZ * forward + sideZ * sideOffset;
                if (isVoidColumn(x, target.posY, z)) {
                    score += (side == 0 ? 1.4D : 1.0D) * (DYNAMIC_SCAN_DISTANCE + DYNAMIC_SCAN_STEP - forward);
                    centerVoid |= side == 0;
                }
            }
            if (centerVoid) {
                score += ++consecutiveCenterVoid * 2.0D;
            } else {
                consecutiveCenterVoid = 0;
            }
        }
        return score;
    }

    private boolean isDynamicPathClear(EntityPlayer target, AxisAlignedBB baseBox,
                                       double forwardX, double forwardZ, double fromForward, double toForward) {
        for (double forward = fromForward + DYNAMIC_WALL_CHECK_STEP;
             forward <= toForward + 1.0E-4D;
             forward += DYNAMIC_WALL_CHECK_STEP) {
            if (hasBlockCollision(target, baseBox.offset(forwardX * forward, 0.0D, forwardZ * forward))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasBlockCollision(EntityPlayer target, AxisAlignedBB box) {
        WorldClient world = Mc.world();
        if (world == null) {
            return true;
        }
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX + 1.0D);
        int minY = MathHelper.floor_double(box.minY);
        int maxY = MathHelper.floor_double(box.maxY + 1.0D);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ + 1.0D);
        List<AxisAlignedBB> collisions = new ArrayList<>();
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                if (!world.isBlockLoaded(blockPos.set(x, 64, z))) {
                    return true;
                }
                for (int y = minY; y < maxY; y++) {
                    if (y < 0 || y >= 256) {
                        return true;
                    }
                    blockPos.set(x, y, z);
                    IBlockState state = world.getBlockState(blockPos);
                    state.getBlock().addCollisionBoxesToList(world, blockPos, state, box, collisions, target);
                    if (!collisions.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isVoidColumn(double x, double y, double z) {
        WorldClient world = Mc.world();
        if (world == null) {
            return false;
        }
        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        int startY = MathHelper.floor_double(y) - 1;
        int endY = Math.max(0, startY - VOID_SCAN_DEPTH);
        for (int blockY = startY; blockY >= endY; blockY--) {
            if (!world.isAirBlock(new BlockPos(blockX, blockY, blockZ))) {
                return false;
            }
        }
        return true;
    }

    private void updateDisplaceSide(EntityPlayer target, double voidX, double voidZ) {
        EntityPlayerSP self = Mc.player();
        if (self == null) {
            return;
        }
        double targetDx = target.posX - self.posX;
        double targetDz = target.posZ - self.posZ;
        double voidDx = voidX - self.posX;
        double voidDz = voidZ - self.posZ;
        displaceLeft = targetDx * voidDz - targetDz * voidDx < 0.0D;
    }

    private float getFixedDisplaceYaw() {
        EntityPlayerSP self = Mc.player();
        if (self == null) {
            return 0.0F;
        }
        return displaceLeft ? self.rotationYaw - yawOffset.getValue() : self.rotationYaw + yawOffset.getValue();
    }

    private void clearActiveState() {
        startArrowFade();
        active = false;
        displaceThisTick = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        renderDisplaceYaw = null;
        renderTarget = null;
    }

    private void clearArrowState() {
        fadingDisplaceYaw = null;
        fadingTarget = null;
        arrowFadeStartMs = 0L;
        lastRenderedDisplaceYaw = null;
        lastRenderedTarget = null;
        lastRenderedArrowMs = 0L;
    }

    private void startArrowFade() {
        long nowMs = System.currentTimeMillis();
        if (lastRenderedDisplaceYaw != null && lastRenderedTarget != null && !lastRenderedTarget.isDead
                && nowMs - lastRenderedArrowMs <= ARROW_FADE_MS) {
            fadingDisplaceYaw = lastRenderedDisplaceYaw;
            fadingTarget = lastRenderedTarget;
            arrowFadeStartMs = nowMs;
        }
        lastRenderedDisplaceYaw = null;
        lastRenderedTarget = null;
        lastRenderedArrowMs = 0L;
    }

    private void pruneTargetDelayStates() {
        WorldClient world = Mc.world();
        if (world == null) {
            targetWindowStartTicks.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Integer>> iterator = targetWindowStartTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Entity entity = world.getEntityByID(iterator.next().getKey());
            if (!(entity instanceof EntityPlayer) || entity.isDead || ((EntityPlayer) entity).deathTime != 0) {
                iterator.remove();
            }
        }
    }

    private boolean shouldDisplaceInCurrentWindow(EntityPlayer target, int currentTick) {
        int targetId = target.getEntityId();
        Integer windowStartTick = targetWindowStartTicks.get(targetId);
        if (windowStartTick == null || currentTick - windowStartTick >= DISPLACE_WINDOW_TICKS) {
            targetWindowStartTicks.put(targetId, currentTick);
            return true;
        }
        int delayTicks = msToTicks(delayMs.getValue());
        return delayTicks <= 0 || currentTick - windowStartTick >= delayTicks;
    }

    private void renderArrow(EntityPlayer target, float yaw, float alpha, float partialTicks) {
        double centerX = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks;
        double centerY = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks + target.height * 0.5D;
        double centerZ = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks;
        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double baseOffset = target.width * 0.5D + ARROW_FORWARD_GAP;
        double tailX = centerX + forwardX * baseOffset;
        double tailZ = centerZ + forwardZ * baseOffset;
        double bodyEndX = tailX + forwardX * ARROW_BODY_LENGTH;
        double bodyEndZ = tailZ + forwardZ * ARROW_BODY_LENGTH;
        double headBackX = tailX + forwardX * (ARROW_BODY_LENGTH - ARROW_HEAD_BACKSET);
        double headBackZ = tailZ + forwardZ * (ARROW_BODY_LENGTH - ARROW_HEAD_BACKSET);
        double tipX = bodyEndX + forwardX * ARROW_HEAD_LENGTH;
        double tipZ = bodyEndZ + forwardZ * ARROW_HEAD_LENGTH;
        double viewerX = Mc.renderManager().viewerPosX;
        double viewerY = Mc.renderManager().viewerPosY;
        double viewerZ = Mc.renderManager().viewerPosZ;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.82F * alpha);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex3d(tailX - viewerX, centerY - viewerY, tailZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, -ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glEnd();
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private void arrowVertex(double x, double y, double z, double verticalOffset,
                             double viewerX, double viewerY, double viewerZ) {
        GL11.glVertex3d(x - viewerX, y + verticalOffset - viewerY, z - viewerZ);
    }
}
