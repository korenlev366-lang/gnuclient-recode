package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights dropped diamond/emerald/gold/iron items (Raven-bS ItemESP parity).
 */
public final class ItemEspModule extends Module {

    // Item IDs (vanilla 1.8.9): diamond=264, emerald=388, gold_ingot=266, iron_ingot=265
    private static final int DIAMOND_ID = 264;
    private static final int EMERALD_ID = 388;
    private static final int GOLD_ID = 266;
    private static final int IRON_ID = 265;

    static final class EntityData {
        double lastX;
        double lastY;
        double lastZ;
        double posX;
        double posY;
        double posZ;
        float cr;
        float cg;
        float cb;
    }

    private final BoolSetting diamonds = addSetting(new BoolSetting("Diamonds", true));
    private final BoolSetting emeralds = addSetting(new BoolSetting("Emeralds", true));
    private final BoolSetting gold = addSetting(new BoolSetting("Gold", true));
    private final BoolSetting iron = addSetting(new BoolSetting("Iron", true));
    private final SliderSetting maxDist = addSetting(new SliderSetting("Max Distance", 64.0f, 16.0f, 128.0f));

    private final List<EntityData> cache = new ArrayList<>();

    public ItemEspModule() {
        super("ItemESP", "Highlight valuable dropped items", Category.VISUALS);
    }

    @Override
    public void onEnable() {
        cache.clear();
    }

    @Override
    public void onDisable() {
        cache.clear();
    }

    @Override
    public void onTick() {
        cache.clear();

        if (Mc.player() == null || Mc.world() == null)
            return;

        double px = Mc.player().posX;
        double py = Mc.player().posY;
        double pz = Mc.player().posZ;
        double maxDistSq = maxDist.getValue() * maxDist.getValue();

        for (Entity entity : Mc.getWorldEntitiesFiltered(Mc.world())) {
            if (!(entity instanceof EntityItem))
                continue;

            EntityItem itemEntity = (EntityItem) entity;
            ItemStack stack = itemEntity.getEntityItem();
            if (stack == null)
                continue;

            Item item = stack.getItem();
            if (item == null)
                continue;

            int itemId = Item.getIdFromItem(item);
            float[] color = colorForId(itemId);
            if (color == null)
                continue;

            double posX = entity.posX;
            double posY = entity.posY;
            double posZ = entity.posZ;

            double dx = posX - px;
            double dy = posY - py;
            double dz = posZ - pz;
            if (dx * dx + dy * dy + dz * dz > maxDistSq)
                continue;

            EntityData data = new EntityData();
            data.lastX = entity.lastTickPosX;
            data.lastY = entity.lastTickPosY;
            data.lastZ = entity.lastTickPosZ;
            data.posX = posX;
            data.posY = posY;
            data.posZ = posZ;
            data.cr = color[0];
            data.cg = color[1];
            data.cb = color[2];
            cache.add(data);
        }
    }

    @Override
    public void onRender(float partialTicks) {
        if (cache.isEmpty())
            return;

        if (!Mc.isInGame())
            return;

        double[] vp = Mc.getViewerPos(partialTicks);
        double rvpX = vp[0];
        double rvpY = vp[1];
        double rvpZ = vp[2];

        RenderHelper.begin();

        for (EntityData data : cache) {
            double ix = Mc.lerp(data.lastX, data.posX, partialTicks);
            double iy = Mc.lerp(data.lastY, data.posY, partialTicks);
            double iz = Mc.lerp(data.lastZ, data.posZ, partialTicks);

            double rx = ix - rvpX;
            double ry = iy - rvpY;
            double rz = iz - rvpZ;

            EspDraw.fill(
                    rx - 0.15, ry, rz - 0.15,
                    rx + 0.15, ry + 0.25, rz + 0.15,
                    data.cr, data.cg, data.cb);
        }

        RenderHelper.end();
    }

    /**
     * Map item ID to (r,g,b) color, respecting per-item toggle settings.
     * @return float[3] {r, g, b} or null if not a tracked item / toggled off.
     */
    private float[] colorForId(int id) {
        switch (id) {
            case DIAMOND_ID:
                return diamonds.getValue() ? new float[] { 0.3f, 0.9f, 1.0f } : null;
            case EMERALD_ID:
                return emeralds.getValue() ? new float[] { 0.3f, 1.0f, 0.3f } : null;
            case GOLD_ID:
                return gold.getValue() ? new float[] { 1.0f, 0.85f, 0.1f } : null;
            case IRON_ID:
                return iron.getValue() ? new float[] { 0.85f, 0.85f, 0.85f } : null;
            default:
                return null;
        }
    }
}
