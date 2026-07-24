package gnu.client.module.modules.misc;

import gnu.client.anticheat.AnticheatManager;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.runtime.mc.Mc;

/**
 * Other-player cheat detector (OpenMyAU HackerDetector style).
 * Chat alerts only — does not send, cancel, or sanitize packets.
 */
public final class AnticheatModule extends Module {

    private final BoolSetting scaffold = addSetting(new BoolSetting("Scaffold", true));
    private final BoolSetting killAura = addSetting(new BoolSetting("KillAura", true));
    private final BoolSetting autoBlock = addSetting(new BoolSetting("AutoBlock", true));
    private final BoolSetting noSlow = addSetting(new BoolSetting("NoSlow", true));
    private final BoolSetting blink = addSetting(new BoolSetting("Blink", true));
    private final BoolSetting reach = addSetting(new BoolSetting("Reach", true));
    private final BoolSetting velocity = addSetting(new BoolSetting("Velocity", true));
    private final BoolSetting speed = addSetting(new BoolSetting("Speed", true));
    private final BoolSetting flight = addSetting(new BoolSetting("Flight", true));
    private final BoolSetting ground = addSetting(new BoolSetting("Ground", true));
    private final BoolSetting multiAura = addSetting(new BoolSetting("MultiAura", true));
    private final BoolSetting prediction = addSetting(new BoolSetting("Prediction", true));
    private final BoolSetting lagrange = addSetting(new BoolSetting("Lagrange", true));
    private final BoolSetting backtrack = addSetting(new BoolSetting("Backtrack", true));
    private final BoolSetting lagAbuse = addSetting(new BoolSetting("Lag Abuse", true));
    private final BoolSetting sound = addSetting(new BoolSetting("Sound", true));
    private final BoolSetting ignoreBots = addSetting(new BoolSetting("Ignore Bots", true));

    public AnticheatModule() {
        super("Anticheat", "Detects other players cheating and alerts you in chat", Category.MISC);
    }

    @Override
    public void onEnable() {
        pushConfig();
        AnticheatManager.instance().setActive(true);
    }

    @Override
    public void onDisable() {
        AnticheatManager.instance().setActive(false);
    }

    @Override
    public void onTick() {
        if (!isEnabled() || !Mc.isInGame())
            return;
        pushConfig();
        AnticheatManager.instance().tick();
    }

    private void pushConfig() {
        AnticheatManager.instance().configure(
                scaffold.getValue(),
                killAura.getValue(),
                autoBlock.getValue(),
                noSlow.getValue(),
                blink.getValue(),
                reach.getValue(),
                velocity.getValue(),
                speed.getValue(),
                flight.getValue(),
                ground.getValue(),
                multiAura.getValue(),
                prediction.getValue(),
                lagrange.getValue(),
                backtrack.getValue(),
                lagAbuse.getValue(),
                sound.getValue(),
                ignoreBots.getValue());
    }

    @Override
    public String[] getSuffix() {
        return new String[] { "Watch" };
    }
}
