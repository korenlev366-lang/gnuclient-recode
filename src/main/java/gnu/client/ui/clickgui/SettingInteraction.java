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

/**
 * Hit-testing and value mutation for nested module settings. After mutations,
 * requests a config save; {@link Setting#setValue} stays side-effect free.
 */
public final class SettingInteraction {

    public static final float ROW_H = UiKit.ROW_HEIGHT;

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
        int n = 0;
        for (Setting<?> setting : module.getSettings()) {
            if (setting.isVisible()) {
                n++;
            }
        }
        return (n + 1) * ROW_H; // settings + bind row
    }

    public void render(Module module, float x, float y, float width, float alpha, float scale) {
        if (module == null) {
            return;
        }
        float rowY = y;
        for (Setting<?> setting : module.getSettings()) {
            if (!setting.isVisible()) {
                continue;
            }
            renderSetting(setting, x, rowY, width, alpha, scale);
            rowY += ROW_H;
        }
        renderBindRow(module, x, rowY, width, alpha, scale);
    }

    public boolean mouseClicked(Module module, float x, float y, float width,
            int mouseX, int mouseY, int button) {
        if (module == null) {
            return false;
        }
        float rowY = y;
        for (Setting<?> setting : module.getSettings()) {
            if (!setting.isVisible()) {
                continue;
            }
            if (contains(mouseX, mouseY, x, rowY, width, ROW_H)) {
                return clickSetting(setting, x, rowY, width, mouseX, button);
            }
            rowY += ROW_H;
        }
        if (contains(mouseX, mouseY, x, rowY, width, ROW_H)) {
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

    private boolean clickSetting(Setting<?> setting, float x, float y, float width,
            int mouseX, int button) {
        if (setting instanceof BoolSetting) {
            if (button != 0) {
                return true;
            }
            BoolSetting bool = (BoolSetting) setting;
            bool.setValue(!bool.getValue());
            ConfigManager.INSTANCE.requestSave();
            return true;
        }
        if (setting instanceof ModeSetting) {
            if (button != 0 && button != 1) {
                return true;
            }
            ModeSetting mode = (ModeSetting) setting;
            int size = mode.getModes().size();
            if (size <= 0) {
                return true;
            }
            int next = mode.getValue() + (button == 1 ? -1 : 1);
            if (next >= size) {
                next = 0;
            }
            if (next < 0) {
                next = size - 1;
            }
            mode.setValue(next);
            ConfigManager.INSTANCE.requestSave();
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

    private void renderSetting(Setting<?> setting, float x, float y, float width,
            float alpha, float scale) {
        float sx = UiKit.PixelAlign.snap(x + 8f, scale);
        float sy = UiKit.PixelAlign.snap(y + (ROW_H - UiFont.height()) * 0.5f, scale);
        int labelColor = UiKit.withAlpha(UiKit.MUTED, alpha);
        int valueColor = UiKit.withAlpha(0xFFC4B5FD, alpha);

        UiFont.draw(setting.getName(), sx, sy, 8f, labelColor);

        String value = formatValue(setting);
        float vw = UiFont.width(value, 8f);
        UiFont.draw(value, UiKit.PixelAlign.snap(x + width - 8f - vw, scale), sy, 8f, valueColor);

        if (setting instanceof SliderSetting) {
            SliderSetting slider = (SliderSetting) setting;
            float trackX = x + 8f;
            float trackW = width - 16f;
            float trackY = y + ROW_H - 5f;
            float range = slider.getMax() - slider.getMin();
            float pct = range <= 0f ? 0f : (slider.getValue() - slider.getMin()) / range;
            pct = UiKit.clamp01(pct);
            UiKit.drawRoundedPanel(trackX, trackY, trackW, 2f, 1f,
                    UiKit.withAlpha(0x33FFFFFF, alpha));
            UiKit.drawRoundedPanel(trackX, trackY, trackW * pct, 2f, 1f,
                    UiKit.withAlpha(UiKit.ACCENT, alpha));
        }
    }

    private void renderBindRow(Module module, float x, float y, float width,
            float alpha, float scale) {
        float sx = UiKit.PixelAlign.snap(x + 8f, scale);
        float sy = UiKit.PixelAlign.snap(y + (ROW_H - UiFont.height()) * 0.5f, scale);
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

    private static String formatValue(Setting<?> setting) {
        if (setting instanceof BoolSetting) {
            return ((BoolSetting) setting).getValue() ? "ON" : "OFF";
        }
        if (setting instanceof SliderSetting) {
            return String.format("%.2f", ((SliderSetting) setting).getValue());
        }
        if (setting instanceof ModeSetting) {
            return ((ModeSetting) setting).getCurrentMode();
        }
        return String.valueOf(setting.getValue());
    }

    private static boolean contains(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my < y + h;
    }
}
