package gnu.client.ui;

import gnu.client.GnuClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Shared Lux UI helpers: theme tokens, animation, pixel/scissor math, GL restore,
 * and translucent rounded panels.
 */
public final class UiKit {

    public static final int SURFACE = 0xD90F121B;
    public static final int SURFACE_STRONG = 0xF5141823;
    public static final int LINE = 0x13FFFFFF;
    public static final int TEXT = 0xFFF5F6FA;
    public static final int MUTED = 0xFF969CAB;
    public static final int ACCENT = 0xFF8B5CF6;
    public static final int ACCENT_2 = 0xFF5F8CFF;
    public static final int SUCCESS = 0xFF57D7A0;
    public static final int DANGER = 0xFFFF7187;

    public static final float RADIUS_PANEL = 13f;
    public static final float RADIUS_ROW = 8f;
    public static final float RADIUS_PILL = 7f;
    public static final float RADIUS_TOAST = 12f;

    /** Module row header (name + bind + toggle) — mockup ~37px. */
    public static final float ROW_HEIGHT = 34f;
    public static final float COLUMN_WIDTH = 156f;
    public static final float TOAST_MAX_WIDTH = 260f;
    public static final float TOAST_MAX_HEIGHT = 52f;

    public static final float DURATION_FAST_MS = 180f;
    public static final float DURATION_MED_MS = 220f;
    public static final float DURATION_SLOW_MS = 260f;

    private static final float DT_CAP_SEC = 0.050f;

    private UiKit() {
    }

    public static int withAlpha(int argb, float alphaMul) {
        float a = ((argb >>> 24) & 0xFF) / 255f * clamp01(alphaMul);
        int ai = Math.round(a * 255f) & 0xFF;
        return (ai << 24) | (argb & 0x00FFFFFF);
    }

    public static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        if (v > 1f) {
            return 1f;
        }
        return v;
    }

    /** Frame clock: nanoTime, dt capped ~50ms, optional speed multiplier. */
    public static final class UiClock {
        private long lastNanos = System.nanoTime();
        private float dtSec;
        private float speed = 1f;

        public void tick() {
            long now = System.nanoTime();
            float raw = (now - lastNanos) / 1_000_000_000f;
            lastNanos = now;
            if (raw < 0f) {
                raw = 0f;
            }
            if (raw > DT_CAP_SEC) {
                raw = DT_CAP_SEC;
            }
            dtSec = raw * speed;
        }

        public float dt() {
            return dtSec;
        }

        public float dtMs() {
            return dtSec * 1000f;
        }

        public void setSpeed(float speedMul) {
            speed = speedMul < 0f ? 0f : speedMul;
        }

        public float getSpeed() {
            return speed;
        }

        public void reset() {
            lastNanos = System.nanoTime();
            dtSec = 0f;
        }
    }

    /** Exponential ease toward 1: {@code 1 - exp(-k * dt)}. */
    public static final class ExpEase {
        private ExpEase() {
        }

        public static float step(float k, float dtSec) {
            if (dtSec <= 0f || k <= 0f) {
                return 0f;
            }
            return (float) (1.0 - Math.exp(-k * dtSec));
        }

        /** Map a duration in ms to a reasonable k for ~95% settle. */
        public static float kForDurationMs(float durationMs, float speedMul) {
            float d = durationMs <= 1f ? 1f : durationMs;
            float speed = speedMul <= 0f ? 0.0001f : speedMul;
            return (3f / (d / 1000f)) * speed;
        }

        public static float toward(float current, float target, float k, float dtSec) {
            float t = step(k, dtSec);
            return current + (target - current) * t;
        }
    }

    public static final class AnimatedFloat {
        private float value;
        private float target;
        private float k = ExpEase.kForDurationMs(DURATION_MED_MS, 1f);

        public AnimatedFloat(float initial) {
            this.value = initial;
            this.target = initial;
        }

        public void setTarget(float target) {
            this.target = target;
        }

        public void snap(float v) {
            this.value = v;
            this.target = v;
        }

        public void setDurationMs(float durationMs, float speedMul) {
            this.k = ExpEase.kForDurationMs(durationMs, speedMul);
        }

        public void setK(float k) {
            this.k = k;
        }

        public float update(float dtSec) {
            value = ExpEase.toward(value, target, k, dtSec);
            return value;
        }

        public float get() {
            return value;
        }

        public float getTarget() {
            return target;
        }

        public boolean settled(float epsilon) {
            return Math.abs(value - target) <= epsilon;
        }
    }

    /** Physical-pixel snapping and framebuffer scissor conversion (pure math). */
    public static final class PixelAlign {
        private PixelAlign() {
        }

        public static float snap(float v, float scale) {
            if (scale == 0f) {
                return v;
            }
            return Math.round(v * scale) / scale;
        }

        /**
         * Convert scaled GUI rect to GL scissor rect (origin bottom-left), clamped
         * to the framebuffer.
         */
        public static FbRect toFramebufferRect(float x, float y, float w, float h,
                float scale, int displayW, int displayH) {
            int left = Math.round(x * scale);
            int top = Math.round(y * scale);
            int right = Math.round((x + w) * scale);
            int bottom = Math.round((y + h) * scale);

            if (right < left) {
                int t = left;
                left = right;
                right = t;
            }
            if (bottom < top) {
                int t = top;
                top = bottom;
                bottom = t;
            }

            int fbX = left;
            int fbW = right - left;
            int fbY = displayH - bottom;
            int fbH = bottom - top;

            return clampRect(fbX, fbY, fbW, fbH, displayW, displayH);
        }

        public static FbRect clampRect(int x, int y, int w, int h, int displayW, int displayH) {
            int x2 = x + w;
            int y2 = y + h;
            int nx = Math.max(0, Math.min(x, displayW));
            int ny = Math.max(0, Math.min(y, displayH));
            int nx2 = Math.max(0, Math.min(x2, displayW));
            int ny2 = Math.max(0, Math.min(y2, displayH));
            return new FbRect(nx, ny, Math.max(0, nx2 - nx), Math.max(0, ny2 - ny));
        }
    }

    public static final class FbRect {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public FbRect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }
    }

    /** Nested scissor with intersection. Push/pop apply GL when {@link #applyGl} is true. */
    public static final class ScissorStack {
        private final Deque<FbRect> stack = new ArrayDeque<FbRect>();
        private boolean applyGl = true;

        public void setApplyGl(boolean applyGl) {
            this.applyGl = applyGl;
        }

        public void push(int x, int y, int width, int height) {
            FbRect next = new FbRect(x, y, width, height);
            if (!stack.isEmpty()) {
                next = intersect(stack.peek(), next);
            }
            stack.push(next);
            apply();
        }

        public void pushScaled(float x, float y, float w, float h, float scale, int displayW, int displayH) {
            FbRect r = PixelAlign.toFramebufferRect(x, y, w, h, scale, displayW, displayH);
            push(r.x, r.y, r.width, r.height);
        }

        public void pop() {
            if (!stack.isEmpty()) {
                stack.pop();
            }
            apply();
        }

        public void clear() {
            stack.clear();
            if (applyGl) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }

        public FbRect current() {
            return stack.isEmpty() ? null : stack.peek();
        }

        public int depth() {
            return stack.size();
        }

        public static FbRect intersect(FbRect a, FbRect b) {
            if (a == null) {
                return b;
            }
            if (b == null) {
                return a;
            }
            int x1 = Math.max(a.x, b.x);
            int y1 = Math.max(a.y, b.y);
            int x2 = Math.min(a.x + a.width, b.x + b.width);
            int y2 = Math.min(a.y + a.height, b.y + b.height);
            return new FbRect(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
        }

        private void apply() {
            if (!applyGl) {
                return;
            }
            if (stack.isEmpty()) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                return;
            }
            FbRect r = stack.peek();
            if (r.isEmpty()) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(0, 0, 0, 0);
                return;
            }
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(r.x, r.y, r.width, r.height);
        }
    }

    /**
     * Save/restore blend, blendFunc, color, texture, depth, lighting, shader, matrices,
     * scissor, FBO, viewport.
     */
    public static final class GlGuard {
        private GlGuard() {
        }

        public static void run(Runnable body) {
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
            int blendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
            int blendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
            int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            // GL_FRAMEBUFFER_BINDING (0x8CA6) — same value for EXT/ARB/core.
            // Track binding whenever the extension exists: UiBlur uses private FBOs even
            // when video-settings "Use FBOs" (isFramebufferEnabled) is off.
            final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
            int fbo = 0;
            if (OpenGlHelper.framebufferSupported) {
                fbo = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
            }

            IntBuffer viewport = BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            IntBuffer scissorBox = BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissorBox);
            FloatBuffer color = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();

            try {
                body.run();
            } finally {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(matrixMode);

                if (OpenGlHelper.framebufferSupported) {
                    OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
                }
                GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));

                GL20.glUseProgram(program);
                GL13.glActiveTexture(activeTexture);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding);

                if (texture) {
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                } else {
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                }
                if (blend) {
                    GL11.glEnable(GL11.GL_BLEND);
                } else {
                    GL11.glDisable(GL11.GL_BLEND);
                }
                GL11.glBlendFunc(blendSrc, blendDst);
                GL11.glColor4f(color.get(0), color.get(1), color.get(2), color.get(3));

                if (depth) {
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                } else {
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                }
                if (lighting) {
                    GL11.glEnable(GL11.GL_LIGHTING);
                } else {
                    GL11.glDisable(GL11.GL_LIGHTING);
                }

                if (scissor) {
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                } else {
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                }
                GL11.glScissor(scissorBox.get(0), scissorBox.get(1), scissorBox.get(2), scissorBox.get(3));

                GlStateManager.resetColor();
            }
        }
    }

    public static void drawRoundedPanel(float x, float y, float w, float h, float radius, int argb) {
        RoundedPanel.draw(x, y, w, h, radius, argb);
    }

    /**
     * Soft anti-aliased rounded rect. Prefers an SDF shader; falls back to a
     * cull-safe Tessellator path if shaders fail.
     */
    public static final class RoundedPanel {
        private static final ResourceLocation VERT =
                new ResourceLocation(GnuClientMod.MOD_ID, "shaders/ui_round.vert");
        private static final ResourceLocation FRAG =
                new ResourceLocation(GnuClientMod.MOD_ID, "shaders/ui_round.frag");
        private static final int CORNER_STEPS = 18;

        private static boolean probed;
        private static boolean shaderOk;
        private static int program;
        private static int uniSize;
        private static int uniRadius;
        private static int uniColor;

        private RoundedPanel() {
        }

        public static void draw(float x, float y, float w, float h, float radius, int argb) {
            if (w <= 0f || h <= 0f || ((argb >>> 24) & 0xFF) == 0) {
                return;
            }
            float r = Math.max(0f, Math.min(radius, Math.min(w, h) * 0.5f));
            if (ensureShader()) {
                drawShader(x, y, w, h, r, argb);
            } else {
                drawTessellator(x, y, w, h, r, argb);
            }
        }

        private static boolean ensureShader() {
            if (probed) {
                return shaderOk;
            }
            probed = true;
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null || mc.getResourceManager() == null) {
                    probed = false;
                    return false;
                }
                int vs = compile(GL20.GL_VERTEX_SHADER, readResource(VERT));
                int fs = compile(GL20.GL_FRAGMENT_SHADER, readResource(FRAG));
                if (vs == 0 || fs == 0) {
                    return false;
                }
                program = GL20.glCreateProgram();
                GL20.glAttachShader(program, vs);
                GL20.glAttachShader(program, fs);
                GL20.glLinkProgram(program);
                GL20.glDeleteShader(vs);
                GL20.glDeleteShader(fs);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                    GL20.glDeleteProgram(program);
                    program = 0;
                    return false;
                }
                uniSize = GL20.glGetUniformLocation(program, "u_size");
                uniRadius = GL20.glGetUniformLocation(program, "u_radius");
                uniColor = GL20.glGetUniformLocation(program, "u_color");
                shaderOk = true;
                return true;
            } catch (Throwable t) {
                shaderOk = false;
                program = 0;
                return false;
            }
        }

        private static void drawShader(float x, float y, float w, float h, float radius, int argb) {
            float a = ((argb >>> 24) & 0xFF) / 255f;
            float red = ((argb >> 16) & 0xFF) / 255f;
            float green = ((argb >> 8) & 0xFF) / 255f;
            float blue = (argb & 0xFF) / 255f;

            boolean alphaWas = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            boolean blendWas = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean texWas = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            int prevProg = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);

            GL20.glUseProgram(program);
            GL20.glUniform2f(uniSize, w, h);
            GL20.glUniform1f(uniRadius, radius);
            GL20.glUniform4f(uniColor, red, green, blue, a);

            // 1px pad so AA fringe isn't clipped.
            float pad = 1f;
            float x0 = x - pad;
            float y0 = y - pad;
            float x1 = x + w + pad;
            float y1 = y + h + pad;
            float u0 = -pad / w;
            float v0 = -pad / h;
            float u1 = 1f + pad / w;
            float v1 = 1f + pad / h;

            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            wr.pos(x0, y1, 0.0).tex(u0, v1).endVertex();
            wr.pos(x1, y1, 0.0).tex(u1, v1).endVertex();
            wr.pos(x1, y0, 0.0).tex(u1, v0).endVertex();
            wr.pos(x0, y0, 0.0).tex(u0, v0).endVertex();
            tess.draw();

            GL20.glUseProgram(prevProg);
            if (texWas) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            if (alphaWas) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
            if (!blendWas) {
                GlStateManager.disableBlend();
            }
            GlStateManager.color(1f, 1f, 1f, 1f);
        }

        /** Cull-safe fan path used when the SDF shader is unavailable. */
        private static void drawTessellator(float x, float y, float w, float h, float radius, int argb) {
            float a = ((argb >>> 24) & 0xFF) / 255f;
            float red = ((argb >> 16) & 0xFF) / 255f;
            float green = ((argb >> 8) & 0xFF) / 255f;
            float blue = (argb & 0xFF) / 255f;

            boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean alphaWas = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            GlStateManager.disableCull();
            GlStateManager.disableAlpha();
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(red, green, blue, a);

            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();

            drawRect(wr, tess, x + radius, y, x + w - radius, y + h, red, green, blue, a);
            drawRect(wr, tess, x, y + radius, x + radius, y + h - radius, red, green, blue, a);
            drawRect(wr, tess, x + w - radius, y + radius, x + w, y + h - radius, red, green, blue, a);

            if (radius > 0f) {
                // Clockwise arcs in Y-down GUI space so they survive default CCW cull if re-enabled.
                drawCorner(wr, tess, x + radius, y + radius, radius, 270.0, 180.0, red, green, blue, a);
                drawCorner(wr, tess, x + w - radius, y + radius, radius, 360.0, 270.0, red, green, blue, a);
                drawCorner(wr, tess, x + w - radius, y + h - radius, radius, 90.0, 0.0, red, green, blue, a);
                drawCorner(wr, tess, x + radius, y + h - radius, radius, 180.0, 90.0, red, green, blue, a);
            }

            if (cull) {
                GlStateManager.enableCull();
            }
            if (alphaWas) {
                GlStateManager.enableAlpha();
            }
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }

        private static void drawRect(WorldRenderer wr, Tessellator tess,
                float x1, float y1, float x2, float y2,
                float r, float g, float b, float a) {
            if (x2 <= x1 || y2 <= y1) {
                return;
            }
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(x1, y2, 0.0).color(r, g, b, a).endVertex();
            wr.pos(x2, y2, 0.0).color(r, g, b, a).endVertex();
            wr.pos(x2, y1, 0.0).color(r, g, b, a).endVertex();
            wr.pos(x1, y1, 0.0).color(r, g, b, a).endVertex();
            tess.draw();
        }

        private static void drawCorner(WorldRenderer wr, Tessellator tess,
                double cx, double cy, double radius, double startDeg, double endDeg,
                float r, float g, float b, float a) {
            wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(cx, cy, 0.0).color(r, g, b, a).endVertex();
            for (int i = 0; i <= CORNER_STEPS; i++) {
                double t = startDeg + (endDeg - startDeg) * (i / (double) CORNER_STEPS);
                double rad = Math.toRadians(t);
                wr.pos(cx + Math.cos(rad) * radius, cy + Math.sin(rad) * radius, 0.0)
                        .color(r, g, b, a).endVertex();
            }
            tess.draw();
        }

        private static int compile(int type, String src) {
            int id = GL20.glCreateShader(type);
            GL20.glShaderSource(id, src);
            GL20.glCompileShader(id);
            if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                GL20.glDeleteShader(id);
                return 0;
            }
            return id;
        }

        private static String readResource(ResourceLocation loc) throws Exception {
            InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(loc).getInputStream();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            } finally {
                in.close();
            }
        }
    }
}
