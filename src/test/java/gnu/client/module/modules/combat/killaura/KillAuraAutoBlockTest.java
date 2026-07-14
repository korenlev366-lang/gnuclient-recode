package gnu.client.module.modules.combat.killaura;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillAuraAutoBlockTest {

    @Test
    public void keepsManualBlockOnlyWhenAutoBlockRequirePressIsOff() {
        KillAuraAutoBlock.Context ctx = context(KillAuraAutoBlock.INTERACT, true, false, true);

        assertTrue(KillAuraAutoBlock.shouldKeepBlockingForManualUse(ctx));
    }

    @Test
    public void doesNotKeepManualBlockWhenAutoBlockRequirePressIsOn() {
        KillAuraAutoBlock.Context ctx = context(KillAuraAutoBlock.INTERACT, true, true, true);

        assertFalse(KillAuraAutoBlock.shouldKeepBlockingForManualUse(ctx));
    }

    @Test
    public void doesNotKeepManualBlockWithoutAutoBlockMode() {
        KillAuraAutoBlock.Context ctx = context(KillAuraAutoBlock.NONE, true, false, true);

        assertFalse(KillAuraAutoBlock.shouldKeepBlockingForManualUse(ctx));
    }

    @Test
    public void doesNotKeepManualBlockWithoutSword() {
        KillAuraAutoBlock.Context ctx = context(KillAuraAutoBlock.INTERACT, false, false, true);

        assertFalse(KillAuraAutoBlock.shouldKeepBlockingForManualUse(ctx));
    }

    @Test
    public void doesNotKeepManualBlockWhenUseKeyIsNotDown() {
        KillAuraAutoBlock.Context ctx = context(KillAuraAutoBlock.INTERACT, true, false, false);

        assertFalse(KillAuraAutoBlock.shouldKeepBlockingForManualUse(ctx));
    }

    private static KillAuraAutoBlock.Context context(int mode, boolean canAutoBlock,
                                                     boolean requirePress, boolean manualUseKeyDown) {
        KillAuraAutoBlock.Context ctx = new KillAuraAutoBlock.Context();
        ctx.mode = mode;
        ctx.canAutoBlock = canAutoBlock;
        ctx.requirePress = requirePress;
        ctx.manualUseKeyDown = manualUseKeyDown;
        return ctx;
    }
}
