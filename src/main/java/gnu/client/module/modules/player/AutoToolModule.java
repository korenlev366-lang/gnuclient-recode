package gnu.client.module.modules.player;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.player.scaffold.ScaffoldModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

/**
 * Switches to the best hotbar tool for the looked-at block while mining.
 * Settings mirror wsamiaw AutoTool: delay, switch-back, sneak-only.
 *
 * <p>Only mutates {@code inventory.currentItem} — never force-sends {@code C09}.
 * Eager {@code syncCurrentPlayItem} after the flying packet flags Grim
 * {@code Post held item change v1.8}. Vanilla syncs on the next dig/attack
 * (mouse poll, before {@code C03}).
 */
public final class AutoToolModule extends Module {

    private final SliderSetting switchDelay = addSetting(new SliderSetting("Delay", 0f, 0f, 5f, 1f));
    private final BoolSetting switchBack = addSetting(new BoolSetting("Switch back", true));
    private final BoolSetting sneakOnly = addSetting(new BoolSetting("Sneak only", true));

    private int previousSlot = -1;
    private int activeToolSlot = -1;
    private int tickDelayCounter;

    public AutoToolModule() {
        super("AutoTool", "Switches to the best hotbar tool for the block you mine", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        resetState(false);
    }

    @Override
    public void onDisable() {
        resetState(true);
    }

    /** PRE-style: before living update / C03, same window as wsamiaw AutoTool. */
    @Override
    public void onTickStart() {
        if (!Mc.isInGame()) {
            resetState(true);
            return;
        }

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null) {
            resetState(true);
            return;
        }

        // Manual scroll while swapped — drop ownership so we don't fight the player.
        if (activeToolSlot != -1 && Mc.getHotbarSlot(player) != activeToolSlot) {
            previousSlot = -1;
            activeToolSlot = -1;
            tickDelayCounter = 0;
        }

        if (!canAutoTool(player, world)) {
            resetState(true);
            return;
        }

        if (tickDelayCounter < Math.round(switchDelay.getValue())) {
            tickDelayCounter++;
            return;
        }
        tickDelayCounter++;

        MovingObjectPosition mop = Mc.objectMouseOver();
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return;

        BlockPos pos = mop.getBlockPos();
        if (pos == null)
            return;
        Block block = world.getBlockState(pos).getBlock();
        if (block == null)
            return;
        if (block.getBlockHardness(world, pos) == 0.0f)
            return;

        int best = findBetterSlot(player, block);
        if (best < 0 || best == Mc.getHotbarSlot(player))
            return;

        if (previousSlot < 0)
            previousSlot = Mc.getHotbarSlot(player);
        selectSlot(player, best);
        activeToolSlot = best;
    }

    private boolean canAutoTool(EntityPlayerSP player, WorldClient world) {
        if (Mc.currentScreen() != null || player.isDead || !player.capabilities.allowEdit)
            return false;
        if (isScaffoldActive() || isKillAuraFighting())
            return false;
        if (Mc.isUsingItem(player))
            return false;
        if (!Mc.isAttackKeyDown())
            return false;
        if (sneakOnly.getValue() && !Mc.isSneakKeyHeld())
            return false;

        MovingObjectPosition mop = Mc.objectMouseOver();
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return false;
        BlockPos pos = mop.getBlockPos();
        if (pos == null)
            return false;
        Block block = world.getBlockState(pos).getBlock();
        if (block == null)
            return false;
        float hardness = block.getBlockHardness(world, pos);
        return hardness > 0.0f;
    }

    private static boolean isScaffoldActive() {
        Module module = ModuleManager.instance().getModule("Scaffold");
        return module instanceof ScaffoldModule && module.isEnabled();
    }

    private static boolean isKillAuraFighting() {
        return KillAuraModule.getCurrentTarget() != null;
    }

    /** Best hotbar slot strictly faster than the currently held item, or {@code -1}. */
    private static int findBetterSlot(EntityPlayerSP player, Block block) {
        int current = Mc.getHotbarSlot(player);
        float currentSpeed = Mc.getDigSpeed(player.getHeldItem(), block);
        int bestSlot = Mc.findBestHotbarTool(block);
        if (bestSlot < 0)
            return -1;
        ItemStack bestStack = Mc.getStackInSlot(player.inventory, bestSlot);
        float bestSpeed = Mc.getDigSpeed(bestStack, block);
        if (bestSpeed <= currentSpeed + 1.0e-4f)
            return -1;
        if (bestSlot == current)
            return -1;
        return bestSlot;
    }

    /** Client hotbar only — vanilla {@code syncCurrentPlayItem} sends C09 on next dig. */
    private void selectSlot(EntityPlayerSP player, int slot) {
        if (player == null || slot < 0 || slot > 8)
            return;
        if (Mc.getHotbarSlot(player) != slot)
            Mc.setHotbarSlot(player, slot);
    }

    private void resetState(boolean restore) {
        EntityPlayerSP player = Mc.player();
        if (restore && switchBack.getValue() && previousSlot >= 0 && player != null)
            selectSlot(player, previousSlot);
        previousSlot = -1;
        activeToolSlot = -1;
        tickDelayCounter = 0;
    }
}
