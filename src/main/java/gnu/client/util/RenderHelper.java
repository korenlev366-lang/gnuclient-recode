package gnu.client.util;

import gnu.client.module.modules.settings.PerformanceModule;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immediate-mode GL helpers for world-space overlays during
 * {@code RenderWorldLastEvent}. Uses LWJGL only (no compile-time MC types).
 */
public final class RenderHelper {

    private RenderHelper() {}

    /**
     * Display-list cache for filled boxes (Tier-2 perf). Boxes of the same quantized
     * dimensions reuse one compiled GL display list instead of re-issuing all 36 vertices
     * every frame. Disabled unless {@link PerformanceModule#boxDisplayLists()} is on, and
     * the cache is bounded (LRU) so it can't grow without limit.
     */
    private static final Map<Long, Integer> BOX_LISTS = new LinkedHashMap<Long, Integer>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
            if (size() <= 16)
                return false;
            int list = eldest.getValue();
            GL11.glDeleteLists(list, 1);
            return true;
        }
    };

    private static long boxKey(float w, float h, float d) {
        // Quantize to 0.05 so near-identical boxes (e.g. all players at height 1.8) share a list.
        int qw = Math.round(w / 0.05f) & 0x3FF;
        int qh = Math.round(h / 0.05f) & 0x3FF;
        int qd = Math.round(d / 0.05f) & 0x3FF;
        return ((long) qw << 20) | ((long) qh << 10) | qd;
    }

    /**
     * Draws a filled box centered at the origin with half-extents (w/2, h/2, d/2) using a
     * cached display list when available. Caller must have already set color + called
     * {@link #begin()}. The list is translated by the caller's current matrix.
     */
    public static void drawFilledBoxList(float w, float h, float d) {
        if (!PerformanceModule.boxDisplayLists()) {
            drawFilledBox(-w / 2, -h / 2, -d / 2, w / 2, h / 2, d / 2, 1f, 1f, 1f, 1f);
            return;
        }
        long key = boxKey(w, h, d);
        Integer list = BOX_LISTS.get(key);
        if (list == null) {
            list = GL11.glGenLists(1);
            GL11.glNewList(list, GL11.GL_COMPILE);
            drawFilledBox(-w / 2, -h / 2, -d / 2, w / 2, h / 2, d / 2, 1f, 1f, 1f, 1f);
            GL11.glEndList();
            BOX_LISTS.put(key, list);
        }
        GL11.glCallList(list);
    }

    public static void drawBoundingBox(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a,
            float lineWidth) {
        GL11.glLineWidth(lineWidth);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_LINES);
        // Bottom face
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, minY, minZ);
        // Top face
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, minZ);
        // Verticals
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glEnd();
    }

    public static void drawLine3D(
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a,
            float lineWidth) {
        GL11.glLineWidth(lineWidth);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glEnd();
    }

    public static void drawFilledBox(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        // Bottom
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        // Top
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        // North
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        // South
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        // West
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, minZ);
        // East
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glEnd();
    }

    /**
     * Batched filled boxes: emits every box in a single {@code GL_QUADS} block so the
     * whole set costs one draw call instead of one per box. Caller still wraps with
     * {@link #begin()}/{@link #end()}. Each box is {@code (minX,minY,minZ,maxX,maxY,maxZ)}
     * in world-relative coords; 6 floats per box, bottom-then-top winding.
     */
    public static void drawFilledBoxes(float[] boxes, int boxCount, float r, float g, float b, float a) {
        if (boxCount <= 0)
            return;
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < boxCount; i++) {
            int o = i * 6;
            float minX = boxes[o], minY = boxes[o + 1], minZ = boxes[o + 2];
            float maxX = boxes[o + 3], maxY = boxes[o + 4], maxZ = boxes[o + 5];
            // Bottom
            GL11.glVertex3d(minX, minY, minZ);
            GL11.glVertex3d(maxX, minY, minZ);
            GL11.glVertex3d(maxX, minY, maxZ);
            GL11.glVertex3d(minX, minY, maxZ);
            // Top
            GL11.glVertex3d(minX, maxY, minZ);
            GL11.glVertex3d(minX, maxY, maxZ);
            GL11.glVertex3d(maxX, maxY, maxZ);
            GL11.glVertex3d(maxX, maxY, minZ);
            // North
            GL11.glVertex3d(minX, minY, minZ);
            GL11.glVertex3d(minX, maxY, minZ);
            GL11.glVertex3d(maxX, maxY, minZ);
            GL11.glVertex3d(maxX, minY, minZ);
            // South
            GL11.glVertex3d(minX, minY, maxZ);
            GL11.glVertex3d(maxX, minY, maxZ);
            GL11.glVertex3d(maxX, maxY, maxZ);
            GL11.glVertex3d(minX, maxY, maxZ);
            // West
            GL11.glVertex3d(minX, minY, minZ);
            GL11.glVertex3d(minX, minY, maxZ);
            GL11.glVertex3d(minX, maxY, maxZ);
            GL11.glVertex3d(minX, maxY, minZ);
            // East
            GL11.glVertex3d(maxX, minY, minZ);
            GL11.glVertex3d(maxX, maxY, minZ);
            GL11.glVertex3d(maxX, maxY, maxZ);
            GL11.glVertex3d(maxX, minY, maxZ);
        }
        GL11.glEnd();
    }

    public static void begin() {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
    }

    public static void end() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }
}
