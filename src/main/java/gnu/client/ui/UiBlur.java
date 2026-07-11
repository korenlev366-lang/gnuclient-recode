package gnu.client.ui;

import gnu.client.GnuClientMod;
import gnu.client.common.GnuLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * ClickGUI-only optional half-res separable blur. Default off; permanent session
 * fallback on probe/runtime failure. Not for HUD.
 * <p>
 * Allocates private FBOs via {@link OpenGlHelper} directly so blur still works when
 * video-settings "Use FBOs" is off (Minecraft's {@code Framebuffer} class stubs in
 * that mode and leaves {@code framebufferObject == -1}).
 */
public final class UiBlur {

    private static final ResourceLocation VERT =
            new ResourceLocation(GnuClientMod.MOD_ID, "shaders/ui_blur.vert");
    private static final ResourceLocation FRAG =
            new ResourceLocation(GnuClientMod.MOD_ID, "shaders/ui_blur.frag");
    private static final float BLUR_RADIUS = 4f;

    private static boolean probed;
    private static boolean supported;
    private static boolean sessionFailed;
    private static boolean failLogged;
    private static boolean enabledSetting;

    private static int program;
    private static int uniDiffuse;
    private static int uniInSize;
    private static int uniBlurDir;
    private static int uniRadius;

    private static BlurPass passA;
    private static BlurPass passB;
    /** Full-res scratch for window capture when game FBOs are off. */
    private static BlurPass windowScratch;
    private static boolean frameActive;
    private static boolean captured;

    private UiBlur() {
    }

    /** User setting; blur still requires successful probe. Default off. */
    public static void setEnabled(boolean enabled) {
        boolean rising = enabled && !enabledSetting;
        enabledSetting = enabled;
        // Only re-probe on off→on (ClickGUI calls this every frame).
        if (rising && sessionFailed) {
            sessionFailed = false;
            failLogged = false;
            probed = false;
            supported = false;
        }
    }

    public static boolean isEnabled() {
        return enabledSetting;
    }

    public static boolean isUsable() {
        return enabledSetting && !sessionFailed && probeSupport();
    }

    public static boolean probeSupport() {
        if (sessionFailed) {
            return false;
        }
        if (probed) {
            return supported;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Not ready yet — do not lock a permanent failure; retry on next frame.
        if (mc == null || mc.getResourceManager() == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return false;
        }
        try {
            // Hardware must support FBOs. Do NOT require isFramebufferEnabled() — that is
            // the video-settings toggle and stubs Minecraft's Framebuffer class.
            if (!OpenGlHelper.framebufferSupported) {
                probed = true;
                supported = false;
                failSession("GL framebuffer extension unsupported");
                return false;
            }
            if (!compileProgram()) {
                probed = true;
                supported = false;
                failSession("shader compile failed");
                return false;
            }
            ensureFramebuffers(Math.max(1, mc.displayWidth / 2), Math.max(1, mc.displayHeight / 2));
            if (passA == null || passB == null || !passA.isValid() || !passB.isValid()) {
                probed = true;
                supported = false;
                failSession("fbo alloc failed");
                return false;
            }
            probed = true;
            supported = true;
            return true;
        } catch (Throwable t) {
            probed = true;
            supported = false;
            failSession(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            GnuLog.logError("UiBlur probe failed", t);
            return false;
        }
    }

    public static void beginFrame(boolean needsBlur) {
        frameActive = false;
        captured = false;
        if (!needsBlur || !isUsable()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        if (OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() == null) {
            return;
        }
        try {
            int halfW = Math.max(1, mc.displayWidth / 2);
            int halfH = Math.max(1, mc.displayHeight / 2);
            ensureFramebuffers(halfW, halfH);
            frameActive = true;
        } catch (Throwable t) {
            failSession(t.getMessage());
            GnuLog.logError("UiBlur beginFrame failed", t);
            frameActive = false;
        }
    }

    public static void drawPanel(float x, float y, float w, float h, float radius, float alpha) {
        int color = UiKit.withAlpha(UiKit.SURFACE, alpha);
        if (!frameActive || sessionFailed) {
            UiKit.drawRoundedPanel(x, y, w, h, radius, color);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            UiKit.drawRoundedPanel(x, y, w, h, radius, color);
            return;
        }
        try {
            if (!captured) {
                captureAndBlur(mc);
                captured = true;
            }
            compositePanel(mc, x, y, w, h, radius, alpha);
        } catch (Throwable t) {
            failSession(t.getMessage());
            GnuLog.logError("UiBlur drawPanel failed", t);
            restoreMain(mc);
            UiKit.drawRoundedPanel(x, y, w, h, radius, color);
        }
    }

    public static void endFrame() {
        if (!frameActive) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            restoreMain(mc);
        } catch (Throwable t) {
            failSession(t.getMessage());
            GnuLog.logError("UiBlur endFrame failed", t);
        } finally {
            frameActive = false;
            captured = false;
            GL20.glUseProgram(0);
        }
    }

    private static void captureAndBlur(Minecraft mc) {
        int halfW = passA.width;
        int halfH = passA.height;

        // Blur passes bind half-res viewports; must restore before any ClickGUI draw.
        int[] prevViewport = new int[4];
        java.nio.IntBuffer vpBuf = org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, vpBuf);
        prevViewport[0] = vpBuf.get(0);
        prevViewport[1] = vpBuf.get(1);
        prevViewport[2] = vpBuf.get(2);
        prevViewport[3] = vpBuf.get(3);

        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, halfW, 0.0, halfH, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        boolean depthWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendWas = GL11.glIsEnabled(GL11.GL_BLEND);

        try {
            GlStateManager.disableBlend();
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            if (OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() != null
                    && mc.getFramebuffer().framebufferTexture > 0) {
                passA.clear();
                passA.bind(true);
                GlStateManager.bindTexture(mc.getFramebuffer().framebufferTexture);
                drawTexturedQuad(0, 0, halfW, halfH, true);
            } else {
                copyWindowColorIntoPassA(mc, halfW, halfH);
            }

            // Horizontal blur: passA -> passB
            passB.clear();
            passB.bind(true);
            GL20.glUseProgram(program);
            GL20.glUniform1i(uniDiffuse, 0);
            GL20.glUniform2f(uniInSize, halfW, halfH);
            GL20.glUniform2f(uniBlurDir, 1f, 0f);
            GL20.glUniform1f(uniRadius, BLUR_RADIUS);
            GlStateManager.bindTexture(passA.tex);
            drawTexturedQuad(0, 0, halfW, halfH, false);

            // Vertical blur: passB -> passA
            passA.clear();
            passA.bind(true);
            GL20.glUniform2f(uniBlurDir, 0f, 1f);
            GlStateManager.bindTexture(passB.tex);
            drawTexturedQuad(0, 0, halfW, halfH, false);
        } finally {
            if (depthWas) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            if (blendWas) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            GL20.glUseProgram(0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            restoreMain(mc);
            GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        }
    }

    /**
     * When Minecraft is not rendering into an FBO, copy the window into a full-res scratch
     * texture ({@code glCopyTexSubImage2D} — does not reallocate / invalidate FBO attachments),
     * then downsample into {@code passA}.
     */
    private static void copyWindowColorIntoPassA(Minecraft mc, int halfW, int halfH) {
        int dw = Math.max(1, mc.displayWidth);
        int dh = Math.max(1, mc.displayHeight);
        if (windowScratch == null) {
            windowScratch = new BlurPass();
        }
        windowScratch.ensure(dw, dh);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
        GlStateManager.bindTexture(windowScratch.tex);
        // SubImage keeps existing tex storage so pass FBOs stay complete.
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, dw, dh);

        passA.clear();
        passA.bind(true);
        GlStateManager.bindTexture(windowScratch.tex);
        // Window color is lower-left origin; flip V to match the game-FBO capture path.
        drawTexturedQuad(0, 0, halfW, halfH, true);
    }

    private static void compositePanel(Minecraft mc, float x, float y, float w, float h,
            float radius, float alpha) {
        // Ensure we draw panels to the game target with a full-size viewport.
        restoreMain(mc);
        ScaledResolution sr = new ScaledResolution(mc);
        float scale = sr.getScaleFactor();
        UiKit.FbRect scissor = UiKit.PixelAlign.toFramebufferRect(x, y, w, h, scale,
                mc.displayWidth, mc.displayHeight);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, UiKit.clamp01(alpha));
        GlStateManager.bindTexture(passA.tex);

        boolean scissorWas = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntScissor prev = IntScissor.capture();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissor.x, scissor.y, Math.max(0, scissor.width), Math.max(0, scissor.height));

        float u0 = x / sr.getScaledWidth();
        float v0 = 1f - (y + h) / sr.getScaledHeight();
        float u1 = (x + w) / sr.getScaledWidth();
        float v1 = 1f - y / sr.getScaledHeight();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();

        if (scissorWas) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(prev.x, prev.y, prev.w, prev.h);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        UiKit.RoundedPanel.draw(x, y, w, h, radius, UiKit.withAlpha(UiKit.SURFACE, alpha * 0.55f));
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void restoreMain(Minecraft mc) {
        if (mc == null) {
            return;
        }
        if (OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(true);
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
            GL11.glViewport(0, 0, Math.max(1, mc.displayWidth), Math.max(1, mc.displayHeight));
        }
        GL20.glUseProgram(0);
    }

    private static void ensureFramebuffers(int width, int height) {
        if (passA == null) {
            passA = new BlurPass();
        }
        if (passB == null) {
            passB = new BlurPass();
        }
        passA.ensure(width, height);
        passB.ensure(width, height);
    }

    private static boolean compileProgram() throws Exception {
        if (program != 0) {
            return true;
        }
        String vertSrc = readResource(VERT);
        String fragSrc = readResource(FRAG);
        if (vertSrc == null || fragSrc == null) {
            return false;
        }
        int vert = compileShader(vertSrc, GL20.GL_VERTEX_SHADER);
        int frag = compileShader(fragSrc, GL20.GL_FRAGMENT_SHADER);
        if (vert == 0 || frag == 0) {
            if (vert != 0) {
                GL20.glDeleteShader(vert);
            }
            if (frag != 0) {
                GL20.glDeleteShader(frag);
            }
            return false;
        }
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vert);
        GL20.glAttachShader(prog, frag);
        GL20.glLinkProgram(prog);
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0) {
            GnuLog.log("UiBlur link log: " + GL20.glGetProgramInfoLog(prog, 1024));
            GL20.glDeleteProgram(prog);
            return false;
        }
        program = prog;
        uniDiffuse = GL20.glGetUniformLocation(program, "DiffuseSampler");
        uniInSize = GL20.glGetUniformLocation(program, "InSize");
        uniBlurDir = GL20.glGetUniformLocation(program, "BlurDir");
        uniRadius = GL20.glGetUniformLocation(program, "Radius");
        return true;
    }

    private static int compileShader(String source, int type) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
            GnuLog.log("UiBlur shader compile: " + GL20.glGetShaderInfoLog(id, 1024));
            GL20.glDeleteShader(id);
            return 0;
        }
        return id;
    }

    private static String readResource(ResourceLocation location) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourceManager() == null) {
            return null;
        }
        try {
            InputStream in = mc.getResourceManager().getResource(location).getInputStream();
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
        } catch (Exception e) {
            GnuLog.logError("UiBlur resource read failed " + location, e);
            return null;
        }
    }

    private static void drawTexturedQuad(float x, float y, float w, float h, boolean flipV) {
        float v0 = flipV ? 1f : 0f;
        float v1 = flipV ? 0f : 1f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0f, v1);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(1f, v1);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(1f, v0);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    private static void failSession(String reason) {
        sessionFailed = true;
        supported = false;
        frameActive = false;
        if (!failLogged) {
            failLogged = true;
            GnuLog.log("UiBlur disabled for session: " + reason);
        }
        disposeFramebuffers();
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
    }

    private static void disposeFramebuffers() {
        if (passA != null) {
            passA.delete();
            passA = null;
        }
        if (passB != null) {
            passB.delete();
            passB = null;
        }
        if (windowScratch != null) {
            windowScratch.delete();
            windowScratch = null;
        }
    }

    /**
     * Private color-only FBO that ignores {@link OpenGlHelper#isFramebufferEnabled()}.
     */
    private static final class BlurPass {
        int fbo = -1;
        int tex = -1;
        int width;
        int height;

        boolean isValid() {
            return fbo > 0 && tex > 0 && width > 0 && height > 0;
        }

        void ensure(int w, int h) {
            if (isValid() && width == w && height == h) {
                return;
            }
            delete();
            width = w;
            height = h;
            tex = TextureUtil.glGenTextures();
            fbo = OpenGlHelper.glGenFramebuffers();
            GlStateManager.bindTexture(tex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                    OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
            int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
            if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
                delete();
                throw new IllegalStateException("blur FBO incomplete status=" + status);
            }
        }

        void bind(boolean setViewport) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
            if (setViewport) {
                GL11.glViewport(0, 0, width, height);
            }
        }

        void clear() {
            bind(true);
            GlStateManager.clearColor(0f, 0f, 0f, 0f);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
        }

        void delete() {
            if (fbo > 0) {
                OpenGlHelper.glDeleteFramebuffers(fbo);
                fbo = -1;
            }
            if (tex > 0) {
                TextureUtil.deleteTexture(tex);
                tex = -1;
            }
            width = 0;
            height = 0;
        }
    }

    private static final class IntScissor {
        final int x;
        final int y;
        final int w;
        final int h;

        IntScissor(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        static IntScissor capture() {
            java.nio.IntBuffer buf = org.lwjgl.BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, buf);
            return new IntScissor(buf.get(0), buf.get(1), buf.get(2), buf.get(3));
        }
    }
}
