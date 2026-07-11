package gnu.client.module.modules.visual;

import gnu.client.common.GnuLog;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Player ESP — 3D AABB boxes around other players (OpenMyau ESP mode 3D, simplified).
 */
public final class EspModule extends Module {

    static final class EntityData {
        double lastX;
        double lastY;
        double lastZ;
        double posX;
        double posY;
        double posZ;
        boolean sneaking;
    }

    private final SliderSetting r = addSetting(new SliderSetting("Red", 0.0f, 0.0f, 255.0f));
    private final SliderSetting g = addSetting(new SliderSetting("Green", 255.0f, 0.0f, 255.0f));
    private final SliderSetting b = addSetting(new SliderSetting("Blue", 0.0f, 0.0f, 255.0f));
    private final BoolSetting showSelf = addSetting(new BoolSetting("Show Self", false));

    private final List<EntityData> cache = new ArrayList<>();
    private boolean debugLogged;

    public EspModule() {
        super("ESP", "3D boxes around players", Category.VISUALS);
    }

    @Override
    public void onEnable() {
        debugLogged = false;
        cache.clear();
    }

    @Override
    public void onDisable() {
        cache.clear();
    }

    @Override
    public void onTick() {
        cache.clear();

        if (Mc.player() == null || Mc.world() == null)
            return;

        Entity self = Mc.player();
        for (Entity entity : Mc.getWorldEntitiesFiltered(Mc.world())) {
            if (!showSelf.getValue() && entity == self)
                continue;

            EntityData data = new EntityData();
            data.lastX = entity.lastTickPosX;
            data.lastY = entity.lastTickPosY;
            data.lastZ = entity.lastTickPosZ;
            data.posX = entity.posX;
            data.posY = entity.posY;
            data.posZ = entity.posZ;
            data.sneaking = entity.isSneaking();
            cache.add(data);
        }
    }

    @Override
    public void onRender(float partialTicks) {
        if (cache.isEmpty())
            return;

        if (!Mc.isInGame())
            return;

        double[] vp = Mc.getViewerPos(partialTicks);
        double rvpX = vp[0];
        double rvpY = vp[1];
        double rvpZ = vp[2];

        if (!debugLogged) {
            debugLogged = true;
            EntityData e0 = cache.get(0);
            GnuLog.log("ESP debug: vp=" + rvpX + "," + rvpY + "," + rvpZ
                    + " e0=" + e0.posX + "," + e0.posY + "," + e0.posZ);
        }

        float fr = r.getValue() / 255.0f;
        float fg = g.getValue() / 255.0f;
        float fb = b.getValue() / 255.0f;

        RenderHelper.begin();
        for (EntityData data : cache) {
            double ix = Mc.lerp(data.lastX, data.posX, partialTicks);
            double iy = Mc.lerp(data.lastY, data.posY, partialTicks);
            double iz = Mc.lerp(data.lastZ, data.posZ, partialTicks);
            double rx = ix - rvpX;
            double ry = iy - rvpY;
            double rz = iz - rvpZ;
            double height = data.sneaking ? 1.5 : 1.8;
            EspDraw.fill(
                    rx - 0.3, ry, rz - 0.3,
                    rx + 0.3, ry + height, rz + 0.3,
                    fr, fg, fb);
        }
        RenderHelper.end();
    }
}
