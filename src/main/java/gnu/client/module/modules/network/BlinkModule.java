package gnu.client.module.modules.network;

import gnu.client.GnuClientMod;
import gnu.client.lag.api.EnumLagDirection;
import gnu.client.lag.api.LagRequest;
import gnu.client.lag.timeout.ModuleBackedTimeout;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public final class BlinkModule extends Module {

    private static final String[] MODE_LABELS = new String[] { "Inbound", "Outbound", "Both" };

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", 1, Arrays.asList(MODE_LABELS)));
    private final BoolSetting maxDuration = addSetting(new BoolSetting("Max duration", false));
    private final SliderSetting disableAfterMs = addSetting(new SliderSetting("Disable after", 500.0f, 50.0f, 20000.0f, 50.0f));
    private final BoolSetting disableOnAttack = addSetting(new BoolSetting("Disable on Attack", false));
    private final BoolSetting initialPosition = addSetting(new BoolSetting("Show initial position", true));
    private Vec3 pos;
    private LagRequest activeLag;
    private int blinkTicks;
    private long enableTime;

    public BlinkModule() {
        super("Blink", "Hold packets with raven-bS Blink behavior", Category.MISC);
        disableAfterMs.visibleWhen(() -> maxDuration.getValue());
    }

    @Override
    public void onEnable() {
        EntityPlayer player = Mc.player();
        if (player == null || GnuClientMod.lagHandler == null) {
            setEnabled(false);
            return;
        }

        pos = new Vec3(player.posX, player.posY, player.posZ);
        blinkTicks = 0;
        enableTime = System.currentTimeMillis();
        activeLag = new LagRequest(lagDirectionsForMode(), new ModuleBackedTimeout(this));
        GnuClientMod.lagHandler.requestLag(activeLag);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        activeLag = null;
    }

    @Override
    public void guiUpdate() {
        disableAfterMs.visibleWhen(() -> maxDuration.getValue());
    }

    @Override
    public void onTick() {
        blinkTicks++;
        if (maxDuration.getValue()
            && System.currentTimeMillis() - enableTime >= (int) disableAfterMs.getInput()) {
            setEnabled(false);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!isEnabled() || !disableOnAttack.getValue() || !Mc.isInGame())
            return;
        if (event.entityPlayer != Mc.player())
            return;
        setEnabled(false);
    }

    @Override
    public void onRender(float partialTicks) {
        if (pos == null || !initialPosition.getValue() || !Mc.isInGame())
            return;

        double[] viewerPos = Mc.getViewerPos(partialTicks);
        RenderHelper.begin();
        EspDraw.fillWithGlow(
                pos.xCoord - viewerPos[0] - 0.3,
                pos.yCoord - viewerPos[1],
                pos.zCoord - viewerPos[2] - 0.3,
                pos.xCoord - viewerPos[0] + 0.3,
                pos.yCoord - viewerPos[1] + 1.8,
                pos.zCoord - viewerPos[2] + 0.3,
                0.0f,
                1.0f,
                0.0f);
        RenderHelper.end();
    }

    @Override
    public String[] getSuffix() {
        return new String[] { String.valueOf(blinkTicks) };
    }

    private Set<EnumLagDirection> lagDirectionsForMode() {
        switch (mode.getIndex()) {
            case 0:
                return EnumLagDirection.ONLY_INBOUND;
            case 2:
                return EnumLagDirection.BIDIRECTIONAL;
            case 1:
            default:
                return EnumLagDirection.ONLY_OUTBOUND;
        }
    }
}
