package gnu.client.ui.hud;

import gnu.client.GnuClientMod;
import gnu.client.config.ConfigManager;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.settings.ClickGuiModule;
import gnu.client.module.modules.visual.HudModule;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Race-safe ArrayList + notification overlay. Drain dirty identities, sample
 * {@link Module#isEnabled()}, then animate.
 */
public final class HudRenderer {

    private static final float ARRAY_MARGIN = 12f;
    private static final float WM_MARGIN = ARRAY_MARGIN;
    private static final float WM_PAD_X = 10f;
    private static final float WM_PAD_Y = 7f;
    private static final float WM_RADIUS = 12f;
    private static final float WM_RIM = 1f;
    private static final float ARRAY_GAP = 3f;
    private static final float ARRAY_PAD_X = 10f;
    private static final float ARRAY_PAD_L = 9f;
    private static final float ARRAY_MIN_H = 18f;
    private static final float ARRAY_ACCENT_W = 2f;
    private static final float TOAST_GAP = 6f;
    private static final float TOAST_MARGIN = 12f;
    private static final float TOAST_ICON = 26f;
    private static final float TOAST_PAD = 10f;
    private static final int MAX_SUFFIX_LEN = 24;
    private static final int MAX_SUFFIX_PARTS = 2;
    private static final float SETTLE_EPS = 0.01f;

    private static final HudRenderer INSTANCE = new HudRenderer();

    private final UiKit.UiClock clock = new UiKit.UiClock();
    private final NotificationQueue notifications = new NotificationQueue();
    private final Map<Module, Boolean> baselines = new IdentityHashMap<Module, Boolean>();
    private final Map<String, ArrayRow> rows = new HashMap<String, ArrayRow>();
    private final Map<String, Module> enabledEligible = new HashMap<String, Module>();
    private final List<ArrayRow> sortedRows = new ArrayList<ArrayRow>();
    private boolean seeded;
    private boolean pendingSilentReseed = true;
    private boolean sawLoading;

    private HudRenderer() {
    }

    public static HudRenderer instance() {
        return INSTANCE;
    }

    public NotificationQueue getNotificationQueue() {
        return notifications;
    }

    /** HUD enabled — silent reseed next frame; do not toast config/script state. */
    public void onHudEnabled() {
        pendingSilentReseed = true;
        clock.reset();
    }

    /** HUD disabled — clear toasts and array animation state. */
    public void onHudDisabled() {
        notifications.clearAll();
        rows.clear();
        baselines.clear();
        seeded = false;
        pendingSilentReseed = true;
    }

    public void requestSilentReseed() {
        pendingSilentReseed = true;
    }

    public boolean hasActiveNotifications() {
        return notifications.hasActive();
    }

    public void render(Object scaledResolution) {
        if (!(scaledResolution instanceof ScaledResolution)) {
            return;
        }
        final ScaledResolution sr = (ScaledResolution) scaledResolution;
        final HudModule hud = HudModule.instance();
        if (hud == null || !hud.isEnabled()) {
            return;
        }

        applyFontAndSpeed();
        clock.tick();
        float dt = clock.dt();
        long nowNs = System.nanoTime();

        if (ConfigManager.instance().isLoading()) {
            sawLoading = true;
            notifications.setSuppress(true);
            pendingSilentReseed = true;
        } else {
            notifications.setSuppress(false);
            if (sawLoading) {
                sawLoading = false;
                pendingSilentReseed = true;
            }
        }

        Set<Module> drained = ModuleToggleSignals.drain();

        if (pendingSilentReseed || !seeded) {
            silentReseed();
            drained = CollectionsEmpty.modules();
        } else {
            reconcileNotifications(drained);
        }

        if (hud.wantsArray()) {
            reconcileArray(hud.wantsSuffixes(), dt);
        } else {
            rows.clear();
        }

        notifications.advance(nowNs);
        advanceToastAnims(dt, nowNs);

        final boolean drawArray = hud.wantsArray() && !rows.isEmpty();
        final boolean drawToasts = hud.wantsNotifications() && notifications.hasActive();
        final boolean drawWatermark = hud.wantsWatermark();
        if (!drawArray && !drawToasts && !drawWatermark) {
            return;
        }

        final float scale = sr.getScaleFactor();
        final int sw = sr.getScaledWidth();
        final int sh = sr.getScaledHeight();

        UiKit.GlGuard.run(new Runnable() {
            @Override
            public void run() {
                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                if (drawWatermark) {
                    drawWatermark(scale);
                }
                if (drawArray) {
                    drawArrayList(sw, scale, hud.wantsSuffixes());
                }
                if (drawToasts) {
                    drawNotifications(sw, sh, scale, nowNs);
                }
                // Depth/lighting restored by GlGuard.finally
            }
        });
    }

    private void applyFontAndSpeed() {
        ClickGuiModule gui = ClickGuiModule.instance();
        float speed = 1f;
        if (gui != null) {
            UiFont.setMode(gui.resolveFontMode());
            speed = gui.getAnimationSpeed();
        }
        clock.setSpeed(speed);
    }

    private void silentReseed() {
        seedBaselines(ModuleManager.INSTANCE.all(), baselines);
        ModuleToggleSignals.drain();
        seeded = true;
        pendingSilentReseed = false;
    }

    private void reconcileNotifications(Set<Module> drained) {
        boolean notify = HudModule.instance() != null && HudModule.instance().wantsNotifications();
        applyFinalStateDeltas(drained, baselines, notifications, notify);
    }

    /**
     * Drain-then-sample coalescing: for each dirty identity, read final
     * {@link Module#isEnabled()}, compare to baseline, enqueue only on real deltas.
     * Returns number of notifications enqueued (for tests).
     */
    public static int applyFinalStateDeltas(Set<Module> drained,
            Map<Module, Boolean> baselines,
            NotificationQueue queue,
            boolean enqueueNotifications) {
        if (drained == null || drained.isEmpty() || baselines == null || queue == null) {
            return 0;
        }
        int pushed = 0;
        for (Module module : drained) {
            if (module == null || module instanceof HudModule) {
                continue;
            }
            boolean finalEnabled = module.isEnabled();
            Boolean baseline = baselines.get(module);
            if (baseline == null) {
                // First-seen identity: seed silently, never toast.
                baselines.put(module, Boolean.valueOf(finalEnabled));
                continue;
            }
            if (baseline.booleanValue() == finalEnabled) {
                continue;
            }
            baselines.put(module, Boolean.valueOf(finalEnabled));
            if (enqueueNotifications) {
                queue.pushStateChange(module);
                pushed++;
            }
        }
        return pushed;
    }

    /** Seed baselines from current module states without enqueueing. */
    public static void seedBaselines(Iterable<Module> modules, Map<Module, Boolean> baselines) {
        if (modules == null || baselines == null) {
            return;
        }
        baselines.clear();
        for (Module m : modules) {
            if (m != null) {
                baselines.put(m, Boolean.valueOf(m.isEnabled()));
            }
        }
    }

    private void reconcileArray(boolean showSuffixes, float dt) {
        Map<String, Module> enabledEligible = this.enabledEligible;
        enabledEligible.clear();
        for (Module m : ModuleManager.INSTANCE.all()) {
            if (!isArrayEligible(m) || !m.isEnabled()) {
                continue;
            }
            enabledEligible.put(m.getName(), m);
        }

        for (Map.Entry<String, Module> e : enabledEligible.entrySet()) {
            ArrayRow row = rows.get(e.getKey());
            if (row == null) {
                row = new ArrayRow(e.getValue());
                rows.put(e.getKey(), row);
                row.visibility.snap(0f);
            } else {
                row.module = e.getValue();
            }
            row.exiting = false;
            row.refreshLabel(showSuffixes);
            row.visibility.setTarget(1f);
            row.visibility.setDurationMs(UiKit.DURATION_MED_MS, 1f);
        }

        Iterator<Map.Entry<String, ArrayRow>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ArrayRow> e = it.next();
            if (enabledEligible.containsKey(e.getKey())) {
                continue;
            }
            ArrayRow row = e.getValue();
            row.exiting = true;
            row.visibility.setTarget(0f);
            row.visibility.setDurationMs(UiKit.DURATION_MED_MS, 1f);
            row.visibility.update(dt);
            if (row.visibility.get() <= SETTLE_EPS && row.visibility.settled(SETTLE_EPS)) {
                it.remove();
            }
        }

        for (ArrayRow row : rows.values()) {
            if (!row.exiting) {
                row.visibility.update(dt);
            }
            row.refreshLabel(showSuffixes);
        }

        List<ArrayRow> sorted = sortedRows;
        sorted.clear();
        sorted.addAll(rows.values());
        CollectionsSort.sortRows(sorted);
        float y = ARRAY_MARGIN;
        for (ArrayRow row : sorted) {
            row.targetY = y;
            row.layoutY = UiKit.ExpEase.toward(row.layoutY, row.targetY,
                    UiKit.ExpEase.kForDurationMs(UiKit.DURATION_MED_MS, 1f), dt);
            if (!row.yInitialized) {
                row.layoutY = y;
                row.yInitialized = true;
            }
            y += (ARRAY_MIN_H + ARRAY_GAP) * Math.max(row.visibility.get(), 0.05f);
        }
    }

    private static boolean isArrayEligible(Module m) {
        if (m == null || m instanceof HudModule) {
            return false;
        }
        if (m.getCategory() == Category.SETTINGS) {
            return false;
        }
        if (m.isHidden()) {
            return false;
        }
        return true;
    }

    static String sanitizeSuffixes(String[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int parts = 0;
        for (String part : raw) {
            if (part == null) {
                continue;
            }
            String cleaned = part.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (cleaned.length() > MAX_SUFFIX_LEN) {
                cleaned = cleaned.substring(0, MAX_SUFFIX_LEN);
            }
            if (parts > 0) {
                sb.append(' ');
            }
            sb.append(cleaned);
            parts++;
            if (parts >= MAX_SUFFIX_PARTS) {
                break;
            }
        }
        return sb.toString();
    }

    static String displayLabel(Module module, boolean showSuffixes) {
        if (module == null) {
            return "";
        }
        if (!showSuffixes) {
            return module.getName();
        }
        String suffix = sanitizeSuffixes(module.getSuffix());
        if (suffix.isEmpty()) {
            return module.getName();
        }
        return module.getName() + " " + suffix;
    }

    private void drawWatermark(float scale) {
        String brand = "gnuclient";
        String ver = GnuClientMod.VERSION;
        int fps = Minecraft.getDebugFPS();
        String fpsText = fps + " FPS";
        String sep = " | ";

        float brandW = UiFont.width(brand);
        float sepW = UiFont.width(sep);
        float verW = UiFont.width(ver);
        float fpsW = UiFont.width(fpsText);
        float textH = UiFont.height();
        float innerW = brandW + sepW + verW + sepW + fpsW;
        float w = innerW + WM_PAD_X * 2f;
        float h = textH + WM_PAD_Y * 2f;

        float x = UiKit.PixelAlign.snap(WM_MARGIN, scale);
        float y = UiKit.PixelAlign.snap(WM_MARGIN, scale);
        w = UiKit.PixelAlign.snap(w, scale);
        h = UiKit.PixelAlign.snap(h, scale);

        UiKit.drawRoundedPanel(x - WM_RIM, y - WM_RIM, w + WM_RIM * 2f, h + WM_RIM * 2f,
                WM_RADIUS + WM_RIM, UiKit.ACCENT);
        UiKit.drawRoundedPanel(x, y, w, h, WM_RADIUS, UiKit.SURFACE_STRONG);

        float tx = x + WM_PAD_X;
        float ty = UiKit.PixelAlign.snap(y + (h - textH) * 0.5f, scale);

        UiFont.draw(brand, tx, ty, UiKit.ACCENT);
        tx += brandW;
        UiFont.draw(sep, tx, ty, UiKit.MUTED_DIM);
        tx += sepW;
        UiFont.draw(ver, tx, ty, UiKit.MUTED);
        tx += verW;
        UiFont.draw(sep, tx, ty, UiKit.MUTED_DIM);
        tx += sepW;
        UiFont.draw(fpsText, tx, ty, UiKit.MUTED);
    }

    private void drawArrayList(int scaledWidth, float scale, boolean showSuffixes) {
        List<ArrayRow> sorted = sortedRows;
        sorted.clear();
        sorted.addAll(rows.values());
        CollectionsSort.sortRows(sorted);
        float right = scaledWidth - ARRAY_MARGIN;
        for (ArrayRow row : sorted) {
            float vis = UiKit.clamp01(row.visibility.get());
            if (vis <= 0.01f) {
                continue;
            }
            float suffixW = 0f;
            String suffix = row.suffix;
            if (showSuffixes && suffix != null && !suffix.isEmpty()) {
                int fixed = row.module.getFixedSuffixWidth();
                suffixW = (fixed >= 0 ? fixed : UiFont.width(suffix)) + 6f;
            }
            float nameW = UiFont.width(row.name);
            float contentW = nameW + suffixW;
            float w = contentW + ARRAY_PAD_L + ARRAY_PAD_X;
            float h = ARRAY_MIN_H;
            float slide = (1f - vis) * 12f;
            float x = right - w + slide;
            float y = row.layoutY;
            x = UiKit.PixelAlign.snap(x, scale);
            y = UiKit.PixelAlign.snap(y, scale);
            w = UiKit.PixelAlign.snap(w, scale);
            h = UiKit.PixelAlign.snap(h, scale);

            int bg = UiKit.withAlpha(0xCC0C0F16, vis * 0.94f);
            UiKit.drawRoundedPanel(x, y, w, h, UiKit.RADIUS_PILL, bg);

            float accentX = x + w - 3f - ARRAY_ACCENT_W;
            float accentY = y + 4f;
            float accentH = h - 8f;
            drawAccentBar(accentX, accentY, ARRAY_ACCENT_W, accentH, vis);

            float textY = y + (h - UiFont.height()) * 0.5f;
            int textColor = UiKit.withAlpha(UiKit.TEXT, vis);
            UiFont.draw(row.name, x + ARRAY_PAD_L, textY, textColor);
            if (showSuffixes && suffix != null && !suffix.isEmpty()) {
                int muted = UiKit.withAlpha(0xFF8F82C9, vis);
                UiFont.draw(suffix, x + ARRAY_PAD_L + nameW + 6f, textY, 8f, muted);
            }
        }
    }

    private void drawAccentBar(float x, float y, float w, float h, float alpha) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        float a = UiKit.clamp01(alpha);
        float r1 = ((UiKit.ACCENT >> 16) & 0xFF) / 255f;
        float g1 = ((UiKit.ACCENT >> 8) & 0xFF) / 255f;
        float b1 = (UiKit.ACCENT & 0xFF) / 255f;
        float r2 = ((UiKit.ACCENT_2 >> 16) & 0xFF) / 255f;
        float g2 = ((UiKit.ACCENT_2 >> 8) & 0xFF) / 255f;
        float b2 = (UiKit.ACCENT_2 & 0xFF) / 255f;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x, y + h, 0).color(r2, g2, b2, a).endVertex();
        wr.pos(x + w, y + h, 0).color(r2, g2, b2, a).endVertex();
        wr.pos(x + w, y, 0).color(r1, g1, b1, a).endVertex();
        wr.pos(x, y, 0).color(r1, g1, b1, a).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();
    }

    private void advanceToastAnims(float dt, long nowNs) {
        float kIn = UiKit.ExpEase.kForDurationMs(UiKit.DURATION_SLOW_MS, 1f);
        float kOut = UiKit.ExpEase.kForDurationMs(400f, 1f);
        float kY = UiKit.ExpEase.kForDurationMs(UiKit.DURATION_MED_MS, 1f);
        List<NotificationQueue.Entry> bottomFirst = notifications.bottomFirst();
        float yFromBottom = 0f;
        for (NotificationQueue.Entry e : bottomFirst) {
            if (e.isExiting()) {
                e.animOut = UiKit.ExpEase.toward(e.animOut, 1f, kOut, dt);
            } else {
                e.animIn = UiKit.ExpEase.toward(e.animIn, 1f, kIn, dt);
            }
            e.targetY = yFromBottom;
            e.layoutY = UiKit.ExpEase.toward(e.layoutY, e.targetY, kY, dt);
            float h = UiKit.TOAST_MAX_HEIGHT;
            yFromBottom += (h + TOAST_GAP) * Math.max(0.15f, e.alpha());
        }
    }

    private void drawNotifications(int scaledWidth, int scaledHeight, float scale, long nowNs) {
        List<NotificationQueue.Entry> bottomFirst = notifications.bottomFirst();
        float toastW = Math.min(UiKit.TOAST_MAX_WIDTH, scaledWidth - TOAST_MARGIN * 2f);
        for (NotificationQueue.Entry e : bottomFirst) {
            float alpha = UiKit.clamp01(e.alpha());
            if (alpha <= 0.01f) {
                continue;
            }
            float slideX = e.isExiting() ? e.animOut * 18f : (1f - e.animIn) * 0f;
            float slideY = e.isExiting() ? 0f : (1f - e.animIn) * 16f;
            float h = UiKit.TOAST_MAX_HEIGHT;
            float x = scaledWidth - TOAST_MARGIN - toastW + slideX;
            float y = scaledHeight - TOAST_MARGIN - h - e.layoutY - slideY;
            x = UiKit.PixelAlign.snap(x, scale);
            y = UiKit.PixelAlign.snap(y, scale);
            float w = UiKit.PixelAlign.snap(toastW, scale);
            h = UiKit.PixelAlign.snap(h, scale);

            int bg = UiKit.withAlpha(UiKit.SURFACE_STRONG, alpha * 0.95f);
            UiKit.drawRoundedPanel(x, y, w, h, UiKit.RADIUS_TOAST, bg);

            int accent = e.enabled ? UiKit.SUCCESS : UiKit.DANGER;
            float iconX = x + TOAST_PAD;
            float iconY = y + (h - TOAST_ICON) * 0.5f;
            int iconBg = UiKit.withAlpha(accent, alpha * 0.11f);
            UiKit.drawRoundedPanel(iconX, iconY, TOAST_ICON, TOAST_ICON, 7f, iconBg);
            String mark = e.enabled ? "+" : "x";
            int markColor = UiKit.withAlpha(accent, alpha);
            float markW = UiFont.width(mark);
            UiFont.draw(mark, iconX + (TOAST_ICON - markW) * 0.5f,
                    iconY + (TOAST_ICON - UiFont.height()) * 0.5f, markColor);

            float textX = iconX + TOAST_ICON + 8f;
            UiFont.draw(e.moduleName, textX, y + 14f, UiKit.withAlpha(UiKit.TEXT, alpha));
            String copy = e.enabled ? "Module enabled successfully" : "Module disabled successfully";
            UiFont.draw(copy, textX, y + 28f, 8f, UiKit.withAlpha(UiKit.MUTED, alpha));

            float railL = x + 12f;
            float railR = x + w - 12f;
            float railY = y + h - 3f;
            float railW = railR - railL;
            UiKit.drawRoundedPanel(railL, railY, railW, 2f, 3f,
                    UiKit.withAlpha(0x0AFFFFFF, alpha));
            float progress = e.progress(nowNs);
            if (progress > 0.001f) {
                drawProgressRail(railL, railY, railW * progress, 2f, alpha);
            }
        }
    }

    private void drawProgressRail(float x, float y, float w, float h, float alpha) {
        if (w <= 0f) {
            return;
        }
        float a = UiKit.clamp01(alpha);
        float r1 = ((UiKit.ACCENT >> 16) & 0xFF) / 255f;
        float g1 = ((UiKit.ACCENT >> 8) & 0xFF) / 255f;
        float b1 = (UiKit.ACCENT & 0xFF) / 255f;
        float r2 = ((UiKit.ACCENT_2 >> 16) & 0xFF) / 255f;
        float g2 = ((UiKit.ACCENT_2 >> 8) & 0xFF) / 255f;
        float b2 = (UiKit.ACCENT_2 & 0xFF) / 255f;
        GlStateManager.disableTexture2D();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x, y + h, 0).color(r1, g1, b1, a).endVertex();
        wr.pos(x + w, y + h, 0).color(r2, g2, b2, a).endVertex();
        wr.pos(x + w, y, 0).color(r2, g2, b2, a).endVertex();
        wr.pos(x, y, 0).color(r1, g1, b1, a).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();
    }

    /** Pure helpers exposed for unit tests. */
    public static final class Pure {
        private Pure() {
        }

        public static String sanitizeSuffixes(String[] raw) {
            return HudRenderer.sanitizeSuffixes(raw);
        }

        public static boolean isArrayEligible(Module m) {
            return HudRenderer.isArrayEligible(m);
        }

        /**
         * ArrayList sort key: width descending, then name ascending (ignore case).
         * Returns negative if {@code a} should sort before {@code b}.
         */
        public static int compareArrayOrder(float widthA, String nameA, float widthB, String nameB) {
            int byWidth = Float.compare(widthB, widthA);
            if (byWidth != 0) {
                return byWidth;
            }
            String na = nameA == null ? "" : nameA;
            String nb = nameB == null ? "" : nameB;
            return na.compareToIgnoreCase(nb);
        }
    }

    private static final class ArrayRow {
        Module module;
        String name;
        String suffix;
        String label;
        float measuredWidth;
        final UiKit.AnimatedFloat visibility = new UiKit.AnimatedFloat(0f);
        float layoutY;
        float targetY;
        boolean exiting;
        boolean yInitialized;

        ArrayRow(Module module) {
            this.module = module;
            refreshLabel(true);
        }

        void refreshLabel(boolean showSuffixes) {
            name = module.getName();
            suffix = sanitizeSuffixes(module.getSuffix());
            label = displayLabel(module, showSuffixes);
            float nameW = UiFont.width(name);
            float suffixW;
            if (showSuffixes && suffix != null && !suffix.isEmpty()) {
                int fixed = module.getFixedSuffixWidth();
                suffixW = (fixed >= 0 ? fixed : UiFont.width(suffix)) + 6f;
            } else {
                suffixW = 0f;
            }
            measuredWidth = nameW + suffixW + ARRAY_PAD_L + ARRAY_PAD_X;
        }
    }

    /** Avoid importing java.util.Collections name clash with sort helper. */
    private static final class CollectionsEmpty {
        static Set<Module> modules() {
            return java.util.Collections.emptySet();
        }
    }

    private static final class CollectionsSort {
        static void sortRows(List<ArrayRow> sorted) {
            java.util.Collections.sort(sorted, new Comparator<ArrayRow>() {
                @Override
                public int compare(ArrayRow a, ArrayRow b) {
                    return Pure.compareArrayOrder(a.measuredWidth, a.name, b.measuredWidth, b.name);
                }
            });
        }
    }
}
