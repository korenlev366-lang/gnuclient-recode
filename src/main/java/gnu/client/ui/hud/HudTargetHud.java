package gnu.client.ui.hud;

import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.visual.HudModule;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;
import gnu.client.ui.clickgui.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Lux Target HUD — preview always in ClickGUI; in-game only when KillAura has a target.
 */
public final class HudTargetHud {

    private static final float RIM = 1.25f;
    private static final float RADIUS = UiKit.RADIUS_PANEL;
    private static final float PAD = 10f;
    private static final float HEAD = 28f;
    private static final float GAP = 8f;
    private static final float BAR_H = 6f;
    private static final float MIN_W = 140f;
    private static final float H = PAD * 2f + HEAD;

    private static final HudTargetHud INSTANCE = new HudTargetHud();

    private final HudDrag drag = new HudDrag();
    private float displayHp = 20f;
    private EntityLivingBase lastTarget;

    private HudTargetHud() {
    }

    public static HudTargetHud instance() {
        return INSTANCE;
    }

    public void render(ScaledResolution sr) {
        HudModule hud = HudModule.instance();
        if (hud == null || !hud.isEnabled() || !hud.wantsTargetHud() || sr == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        boolean preview = mc.currentScreen instanceof ClickGuiScreen;
        EntityLivingBase target = resolveTarget(preview);
        if (target == null && !preview) {
            return;
        }

        String name;
        float hp;
        float maxHp;
        float absorption;
        ResourceLocation skin = null;

        if (target != null) {
            name = target.getName();
            hp = target.getHealth();
            maxHp = Math.max(1f, target.getMaxHealth());
            absorption = target.getAbsorptionAmount();
            skin = skinOf(target);
            if (target != lastTarget) {
                displayHp = hp + absorption;
                lastTarget = target;
            }
        } else {
            // ClickGUI placeholder when no self entity
            name = "Target";
            hp = 20f;
            maxHp = 20f;
            absorption = 0f;
            lastTarget = null;
        }

        float targetHp = hp + absorption;
        displayHp += (targetHp - displayHp) * 0.35f;

        FontMetrics metrics = measure(name);
        float textBlockW = Math.max(metrics.nameW, metrics.hpW);
        float panelW = Math.max(MIN_W, PAD + HEAD + GAP + textBlockW + PAD);
        float panelH = H;

        float defaultX = sr.getScaledWidth() / 2f - panelW / 2f;
        float defaultY = sr.getScaledHeight() / 2f + 40f;

        drag.setBounds(drag.getX(), drag.getY(), panelW, panelH);
        drag.applyConfig(hud.targetHudX(), hud.targetHudY(), defaultX, defaultY);
        if (drag.tick(sr, hud.wantsTargetHudUnlock())) {
            hud.setTargetHudPos(drag.getX(), drag.getY());
        }
        drag.clamp(sr);

        float scale = sr.getScaleFactor();
        float x = UiKit.PixelAlign.snap(drag.getX(), scale);
        float y = UiKit.PixelAlign.snap(drag.getY(), scale);
        float w = UiKit.PixelAlign.snap(panelW, scale);
        float h = UiKit.PixelAlign.snap(panelH, scale);
        drag.setBounds(x, y, w, h);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        UiKit.drawRoundedPanel(x - RIM, y - RIM, w + RIM * 2f, h + RIM * 2f,
                RADIUS + RIM, UiKit.ACCENT);
        UiKit.drawRoundedPanel(x, y, w, h, RADIUS, UiKit.SURFACE_STRONG);
        UiKit.drawRoundedPanel(x + 1f, y + 1f, w - 2f, 16f, RADIUS - 1f, 0x0AFFFFFF);

        float headX = x + PAD;
        float headY = y + (h - HEAD) * 0.5f;
        drawHead(skin, headX, headY, HEAD);

        float tx = headX + HEAD + GAP;
        float nameY = y + PAD - 1f;
        UiFont.draw(stripColors(name), tx, nameY, UiKit.TEXT);

        float ratio = UiKit.clamp01(displayHp / (maxHp + Math.max(0f, absorption)));
        float barW = w - PAD - (tx - x);
        float barX = tx;
        float barY = y + h - PAD - BAR_H;
        UiKit.drawRoundedPanel(barX, barY, barW, BAR_H, BAR_H * 0.5f, 0x33000000);
        int barColor = healthColor(ratio);
        UiKit.drawRoundedPanel(barX, barY, Math.max(BAR_H, barW * ratio), BAR_H, BAR_H * 0.5f, barColor);

        String hpText = formatHp(hp, absorption);
        UiFont.draw(hpText, tx, barY - UiFont.height() - 3f, 7.5f, UiKit.MUTED);

        if (hud.wantsTargetHudUnlock()) {
            UiKit.drawRoundedPanel(x + 3f, y + 6f, 2f, h - 12f, 2f,
                    UiKit.withAlpha(UiKit.ACCENT, 0.55f));
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private EntityLivingBase resolveTarget(boolean preview) {
        Entity ka = KillAuraModule.getCurrentTarget();
        if (ka instanceof EntityLivingBase) {
            return (EntityLivingBase) ka;
        }
        if (preview) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                return mc.thePlayer;
            }
        }
        return null;
    }

    private static ResourceLocation skinOf(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) {
            return null;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(entity.getUniqueID());
        if (info == null) {
            info = mc.getNetHandler().getPlayerInfo(entity.getName());
        }
        return info != null ? info.getLocationSkin() : null;
    }

    private static void drawHead(ResourceLocation skin, float x, float y, float size) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        if (skin == null) {
            UiKit.drawRoundedPanel(x, y, size, size, 8f, 0x22FFFFFF);
            return;
        }
        mc.getTextureManager().bindTexture(skin);
        Gui.drawScaledCustomSizeModalRect(
                Math.round(x), Math.round(y),
                8f, 8f, 8, 8,
                Math.round(size), Math.round(size),
                64f, 64f);
        Gui.drawScaledCustomSizeModalRect(
                Math.round(x), Math.round(y),
                40f, 8f, 8, 8,
                Math.round(size), Math.round(size),
                64f, 64f);
    }

    private static int healthColor(float ratio) {
        if (ratio > 0.66f) {
            return UiKit.SUCCESS;
        }
        if (ratio > 0.33f) {
            return 0xFFF0C24B;
        }
        return UiKit.DANGER;
    }

    private static String formatHp(float hp, float absorption) {
        String base = String.format("%.1f HP", hp);
        if (absorption > 0.05f) {
            return base + String.format(" +%.1f", absorption);
        }
        return base;
    }

    private static String stripColors(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static FontMetrics measure(String name) {
        FontMetrics m = new FontMetrics();
        m.nameW = UiFont.width(stripColors(name));
        m.hpW = UiFont.width("20.0 HP +4.0", 7.5f);
        return m;
    }

    private static final class FontMetrics {
        float nameW;
        float hpW;
    }
}
