package gnu.client.ui.clickgui;

import gnu.client.command.KeyNames;
import gnu.client.config.ConfigManager;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;

import java.util.List;

/**
 * Hit-testing and value mutation for nested module settings. Mode settings use
 * mockup-style chip groups; after mutations, requests a config save.
 */
public final class SettingInteraction {

    private static final float BOOL_H = 26f;
    private static final float SLIDER_H = 32f;
    private static final float BIND_H = 26f;
    private static final float CHIP_H = 14f;
    private static final float CHIP_PAD_X = 5f;
    private static final float CHIP_GAP = 3f;
    private static final float CHIP_RADIUS = 5f;
    private static final float MODE_LABEL_H = 12f;
    private static final float MODE_PAD_Y = 4f;

    private SliderSetting dragging;
    private float dragTrackX;
    private float dragTrackW;

    public void reset() {
        dragging = null;
    }

    public boolean isDragging() {
        return dragging != null;
    }

    public float contentHeight(Module module) {
        if (module == null) {
            return 0f;
        }
        module.guiUpdate();
        float h = 0f;
        // Width unknown at measure time — use column body estimate.
        float estimateW = UiKit.COLUMN_WIDTH - 24f;
        for (Setting<?> setting : module.getSettings()) {
            if (setting.isVisible()) {
                h += settingHeight(setting, estimateW);
            }
        }
        return h + BIND_H;
    }

    public void render(Module module, float x, float y, float width, float alpha, float scale) {
        if (module == null) {
            return;
        }
        module.guiUpdate();
        float rowY = y;
        for (Setting<?> setting : module.getSettings()) {
            if (!setting.isVisible()) {
                continue;
            }
            float h = settingHeight(setting, width);
            renderSetting(setting, x, rowY, width, h, alpha, scale);
            rowY += h;
        }
        renderBindRow(module, x, rowY, width, alpha, scale);
    }

    public boolean mouseClicked(Module module, float x, float y, float width,
            int mouseX, int mouseY, int button) {
        if (module == null) {
            return false;
        }
        module.guiUpdate();
        float rowY = y;
        for (Setting<?> setting : module.getSettings()) {
            if (!setting.isVisible()) {
                continue;
            }
            float h = settingHeight(setting, width);
            if (contains(mouseX, mouseY, x, rowY, width, h)) {
                return clickSetting(module, setting, x, rowY, width, h, mouseX, mouseY, button);
            }
            rowY += h;
        }
        if (contains(mouseX, mouseY, x, rowY, width, BIND_H)) {
            return clickBind(module, button);
        }
        return false;
    }

    public void mouseDragged(int mouseX) {
        if (dragging == null || dragTrackW <= 0f) {
            return;
        }
        float pct = (mouseX - dragTrackX) / dragTrackW;
        pct = UiKit.clamp01(pct);
        float val = dragging.getMin() + pct * (dragging.getMax() - dragging.getMin());
        dragging.setValue(val);
        ConfigManager.INSTANCE.requestSave();
    }

    public void mouseReleased() {
        dragging = null;
    }

    private boolean clickSetting(Module module, Setting<?> setting, float x, float y, float width, float height,
            int mouseX, int mouseY, int button) {
        if (setting instanceof BoolSetting) {
            if (button != 0) {
                return true;
            }
            BoolSetting bool = (BoolSetting) setting;
            bool.setValue(!bool.getValue());
            module.guiUpdate();
            ConfigManager.INSTANCE.requestSave();
            return true;
        }
        if (setting instanceof ModeSetting) {
            if (button != 0) {
                return true;
            }
            ModeSetting mode = (ModeSetting) setting;
            int hit = hitModeChip(mode, x, y, width, mouseX, mouseY);
            if (hit >= 0) {
                mode.setValue(hit);
                module.guiUpdate();
                ConfigManager.INSTANCE.requestSave();
            }
            return true;
        }
        if (setting instanceof SliderSetting) {
            if (button != 0) {
                return true;
            }
            SliderSetting slider = (SliderSetting) setting;
            dragging = slider;
            dragTrackX = x + 8f;
            dragTrackW = Math.max(1f, width - 16f);
            float pct = (mouseX - dragTrackX) / dragTrackW;
            pct = UiKit.clamp01(pct);
            float val = slider.getMin() + pct * (slider.getMax() - slider.getMin());
            slider.setValue(val);
            ConfigManager.INSTANCE.requestSave();
            return true;
        }
        return true;
    }

    private boolean clickBind(Module module, int button) {
        if (button == 0 || button == 2) {
            ClientBootstrap.beginRebind(module.getName());
            return true;
        }
        if (button == 1) {
            module.setKeyCode(-1);
            ClientBootstrap.cancelRebind();
            return true;
        }
        return true;
    }

    private void renderSetting(Setting<?> setting, float x, float y, float width, float height,
            float alpha, float scale) {
        if (setting instanceof ModeSetting) {
            renderModeSetting((ModeSetting) setting, x, y, width, alpha, scale);
            return;
        }
        if (setting instanceof BoolSetting) {
            renderBoolSetting((BoolSetting) setting, x, y, width, alpha, scale);
            return;
        }
        if (setting instanceof SliderSetting) {
            renderSliderSetting((SliderSetting) setting, x, y, width, alpha, scale);
            return;
        }
        float sx = UiKit.PixelAlign.snap(x + 8f, scale);
        float sy = UiKit.PixelAlign.snap(y + (height - UiFont.height(8f)) * 0.5f, scale);
        UiFont.draw(setting.getName(), sx, sy, 8f, UiKit.withAlpha(UiKit.MUTED, alpha));
    }

    private void renderModeSetting(ModeSetting mode, float x, float y, float width,
            float alpha, float scale) {
        float labelX = UiKit.PixelAlign.snap(x + 8f, scale);
        float labelY = UiKit.PixelAlign.snap(y + MODE_PAD_Y, scale);
        UiFont.draw(mode.getName(), labelX, labelY, 8f, UiKit.withAlpha(UiKit.MUTED, alpha));

        ChipLayout layout = layoutChips(mode, x, y, width);
        List<String> modes = mode.getModes();
        int selected = mode.getValue();
        for (int i = 0; i < layout.count; i++) {
            float cx = layout.xs[i];
            float cy = layout.ys[i];
            float cw = layout.ws[i];
            boolean on = i == selected;
            int bg = on ? 0x218B5CF6 : 0x09FFFFFF;
            int border = on ? 0x338B5CF6 : 0x00000000;
            int fg = on ? 0xFFD8D0FF : 0xFF777E8C;
            UiKit.drawRoundedPanel(cx, cy, cw, CHIP_H, CHIP_RADIUS, UiKit.withAlpha(bg, alpha));
            if (on) {
                // Soft selected border via inset darker rim
                UiKit.drawRoundedPanel(cx, cy, cw, 1f, 0f, UiKit.withAlpha(border, alpha));
            }
            String label = modes.get(i);
            float tw = UiFont.width(label, 7f);
            UiFont.draw(label,
                    UiKit.PixelAlign.snap(cx + (cw - tw) * 0.5f, scale),
                    UiKit.PixelAlign.snap(cy + (CHIP_H - UiFont.height(7f)) * 0.5f, scale),
                    7f, UiKit.withAlpha(fg, alpha));
        }
    }

    private void renderBoolSetting(BoolSetting bool, float x, float y, float width,
            float alpha, float scale) {
        float sx = UiKit.PixelAlign.snap(x + 8f, scale);
        float sy = UiKit.PixelAlign.snap(y + (BOOL_H - UiFont.height(8f)) * 0.5f, scale);
        UiFont.draw(bool.getName(), sx, sy, 8f, UiKit.withAlpha(UiKit.MUTED, alpha));

        float tw = 22f;
        float th = 12f;
        float tx = x + width - 8f - tw;
        float ty = y + (BOOL_H - th) * 0.5f;
        boolean on = bool.isToggled();
        UiKit.drawRoundedPanel(tx, ty, tw, th, th * 0.5f,
                UiKit.withAlpha(on ? 0xFF7655DF : 0xFF333846, alpha));
        float knob = 8f;
        float kx = on ? tx + tw - knob - 2f : tx + 2f;
        float ky = ty + (th - knob) * 0.5f;
        UiKit.drawRoundedPanel(kx, ky, knob, knob, knob * 0.5f,
                UiKit.withAlpha(on ? 0xFFFFFFFF : 0xFFA8ADBA, alpha));
    }

    private void renderSliderSetting(SliderSetting slider, float x, float y, float width,
            float alpha, float scale) {
        float sx = UiKit.PixelAlign.snap(x + 8f, scale);
        float sy = UiKit.PixelAlign.snap(y + 4f, scale);
        UiFont.draw(slider.getName(), sx, sy, 8f, UiKit.withAlpha(UiKit.MUTED, alpha));

        String value = String.format("%.2f", slider.getValue());
        float vw = UiFont.width(value, 8f);
        UiFont.draw(value, UiKit.PixelAlign.snap(x + width - 8f - vw, scale), sy, 8f,
                UiKit.withAlpha(0xFFC4B5FD, alpha));

        float trackX = x + 8f;
        float trackW = width - 16f;
        float trackY = y + SLIDER_H - 10f;
        float range = slider.getMax() - slider.getMin();
        float pct = range <= 0f ? 0f : (slider.getValue() - slider.getMin()) / range;
        pct = UiKit.clamp01(pct);
        UiKit.drawRoundedPanel(trackX, trackY, trackW, 3f, 2f,
                UiKit.withAlpha(0xFF303543, alpha));
        UiKit.drawRoundedPanel(trackX, trackY, Math.max(3f, trackW * pct), 3f, 2f,
                UiKit.withAlpha(UiKit.ACCENT, alpha));
        float knob = 9f;
        float kx = trackX + trackW * pct - knob * 0.5f;
        float ky = trackY + 1.5f - knob * 0.5f;
        UiKit.drawRoundedPanel(kx, ky, knob, knob, knob * 0.5f,
                UiKit.withAlpha(UiKit.ACCENT, alpha));
        UiKit.drawRoundedPanel(kx + 1.5f, ky + 1.5f, knob - 3f, knob - 3f, (knob - 3f) * 0.5f,
                UiKit.withAlpha(0xFFF5F3FF, alpha));
    }

    private void renderBindRow(Module module, float x, float y, float width,
            float alpha, float scale) {
        float sx = UiKit.PixelAlign.snap(x + 8f, scale);
        float sy = UiKit.PixelAlign.snap(y + (BIND_H - UiFont.height(8f)) * 0.5f, scale);
        boolean listening = ClientBootstrap.rebindModuleName() != null
                && ClientBootstrap.rebindModuleName().equalsIgnoreCase(module.getName());
        int color = UiKit.withAlpha(listening ? 0xFFFFFF55 : 0xFF88CCFF, alpha);
        UiFont.draw(bindLabel(module), sx, sy, 8f, color);
    }

    private static String bindLabel(Module module) {
        String listening = ClientBootstrap.rebindModuleName();
        if (listening != null && listening.equalsIgnoreCase(module.getName())) {
            return "Bind: ...";
        }
        int code = module.getKeyCode();
        if (code <= 0) {
            return "Bind: NONE";
        }
        return "Bind: " + KeyNames.format(code);
    }

    private static float settingHeight(Setting<?> setting, float width) {
        if (setting instanceof ModeSetting) {
            return modeHeight((ModeSetting) setting, width);
        }
        if (setting instanceof SliderSetting) {
            return SLIDER_H;
        }
        return BOOL_H;
    }

    private static float modeHeight(ModeSetting mode, float width) {
        ChipLayout layout = layoutChips(mode, 0f, 0f, width);
        if (layout.count <= 0) {
            return BOOL_H;
        }
        float bottom = 0f;
        for (int i = 0; i < layout.count; i++) {
            bottom = Math.max(bottom, layout.ys[i] + CHIP_H);
        }
        // layout ys are relative to y=0
        return Math.max(BOOL_H, bottom + MODE_PAD_Y);
    }

    private static int hitModeChip(ModeSetting mode, float x, float y, float width,
            int mouseX, int mouseY) {
        ChipLayout layout = layoutChips(mode, x, y, width);
        for (int i = 0; i < layout.count; i++) {
            if (contains(mouseX, mouseY, layout.xs[i], layout.ys[i], layout.ws[i], CHIP_H)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Prefer chips on the same row as the label (right-aligned). If they do not
     * fit, wrap onto following rows under the label.
     */
    private static ChipLayout layoutChips(ModeSetting mode, float x, float y, float width) {
        List<String> modes = mode.getModes();
        int n = modes == null ? 0 : modes.size();
        ChipLayout out = new ChipLayout(n);
        if (n == 0) {
            return out;
        }

        float[] chipW = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            chipW[i] = UiFont.width(modes.get(i), 7f) + CHIP_PAD_X * 2f;
            total += chipW[i];
            if (i > 0) {
                total += CHIP_GAP;
            }
        }

        float labelW = UiFont.width(mode.getName(), 8f) + 12f;
        float innerL = x + 8f;
        float innerR = x + width - 8f;
        float availSameRow = innerR - innerL - labelW - 6f;
        float chipY0 = y + MODE_PAD_Y + (MODE_LABEL_H - CHIP_H) * 0.5f;

        if (total <= availSameRow && availSameRow > 20f) {
            // Right-align on the label row
            float cx = innerR - total;
            for (int i = 0; i < n; i++) {
                out.xs[i] = cx;
                out.ys[i] = chipY0;
                out.ws[i] = chipW[i];
                cx += chipW[i] + CHIP_GAP;
            }
            out.count = n;
            return out;
        }

        // Wrap under the label
        float rowY = y + MODE_PAD_Y + MODE_LABEL_H + 2f;
        float cx = innerL;
        float rowRight = innerR;
        for (int i = 0; i < n; i++) {
            if (cx > innerL && cx + chipW[i] > rowRight) {
                cx = innerL;
                rowY += CHIP_H + CHIP_GAP;
            }
            out.xs[i] = cx;
            out.ys[i] = rowY;
            out.ws[i] = chipW[i];
            cx += chipW[i] + CHIP_GAP;
        }
        out.count = n;
        return out;
    }

    private static boolean contains(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my < y + h;
    }

    private static final class ChipLayout {
        final float[] xs;
        final float[] ys;
        final float[] ws;
        int count;

        ChipLayout(int n) {
            xs = new float[Math.max(0, n)];
            ys = new float[Math.max(0, n)];
            ws = new float[Math.max(0, n)];
            count = 0;
        }
    }
}
