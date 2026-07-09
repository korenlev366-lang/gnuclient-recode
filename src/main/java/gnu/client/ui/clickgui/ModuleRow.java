package gnu.client.ui.clickgui;

import gnu.client.command.KeyNames;
import gnu.client.module.Module;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;

/**
 * One module entry inside a category column: name, enabled accent, hover/toggle/expand
 * animations, and nested settings area.
 */
public final class ModuleRow {

    private static final float PAD = 4f;
    private static final float SETTINGS_PAD = 3f;

    private final Module module;
    private final SettingInteraction settings = new SettingInteraction();

    private final UiKit.AnimatedFloat hover = new UiKit.AnimatedFloat(0f);
    private final UiKit.AnimatedFloat enabled = new UiKit.AnimatedFloat(0f);
    private final UiKit.AnimatedFloat expand = new UiKit.AnimatedFloat(0f);

    private boolean expanded;

    public ModuleRow(Module module) {
        this.module = module;
        enabled.snap(module.isEnabled() ? 1f : 0f);
    }

    public Module getModule() {
        return module;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void toggleExpanded() {
        expanded = !expanded;
    }

    public void resetTransient() {
        expanded = false;
        expand.snap(0f);
        hover.snap(0f);
        settings.reset();
        enabled.snap(module.isEnabled() ? 1f : 0f);
    }

    /** Durations stay fixed; frame dt is scaled via {@link UiKit.UiClock#setSpeed}. */
    public void setAnimSpeed(float ignoredSpeed) {
        hover.setDurationMs(UiKit.DURATION_FAST_MS, 1f);
        enabled.setDurationMs(UiKit.DURATION_MED_MS, 1f);
        expand.setDurationMs(UiKit.DURATION_SLOW_MS, 1f);
    }

    public void update(float dt, boolean hovered) {
        hover.setTarget(hovered ? 1f : 0f);
        enabled.setTarget(module.isEnabled() ? 1f : 0f);
        expand.setTarget(expanded ? 1f : 0f);
        hover.update(dt);
        enabled.update(dt);
        expand.update(dt);
        if (settings.isDragging()) {
            // keep expand open while dragging a slider
            expand.setTarget(1f);
            expand.update(dt);
        }
    }

    /** Animated visible height of this row (header + expanding settings). */
    public float visibleHeight() {
        float settingsH = settings.contentHeight(module) + SETTINGS_PAD;
        return UiKit.ROW_HEIGHT + expand.get() * settingsH;
    }

    /** Hit-test height from discrete {@link #expanded}; expand anim is render-only. */
    public float hitHeight() {
        if (!expanded) {
            return UiKit.ROW_HEIGHT;
        }
        return UiKit.ROW_HEIGHT + settings.contentHeight(module) + SETTINGS_PAD;
    }

    public boolean mouseClicked(float x, float y, float width, int mouseX, int mouseY, int button) {
        float headerH = UiKit.ROW_HEIGHT;
        if (contains(mouseX, mouseY, x, y, width, headerH)) {
            if (button == 0) {
                module.toggle();
                return true;
            }
            if (button == 1) {
                toggleExpanded();
                return true;
            }
            if (button == 2) {
                ClientBootstrap.beginRebind(module.getName());
                expanded = true;
                return true;
            }
            return true;
        }

        // Settings hits only when discrete expanded; do not absorb mid-anim without applying.
        if (!expanded) {
            return false;
        }
        float expandAmt = expand.get();
        float settingsY = y + headerH + SETTINGS_PAD * 0.5f;
        float settingsH = settings.contentHeight(module);
        float settingsBand = settingsH + SETTINGS_PAD;
        if (!contains(mouseX, mouseY, x, y + headerH, width, settingsBand)) {
            return false;
        }
        if (expandAmt < 0.85f) {
            return false;
        }
        return settings.mouseClicked(module, x + PAD, settingsY, width - PAD * 2f,
                mouseX, mouseY, button);
    }

    public void mouseDragged(int mouseX) {
        settings.mouseDragged(mouseX);
    }

    public void mouseReleased() {
        settings.mouseReleased();
    }

    public boolean isDraggingSetting() {
        return settings.isDragging();
    }

    public void render(float x, float y, float width, float alpha, float scale) {
        float h = visibleHeight();
        float hx = UiKit.PixelAlign.snap(x, scale);
        float hy = UiKit.PixelAlign.snap(y, scale);
        float hw = UiKit.PixelAlign.snap(width, scale);
        float hh = UiKit.PixelAlign.snap(h, scale);

        float hoverA = hover.get();
        float en = enabled.get();
        if (hoverA > 0.01f) {
            UiKit.drawRoundedPanel(hx, hy, hw, Math.min(hh, UiKit.ROW_HEIGHT + 2f),
                    UiKit.RADIUS_ROW,
                    UiKit.withAlpha(0x08FFFFFF, alpha * hoverA));
        }

        // Enabled accent bar
        if (en > 0.01f) {
            float barH = Math.max(6f, UiKit.ROW_HEIGHT - 8f);
            UiKit.drawRoundedPanel(hx, hy + 4f, 2f, barH, 1f,
                    UiKit.withAlpha(UiKit.ACCENT, alpha * en));
        }

        int nameColor = UiKit.withAlpha(
                lerpColor(UiKit.MUTED, UiKit.TEXT, en), alpha);
        float textY = UiKit.PixelAlign.snap(hy + (UiKit.ROW_HEIGHT - UiFont.height()) * 0.5f, scale);
        String name = module.getName();
        UiFont.draw(name, UiKit.PixelAlign.snap(hx + 8f, scale), textY, nameColor);

        int code = module.getKeyCode();
        boolean listening = ClientBootstrap.rebindModuleName() != null
                && ClientBootstrap.rebindModuleName().equalsIgnoreCase(module.getName());
        if (listening || (code > 0 && expand.get() < 0.5f)) {
            String bind = listening ? "..." : KeyNames.format(code);
            float bw = UiFont.width(bind, 8f);
            UiFont.draw(bind,
                    UiKit.PixelAlign.snap(hx + hw - 8f - bw, scale),
                    UiKit.PixelAlign.snap(hy + (UiKit.ROW_HEIGHT - UiFont.height(8f)) * 0.5f, scale),
                    8f,
                    UiKit.withAlpha(listening ? 0xFFFFFF55 : 0xFF656B78, alpha));
        }

        float expandAmt = expand.get();
        if (expandAmt > 0.01f) {
            float settingsY = hy + UiKit.ROW_HEIGHT + SETTINGS_PAD * 0.5f;
            float settingsAlpha = alpha * UiKit.clamp01(expandAmt);
            settings.render(module, hx + PAD, settingsY, hw - PAD * 2f, settingsAlpha, scale);
        }
    }

    private static int lerpColor(int from, int to, float t) {
        t = UiKit.clamp01(t);
        int af = (from >>> 24) & 0xFF;
        int rf = (from >>> 16) & 0xFF;
        int gf = (from >>> 8) & 0xFF;
        int bf = from & 0xFF;
        int at = (to >>> 24) & 0xFF;
        int rt = (to >>> 16) & 0xFF;
        int gt = (to >>> 8) & 0xFF;
        int bt = to & 0xFF;
        int a = Math.round(af + (at - af) * t);
        int r = Math.round(rf + (rt - rf) * t);
        int g = Math.round(gf + (gt - gf) * t);
        int b = Math.round(bf + (bt - bf) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static boolean contains(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my < y + h;
    }
}
