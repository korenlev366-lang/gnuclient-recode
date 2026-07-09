package gnu.client.ui.clickgui;

import gnu.client.config.ConfigManager;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.ui.UiBlur;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One floating category panel: header, open/close animation, drag, scroll, z-order,
 * and module rows.
 */
public final class CategoryColumn {

    public static final float WIDTH = UiKit.COLUMN_WIDTH;
    public static final float HEADER_H = 24f;
    public static final float BODY_PAD = 4f;
    public static final float MAX_BODY_H = 240f;

    private final Category category;
    private final List<ModuleRow> rows = new ArrayList<ModuleRow>();

    private float x;
    private float y;
    private boolean open = true;
    private int zOrder;

    private final UiKit.AnimatedFloat openAnim = new UiKit.AnimatedFloat(1f);
    private float scroll;
    private float targetScroll;

    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private String lastSearch = "";

    public CategoryColumn(Category category) {
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public boolean isOpen() {
        return open;
    }

    public int getZOrder() {
        return zOrder;
    }

    public void setZOrder(int zOrder) {
        this.zOrder = zOrder;
    }

    public void bringToFront(int nextZ) {
        this.zOrder = nextZ;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void applyLayout(ClickGuiLayout.Column column) {
        if (column == null) {
            return;
        }
        x = column.getX();
        y = column.getY();
        open = column.isOpen();
        openAnim.snap(open ? 1f : 0f);
    }

    public void persistTo(ClickGuiLayout layout) {
        layout.set(category, Math.round(x), Math.round(y), open);
    }

    public void setModules(List<Module> modules) {
        rows.clear();
        if (modules == null) {
            return;
        }
        for (Module module : modules) {
            rows.add(new ModuleRow(module));
        }
    }

    public List<ModuleRow> getRows() {
        return rows;
    }

    public void resetTransient() {
        dragging = false;
        scroll = 0f;
        targetScroll = 0f;
        for (ModuleRow row : rows) {
            row.resetTransient();
        }
    }

    /** Durations stay fixed; frame dt is scaled via {@link UiKit.UiClock#setSpeed}. */
    public void setAnimSpeed(float ignoredSpeed) {
        openAnim.setDurationMs(UiKit.DURATION_SLOW_MS, 1f);
        for (ModuleRow row : rows) {
            row.setAnimSpeed(ignoredSpeed);
        }
    }

    public void update(float dt, int mouseX, int mouseY, String search) {
        lastSearch = search == null ? "" : search;
        openAnim.setTarget(open ? 1f : 0f);
        openAnim.update(dt);

        if (dragging) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
        }

        float bodyTop = y + HEADER_H;
        float clipH = bodyClipHeight();
        float contentH = contentHeight(lastSearch);
        float maxScroll = Math.max(0f, contentH - clipH);
        if (targetScroll > maxScroll) {
            targetScroll = maxScroll;
        }
        if (targetScroll < 0f) {
            targetScroll = 0f;
        }
        scroll += (targetScroll - scroll) * Math.min(1f, dt * 12f);

        float rowY = bodyTop + BODY_PAD - scroll;
        for (ModuleRow row : rows) {
            if (!matchesSearch(row, lastSearch)) {
                continue;
            }
            float h = row.visibleHeight();
            boolean hovered = open
                    && contains(mouseX, mouseY, x + BODY_PAD, rowY, WIDTH - BODY_PAD * 2f, h)
                    && mouseY >= bodyTop && mouseY < bodyTop + clipH;
            row.update(dt, hovered);
            rowY += h + 2f;
        }
    }

    public float visibleHeight() {
        float openAmt = openAnim.get();
        return HEADER_H + openAmt * (bodyClipHeight() + BODY_PAD);
    }

    /** Hit-test height from discrete {@link #open}; openAnim is render-only. */
    public float hitHeight() {
        if (!open) {
            return HEADER_H;
        }
        return HEADER_H + bodyClipHeight() + BODY_PAD;
    }

    private float bodyClipHeight() {
        float content = contentHeight(lastSearch);
        return Math.min(MAX_BODY_H, Math.max(0f, content + BODY_PAD));
    }

    private float contentHeight(String search) {
        float h = 0f;
        for (ModuleRow row : rows) {
            if (!matchesSearch(row, search)) {
                continue;
            }
            h += row.visibleHeight() + 2f;
        }
        return h;
    }

    public boolean containsPoint(int mouseX, int mouseY) {
        return contains(mouseX, mouseY, x, y, WIDTH, hitHeight());
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button, String search) {
        if (!containsPoint(mouseX, mouseY)) {
            return false;
        }

        if (contains(mouseX, mouseY, x, y, WIDTH, HEADER_H)) {
            if (button == 0) {
                dragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
                return true;
            }
            if (button == 1) {
                open = !open;
                persistLayoutNow();
                return true;
            }
            return true;
        }

        // Closed/closing: header-only hits; do not absorb body clicks.
        if (!open) {
            return false;
        }

        float bodyTop = y + HEADER_H;
        float clipH = bodyClipHeight();
        if (!contains(mouseX, mouseY, x, bodyTop, WIDTH, clipH)) {
            return true;
        }

        float rowY = bodyTop + BODY_PAD - scroll;
        for (ModuleRow row : rows) {
            if (!matchesSearch(row, search)) {
                continue;
            }
            float h = row.hitHeight();
            if (contains(mouseX, mouseY, x + BODY_PAD, rowY, WIDTH - BODY_PAD * 2f, h)
                    && mouseY >= bodyTop && mouseY < bodyTop + clipH) {
                return row.mouseClicked(x + BODY_PAD, rowY, WIDTH - BODY_PAD * 2f,
                        mouseX, mouseY, button);
            }
            rowY += h + 2f;
        }
        return true;
    }

    public void mouseReleased() {
        if (dragging) {
            dragging = false;
            persistLayoutNow();
        }
        for (ModuleRow row : rows) {
            row.mouseReleased();
        }
    }

    public void mouseDragged(int mouseX, int mouseY) {
        if (dragging) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
        }
        for (ModuleRow row : rows) {
            if (row.isDraggingSetting()) {
                row.mouseDragged(mouseX);
            }
        }
    }

    public boolean handleScroll(int mouseX, int mouseY, int wheel, String search) {
        if (wheel == 0 || !open || !containsPoint(mouseX, mouseY)) {
            return false;
        }
        float bodyTop = y + HEADER_H;
        if (mouseY < bodyTop) {
            return false;
        }
        float contentH = contentHeight(search);
        float maxScroll = Math.max(0f, contentH - bodyClipHeight());
        targetScroll -= wheel / 120f * 28f;
        if (targetScroll < 0f) {
            targetScroll = 0f;
        }
        if (targetScroll > maxScroll) {
            targetScroll = maxScroll;
        }
        return true;
    }

    public void render(float alpha, float scale, String search, boolean useBlur,
            UiKit.ScissorStack scissors) {
        float openAmt = openAnim.get();
        float h = visibleHeight();
        float px = UiKit.PixelAlign.snap(x, scale);
        float py = UiKit.PixelAlign.snap(y, scale);
        float pw = UiKit.PixelAlign.snap(WIDTH, scale);
        float ph = UiKit.PixelAlign.snap(h, scale);

        if (useBlur) {
            UiBlur.drawPanel(px, py, pw, ph, UiKit.RADIUS_PANEL, alpha);
        } else {
            UiKit.drawRoundedPanel(px, py, pw, ph, UiKit.RADIUS_PANEL,
                    UiKit.withAlpha(UiKit.SURFACE, alpha));
        }

        // Header
        String title = category.name();
        float titleY = UiKit.PixelAlign.snap(py + (HEADER_H - UiFont.height()) * 0.5f, scale);
        UiFont.draw(title, UiKit.PixelAlign.snap(px + 12f, scale), titleY,
                UiKit.withAlpha(UiKit.TEXT, alpha));
        String count = String.valueOf(visibleRowCount(search));
        float cw = UiFont.width(count, 8f);
        UiFont.draw(count, UiKit.PixelAlign.snap(px + pw - 12f - cw, scale),
                UiKit.PixelAlign.snap(py + (HEADER_H - UiFont.height(8f)) * 0.5f, scale),
                8f, UiKit.withAlpha(UiKit.MUTED, alpha));

        if (openAmt < 0.02f) {
            return;
        }

        float bodyTop = py + HEADER_H;
        float clipH = bodyClipHeight() * openAmt;
        Minecraft mc = Minecraft.getMinecraft();
        int displayW = mc != null ? mc.displayWidth : 0;
        int displayH = mc != null ? mc.displayHeight : 0;
        scissors.pushScaled(px, bodyTop, pw, clipH, scale, displayW, displayH);
        try {
            float rowY = bodyTop + BODY_PAD - scroll;
            for (ModuleRow row : rows) {
                if (!matchesSearch(row, search)) {
                    continue;
                }
                float rh = row.visibleHeight();
                if (rowY + rh >= bodyTop - 2f && rowY <= bodyTop + clipH + 2f) {
                    row.render(px + BODY_PAD, rowY, pw - BODY_PAD * 2f, alpha * openAmt, scale);
                }
                rowY += rh + 2f;
            }
        } finally {
            scissors.pop();
        }
    }

    private int visibleRowCount(String search) {
        int n = 0;
        for (ModuleRow row : rows) {
            if (matchesSearch(row, search)) {
                n++;
            }
        }
        return n;
    }

    private void persistLayoutNow() {
        ClickGuiLayout layout = ConfigManager.INSTANCE.getClickGuiLayout();
        persistTo(layout);
        ConfigManager.INSTANCE.setClickGuiLayout(layout);
    }

    private static boolean matchesSearch(ModuleRow row, String search) {
        if (search == null || search.isEmpty()) {
            return true;
        }
        String q = search.toLowerCase(Locale.ROOT);
        return row.getModule().getName().toLowerCase(Locale.ROOT).contains(q);
    }

    private static boolean contains(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my < y + h;
    }
}
