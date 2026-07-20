package gnu.client.module.modules.visual;

import gnu.client.common.GnuLog;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

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
    private final List<Entity> scratch = new ArrayList<>();
    private float[] boxBuffer = new float[6 * 64];
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
        for (Entity entity : Mc.getWorldEntitiesFilteredInto(Mc.world(), scratch)) {
            if (!showSelf.getValue() && entity == self)
                continue;

            EntityData data = obtain(cache, cache.size());
            data.lastX = entity.lastTickPosX;
            data.lastY = entity.lastTickPosY;
            data.lastZ = entity.lastTickPosZ;
            data.posX = entity.posX;
            data.posY = entity.posY;
            data.posZ = entity.posZ;
            data.sneaking = entity.isSneaking();
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

        int n = cache.size();
        RenderHelper.begin();
        GL11.glColor4f(fr, fg, fb, EspDraw.DEFAULT_FILL_ALPHA);

        if (PerformanceModule.boxDisplayLists()) {
            // Tier-2: each box is a translated call into a cached display list.
            for (int i = 0; i < n; i++) {
                EntityData data = cache.get(i);
                double ix = Mc.lerp(data.lastX, data.posX, partialTicks);
                double iy = Mc.lerp(data.lastY, data.posY, partialTicks);
                double iz = Mc.lerp(data.lastZ, data.posZ, partialTicks);
                double rx = ix - rvpX;
                double ry = iy - rvpY;
                double rz = iz - rvpZ;
                double height = data.sneaking ? 1.5 : 1.8;
                GL11.glPushMatrix();
                GL11.glTranslated(rx, ry + height * 0.5, rz);
                // Same local AABB as drawFilledBoxList(0.6, height, 0.6): centered half-extents.
                RenderHelper.drawGlowingBoundingBox(
                        -0.3, -height * 0.5, -0.3,
                        0.3, height * 0.5, 0.3,
                        fr, fg, fb);
                // Glow leaves line color at core alpha — restore soft fill color before list.
                GL11.glColor4f(fr, fg, fb, EspDraw.DEFAULT_FILL_ALPHA);
                RenderHelper.drawFilledBoxList(0.6f, (float) height, 0.6f);
                GL11.glPopMatrix();
            }
        } else {
            ensureBoxCapacity(n);
            for (int i = 0; i < n; i++) {
                EntityData data = cache.get(i);
                double ix = Mc.lerp(data.lastX, data.posX, partialTicks);
                double iy = Mc.lerp(data.lastY, data.posY, partialTicks);
                double iz = Mc.lerp(data.lastZ, data.posZ, partialTicks);
                double rx = ix - rvpX;
                double ry = iy - rvpY;
                double rz = iz - rvpZ;
                double height = data.sneaking ? 1.5 : 1.8;
                int o = i * 6;
                boxBuffer[o] = (float) (rx - 0.3);
                boxBuffer[o + 1] = (float) ry;
                boxBuffer[o + 2] = (float) (rz - 0.3);
                boxBuffer[o + 3] = (float) (rx + 0.3);
                boxBuffer[o + 4] = (float) (ry + height);
                boxBuffer[o + 5] = (float) (rz + 0.3);
            }
            EspDraw.fillBatchedWithGlow(boxBuffer, n, fr, fg, fb, 0f);
        }

        RenderHelper.end();
    }

    private static EntityData obtain(List<EntityData> list, int index) {
        if (index < list.size())
            return list.get(index);
        EntityData data = new EntityData();
        list.add(data);
        return data;
    }

    private void ensureBoxCapacity(int n) {
        if (boxBuffer.length >= n * 6)
            return;
        int cap = boxBuffer.length;
        while (cap < n * 6)
            cap <<= 1;
        boxBuffer = new float[cap];
    }
}
