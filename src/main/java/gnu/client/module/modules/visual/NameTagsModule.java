package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.module.modules.combat.AntiBotModule;
import gnu.client.module.modules.combat.RavenAntiBot;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Custom player nametags via 2D screen-space projection (vanilla-style overlay).
 */
public final class NameTagsModule extends Module {

    static final class EntityData {
        double lastX;
        double lastY;
        double lastZ;
        double posX;
        double posY;
        double posZ;
        boolean sneaking;
        String tag;
    }

    private final SliderSetting scale = addSetting(new SliderSetting("Scale", 1.0f, 0.5f, 3.0f));
    private final BoolSetting autoScale = addSetting(new BoolSetting("Auto Scale", true));
    private final BoolSetting showHealth = addSetting(new BoolSetting("Health", true));
    private final BoolSetting showSelf = addSetting(new BoolSetting("Show Self", false));
    private final BoolSetting background = addSetting(new BoolSetting("Background", true));

    private final List<EntityData> cache = new ArrayList<>();

    private double vpX, vpY, vpZ;
    private double lastVpX, lastVpY, lastVpZ;
    private int mcDisplayWidth = 1;
    private int mcDisplayHeight = 1;
    private float lastPartialTicks;

    private final FloatBuffer savedModelview = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer savedProjection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer savedViewport = BufferUtils.createIntBuffer(16);
    private boolean glStateCaptured;

    public NameTagsModule() {
        super("NameTags", "Custom player nametags", Category.VISUALS);
    }

    @Override
    public void onEnable() {
        cache.clear();
        glStateCaptured = false;
    }

    @Override
    public void onDisable() {
        cache.clear();
        glStateCaptured = false;
    }

    /** Called at end of world render while GL matrices match the 3D scene. */
    public void captureGlState(float partialTicks) {
        lastPartialTicks = partialTicks;
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, savedModelview);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, savedProjection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, savedViewport);
        glStateCaptured = true;
    }

    @Override
    public void onTick() {
        cache.clear();

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null)
            return;

        mcDisplayWidth = Mc.mc().displayWidth;
        mcDisplayHeight = Mc.mc().displayHeight;
        if (mcDisplayWidth < 1)
            mcDisplayWidth = 1;
        if (mcDisplayHeight < 1)
            mcDisplayHeight = 1;

        for (Entity entity : Mc.getWorldEntitiesFiltered(world)) {
            if (!showSelf.getValue() && entity == player)
                continue;

            if (AntiBotModule.isActive() && RavenAntiBot.isBot(entity))
                continue;

            if (!(entity instanceof EntityPlayer))
                continue;

            String tag = buildTag((EntityPlayer) entity);
            if (tag == null || tag.isEmpty())
                continue;

            EntityData data = new EntityData();
            data.lastX = entity.lastTickPosX;
            data.lastY = entity.lastTickPosY;
            data.lastZ = entity.lastTickPosZ;
            data.posX = entity.posX;
            data.posY = entity.posY;
            data.posZ = entity.posZ;
            data.sneaking = entity.isSneaking();
            data.tag = tag;
            cache.add(data);
        }

        lastVpX = vpX;
        lastVpY = vpY;
        lastVpZ = vpZ;
        double[] vp = Mc.getViewerPos(1.0f);
        vpX = vp[0];
        vpY = vp[1];
        vpZ = vp[2];
    }

    @Override
    public void onOverlay(Object sr) {
        if (cache.isEmpty() || !glStateCaptured)
            return;

        FontRenderer fr = Mc.fontRenderer();
        if (fr == null)
            return;

        if (!(sr instanceof ScaledResolution))
            return;
        ScaledResolution scaled = (ScaledResolution) sr;
        int sw = scaled.getScaledWidth();
        int sh = scaled.getScaledHeight();
        if (sw < 1 || sh < 1)
            return;

        double rvpX = lastVpX + (vpX - lastVpX) * lastPartialTicks;
        double rvpY = lastVpY + (vpY - lastVpY) * lastPartialTicks;
        double rvpZ = lastVpZ + (vpZ - lastVpZ) * lastPartialTicks;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, sw, sh, 0.0, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        for (EntityData data : cache) {
            double wx = Mc.lerp(data.lastX, data.posX, lastPartialTicks);
            double wy = Mc.lerp(data.lastY, data.posY, lastPartialTicks)
                    + (data.sneaking ? 1.5 : 1.8) + 0.3;
            double wz = Mc.lerp(data.lastZ, data.posZ, lastPartialTicks);

            double[] screen = projectToScreen(wx, wy, wz, rvpX, rvpY, rvpZ);
            if (screen == null)
                continue;
            if (screen[2] < 0.0 || screen[2] > 1.0)
                continue;

            float sx = (float) (screen[0] / mcDisplayWidth * sw);
            float sy = (float) ((1.0 - screen[1] / mcDisplayHeight) * sh);

            double dist = Math.sqrt(
                    (wx - rvpX) * (wx - rvpX)
                            + (wy - rvpY) * (wy - rvpY)
                            + (wz - rvpZ) * (wz - rvpZ));
            float scaleFactor = autoScale.getValue()
                    ? Math.max(0.5f, Math.min(1.5f, (float) dist * 0.05f + 0.5f))
                    : scale.getValue();

            int strWidth = fr.getStringWidth(data.tag);
            if (strWidth <= 0)
                continue;

            GL11.glPushMatrix();
            GL11.glTranslatef(sx, sy, 0.0f);
            GL11.glScalef(scaleFactor, scaleFactor, 1.0f);

            if (background.getValue()) {
                drawRect(-strWidth / 2 - 1, -1, strWidth / 2 + 1, 9, 0x80000000);
            }

            fr.drawStringWithShadow(data.tag, -strWidth / 2.0f, 0.0f, 0xFFFFFF);

            GL11.glPopMatrix();
        }

        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
    }

    private double[] projectToScreen(double wx, double wy, double wz,
            double camX, double camY, double camZ) {
        float x = (float) (wx - camX);
        float y = (float) (wy - camY);
        float z = (float) (wz - camZ);

        float[] eye = transformPoint(savedModelview, x, y, z, 1.0f);
        float[] clip = transformPoint(savedProjection, eye[0], eye[1], eye[2], eye[3]);
        if (clip[3] == 0.0f)
            return null;

        float ndcX = clip[0] / clip[3];
        float ndcY = clip[1] / clip[3];
        float ndcZ = clip[2] / clip[3];

        int vx = savedViewport.get(0);
        int vy = savedViewport.get(1);
        int vw = savedViewport.get(2);
        int vh = savedViewport.get(3);

        return new double[] {
                vx + (ndcX + 1.0f) * 0.5f * vw,
                vy + (ndcY + 1.0f) * 0.5f * vh,
                (ndcZ + 1.0f) * 0.5f
        };
    }

    private static float[] transformPoint(FloatBuffer matrix, float x, float y, float z, float w) {
        float m0 = matrix.get(0);
        float m1 = matrix.get(1);
        float m2 = matrix.get(2);
        float m3 = matrix.get(3);
        float m4 = matrix.get(4);
        float m5 = matrix.get(5);
        float m6 = matrix.get(6);
        float m7 = matrix.get(7);
        float m8 = matrix.get(8);
        float m9 = matrix.get(9);
        float m10 = matrix.get(10);
        float m11 = matrix.get(11);
        float m12 = matrix.get(12);
        float m13 = matrix.get(13);
        float m14 = matrix.get(14);
        float m15 = matrix.get(15);
        return new float[] {
                m0 * x + m4 * y + m8 * z + m12 * w,
                m1 * x + m5 * y + m9 * z + m13 * w,
                m2 * x + m6 * y + m10 * z + m14 * w,
                m3 * x + m7 * y + m11 * z + m15 * w
        };
    }

    private String buildTag(EntityPlayer entity) {
        if (entity.getDisplayName() == null)
            return null;
        String name = entity.getDisplayName().getFormattedText();
        if (showHealth.getValue()) {
            name = name + " \u00a7c" + (int) entity.getHealth() + "\u00a7f\u2665";
        }
        return name;
    }

    private void drawRect(int x1, int y1, int x2, int y2, int color) {
        float a = (color >> 24 & 255) / 255.0f;
        float r = (color >> 16 & 255) / 255.0f;
        float g = (color >> 8 & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
