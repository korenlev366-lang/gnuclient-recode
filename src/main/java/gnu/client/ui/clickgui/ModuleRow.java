package gnu.client.ui.clickgui;

import gnu.client.command.KeyNames;
import gnu.client.module.Module;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;

/**
 * One module entry: mockup-style name + bind, expand chevron, pill toggle,
 * left accent, and nested settings.
 */
public final class ModuleRow {

    private static final float PAD = 6f;
    private static final float SETTINGS_PAD = 4f;
    private static final float SWITCH_W = 27f;
    private static final float SWITCH_H = 15f;
    private static final float CHEVRON_W = 18f;

    private final Module module;
    private final SettingInteraction settings = new SettingInteraction();

    private final UiKit.AnimatedFloat hover = new UiKit.AnimatedFloat(0f);
    private final UiKit.AnimatedFloat enabled = new UiKit.AnimatedFloat(0f);
    private final UiKit.AnimatedFloat expand = new UiKit.AnimatedFloat(0f);
    private final UiKit.AnimatedFloat knob = new UiKit.AnimatedFloat(0f);

    private boolean expanded;

    public ModuleRow(Module module) {
        this.module = module;
        float on = module.isEnabled() ? 1f : 0f;
        enabled.snap(on);
        knob.snap(on);
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
        float on = module.isEnabled() ? 1f : 0f;
        enabled.snap(on);
        knob.snap(on);
    }

    /** Durations stay fixed; frame dt is scaled via {@link UiKit.UiClock#setSpeed}. */
    public void setAnimSpeed(float ignoredSpeed) {
        hover.setDurationMs(UiKit.DURATION_FAST_MS, 1f);
        enabled.setDurationMs(UiKit.DURATION_MED_MS, 1f);
        expand.setDurationMs(UiKit.DURATION_SLOW_MS, 1f);
        knob.setDurationMs(UiKit.DURATION_FAST_MS, 1f);
    }

    public void update(float dt, boolean hovered) {
        hover.setTarget(hovered ? 1f : 0f);
        float on = module.isEnabled() ? 1f : 0f;
        enabled.setTarget(on);
        knob.setTarget(on);
        expand.setTarget(expanded ? 1f : 0f);
        hover.update(dt);
        enabled.update(dt);
        knob.update(dt);
        expand.update(dt);
        if (settings.isDragging()) {
            expand.setTarget(1f);
            expand.update(dt);
        }
    }

    public float visibleHeight() {
        float settingsH = settings.contentHeight(module) + SETTINGS_PAD;
        return UiKit.ROW_HEIGHT + expand.get() * settingsH;
    }

    public float hitHeight() {
        if (!expanded) {
            return UiKit.ROW_HEIGHT;
        }
        return UiKit.ROW_HEIGHT + settings.contentHeight(module) + SETTINGS_PAD;
    }

    public boolean mouseClicked(float x, float y, float width, int mouseX, int mouseY, int button) {
        float headerH = UiKit.ROW_HEIGHT;
        if (contains(mouseX, mouseY, x, y, width, headerH)) {
            float switchX = x + width - PAD - SWITCH_W;
            float switchY = y + (headerH - SWITCH_H) * 0.5f;
            float chevronX = switchX - 4f - CHEVRON_W;

            if (button == 0 && contains(mouseX, mouseY, switchX, switchY, SWITCH_W, SWITCH_H)) {
                module.toggle();
                return true;
            }
            if (button == 0 && contains(mouseX, mouseY, chevronX, y, CHEVRON_W, headerH)) {
                toggleExpanded();
                return true;
            }
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
        float rowH = UiKit.ROW_HEIGHT;

        float hoverA = hover.get();
        float en = enabled.get();

        // Soft rounded hover / active wash
        if (hoverA > 0.01f || en > 0.01f) {
            float wash = Math.max(hoverA * 0.045f, en * 0.03f);
            UiKit.drawRoundedPanel(hx, hy, hw, rowH, UiKit.RADIUS_ROW,
                    UiKit.withAlpha(0xFFFFFFFF, alpha * wash));
        }

        // Left accent bar (mockup)
        if (en > 0.01f) {
            float barH = rowH - 16f;
            UiKit.drawRoundedPanel(hx, hy + 8f, 2f, barH, 2f,
                    UiKit.withAlpha(UiKit.ACCENT, alpha * en));
        }

        float switchX = hx + hw - PAD - SWITCH_W;
        float chevronX = switchX - 4f - CHEVRON_W;
        float textLeft = hx + 10f;
        float textRight = chevronX - 4f;

        int nameColor = UiKit.withAlpha(lerpColor(0xFFB3B8C4, UiKit.TEXT, en), alpha);
        float nameY = UiKit.PixelAlign.snap(hy + 7f, scale);
        String name = module.getName();
        // Clip visually by not drawing past chevron — truncate if needed
        float maxNameW = Math.max(8f, textRight - textLeft);
        if (UiFont.width(name) > maxNameW) {
            while (name.length() > 1 && UiFont.width(name + "…") > maxNameW) {
                name = name.substring(0, name.length() - 1);
            }
            name = name + "…";
        }
        UiFont.draw(name, UiKit.PixelAlign.snap(textLeft, scale), nameY, nameColor);

        int code = module.getKeyCode();
        boolean listening = ClientBootstrap.rebindModuleName() != null
                && ClientBootstrap.rebindModuleName().equalsIgnoreCase(module.getName());
        String bind = listening ? "..." : (code > 0 ? KeyNames.format(code) : "NONE");
        UiFont.draw(bind,
                UiKit.PixelAlign.snap(textLeft, scale),
                UiKit.PixelAlign.snap(hy + 19f, scale),
                7.5f,
                UiKit.withAlpha(listening ? 0xFFFFFF55 : 0xFF656B78, alpha));

        // Expand chevron
        float chevronRot = expand.get();
        drawChevron(chevronX + CHEVRON_W * 0.5f, hy + rowH * 0.5f, chevronRot,
                UiKit.withAlpha(expanded ? 0xFFB9A6FF : 0xFF666D7A, alpha), scale);

        // Pill toggle
        drawSwitch(switchX, hy + (rowH - SWITCH_H) * 0.5f, alpha, scale);

        float expandAmt = expand.get();
        if (expandAmt > 0.01f) {
            float settingsY = hy + rowH + SETTINGS_PAD * 0.5f;
            float settingsAlpha = alpha * UiKit.clamp01(expandAmt);
            settings.render(module, hx + PAD, settingsY, hw - PAD * 2f, settingsAlpha, scale);
        }
    }

    private void drawSwitch(float x, float y, float alpha, float scale) {
        float sx = UiKit.PixelAlign.snap(x, scale);
        float sy = UiKit.PixelAlign.snap(y, scale);
        float t = knob.get();
        int track = lerpColor(0xFF303542, UiKit.ACCENT, t);
        UiKit.drawRoundedPanel(sx, sy, SWITCH_W, SWITCH_H, SWITCH_H * 0.5f,
                UiKit.withAlpha(track, alpha));
        float knobSize = 11f;
        float travel = SWITCH_W - knobSize - 4f;
        float kx = sx + 2f + travel * t;
        float ky = sy + (SWITCH_H - knobSize) * 0.5f;
        UiKit.drawRoundedPanel(kx, ky, knobSize, knobSize, knobSize * 0.5f,
                UiKit.withAlpha(0xFFF5F6FA, alpha));
    }

    private static void drawChevron(float cx, float cy, float openAmt, int argb, float scale) {
        // Simple ">" made of two short bars via thin rounded panels, rotated by openAmt.
        float a = ((argb >>> 24) & 0xFF) / 255f;
        if (a <= 0f) {
            return;
        }
        // Approximate chevron with two small quads using rounded panels
        float size = 5f;
        float x = UiKit.PixelAlign.snap(cx - 2f + openAmt * 2f, scale);
        float y = UiKit.PixelAlign.snap(cy - size * 0.5f, scale);
        UiKit.drawRoundedPanel(x, y, 1.5f, size, 1f, argb);
        UiKit.drawRoundedPanel(x + 3.5f, y + 1.5f, 1.5f, size - 3f, 1f,
                UiKit.withAlpha(argb, ((argb >>> 24) & 0xFF) / 255f * 0.55f));
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
