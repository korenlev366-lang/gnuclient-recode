package gnu.client.runtime;

import gnu.client.mixin.impl.accessors.IAccessorEntityPlayer;
import gnu.client.module.modules.combat.KillAuraModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;

/**
 * OpenMyau EntityRenderer fake-block pose: temporarily set {@code itemInUse} for the
 * render frame so ItemRenderer takes the BLOCK path. Lives outside mixins so Mixin 0.7
 * does not try to remap {@link IAccessorEntityPlayer} onto EntityRenderer.
 */
public final class AutoBlockPoseHook {
    private static ItemStack savedItemInUse;
    private static Integer savedItemInUseCount;

    private AutoBlockPoseHook() {}

    public static void beginFrame() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null)
            return;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || !KillAuraModule.shouldRenderBlocking())
            return;
        ItemStack held = player.inventory.getCurrentItem();
        if (held == null)
            return;
        IAccessorEntityPlayer acc = (IAccessorEntityPlayer) player;
        savedItemInUse = acc.getItemInUse();
        savedItemInUseCount = acc.getItemInUseCount();
        acc.setItemInUse(held);
        acc.setItemInUseCount(69000);
    }

    public static void endFrame() {
        if (savedItemInUseCount == null)
            return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc != null ? mc.thePlayer : null;
        if (player != null) {
            IAccessorEntityPlayer acc = (IAccessorEntityPlayer) player;
            acc.setItemInUse(savedItemInUse);
            acc.setItemInUseCount(savedItemInUseCount);
        }
        savedItemInUse = null;
        savedItemInUseCount = null;
    }
}
