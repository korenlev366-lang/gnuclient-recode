package gnu.client.module.modules.network;

import gnu.client.mixin.impl.accessors.IAccessorNetworkPlayerInfo;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.network.BacktrackModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.util.UUID;

/**
 * Timewarp PingFix — hides the inflated ping caused by lag modules
 * (Backtrack, Lagrange, KnockbackDelay, Blink) by fixing the displayed
 * response time in the tab list.
 *
 * <p>When lag modules are active, the server sees delayed packets and
 * reports a higher ping. PingFix overwrites {@code NetworkPlayerInfo.responseTime}
 * with a configurable value each tick.
 *
 * <p>In "Freeze on lag" mode, the module captures the clean ping before
 * lag starts and freezes it, rather than always showing a fake value.
 *
 * <p>Matched to Timewarp ctw.dll companion module behavior.
 */
public final class PingFixModule extends Module {

    private final SliderSetting fakePing = addSetting(new SliderSetting("Ping", 5.0f, 0.0f, 500.0f));
    private final BoolSetting freezeOnLag = addSetting(new BoolSetting("Freeze on lag", true));

    private int cleanPing;
    private boolean wasLagging;

    public PingFixModule() {
        super("PingFix", "Hide high ping caused by lag modules", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        cleanPing = 0;
        wasLagging = false;
    }

    @Override
    public void onDisable() {
        // no cleanup needed
    }

    @Override
    public void onTick() {
        if (!isEnabled())
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        NetHandlerPlayClient netHandler = Mc.netHandler();
        if (netHandler == null)
            return;

        UUID uid = player.getUniqueID();
        NetworkPlayerInfo info = netHandler.getPlayerInfo(uid);
        if (info == null)
            return;

        IAccessorNetworkPlayerInfo pingInfo = (IAccessorNetworkPlayerInfo) info;
        boolean currentlyLagging = isAnyLagModuleActive();

        if (freezeOnLag.getValue() && currentlyLagging) {
            if (!wasLagging) {
                cleanPing = pingInfo.getResponseTime();
                wasLagging = true;
            }
            pingInfo.setResponseTime(Math.max(cleanPing, fakePing.getValue().intValue()));
        } else {
            wasLagging = false;
            pingInfo.setResponseTime(fakePing.getValue().intValue());
        }
    }

    /** Returns true if any known lag module is actively delaying packets. */
    private boolean isAnyLagModuleActive() {
        Module bt = ModuleManager.INSTANCE.getModule("Back Track");
        if (bt instanceof BacktrackModule && ((BacktrackModule) bt).isLagging())
            return true;

        Module lag = ModuleManager.INSTANCE.getModule("Lagrange");
        if (lag instanceof LagrangeModule && lag.isEnabled()
                && ((LagrangeModule) lag).hasQueuedPackets())
            return true;

        if (KnockbackDelayModule.isOwningInboundQueue()
                || KnockbackDelayModule.isBlockingBacktrack())
            return true;

        Module blink = ModuleManager.INSTANCE.getModule("Blink");
        if (blink instanceof BlinkModule && blink.isEnabled())
            return true;

        return false;
    }
}
