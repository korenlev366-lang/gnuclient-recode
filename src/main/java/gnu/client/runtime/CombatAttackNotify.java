package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.AutoBlockModule;
import gnu.client.module.modules.combat.ReachModule;
import gnu.client.module.modules.combat.WTapModule;
import gnu.client.module.modules.network.BacktrackModule;
import gnu.client.runtime.mc.McAccess;

/** Forge attack notification helper — dispatches attack/swing events to combat modules. */
public final class CombatAttackNotify {

    private static boolean prevPhysicalLmb;

    private CombatAttackNotify() {}

    public static void noteAttack(Object target) {
        if (!McAccess.isInGame() || target == null)
            return;

        Module backtrack = ModuleManager.INSTANCE.getModule("Back Track");
        if (backtrack instanceof BacktrackModule && backtrack.isEnabled())
            ((BacktrackModule) backtrack).noteForgeAttack(target);

        Module wTap = ModuleManager.INSTANCE.getModule("W Tap");
        if (wTap instanceof WTapModule && wTap.isEnabled())
            ((WTapModule) wTap).noteForgeAttack(target);

        Module autoBlock = ModuleManager.INSTANCE.getModule("Auto Block");
        if (autoBlock instanceof AutoBlockModule && autoBlock.isEnabled())
            ((AutoBlockModule) autoBlock).noteAttack(target);
    }

    public static void tickReachOnLmbEdge() {
        if (!McAccess.isResolved() || !McAccess.isInGame())
            return;
        boolean lmb = McAccess.isPhysicalLmbDown();
        if (lmb && !prevPhysicalLmb)
            ReachModule.applyIfEnabled();
        prevPhysicalLmb = lmb;
    }
}
