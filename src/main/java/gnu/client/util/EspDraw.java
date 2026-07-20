package gnu.client.util;

/**
 * World ESP box helpers. Callers own {@link RenderHelper#begin()}/{@link RenderHelper#end()}.
 * <p>
 * Plain {@link #fill}/{@link #fillBatched} stay soft-fill-only (Item/Bed ESP).
 * Glow APIs ({@link #fillWithGlow}, {@link #fillBatchedWithGlow}) add multi-pass line
 * bloom then soft fill — intended for players/network ESP.
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
     * Glow outline then soft fill using {@link #DEFAULT_FILL_ALPHA}.
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fillWithGlow(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b) {
        fillWithGlow(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, DEFAULT_FILL_ALPHA);
    }

    /**
     * Glow outline then soft fill with explicit alpha (≤ 0 falls back to default).
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fillWithGlow(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float alpha) {
        RenderHelper.drawGlowingBoundingBox(minX, minY, minZ, maxX, maxY, maxZ, r, g, b);
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

    /**
     * Per-box glow outline then soft fill from the same flat
     * {@code float[6 * boxCount]} layout as {@link #fillBatched}
     * (minX,minY,minZ,maxX,maxY,maxZ each). {@code alpha} ≤ 0 falls back to default.
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fillBatchedWithGlow(
            float[] boxes, int boxCount,
            float r, float g, float b, float alpha) {
        if (boxCount <= 0)
            return;
        float a = resolveAlpha(alpha);
        for (int i = 0; i < boxCount; i++) {
            int o = i * 6;
            float minX = boxes[o], minY = boxes[o + 1], minZ = boxes[o + 2];
            float maxX = boxes[o + 3], maxY = boxes[o + 4], maxZ = boxes[o + 5];
            RenderHelper.drawGlowingBoundingBox(minX, minY, minZ, maxX, maxY, maxZ, r, g, b);
            RenderHelper.drawFilledBox(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);
        }
    }
}
