package gnu.client.ui.hud;

import gnu.client.ui.clickgui.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

/**
 * Shared LMB drag for HUD panels. Persists while dragging so a mouse-up frame
 * cannot reload stale config and overwrite the new position.
 */
public final class HudDrag {

    private boolean dragging;
    private boolean wasDown;
    private float offX;
    private float offY;
    private float x;
    private float y;
    private float w;
    private float h;

    public boolean isDragging() {
        return dragging;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public void setBounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /**
     * Apply configured position unless mid-drag (keeps live drag coords).
     */
    public void applyConfig(float cfgX, float cfgY, float defaultX, float defaultY) {
        if (dragging) {
            return;
        }
        if (cfgX < 0f || cfgY < 0f) {
            x = defaultX;
            y = defaultY;
        } else {
            x = cfgX;
            y = cfgY;
        }
    }

    public void clamp(ScaledResolution sr) {
        float maxX = Math.max(0f, sr.getScaledWidth() - w);
        float maxY = Math.max(0f, sr.getScaledHeight() - h);
        if (x < 0f) {
            x = 0f;
        }
        if (y < 0f) {
            y = 0f;
        }
        if (x > maxX) {
            x = maxX;
        }
        if (y > maxY) {
            y = maxY;
        }
    }

    /**
     * @return true if position should be written to settings this frame
     */
    public boolean tick(ScaledResolution sr, boolean unlock) {
        if (!unlock || !canDragNow()) {
            boolean released = dragging;
            dragging = false;
            wasDown = Mouse.isButtonDown(0);
            return released;
        }

        Minecraft mc = Minecraft.getMinecraft();
        int mx = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
        int my = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
        boolean down = Mouse.isButtonDown(0);
        boolean persist = false;

        if (down && !wasDown) {
            if (contains(mx, my)) {
                dragging = true;
                offX = mx - x;
                offY = my - y;
            }
        }
        if (dragging) {
            x = mx - offX;
            y = my - offY;
            clamp(sr);
            persist = true;
        }
        if (!down) {
            if (dragging) {
                persist = true;
            }
            dragging = false;
        }
        wasDown = down;
        return persist;
    }

    private boolean contains(float mx, float my) {
        return mx >= x && my >= y && mx <= x + w && my <= y + h;
    }

    private static boolean canDragNow() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return false;
        }
        return mc.currentScreen instanceof GuiChat
                || mc.currentScreen instanceof ClickGuiScreen;
    }
}
