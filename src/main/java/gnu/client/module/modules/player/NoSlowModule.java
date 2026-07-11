package gnu.client.module.modules.player;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.FloatManager;
import gnu.client.runtime.FloatModules;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * NoSlow — OpenMyau settings/API + FloatManager.NO_SLOW ownership.
 * Slowdown cancel mixin is Task 5; do not activate BlinkModules.NO_SLOW.
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

    private final MotionCounter motionCounter = new MotionCounter();

    /** Mutable counter for grim 20/100 alternation (testable). */
    public static final class MotionCounter {
        public int count;
    }

    public NoSlowModule() {
        super("NoSlow", "Cancel item-use slowdown (sword/food/bow)", Category.PLAYER);
        updateSettingVisibility();
    }

    @Override
    public void guiUpdate() {
        updateSettingVisibility();
    }

    public static NoSlowModule instance() {
        Module module = ModuleManager.instance().getModule("NoSlow");
        return module instanceof NoSlowModule ? (NoSlowModule) module : null;
    }

    /**
     * OpenMyau grim motion: {@code count++} then even→100, odd→20.
     * First call returns 20.
     */
    public static int grimMotionPercent(MotionCounter c) {
        c.count++;
        if (c.count % 2 == 0)
            return 100;
        return 20;
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

    public boolean isAnyActive() {
        return Mc.isUsingItem() && (isSwordActive() || isFoodActive() || isBowActive());
    }

    public boolean canSprint() {
        return (isSwordActive() && swordSprint.getValue())
                || (isFoodActive() && foodSprint.getValue())
                || (isBowActive() && bowSprint.getValue());
    }

    public int getMotionMultiplier() {
        if (Mc.isHoldingSword()) {
            if (swordMode.getValue() == MODE_GRIM)
                return grimMotionPercent(motionCounter);
            motionCounter.count++;
            return Math.round(swordMotion.getValue());
        }
        if (isEating()) {
            if (foodMode.getValue() == MODE_GRIM)
                return grimMotionPercent(motionCounter);
            motionCounter.count++;
            return Math.round(foodMotion.getValue());
        }
        if (Mc.isHoldingBow()) {
            if (bowMode.getValue() == MODE_GRIM)
                return grimMotionPercent(motionCounter);
            motionCounter.count++;
            return Math.round(bowMotion.getValue());
        }
        motionCounter.count++;
        return 100;
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {
        FloatManager.INSTANCE.setFloatState(false, FloatModules.NO_SLOW);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            FloatManager.INSTANCE.setFloatState(false, FloatModules.NO_SLOW);
            return;
        }
        FloatManager.INSTANCE.setFloatState(isAnyActive(), FloatModules.NO_SLOW);
    }

    private void updateSettingVisibility() {
        int sm = swordMode.getValue();
        swordMotion.setVisible(sm == MODE_VANILLA);
        swordSprint.setVisible(sm != MODE_NONE);
        killAuraOnly.setVisible(sm != MODE_NONE);

        int fm = foodMode.getValue();
        foodMotion.setVisible(fm == MODE_VANILLA);
        foodSprint.setVisible(fm != MODE_NONE);

        int bm = bowMode.getValue();
        bowMotion.setVisible(bm == MODE_VANILLA);
        bowSprint.setVisible(bm != MODE_NONE);
    }

    /**
     * OpenMyau {@code ItemUtil.isEating} — EAT/DRINK use action, splash potions excluded.
     * Does not require {@code isUsingItem} (gated by {@link #isAnyActive}).
     */
    static boolean isEating() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        return isEatingStack(player.getHeldItem());
    }

    /** OpenMyau eating check on a held stack (no using-item gate). */
    public static boolean isEatingStack(ItemStack itemStack) {
        if (itemStack == null)
            return false;
        boolean splash = ItemPotion.isSplash(itemStack.getItem().getMetadata(itemStack));
        return matchesEatingUseAction(itemStack.getItemUseAction(), splash);
    }

    /**
     * Pure OpenMyau eating predicate for unit tests.
     * Splash potions never count; otherwise EAT or DRINK.
     */
    public static boolean matchesEatingUseAction(EnumAction action, boolean splashPotion) {
        if (splashPotion)
            return false;
        return action == EnumAction.EAT || action == EnumAction.DRINK;
    }
}
