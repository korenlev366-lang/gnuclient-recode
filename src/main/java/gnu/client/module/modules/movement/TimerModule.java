package gnu.client.module.modules.movement;

import gnu.client.mixin.impl.accessors.IAccessorMinecraft;
import gnu.client.mixin.impl.accessors.IAccessorTimer;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.util.Timer;

/**
 * Simple timer — sets {@code Timer.timerSpeed} while enabled and restores vanilla
 * {@code 1.0f} on disable.
 */
public final class TimerModule extends Module {

    private final SliderSetting speed =
            addSetting(new SliderSetting("Speed", 1.0f, 0.0f, 2.0f, 0.01f));

    public TimerModule() {
        super("Timer", "Change game tick speed", Category.MOVEMENT);
    }

    /** Safety net — restore vanilla speed when neither Timer, TimerRange, nor Speed owns it. */
    public static void maintain() {
        if (gnu.client.module.modules.combat.TimerRangeModule.isControllingTimer())
            return;
        if (SpeedModule.isControllingTimer())
            return;
        Module module = ModuleManager.INSTANCE.getModule("Timer");
        if (!(module instanceof TimerModule) || !module.isEnabled())
            Mc.resetTimer();
    }

    @Override
    public void onEnable() {
        apply();
    }

    @Override
    public void onDisable() {
        Mc.resetTimer();
    }

    @Override
    public void onTickStart() {
        apply();
    }

    @Override
    public void onTick() {
        apply();
    }

    private void apply() {
        if (!isEnabled())
            return;
        Timer timer = ((IAccessorMinecraft) Mc.mc()).getTimer();
        if (timer != null)
            ((IAccessorTimer) timer).setTimerSpeed(speed.getValue());
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%.2fx", speed.getValue())};
    }
}
