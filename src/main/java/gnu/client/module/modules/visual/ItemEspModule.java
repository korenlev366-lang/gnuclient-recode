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
    private final List<Entity> scratch = new ArrayList<>();
    private float[] boxBuffer = new float[6 * 32];
    private final float[] singleBox = new float[6];

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

        for (Entity entity : Mc.getWorldEntitiesFilteredInto(Mc.world(), scratch)) {
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

            EntityData data = obtain(cache, cache.size());
            data.lastX = entity.lastTickPosX;
            data.lastY = entity.lastTickPosY;
            data.lastZ = entity.lastTickPosZ;
            data.posX = posX;
            data.posY = posY;
            data.posZ = posZ;
            data.cr = color[0];
            data.cg = color[1];
            data.cb = color[2];
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

        int n = cache.size();
        ensureBoxCapacity(n);
        for (int i = 0; i < n; i++) {
            EntityData data = cache.get(i);
            double ix = Mc.lerp(data.lastX, data.posX, partialTicks);
            double iy = Mc.lerp(data.lastY, data.posY, partialTicks);
            double iz = Mc.lerp(data.lastZ, data.posZ, partialTicks);

            double rx = ix - rvpX;
            double ry = iy - rvpY;
            double rz = iz - rvpZ;

            int o = i * 6;
            boxBuffer[o] = (float) (rx - 0.15);
            boxBuffer[o + 1] = (float) ry;
            boxBuffer[o + 2] = (float) (rz - 0.15);
            boxBuffer[o + 3] = (float) (rx + 0.15);
            boxBuffer[o + 4] = (float) (ry + 0.25);
            boxBuffer[o + 5] = (float) (rz + 0.15);
        }

        RenderHelper.begin();
        for (int i = 0; i < n; i++) {
            EntityData data = cache.get(i);
            System.arraycopy(boxBuffer, i * 6, singleBox, 0, 6);
            EspDraw.fillBatched(singleBox, 1, data.cr, data.cg, data.cb, 0f);
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

    private static EntityData obtain(List<EntityData> list, int index) {
        if (index < list.size())
            return list.get(index);
        EntityData data = new EntityData();
        list.add(data);
        return data;
    }

    private void ensureBoxCapacity(int n) {
        if (boxBuffer.length >= n * 6)
            return;
        int cap = boxBuffer.length;
        while (cap < n * 6)
            cap <<= 1;
        boxBuffer = new float[cap];
    }
}
