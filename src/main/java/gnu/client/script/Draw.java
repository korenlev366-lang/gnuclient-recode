package gnu.client.script;

import gnu.client.runtime.mc.Mc;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;

/**
 * Script-facing draw helpers for world ESP and HUD text.
 * World draws must run inside {@code onRender(float)}; HUD text in {@code onOverlay}.
 */
public final class Draw {

    public static final Draw INSTANCE = new Draw();

    private Draw() {}

    /** Begin world-space GL state (call once at the start of {@code onRender}). */
    public void beginWorld() {
        RenderHelper.begin();
    }

    /** End world-space GL state. */
    public void endWorld() {
        RenderHelper.end();
    }

    public void box(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a) {
        EspDraw.fill(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);
    }

    public void boxOutline(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a, float lineWidth) {
        RenderHelper.drawBoundingBox(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);
    }

    public void line(
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a, float lineWidth) {
        RenderHelper.drawLine3D(x1, y1, z1, x2, y2, z2, r, g, b, a, lineWidth);
    }

    /** Soft fill around an entity AABB (interpolated with partial ticks). */
    public void entityBox(Object entity, float partialTicks, float r, float g, float b, float a) {
        Entity e = asEntity(entity);
        if (e == null)
            return;
        double x = Mc.interpX(e, partialTicks);
        double y = Mc.interpY(e, partialTicks);
        double z = Mc.interpZ(e, partialTicks);
        AxisAlignedBB bb = e.getEntityBoundingBox();
        if (bb == null)
            return;
        double dx = x - e.posX;
        double dy = y - e.posY;
        double dz = z - e.posZ;
        box(bb.minX + dx, bb.minY + dy, bb.minZ + dz,
                bb.maxX + dx, bb.maxY + dy, bb.maxZ + dz,
                r, g, b, a);
    }

    /** Tracer line from viewer eyes to entity center. */
    public void tracer(Object entity, float partialTicks, float r, float g, float b, float a, float width) {
        Entity e = asEntity(entity);
        if (e == null)
            return;
        double[] viewer = Mc.getViewerPos(partialTicks);
        double x = Mc.interpX(e, partialTicks);
        double y = Mc.interpY(e, partialTicks) + e.height * 0.5;
        double z = Mc.interpZ(e, partialTicks);
        line(viewer[0], viewer[1], viewer[2], x, y, z, r, g, b, a, width);
    }

    /** 2D HUD text (call from {@code onOverlay}). */
    public void text(String s, float x, float y, int color) {
        FontRenderer fr = Mc.fontRenderer();
        if (fr == null || s == null)
            return;
        fr.drawStringWithShadow(s, x, y, color);
    }

    public void text(String s, float x, float y, int color, boolean shadow) {
        FontRenderer fr = Mc.fontRenderer();
        if (fr == null || s == null)
            return;
        if (shadow)
            fr.drawStringWithShadow(s, x, y, color);
        else
            fr.drawString(s, (int) x, (int) y, color);
    }

    public int textWidth(String s) {
        FontRenderer fr = Mc.fontRenderer();
        return fr == null || s == null ? 0 : fr.getStringWidth(s);
    }

    private static Entity asEntity(Object entity) {
        return entity instanceof Entity ? (Entity) entity : null;
    }
}
