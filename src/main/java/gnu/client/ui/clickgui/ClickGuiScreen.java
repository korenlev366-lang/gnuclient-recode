package gnu.client.ui.clickgui;

import gnu.client.config.ConfigManager;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.settings.ClickGuiModule;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.ui.UiBlur;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Lux ClickGUI orchestrator: searchable top bar, z-ordered draggable category columns,
 * and setting UX via {@link CategoryColumn} / {@link ModuleRow} / {@link SettingInteraction}.
 */
public class ClickGuiScreen extends GuiScreen {

    private static final float TOP_BAR_H = 36f;
    private static final float TOP_BAR_Y = 10f;
    private static final float SEARCH_W = 240f;

    private final List<CategoryColumn> columns = new ArrayList<CategoryColumn>();
    private final UiKit.UiClock clock = new UiKit.UiClock();
    private final UiKit.ScissorStack scissors = new UiKit.ScissorStack();

    private String search = "";
    private boolean searchFocused;
    private int nextZ = 1;
    private boolean layoutDirty;

    public ClickGuiScreen() {
        rebuild();
    }

    public void rebuild() {
        Map<Category, List<Module>> byCategory = new EnumMap<Category, List<Module>>(Category.class);
        for (Category cat : Category.values()) {
            byCategory.put(cat, new ArrayList<Module>());
        }
        for (Module module : ModuleManager.INSTANCE.all()) {
            if (module.getCategory() == Category.SETTINGS && "Settings".equals(module.getName())) {
                continue;
            }
            byCategory.get(module.getCategory()).add(module);
        }

        ClickGuiLayout layout = ConfigManager.INSTANCE.getClickGuiLayout();
        columns.clear();
        int z = 0;
        for (Category category : Category.values()) {
            List<Module> modules = byCategory.get(category);
            if (modules == null || modules.isEmpty()) {
                continue;
            }
            CategoryColumn column = new CategoryColumn(category);
            column.applyLayout(layout.get(category));
            column.setModules(modules);
            column.setZOrder(z++);
            columns.add(column);
        }
        nextZ = z;
    }

    @Override
    public void initGui() {
        rebuild();
        resetTransient();
        clock.reset();
    }

    private void resetTransient() {
        search = "";
        searchFocused = false;
        layoutDirty = false;
        ClientBootstrap.cancelRebind();
        for (CategoryColumn column : columns) {
            column.resetTransient();
        }
    }

    @Override
    public void onGuiClosed() {
        persistAllLayout();
        ClientBootstrap.cancelRebind();
        search = "";
        searchFocused = false;
        for (CategoryColumn column : columns) {
            column.resetTransient();
        }
        ConfigManager.INSTANCE.flush();
        UiBlur.endFrame();
    }

    private void persistAllLayout() {
        ClickGuiLayout layout = ConfigManager.INSTANCE.getClickGuiLayout();
        for (CategoryColumn column : columns) {
            column.persistTo(layout);
        }
        ConfigManager.INSTANCE.setClickGuiLayout(layout);
        layoutDirty = false;
    }

    private void applyVisualSettings() {
        ClickGuiModule gui = ClickGuiModule.instance();
        float speed = 1f;
        if (gui != null) {
            UiFont.setMode(gui.resolveFontMode());
            UiBlur.setEnabled(gui.isBlurEnabled());
            speed = gui.getAnimationSpeed();
        }
        clock.setSpeed(speed);
        for (CategoryColumn column : columns) {
            column.setAnimSpeed(speed);
        }
    }

    private float panelAlpha() {
        ClickGuiModule gui = ClickGuiModule.instance();
        return gui != null ? gui.getPanelOpacity() : 0.84f;
    }

    private float userScale() {
        float s = ClickGuiModule.resolveScale();
        return s <= 0f ? 1.0f : s;
    }

    private int logicalX(int mouseX) {
        return Math.round(mouseX / userScale());
    }

    private int logicalY(int mouseY) {
        return Math.round(mouseY / userScale());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        applyVisualSettings();
        clock.tick();
        float dt = clock.dt();
        float alpha = panelAlpha();
        boolean blur = UiBlur.isEnabled();

        int lx = logicalX(mouseX);
        int ly = logicalY(mouseY);
        for (CategoryColumn column : columns) {
            column.update(dt, lx, ly, search);
            column.mouseDragged(lx, ly);
        }

        final ScaledResolution sr = new ScaledResolution(mc);
        final float scale = sr.getScaleFactor();
        final float panelAlpha = alpha;
        final boolean wantBlur = blur;
        final String searchQuery = search;
        final int logicalScreenW = Math.round(sr.getScaledWidth() / userScale());
        UiKit.GlGuard.run(new Runnable() {
            @Override
            public void run() {
                UiBlur.beginFrame(wantBlur);
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.scale(userScale(), userScale(), 1f);
                    drawTopBar(logicalScreenW, panelAlpha, scale);
                    List<CategoryColumn> ordered = sortedByZ();
                    for (CategoryColumn column : ordered) {
                        column.render(panelAlpha, scale, userScale(), searchQuery,
                                wantBlur && UiBlur.isUsable(), scissors);
                    }
                } finally {
                    GlStateManager.popMatrix();
                    scissors.clear();
                    UiBlur.endFrame();
                }
            }
        });

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTopBar(int screenW, float alpha, float scale) {
        float barW = Math.min(480f, screenW - 24f);
        float barX = (screenW - barW) * 0.5f;
        float barY = TOP_BAR_Y;
        float bx = UiKit.PixelAlign.snap(barX, scale);
        float by = UiKit.PixelAlign.snap(barY, scale);
        float bw = UiKit.PixelAlign.snap(barW, scale);
        float bh = UiKit.PixelAlign.snap(TOP_BAR_H, scale);

        UiKit.drawRoundedPanel(bx, by, bw, bh, 14f, UiKit.withAlpha(UiKit.SURFACE_STRONG, alpha));

        UiFont.draw("GNUClient", UiKit.PixelAlign.snap(bx + 14f, scale),
                UiKit.PixelAlign.snap(by + 8f, scale),
                UiKit.withAlpha(UiKit.TEXT, alpha));
        UiFont.draw("LUX INTERFACE",
                UiKit.PixelAlign.snap(bx + 14f, scale),
                UiKit.PixelAlign.snap(by + 20f, scale),
                7f, UiKit.withAlpha(UiKit.MUTED, alpha));

        float searchW = Math.min(SEARCH_W, barW * 0.48f);
        float searchX = bx + (bw - searchW) * 0.5f;
        float searchY = by + 7f;
        float searchH = 22f;
        UiKit.drawRoundedPanel(searchX, searchY, searchW, searchH, 11f,
                UiKit.withAlpha(searchFocused ? 0x228B5CF6 : 0x12FFFFFF, alpha));

        String shown = search.isEmpty() && !searchFocused ? "Search modules..." : search;
        int color = search.isEmpty() && !searchFocused
                ? UiKit.withAlpha(0xFF676E7D, alpha)
                : UiKit.withAlpha(UiKit.TEXT, alpha);
        UiFont.draw(shown,
                UiKit.PixelAlign.snap(searchX + 10f, scale),
                UiKit.PixelAlign.snap(searchY + (searchH - UiFont.height()) * 0.5f, scale),
                color);
    }

    private boolean searchHit(int mouseX, int mouseY) {
        float screenW = width / userScale();
        float barW = Math.min(480f, screenW - 24f);
        float barX = (screenW - barW) * 0.5f;
        float searchW = Math.min(SEARCH_W, barW * 0.48f);
        float searchX = barX + (barW - searchW) * 0.5f;
        float searchY = TOP_BAR_Y + 7f;
        return mouseX >= searchX && mouseX <= searchX + searchW
                && mouseY >= searchY && mouseY <= searchY + 22f;
    }

    private List<CategoryColumn> sortedByZ() {
        List<CategoryColumn> ordered = new ArrayList<CategoryColumn>(columns);
        Collections.sort(ordered, new Comparator<CategoryColumn>() {
            @Override
            public int compare(CategoryColumn a, CategoryColumn b) {
                return Integer.compare(a.getZOrder(), b.getZOrder());
            }
        });
        return ordered;
    }

    private List<CategoryColumn> sortedByZDesc() {
        List<CategoryColumn> ordered = sortedByZ();
        Collections.reverse(ordered);
        return ordered;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int lx = logicalX(mouseX);
        int ly = logicalY(mouseY);
        if (searchHit(lx, ly)) {
            searchFocused = true;
            return;
        }
        searchFocused = false;

        for (CategoryColumn column : sortedByZDesc()) {
            if (column.containsPoint(lx, ly)) {
                column.bringToFront(nextZ++);
                column.mouseClicked(lx, ly, mouseButton, search);
                layoutDirty = true;
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for (CategoryColumn column : columns) {
            column.mouseReleased();
        }
        if (layoutDirty) {
            persistAllLayout();
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        int lx = logicalX(mouseX);
        int ly = logicalY(mouseY);
        for (CategoryColumn column : columns) {
            column.mouseDragged(lx, ly);
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int lx = logicalX(mouseX);
        int ly = logicalY(mouseY);
        for (CategoryColumn column : sortedByZDesc()) {
            if (column.handleScroll(lx, ly, wheel, search)) {
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Screen owns rebind while open.
        if (ClientBootstrap.isRebindActive()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                ClientBootstrap.cancelRebind();
                return;
            }
            if (keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
                String name = ClientBootstrap.rebindModuleName();
                if (name != null) {
                    ClientBootstrap.setModuleKeyCode(name, -1);
                }
                ClientBootstrap.cancelRebind();
                return;
            }
            if (keyCode > 0 && !isModifier(keyCode)) {
                String name = ClientBootstrap.rebindModuleName();
                if (name != null) {
                    ClientBootstrap.setModuleKeyCode(name, keyCode);
                }
                ClientBootstrap.cancelRebind();
                return;
            }
            return;
        }

        if (searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                searchFocused = false;
                return;
            }
            if (keyCode == Keyboard.KEY_BACK && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                return;
            }
            if (typedChar >= 32 && typedChar != 127) {
                search = search + typedChar;
                return;
            }
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private static boolean isModifier(int keyCode) {
        return keyCode == Keyboard.KEY_LSHIFT || keyCode == Keyboard.KEY_RSHIFT
                || keyCode == Keyboard.KEY_LCONTROL || keyCode == Keyboard.KEY_RCONTROL
                || keyCode == Keyboard.KEY_LMENU || keyCode == Keyboard.KEY_RMENU;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
