package gnu.client.module.modules.player.scaffold;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

import java.util.Arrays;

/**
 * Silent scaffold bridge (KA-style rotations + MoveFix). Place/tick logic lands in a
 * follow-up; this shell owns settings, lifecycle, spoof slot, and static hook entrypoints.
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
        lastSentYaw = lastSentPitch = Float.MIN_VALUE;
        tellyLookForward = false;
        liveTarget = null;
    }

    @Override
    public void onDisable() {
        clearRotationIfOwned();
        restoreHotbarToSpoof();
        liveTarget = null;
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

    /** Task 6 fills silent rotation / target find. */
    private void preUpdate(Object player) {
    }

    /** Task 6 fills place-with-silent-look. */
    private void beforeWalkingPlace(Object player) {
    }

    /** Task 6 fills MoveFix + Telly jump. */
    private void doPatchMovementInput(Object movInput) {
    }
}
