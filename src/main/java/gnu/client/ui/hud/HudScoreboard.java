package gnu.client.ui.hud;

import gnu.client.module.modules.visual.HudModule;
import gnu.client.ui.UiKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lux-styled sidebar scoreboard: rounded panel, accent outline, movable when unlock is on.
 */
public final class HudScoreboard {

    private static final float PAD_X = 10f;
    private static final float PAD_Y = 8f;
    private static final float RIM = 1.25f;
    private static final float RADIUS = UiKit.RADIUS_PANEL;
    private static final float TITLE_GAP = 6f;
    private static final float LINE_GAP = 1f;

    private static final HudScoreboard INSTANCE = new HudScoreboard();

    private final HudDrag drag = new HudDrag();

    private HudScoreboard() {
    }

    public static HudScoreboard instance() {
        return INSTANCE;
    }

    public static boolean shouldReplace() {
        HudModule hud = HudModule.instance();
        return hud != null && hud.isEnabled() && hud.wantsScoreboard();
    }

    /**
     * @return true if vanilla scoreboard was replaced (caller should cancel).
     */
    public boolean tryRender(ScoreObjective objective, ScaledResolution sr) {
        if (!shouldReplace() || objective == null || sr == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return false;
        }

        Scoreboard board = objective.getScoreboard();
        List<Score> scores = collectScores(board, objective);
        FontRenderer fr = mc.fontRendererObj;
        if (fr == null) {
            return false;
        }

        String title = objective.getDisplayName();
        float lineH = fr.FONT_HEIGHT + LINE_GAP;
        float titleH = fr.FONT_HEIGHT;

        float maxW = fr.getStringWidth(title);
        List<String> lines = new ArrayList<String>(scores.size());
        for (Score score : scores) {
            ScorePlayerTeam team = board.getPlayersTeam(score.getPlayerName());
            String name = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            lines.add(name);
            maxW = Math.max(maxW, fr.getStringWidth(name));
        }

        float innerW = maxW;
        float innerH = titleH + TITLE_GAP + lines.size() * lineH;
        if (lines.isEmpty()) {
            innerH = titleH;
        }
        float panelW = innerW + PAD_X * 2f;
        float panelH = innerH + PAD_Y * 2f;

        float defaultX = sr.getScaledWidth() - panelW - 4f;
        float defaultY = sr.getScaledHeight() / 2f - panelH / 3f;

        HudModule hud = HudModule.instance();
        drag.setBounds(drag.getX(), drag.getY(), panelW, panelH);
        drag.applyConfig(hud.scoreboardX(), hud.scoreboardY(), defaultX, defaultY);
        if (drag.tick(sr, hud.wantsScoreboardUnlock())) {
            hud.setScoreboardPos(drag.getX(), drag.getY());
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
        float headerH = titleH + PAD_Y + TITLE_GAP * 0.5f;
        UiKit.drawRoundedPanel(x + 1f, y + 1f, w - 2f, Math.min(headerH, h - 2f),
                RADIUS - 1f, 0x0AFFFFFF);
        float sepY = y + PAD_Y + titleH + TITLE_GAP * 0.35f;
        UiKit.drawRoundedPanel(x + 10f, sepY, w - 20f, 1f, 0f, UiKit.LINE);

        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);

        int titleX = Math.round(x + (w - fr.getStringWidth(title)) * 0.5f);
        int titleY = Math.round(y + PAD_Y);
        fr.drawString(title, titleX, titleY, 0xFFFFFFFF, true);

        float ty = y + PAD_Y + titleH + TITLE_GAP;
        for (int i = lines.size() - 1; i >= 0; i--) {
            fr.drawString(lines.get(i), Math.round(x + PAD_X), Math.round(ty), 0xFFFFFFFF, true);
            ty += lineH;
        }

        if (hud.wantsScoreboardUnlock()) {
            UiKit.drawRoundedPanel(x + 3f, y + 6f, 2f, h - 12f, 2f,
                    UiKit.withAlpha(UiKit.ACCENT, 0.55f));
        }

        GlStateManager.popMatrix();
        return true;
    }

    private static List<Score> collectScores(Scoreboard board, ScoreObjective objective) {
        Collection<Score> raw = board.getSortedScores(objective);
        List<Score> list = new ArrayList<Score>();
        for (Score score : raw) {
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
                list.add(score);
            }
        }
        if (list.size() > 15) {
            return new ArrayList<Score>(list.subList(list.size() - 15, list.size()));
        }
        return list;
    }
}
