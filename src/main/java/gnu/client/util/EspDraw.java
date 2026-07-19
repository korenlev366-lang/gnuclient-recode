package gnu.client.util;

/**
 * Soft fill-only world ESP boxes. Callers own {@link RenderHelper#begin()}/{@link RenderHelper#end()}.
 * Does not draw outlines.
 */
public final class EspDraw {

    public static final float DEFAULT_FILL_ALPHA = 0.16f;

    private EspDraw() {}

    /** Returns {@code alpha} if > 0, otherwise {@link #DEFAULT_FILL_ALPHA}. */
    public static float resolveAlpha(float alpha) {
        return alpha > 0f ? alpha : DEFAULT_FILL_ALPHA;
    }

    /**
     * Soft fill using {@link #DEFAULT_FILL_ALPHA}.
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fill(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b) {
        fill(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, DEFAULT_FILL_ALPHA);
    }

    /**
     * Soft fill with explicit alpha (≤ 0 falls back to default).
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fill(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float alpha) {
        RenderHelper.drawFilledBox(
                minX, minY, minZ, maxX, maxY, maxZ,
                r, g, b, resolveAlpha(alpha));
    }

    /**
     * Batched soft fill: draws {@code boxCount} boxes from a flat
     * {@code float[6 * boxCount]} buffer (minX,minY,minZ,maxX,maxY,maxZ each) in a
     * single draw call. {@code alpha} ≤ 0 falls back to default.
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fillBatched(
            float[] boxes, int boxCount,
            float r, float g, float b, float alpha) {
        RenderHelper.drawFilledBoxes(boxes, boxCount, r, g, b, resolveAlpha(alpha));
    }
}
