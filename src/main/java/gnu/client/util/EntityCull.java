package gnu.client.util;

import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;

/**
 * Render-only entity culling for {@code RenderManager.doRenderEntity}.
 * Does not affect ESP / combat entity iteration.
 */
public final class EntityCull {

    public static final float AGGRESSIVE_MAX_DISTANCE = 48f;

    private static Frustum frustum;
    private static double lastVx, lastVy, lastVz;
    private static float lastPartial = -1f;

    private EntityCull() {}

    public static boolean skipByCategory(
            boolean aggressive,
            boolean isPlayer,
            boolean isItem,
            boolean isArmorStand,
            boolean isNonPlayerLiving) {
        if (!aggressive)
            return false;
        if (isPlayer)
            return false;
        return isItem || isArmorStand || isNonPlayerLiving;
    }

    public static boolean skipByDistance(boolean aggressive, double distSq, float maxDist) {
        if (!aggressive)
            return false;
        return distSq > (double) maxDist * (double) maxDist;
    }

    private static void ensureFrustum(Entity viewer, float partialTicks) {
        if (frustum == null)
            frustum = new Frustum();
        double vx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double vy = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double vz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;
        if (partialTicks == lastPartial && vx == lastVx && vy == lastVy && vz == lastVz)
            return;
        lastPartial = partialTicks;
        lastVx = vx;
        lastVy = vy;
        lastVz = vz;
        frustum.setPosition(vx, vy, vz);
    }

    public static boolean shouldRenderEntity(Entity entity, float partialTicks) {
        if (!PerformanceModule.entityCull())
            return true;
        if (entity == null)
            return true;
        Entity self = Mc.player();
        if (self != null && (entity == self || entity == self.ridingEntity))
            return true;

        boolean aggressive = PerformanceModule.cullModeAggressive();
        boolean isPlayer = entity instanceof EntityPlayer;
        boolean isItem = entity instanceof EntityItem;
        boolean isArmorStand = entity instanceof EntityArmorStand;
        boolean isNonPlayerLiving = entity instanceof EntityLivingBase && !isPlayer;

        if (skipByCategory(aggressive, isPlayer, isItem, isArmorStand, isNonPlayerLiving))
            return false;

        Entity viewer = Mc.renderViewEntity();
        if (viewer == null)
            viewer = self;
        if (viewer != null
                && skipByDistance(aggressive, entity.getDistanceSqToEntity(viewer), AGGRESSIVE_MAX_DISTANCE))
            return false;

        if (viewer != null) {
            ensureFrustum(viewer, partialTicks);
            if (!frustum.isBoundingBoxInFrustum(entity.getEntityBoundingBox()))
                return false;
        }
        return true;
    }
}
