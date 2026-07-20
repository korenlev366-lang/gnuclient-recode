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
        // Format nibble: bump when list contents change (v2 = vertices-only, no baked color).
        int qw = Math.round(w / 0.05f) & 0x3FF;
        int qh = Math.round(h / 0.05f) & 0x3FF;
        int qd = Math.round(d / 0.05f) & 0x3FF;
        return (2L << 40) | ((long) qw << 20) | ((long) qh << 10) | qd;
    }

    /**
     * Draws a filled box centered at the origin with half-extents (w/2, h/2, d/2) using a
     * cached display list when available. Caller must have already set color + called
     * {@link #begin()}. The list is translated by the caller's current matrix.
     *
     * <p>Lists store <b>vertices only</b> (no {@code glColor}) so the caller color/alpha apply.
     */
    public static void drawFilledBoxList(float w, float h, float d) {
        if (!PerformanceModule.boxDisplayLists()) {
            // Color already set by caller — emit vertices only.
            emitFilledBoxVertices(-w / 2, -h / 2, -d / 2, w / 2, h / 2, d / 2);
            return;
        }
        long key = boxKey(w, h, d);
        Integer list = BOX_LISTS.get(key);
        if (list == null) {
            list = GL11.glGenLists(1);
            GL11.glNewList(list, GL11.GL_COMPILE);
            emitFilledBoxVertices(-w / 2, -h / 2, -d / 2, w / 2, h / 2, d / 2);
            GL11.glEndList();
            BOX_LISTS.put(key, list);
        }
        GL11.glCallList(list);
    }

    /** Drop cached box lists (e.g. after list format change). Safe to call anytime. */
    public static void clearBoxDisplayLists() {
        for (Integer list : BOX_LISTS.values()) {
            if (list != null)
                GL11.glDeleteLists(list, 1);
        }
        BOX_LISTS.clear();
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

    private static final float[] GLOW_LINE_WIDTHS = {4f, 2.5f, 1.5f, 1f};
    private static final float[] GLOW_LINE_ALPHAS = {0.10f, 0.18f, 0.40f, 0.90f};

    /**
     * Multi-pass line bloom outline (wide soft outer passes → sharp inner core).
     * Reuses the same 12-edge {@code GL_LINES} pattern as {@link #drawBoundingBox}.
     * Must be called between {@link #begin()} and {@link #end()}.
     */
    public static void drawGlowingBoundingBox(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b) {
        boolean smoothWas = GL11.glIsEnabled(GL11.GL_LINE_SMOOTH);
        if (!smoothWas)
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        for (int i = 0; i < GLOW_LINE_WIDTHS.length; i++) {
            drawBoundingBox(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    r, g, b, GLOW_LINE_ALPHAS[i], GLOW_LINE_WIDTHS[i]);
        }
        if (!smoothWas)
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
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
        emitFilledBoxVertices(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Vertex-only filled box (no color). Used by display lists so caller color applies. */
    private static void emitFilledBoxVertices(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
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
        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }
}
