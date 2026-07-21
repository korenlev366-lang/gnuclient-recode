package gnu.client.module.modules.player;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.combat.killaura.KillAuraAutoBlock;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.utility.PacketUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.MovementInput;

import java.util.Arrays;
import java.util.List;

/**
 * NoSlow — OpenMyau settings with wsamiaw Grim C09 slot-spoof (+ optional killaura-only).
 */
public final class NoSlowModule extends Module {

    public static final int MODE_NONE = 0;
    public static final int MODE_VANILLA = 1;
    public static final int MODE_GRIM = 2;

    private static final List<String> MODES = Arrays.asList("NONE", "VANILLA", "GRIM");

    private final ModeSetting swordMode = addSetting(new ModeSetting("sword-mode", MODE_VANILLA, MODES));
    private final SliderSetting swordMotion = addSetting(new SliderSetting("sword-motion", 100f, 0f, 100f, 1f));
    private final BoolSetting swordSprint = addSetting(new BoolSetting("sword-sprint", true));
    private final BoolSetting killAuraOnly = addSetting(new BoolSetting("killaura-only", false));

    private final ModeSetting foodMode = addSetting(new ModeSetting("food-mode", MODE_NONE, MODES));
    private final SliderSetting foodMotion = addSetting(new SliderSetting("food-motion", 100f, 0f, 100f, 1f));
    private final BoolSetting foodSprint = addSetting(new BoolSetting("food-sprint", true));

    private final ModeSetting bowMode = addSetting(new ModeSetting("bow-mode", MODE_NONE, MODES));
    private final SliderSetting bowMotion = addSetting(new SliderSetting("bow-motion", 100f, 0f, 100f, 1f));
    private final BoolSetting bowSprint = addSetting(new BoolSetting("bow-sprint", true));

    /** wsamiaw swap-slot — alternate C09 target while Grim spoofing. */
    private final SliderSetting swapSlot = addSetting(new SliderSetting("swap-slot", 1f, 0f, 8f, 1f));

    private boolean spoofingSlot;
    private boolean toggleSlot;
    private int lastSentSlot = -1;

    public NoSlowModule() {
        super("NoSlow", "Cancel item-use slowdown (sword/food/bow)", Category.PLAYER);
        // wsamiaw: motion/sprint visible whenever mode != NONE (including GRIM)
        swordMotion.visibleWhen(() -> swordMode.getValue() != MODE_NONE);
        swordSprint.visibleWhen(() -> swordMode.getValue() != MODE_NONE);
        killAuraOnly.visibleWhen(() -> swordMode.getValue() != MODE_NONE);
        foodMotion.visibleWhen(() -> foodMode.getValue() != MODE_NONE);
        foodSprint.visibleWhen(() -> foodMode.getValue() != MODE_NONE);
        bowMotion.visibleWhen(() -> bowMode.getValue() != MODE_NONE);
        bowSprint.visibleWhen(() -> bowMode.getValue() != MODE_NONE);
        swapSlot.visibleWhen(() -> swordMode.getValue() == MODE_GRIM || foodMode.getValue() == MODE_GRIM);
    }

    public static NoSlowModule instance() {
        Module module = ModuleManager.instance().getModule("NoSlow");
        return module instanceof NoSlowModule ? (NoSlowModule) module : null;
    }

    public boolean isSwordActive() {
        if (killAuraOnly.getValue()) {
            Module ka = ModuleManager.instance().getModule("KillAura");
            if (!(ka instanceof KillAuraModule) || !ka.isEnabled())
                return false;
            if (KillAuraModule.getCurrentTarget() == null)
                return false;
        }
        return swordMode.getValue() != MODE_NONE && Mc.isHoldingSword();
    }

    public boolean isFoodActive() {
        return foodMode.getValue() != MODE_NONE && isEating();
    }

    public boolean isBowActive() {
        return bowMode.getValue() != MODE_NONE && Mc.isHoldingBow();
    }

    public boolean isGrimMode() {
        return (swordMode.getValue() == MODE_GRIM && Mc.isHoldingSword())
            || (foodMode.getValue() == MODE_GRIM && isEating());
    }

    public boolean isAnyActive() {
        if (isGrimMode())
            return Mc.isUsingItem();
        return Mc.isUsingItem() && (isSwordActive() || isFoodActive() || isBowActive());
    }

    public boolean canSprint() {
        if (isGrimMode())
            return true;
        return (isSwordActive() && swordSprint.getValue())
            || (isFoodActive() && foodSprint.getValue())
            || (isBowActive() && bowSprint.getValue());
    }

    public int getMotionMultiplier() {
        // Match wsamiaw: prefer held-item type, not special-cased Grim branch.
        if (Mc.isHoldingSword())
            return Math.round(swordMotion.getValue());
        if (isEating())
            return Math.round(foodMotion.getValue());
        if (Mc.isHoldingBow())
            return Math.round(bowMotion.getValue());
        return 100;
    }

    /**
     * wsamiaw LivingUpdate: scale move input by motion % and optionally clear sprint.
     * Called after vanilla use-slow is suppressed by {@link gnu.client.mixin.impl.entity.MixinEntityPlayerSPNoSlow}.
     */
    public void applyLivingUpdateNoSlow() {
        if (!isEnabled() || !isAnyActive())
            return;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        MovementInput input = player.movementInput;
        if (input == null)
            return;
        float multiplier = getMotionMultiplier() / 100.0f;
        input.moveForward *= multiplier;
        input.moveStrafe *= multiplier;
        if (!canSprint())
            player.setSprinting(false);
    }

    @Override
    public void onEnable() {
        resetSlotSpoof();
    }

    @Override
    public void onDisable() {
        resetSlotSpoof();
    }

    @Override
    public void onTickStart() {
        // wsamiaw: skip C09 spoof only when KillAura autoblock is GRIM (owns block packets).
        if (isGrimMode() && Mc.isUsingItem() && !killAuraGrimAutoBlockOwns()) {
            updateGrimSlotSpoof();
        } else {
            resetSlotSpoof();
        }
    }

    @Override
    public void onTick() {}

    /** wsamiaw: {@code killAura.autoBlock == GRIM} only — not every AB session. */
    private static boolean killAuraGrimAutoBlockOwns() {
        Module module = ModuleManager.instance().getModule("KillAura");
        if (!(module instanceof KillAuraModule) || !module.isEnabled())
            return false;
        return ((KillAuraModule) module).getAutoBlockMode() == KillAuraAutoBlock.GRIM;
    }

    private void updateGrimSlotSpoof() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        int item = player.inventory.currentItem;
        if (!spoofingSlot) {
            spoofingSlot = true;
            toggleSlot = true;
            lastSentSlot = -1;
        }
        int target = nextGrimSlot(item, Math.round(swapSlot.getValue()), toggleSlot, lastSentSlot);
        PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(target));
        lastSentSlot = target;
        toggleSlot = !toggleSlot;
    }

    private void resetSlotSpoof() {
        EntityPlayerSP player = Mc.player();
        if (spoofingSlot && player != null) {
            int item = player.inventory.currentItem;
            if (item != lastSentSlot)
                PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(item));
        }
        spoofingSlot = false;
        toggleSlot = false;
        lastSentSlot = -1;
    }

    static int nextGrimSlot(int currentSlot, int swapSlot, boolean toggle, int lastSentSlot) {
        int target = toggle ? swapSlot : currentSlot;
        if (target == lastSentSlot)
            target = (target + 1) % 9;
        return target;
    }

    static boolean isEating() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        return isEatingStack(player.getHeldItem());
    }

    public static boolean isEatingStack(ItemStack itemStack) {
        if (itemStack == null)
            return false;
        boolean splash = ItemPotion.isSplash(itemStack.getItem().getMetadata(itemStack));
        return matchesEatingUseAction(itemStack.getItemUseAction(), splash);
    }

    public static boolean matchesEatingUseAction(EnumAction action, boolean splashPotion) {
        if (splashPotion)
            return false;
        return action == EnumAction.EAT || action == EnumAction.DRINK;
    }
}
