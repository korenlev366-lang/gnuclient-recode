package gnu.client.module.modules.combat.killaura;

import gnu.client.mixin.impl.accessors.IAccessorPlayerControllerMP;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.network.LagrangeModule;
import gnu.client.module.modules.player.NoSlowModule;
import gnu.client.module.modules.player.scaffold.ScaffoldModule;
import gnu.client.runtime.BlinkManager;
import gnu.client.runtime.BlinkModules;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Random;

/**
 * OpenMyau KillAura auto-block mode switch (cases 0–9).
 * KillAuraModule owns settings and combat gates; this helper owns block/blink state.
 */
public final class KillAuraAutoBlock {

    public static final int NONE = 0;
    public static final int VANILLA = 1;
    public static final int SPOOF = 2;
    public static final int HYPIXEL = 3;
    public static final int BLINK = 4;
    public static final int INTERACT = 5;
    public static final int SWAP = 6;
    public static final int LEGIT = 7;
    public static final int FAKE = 8;
    public static final int GRIM = 9;

    private final Random random = new Random();

    private boolean isBlocking;
    private boolean fakeBlockState;
    private boolean blockingState;
    private int blockTick;
    private boolean blinkReset;
    private int grimState;
    private int grimReleaseTick;
    private int lastMode = NONE;

    /** Inputs for one KillAura preUpdate combat tick. */
    public static final class Context {
        public int mode;
        /** Any living candidate within AutoBlockRange (KA computes). */
        public boolean hasValidTarget;
        /** {@code target != null && canAttack()} from KA (before autoblock mutates). */
        public boolean attackEligible;
        /** Sword held and optional use-key press. */
        public boolean canAutoBlock;
        /** User is physically holding RMB/use item; used to avoid fighting manual block. */
        public boolean manualUseKeyDown;
        /** KillAura AutoBlockRequirePress setting. */
        public boolean requirePress;
        public int grimReleaseDelay;
        public long attackDelayMs;
        public float yaw;
        public float pitch;
        /** Current KA target for interactAttack; may be null. */
        public EntityLivingBase target;
    }

    /** Outputs for Task 8 wiring. */
    public static final class TickResult {
        public boolean attackAllowed;
        /** After attack: interactAttack if attacked, else startBlock. */
        public boolean swap;
        /** After attack: pulse AUTO_BLOCK blink false→true. */
        public boolean blockedBlinkPulse;
        /** OpenMyau {@code isBlocking} — use AutoBlockCPS when true. */
        public boolean blockingSession;
    }

    public void reset() {
        setAutoBlockBlink(false);
        blinkReset = false;
        blockTick = 0;
        grimState = 0;
        grimReleaseTick = 0;
        isBlocking = false;
        fakeBlockState = false;
        if (blockingState || isPlayerBlocking())
            stopBlock();
        blockingState = false;
        lastMode = NONE;
    }

    /** OpenMyau POST: release then reacquire AUTO_BLOCK when blinkReset was set. */
    public void onPostUpdate() {
        if (!blinkReset)
            return;
        blinkReset = false;
        setAutoBlockBlink(false);
        setAutoBlockBlink(true);
    }

    /**
     * Call each KA preUpdate when KA has combat context (enabled / target path).
     * Mirrors OpenMyau PRE auto-block switch; does not perform the attack itself.
     */
    /**
     * When AutoBlockRequirePress is off, KillAura may auto-block without the user
     * holding RMB. If the user then manually holds block, do not auto-release it
     * just because the attack tick ended; that creates Grim PacketOrderI release
     * failures while the client/server still has rightClicking=true.
     */
    static boolean shouldKeepBlockingForManualUse(Context ctx) {
        return ctx != null
            && ctx.mode != NONE
            && ctx.canAutoBlock
            && !ctx.requirePress
            && ctx.manualUseKeyDown;
    }

    public TickResult tick(Context ctx) {
        TickResult result = new TickResult();
        if (ctx == null) {
            result.attackAllowed = false;
            return result;
        }
        lastMode = ctx.mode;
        boolean attack = ctx.attackEligible;
        boolean block = attack && ctx.canAutoBlock;
        if (!block) {
            if (shouldKeepBlockingForManualUse(ctx)) {
                setAutoBlockBlink(false);
                isBlocking = true;
                fakeBlockState = false;
                blockTick = 0;
            } else {
                setAutoBlockBlink(false);
                isBlocking = false;
                fakeBlockState = false;
                blockTick = 0;
                if (blockingState || isPlayerBlocking())
                    stopBlock();
            }
        }
        result.attackAllowed = attack;
        result.swap = false;
        result.blockedBlinkPulse = false;
        if (!attack) {
            result.blockingSession = isBlocking;
            return result;
        }

        boolean swap = false;
        boolean blocked = false;
        if (block) {
            boolean digging = isDigging();
            boolean placing = isPlacing();
            switch (ctx.mode) {
                case NONE:
                    if (isUseKeyDown()) {
                        isBlocking = true;
                        if (!isPlayerBlocking() && !digging && !placing)
                            swap = true;
                    } else {
                        isBlocking = false;
                        if (isPlayerBlocking() && !digging && !placing)
                            stopBlock();
                    }
                    setAutoBlockBlink(false);
                    fakeBlockState = false;
                    break;
                case VANILLA:
                    if (ctx.hasValidTarget) {
                        if (!isPlayerBlocking() && !digging && !placing)
                            swap = true;
                        setAutoBlockBlink(false);
                        isBlocking = true;
                        fakeBlockState = false;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case SPOOF:
                    if (ctx.hasValidTarget) {
                        int item = currentPlayerItem();
                        EntityPlayerSP player = Mc.player();
                        if (digging || placing
                                || player == null
                                || player.inventory.currentItem != item
                                || isPlayerBlocking() && blockTick != 0
                                || ctx.attackDelayMs > 0L && ctx.attackDelayMs <= 50L) {
                            blockTick = 0;
                        } else {
                            int slot = findEmptySlot(item);
                            Mc.sendHeldItemChange(slot);
                            Mc.sendHeldItemChange(item);
                            clearBlockAfterSlotChange(player);
                            swap = true;
                            blockTick = 1;
                        }
                        setAutoBlockBlink(false);
                        isBlocking = true;
                        fakeBlockState = false;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case HYPIXEL:
                    if (ctx.hasValidTarget) {
                        if (!digging && !placing) {
                            switch (blockTick) {
                                case 0:
                                    if (!isPlayerBlocking())
                                        swap = true;
                                    blocked = true;
                                    blockTick = 1;
                                    break;
                                case 1:
                                    if (isPlayerBlocking()) {
                                        NoSlowModule noSlow = NoSlowModule.instance();
                                        if (noSlow != null && noSlow.isEnabled()) {
                                            EntityPlayerSP p = Mc.player();
                                            if (p != null) {
                                                int randomSlot = random.nextInt(9);
                                                while (randomSlot == p.inventory.currentItem)
                                                    randomSlot = random.nextInt(9);
                                                Mc.sendHeldItemChange(randomSlot);
                                                Mc.sendHeldItemChange(p.inventory.currentItem);
                                                clearBlockAfterSlotChange(p);
                                            }
                                        }
                                        stopBlock();
                                        attack = false;
                                    }
                                    if (ctx.attackDelayMs <= 50L)
                                        blockTick = 0;
                                    break;
                                default:
                                    blockTick = 0;
                            }
                        }
                        isBlocking = true;
                        fakeBlockState = true;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case BLINK:
                    if (ctx.hasValidTarget) {
                        if (!digging && !placing) {
                            switch (blockTick) {
                                case 0:
                                    if (!isPlayerBlocking())
                                        swap = true;
                                    blinkReset = true;
                                    blockTick = 1;
                                    break;
                                case 1:
                                    if (isPlayerBlocking()) {
                                        stopBlock();
                                        attack = false;
                                    }
                                    if (ctx.attackDelayMs <= 50L)
                                        blockTick = 0;
                                    break;
                                default:
                                    blockTick = 0;
                            }
                        }
                        isBlocking = true;
                        fakeBlockState = true;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case INTERACT:
                    if (ctx.hasValidTarget) {
                        int item = currentPlayerItem();
                        EntityPlayerSP player = Mc.player();
                        if (player != null
                                && player.inventory.currentItem == item
                                && !digging
                                && !placing) {
                            switch (blockTick) {
                                case 0:
                                    if (!isPlayerBlocking())
                                        swap = true;
                                    blinkReset = true;
                                    blockTick = 1;
                                    break;
                                case 1:
                                    if (isPlayerBlocking()) {
                                        int slot = findEmptySlot(item);
                                        Mc.sendHeldItemChange(slot);
                                        setCurrentPlayerItem(slot);
                                        clearBlockAfterSlotChange(player);
                                        attack = false;
                                    }
                                    if (ctx.attackDelayMs <= 50L)
                                        blockTick = 0;
                                    break;
                                default:
                                    blockTick = 0;
                            }
                        }
                        isBlocking = true;
                        fakeBlockState = true;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case SWAP:
                    if (ctx.hasValidTarget) {
                        int item = currentPlayerItem();
                        EntityPlayerSP player = Mc.player();
                        if (player != null
                                && player.inventory.currentItem == item
                                && !digging
                                && !placing) {
                            switch (blockTick) {
                                case 0: {
                                    int slot = findSwordSlot(item);
                                    if (slot != -1) {
                                        if (!isPlayerBlocking())
                                            swap = true;
                                        blockTick = 1;
                                    }
                                    break;
                                }
                                case 1: {
                                    int swordsSlot = findSwordSlot(item);
                                    if (swordsSlot == -1) {
                                        blockTick = 0;
                                    } else if (!isPlayerBlocking()) {
                                        swap = true;
                                    } else if (ctx.attackDelayMs <= 50L) {
                                        Mc.sendHeldItemChange(swordsSlot);
                                        setCurrentPlayerItem(swordsSlot);
                                        clearBlockAfterSlotChange(player);
                                        startBlock(player.inventory.getStackInSlot(swordsSlot));
                                        attack = false;
                                        blockTick = 0;
                                    }
                                    break;
                                }
                                default:
                                    blockTick = 0;
                            }
                            setAutoBlockBlink(false);
                            isBlocking = true;
                            fakeBlockState = true;
                            break;
                        }
                    }
                    setAutoBlockBlink(false);
                    isBlocking = false;
                    fakeBlockState = false;
                    break;
                case LEGIT:
                    if (ctx.hasValidTarget) {
                        if (!digging && !placing) {
                            switch (blockTick) {
                                case 0:
                                    if (!isPlayerBlocking())
                                        swap = true;
                                    blockTick = 1;
                                    break;
                                case 1:
                                    if (isPlayerBlocking()) {
                                        stopBlock();
                                        attack = false;
                                    }
                                    if (ctx.attackDelayMs <= 50L)
                                        blockTick = 0;
                                    break;
                                default:
                                    blockTick = 0;
                            }
                        }
                        setAutoBlockBlink(false);
                        isBlocking = true;
                        fakeBlockState = false;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case FAKE:
                    setAutoBlockBlink(false);
                    isBlocking = false;
                    fakeBlockState = ctx.hasValidTarget;
                    if (isUseKeyDown() && !isPlayerBlocking() && !digging && !placing)
                        swap = true;
                    break;
                case GRIM:
                    if (ctx.hasValidTarget) {
                        switch (grimState) {
                            case 0:
                                attack = true;
                                isBlocking = false;
                                fakeBlockState = false;
                                grimState = 1;
                                break;
                            case 1:
                                if (!digging && !placing && !isPlayerBlocking()) {
                                    NoSlowModule noSlow = NoSlowModule.instance();
                                    if (noSlow == null || !noSlow.isEnabled() || !noSlow.isGrimMode()) {
                                        EntityPlayerSP player = Mc.player();
                                        if (player != null)
                                            Mc.sendHeldItemChange(grimSwapSlot(player.inventory.currentItem));
                                    }
                                    swap = true;
                                }
                                attack = false;
                                isBlocking = true;
                                fakeBlockState = false;
                                grimState = 2;
                                break;
                            case 2:
                                if (isPlayerBlocking())
                                    stopBlock();
                                attack = false;
                                isBlocking = false;
                                fakeBlockState = false;
                                grimReleaseTick = 0;
                                grimState = 3;
                                break;
                            case 3:
                                grimReleaseTick++;
                                if (grimReleaseTick >= ctx.grimReleaseDelay)
                                    grimState = 4;
                                break;
                            case 4:
                                if (ctx.attackDelayMs <= 0L)
                                    grimState = 0;
                                break;
                            default:
                                grimState = 0;
                                break;
                        }
                        setAutoBlockBlink(false);
                    } else {
                        if (isPlayerBlocking())
                            stopBlock();
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                        grimState = 0;
                        grimReleaseTick = 0;
                    }
                    break;
                default:
                    setAutoBlockBlink(false);
                    isBlocking = false;
                    fakeBlockState = false;
                    break;
            }
        }

        result.attackAllowed = attack;
        result.swap = swap;
        result.blockedBlinkPulse = blocked;
        result.blockingSession = isBlocking;
        return result;
    }

    /**
     * OpenMyau after-attack path: swap → interactAttack or startBlock; blocked → blink pulse.
     */
    public void applyAfterAttack(TickResult result, boolean attacked, float yaw, float pitch, EntityLivingBase target) {
        if (result == null)
            return;
        if (result.swap) {
            if (attacked)
                interactAttack(target, yaw, pitch);
            else
                sendUseItem();
        }
        if (result.blockedBlinkPulse) {
            setAutoBlockBlink(false);
            setAutoBlockBlink(true);
        }
    }

    public boolean isPlayerBlocking() {
        EntityPlayerSP player = Mc.player();
        return player != null
                && (player.isUsingItem() || blockingState)
                && Mc.isHoldingSword();
    }

    public boolean isBlockingSession() {
        return isBlocking;
    }

    public boolean isFakeBlocking() {
        return fakeBlockState && Mc.isHoldingSword();
    }

    /** OpenMyau performAttack: skip when blocking and mode is not VANILLA. */
    public boolean shouldDeferAttack() {
        return isPlayerBlocking() && lastMode != VANILLA;
    }

    public long attackDelayMsWhenBlocking(float autoBlockCps) {
        if (autoBlockCps <= 0.0f)
            return 1000L;
        return (long) (1000.0f / autoBlockCps);
    }

    private void startBlock(ItemStack stack) {
        EntityPlayerSP player = Mc.player();
        if (player == null || stack == null)
            return;
        Mc.startSwordBlock(player, stack);
        blockingState = true;
    }

    private void stopBlock() {
        EntityPlayerSP player = Mc.player();
        if (player == null) {
            blockingState = false;
            return;
        }
        Mc.stopSwordBlock(player);
        blockingState = false;
    }

    /** OpenMyau onPacket C09: clear blockingState and stop client use. */
    private void clearBlockAfterSlotChange(EntityPlayerSP player) {
        blockingState = false;
        if (player != null)
            player.stopUsingItem();
    }

    private void sendUseItem() {
        PlayerControllerMP controller = Mc.controller();
        if (controller instanceof IAccessorPlayerControllerMP)
            ((IAccessorPlayerControllerMP) controller).invokeSyncCurrentPlayItem();
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        startBlock(player.getHeldItem());
    }

    private void interactAttack(EntityLivingBase target, float yaw, float pitch) {
        if (target == null)
            return;
        MovingObjectPosition mop = rayTraceBox(target, yaw, pitch, 8.0);
        if (mop == null)
            return;
        PlayerControllerMP controller = Mc.controller();
        if (controller instanceof IAccessorPlayerControllerMP)
            ((IAccessorPlayerControllerMP) controller).invokeSyncCurrentPlayItem();
        Vec3 hit = mop.hitVec;
        Mc.addToSendQueue(new C02PacketUseEntity(
                target,
                new Vec3(hit.xCoord - target.posX, hit.yCoord - target.posY, hit.zCoord - target.posZ)));
        Mc.addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT));
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        ItemStack held = player.getHeldItem();
        if (held == null)
            return;
        Mc.startSwordBlock(player, held);
        blockingState = true;
    }

    private static MovingObjectPosition rayTraceBox(EntityLivingBase entity, float yaw, float pitch, double distance) {
        EntityPlayerSP player = Mc.player();
        if (player == null || entity == null || distance <= 0.0)
            return null;
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
        Vec3 eye = player.getPositionEyes(1.0f);
        float f = (float) Math.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = (float) Math.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = (float) -Math.cos(-pitch * 0.017453292F);
        float f3 = (float) Math.sin(-pitch * 0.017453292F);
        Vec3 look = new Vec3(f1 * f2, f3, f * f2);
        Vec3 end = eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return box.calculateIntercept(eye, end);
    }

    static int findEmptySlot(int currentSlot) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return Math.floorMod(currentSlot - 1, 9);
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot && player.inventory.getStackInSlot(i) == null)
                return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (stack != null && !stack.hasDisplayName())
                    return i;
            }
        }
        return Math.floorMod(currentSlot - 1, 9);
    }

    private int grimSwapSlot(int currentSlot) {
        return currentSlot == 0 ? 1 : 0;
    }

    static int findSwordSlot(int currentSlot) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return -1;
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                ItemStack item = player.inventory.getStackInSlot(i);
                if (item != null && item.getItem() instanceof ItemSword)
                    return i;
            }
        }
        return -1;
    }

    private void setAutoBlockBlink(boolean state) {
        if (state) {
            Module lag = ModuleManager.INSTANCE.getModule("Lagrange");
            if (lag instanceof LagrangeModule && lag.isEnabled())
                ((LagrangeModule) lag).pauseForBlink();
        }
        BlinkManager.INSTANCE.setBlinkState(state, BlinkModules.AUTO_BLOCK);
    }

    private static boolean isDigging() {
        PlayerControllerMP controller = Mc.controller();
        return controller instanceof IAccessorPlayerControllerMP
                && ((IAccessorPlayerControllerMP) controller).getIsHittingBlock();
    }

    private static boolean isPlacing() {
        Module module = ModuleManager.INSTANCE.getModule("Scaffold");
        return module instanceof ScaffoldModule && module.isEnabled();
    }

    private static boolean isUseKeyDown() {
        return Mc.mc() != null
                && Mc.mc().currentScreen == null
                && Mc.isUseItemKeyDown();
    }

    private static int currentPlayerItem() {
        PlayerControllerMP controller = Mc.controller();
        if (controller instanceof IAccessorPlayerControllerMP)
            return ((IAccessorPlayerControllerMP) controller).getCurrentPlayerItem();
        EntityPlayerSP player = Mc.player();
        return player != null ? player.inventory.currentItem : 0;
    }

    private static void setCurrentPlayerItem(int slot) {
        PlayerControllerMP controller = Mc.controller();
        if (controller instanceof IAccessorPlayerControllerMP)
            ((IAccessorPlayerControllerMP) controller).setCurrentPlayerItem(slot);
    }
}
