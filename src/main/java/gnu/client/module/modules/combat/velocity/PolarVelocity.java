package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;

/**
 * wsamiaw {@code POLAR}: while {@code hurtTime != 0}, attack hit-slow uses {@code 0.59928}
 * instead of vanilla {@code 0.6} (see {@code MixinEntityPlayerHitSlow}).
 */
public final class PolarVelocity extends VelocityMode {

    public static final double POLAR_HIT_SLOW = 0.59928D;

    public PolarVelocity(VelocityModule parent) {
        super("Polar", parent);
    }

    /** Polar + ReduceJump both use the {@code 0.59928} hit-slow constant while hurt. */
    public static boolean usesPolarHitSlow(VelocityModule velocity) {
        if (velocity == null || !velocity.isEnabled())
            return false;
        String mode = velocity.mode.getCurrentMode();
        return "Polar".equals(mode) || "ReduceJump".equals(mode);
    }
}
