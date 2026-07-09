package gnu.client.ui;

import gnu.client.GnuClientMod;
import gnu.client.common.GnuLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * ClickGUI-only optional half-res separable blur. Default off; permanent session
 * fallback on probe/runtime failure. Not for HUD.
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

    private static Framebuffer passA;
    private static Framebuffer passB;
    private static boolean frameActive;
    private static boolean captured;

    private UiBlur() {
    }

    /** User setting; blur still requires successful probe. Default off. */
    public static void setEnabled(boolean enabled) {
        enabledSetting = enabled;
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
        try {
            if (!OpenGlHelper.isFramebufferEnabled()) {
                probed = true;
                supported = false;
                failSession("framebuffer unsupported");
                return false;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) {
                // Client not ready yet — do not lock permanent failure; retry later.
                return false;
            }
            if (!compileProgram()) {
                probed = true;
                supported = false;
                failSession("shader compile failed");
                return false;
            }
            ensureFramebuffers(Math.max(1, mc.displayWidth / 2), Math.max(1, mc.displayHeight / 2));
            if (passA == null || passB == null) {
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
            failSession(t.getMessage());
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
        if (mc == null || mc.getFramebuffer() == null) {
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
        Framebuffer main = mc.getFramebuffer();
        int halfW = passA.framebufferWidth;
        int halfH = passA.framebufferHeight;

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
            // Downsample main color into passA
            passA.framebufferClear();
            passA.bindFramebuffer(true);
            GlStateManager.disableBlend();
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.bindTexture(main.framebufferTexture);
            drawTexturedQuad(0, 0, halfW, halfH, true);

            // Horizontal blur: passA -> passB
            passB.framebufferClear();
            passB.bindFramebuffer(true);
            GL20.glUseProgram(program);
            GL20.glUniform1i(uniDiffuse, 0);
            GL20.glUniform2f(uniInSize, halfW, halfH);
            GL20.glUniform2f(uniBlurDir, 1f, 0f);
            GL20.glUniform1f(uniRadius, BLUR_RADIUS);
            GlStateManager.bindTexture(passA.framebufferTexture);
            drawTexturedQuad(0, 0, halfW, halfH, false);

            // Vertical blur: passB -> passA
            passA.framebufferClear();
            passA.bindFramebuffer(true);
            GL20.glUniform2f(uniBlurDir, 0f, 1f);
            GlStateManager.bindTexture(passB.framebufferTexture);
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
        }
    }

    private static void compositePanel(Minecraft mc, float x, float y, float w, float h,
            float radius, float alpha) {
        ScaledResolution sr = new ScaledResolution(mc);
        float scale = sr.getScaleFactor();
        UiKit.FbRect scissor = UiKit.PixelAlign.toFramebufferRect(x, y, w, h, scale,
                mc.displayWidth, mc.displayHeight);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, UiKit.clamp01(alpha));
        GlStateManager.bindTexture(passA.framebufferTexture);

        boolean scissorWas = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntScissor prev = IntScissor.capture();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissor.x, scissor.y, Math.max(0, scissor.width), Math.max(0, scissor.height));

        // Map panel GUI coords to UV of half-res buffer (full-screen content).
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

        // Soft rounded overlay tint so panels still read as Lux surfaces.
        UiKit.RoundedPanel.draw(x, y, w, h, radius, UiKit.withAlpha(UiKit.SURFACE, alpha * 0.55f));
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void restoreMain(Minecraft mc) {
        if (mc != null && mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(true);
        }
        GL20.glUseProgram(0);
    }

    private static void ensureFramebuffers(int width, int height) {
        if (passA == null || passA.framebufferWidth != width || passA.framebufferHeight != height) {
            if (passA != null) {
                passA.deleteFramebuffer();
            }
            passA = new Framebuffer(width, height, false);
            passA.setFramebufferFilter(GL11.GL_LINEAR);
        }
        if (passB == null || passB.framebufferWidth != width || passB.framebufferHeight != height) {
            if (passB != null) {
                passB.deleteFramebuffer();
            }
            passB = new Framebuffer(width, height, false);
            passB.setFramebufferFilter(GL11.GL_LINEAR);
        }
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
            passA.deleteFramebuffer();
            passA = null;
        }
        if (passB != null) {
            passB.deleteFramebuffer();
            passB = null;
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
