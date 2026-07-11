package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.ReachModule;
import gnu.client.module.modules.combat.MoreKBModule;
import gnu.client.module.modules.combat.WTapModule;
import gnu.client.module.modules.network.BacktrackModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.Entity;

/** Forge attack notification helper — dispatches attack/swing events to combat modules. */
public final class CombatAttackNotify {

    private static boolean prevPhysicalLmb;

    private CombatAttackNotify() {}

    public static void noteAttack(Object target) {
        if (!Mc.isInGame() || target == null)
            return;

        Module backtrack = ModuleManager.INSTANCE.getModule("Back Track");
        if (backtrack instanceof BacktrackModule && backtrack.isEnabled())
            ((BacktrackModule) backtrack).noteForgeAttack(target);

        Module wTap = ModuleManager.INSTANCE.getModule("W Tap");
        if (wTap instanceof WTapModule && wTap.isEnabled() && target instanceof Entity)
            ((WTapModule) wTap).noteForgeAttack((Entity) target);

        Module moreKb = ModuleManager.INSTANCE.getModule("MoreKB");
        if (moreKb instanceof MoreKBModule && moreKb.isEnabled() && target instanceof Entity)
            ((MoreKBModule) moreKb).noteForgeAttack((Entity) target);
    }

    public static void tickReachOnLmbEdge() {
        if (!Mc.isResolved() || !Mc.isInGame())
            return;
        boolean lmb = Mc.isPhysicalLmbDown();
        if (lmb && !prevPhysicalLmb)
            ReachModule.applyIfEnabled();
        prevPhysicalLmb = lmb;
    }
}
