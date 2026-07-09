package gnu.client.ui.clickgui;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Simple category-column ClickGUI for GNUClient Forge mod. */
public class ClickGuiScreen extends GuiScreen {

    private static final int COL_WIDTH = 110;
    private static final int ROW_HEIGHT = 14;
    private static final int HEADER_HEIGHT = 16;

    private final Map<Category, List<Module>> columns = new EnumMap<>(Category.class);
    private Module expanded;
    private Setting<?> dragging;
    private int dragStartX;

    public ClickGuiScreen() {
        rebuild();
    }

    public void rebuild() {
        columns.clear();
        for (Category cat : Category.values()) {
            columns.put(cat, new ArrayList<>());
        }
        for (Module module : ModuleManager.INSTANCE.all()) {
            if (module.getCategory() == Category.SETTINGS && "Settings".equals(module.getName())) {
                continue;
            }
            columns.get(module.getCategory()).add(module);
        }
    }

    @Override
    public void initGui() {
        rebuild();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = 8;
        int y = 8;

        for (Category category : Category.values()) {
            List<Module> modules = columns.get(category);
            if (modules == null || modules.isEmpty()) {
                continue;
            }
            int colHeight = HEADER_HEIGHT + modules.size() * ROW_HEIGHT;
            if (expanded != null && modules.contains(expanded)) {
                colHeight += expanded.getSettings().size() * ROW_HEIGHT;
            }
            drawRect(x, y, x + COL_WIDTH, y + colHeight, 0xAA101010);
            drawCenteredString(fontRendererObj, category.name(), x + COL_WIDTH / 2, y + 4, 0xFFFFFF);

            int rowY = y + HEADER_HEIGHT;
            for (Module module : modules) {
                int color = module.isEnabled() ? 0xFF55FF55 : 0xFFAAAAAA;
                if (module == expanded) {
                    color = 0xFFFFFF55;
                }
                drawString(fontRendererObj, module.getName(), x + 4, rowY + 3, color);
                rowY += ROW_HEIGHT;

                if (module == expanded) {
                    for (Setting<?> setting : module.getSettings()) {
                        if (!setting.isVisible()) {
                            continue;
                        }
                        String label = setting.getName() + ": " + formatSetting(setting);
                        drawString(fontRendererObj, label, x + 8, rowY + 3, 0xFFCCCCCC);
                        if (setting instanceof SliderSetting && setting == dragging) {
                            SliderSetting slider = (SliderSetting) setting;
                            float pct = (mouseX - (x + 8)) / (float) (COL_WIDTH - 16);
                            pct = Math.max(0f, Math.min(1f, pct));
                            float val = slider.getMin() + pct * (slider.getMax() - slider.getMin());
                            slider.setValue(val);
                        }
                        rowY += ROW_HEIGHT;
                    }
                }
            }
            x += COL_WIDTH + 6;
            if (x + COL_WIDTH > sr.getScaledWidth()) {
                x = 8;
                y += colHeight + 8;
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String formatSetting(Setting<?> setting) {
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

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int x = 8;
        int y = 8;
        ScaledResolution sr = new ScaledResolution(mc);

        outer:
        for (Category category : Category.values()) {
            List<Module> modules = columns.get(category);
            if (modules == null || modules.isEmpty()) {
                continue;
            }
            int colHeight = HEADER_HEIGHT + modules.size() * ROW_HEIGHT;
            if (expanded != null && modules.contains(expanded)) {
                colHeight += expanded.getSettings().size() * ROW_HEIGHT;
            }

            int rowY = y + HEADER_HEIGHT;
            for (Module module : modules) {
                if (mouseX >= x && mouseX <= x + COL_WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                    if (mouseButton == 0) {
                        module.toggle();
                    } else if (mouseButton == 1) {
                        expanded = expanded == module ? null : module;
                    }
                    return;
                }
                rowY += ROW_HEIGHT;

                if (module == expanded) {
                    for (Setting<?> setting : module.getSettings()) {
                        if (!setting.isVisible()) {
                            continue;
                        }
                        if (mouseX >= x && mouseX <= x + COL_WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                            if (setting instanceof BoolSetting) {
                                BoolSetting b = (BoolSetting) setting;
                                b.setValue(!b.getValue());
                            } else if (setting instanceof ModeSetting) {
                                ModeSetting mode = (ModeSetting) setting;
                                int next = mode.getValue() + 1;
                                if (next >= mode.getModes().size()) {
                                    next = 0;
                                }
                                mode.setValue(next);
                            } else if (setting instanceof SliderSetting) {
                                dragging = setting;
                                dragStartX = mouseX;
                            }
                            return;
                        }
                        rowY += ROW_HEIGHT;
                    }
                }
            }

            x += COL_WIDTH + 6;
            if (x + COL_WIDTH > sr.getScaledWidth()) {
                x = 8;
                y += colHeight + 8;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = null;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
