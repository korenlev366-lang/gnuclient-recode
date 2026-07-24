package gnu.client.anticheat.predict;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Grim-inspired input possibility expansion for remote players
 * (we don't see WASD — enumerate plausible forward/strafe/jump/slow sets).
 */
public final class InputPossibilities {
    private InputPossibilities() {}

    public static final class Sample {
        public final float forward;
        public final float strafe;
        public final boolean jump;
        public final boolean attackSlow;

        public Sample(float forward, float strafe, boolean jump, boolean attackSlow) {
            this.forward = forward;
            this.strafe = strafe;
            this.jump = jump;
            this.attackSlow = attackSlow;
        }
    }

    /**
     * Compact set: 9 move dirs × jump? × attackSlow? — capped for perf.
     * Idle + cardinals + diagonals; jump only when previously on ground.
     */
    public static List<Sample> enumerate(boolean wasOnGround, boolean recentlyHurt) {
        List<Sample> out = new ArrayList<Sample>(40);
        float[] axes = new float[] { -1.0F, 0.0F, 1.0F };
        for (int fi = 0; fi < axes.length; fi++) {
            for (int si = 0; si < axes.length; si++) {
                float f = axes[fi];
                float s = axes[si];
                // skip both-zero duplicates later handled once
                addVariants(out, f, s, wasOnGround, recentlyHurt);
            }
        }
        return out;
    }

    private static void addVariants(List<Sample> out, float forward, float strafe,
                                    boolean wasOnGround, boolean recentlyHurt) {
        out.add(new Sample(forward, strafe, false, false));
        if (wasOnGround)
            out.add(new Sample(forward, strafe, true, false));
        // Hit-slow possibility when recently in combat (Grim 0.6 xz).
        if (recentlyHurt) {
            out.add(new Sample(forward, strafe, false, true));
            if (wasOnGround)
                out.add(new Sample(forward, strafe, true, true));
        }
    }

    /**
     * Vanilla/Grim FloatInputTransformer-style moveFlying addition for 1.8.
     *
     * @return {addX, addZ} to apply onto motion before collide
     */
    public static double[] movementFromInput(EntityPlayer player, float forward, float strafe,
                                            boolean onGround, float yaw) {
        float bestX = strafe;
        float bestZ = forward;

        if (player != null && player.isSneaking()) {
            bestX *= MovementModel.SNEAK_INPUT_MULTIPLIER;
            bestZ *= MovementModel.SNEAK_INPUT_MULTIPLIER;
        }
        if (player != null && (player.isUsingItem() || player.isBlocking() || player.isEating())) {
            bestX *= MovementModel.USING_ITEM_INPUT_MULTIPLIER;
            bestZ *= MovementModel.USING_ITEM_INPUT_MULTIPLIER;
        }

        bestX *= MovementModel.INPUT_SCALE;
        bestZ *= MovementModel.INPUT_SCALE;

        float speed = moveFactor(player, onGround);
        float lengthSq = bestX * bestX + bestZ * bestZ;
        if (lengthSq < 1.0E-4F)
            return new double[] { 0.0, 0.0 };

        float length = MathHelper.sqrt_float(lengthSq);
        if (length < 1.0F)
            length = 1.0F;
        length = speed / length;
        bestX *= length;
        bestZ *= length;

        float yawRad = yaw * (float) Math.PI / 180.0F;
        float sin = MathHelper.sin(yawRad);
        float cos = MathHelper.cos(yawRad);
        // Grim FloatInputTransformer: sideways*cos - forward*sin, forward*cos + sideways*sin
        double addX = bestX * cos - bestZ * sin;
        double addZ = bestZ * cos + bestX * sin;
        return new double[] { addX, addZ };
    }

    /** landMovementFactor / jumpMovementFactor style speed for moveFlying. */
    public static float moveFactor(EntityPlayer player, boolean onGround) {
        if (!onGround)
            return MovementModel.AIR_MOVE_FACTOR;

        float speed = MovementModel.BASE_GROUND_SPEED;
        if (player != null && player.isSprinting())
            speed *= MovementModel.SPRINT_MULTIPLIER;
        if (player != null && player.isPotionActive(Potion.moveSpeed)) {
            int amp = player.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1;
            speed *= (1.0F + (float) (amp * MovementModel.SPEED_POTION_MULTIPLIER));
        }
        if (player != null && player.isPotionActive(Potion.moveSlowdown)) {
            int amp = player.getActivePotionEffect(Potion.moveSlowdown).getAmplifier() + 1;
            speed *= Math.max(0.1F, 1.0F - 0.15F * amp);
        }

        // Vanilla: friction = speed * (0.16277136 / (slip*0.91)^3)
        float slip = MovementModel.DEFAULT_SLIPPERINESS;
        float f4 = slip * 0.91F;
        float f = 0.16277136F / (f4 * f4 * f4);
        return speed * f;
    }

    public static double jumpVelocity(EntityPlayer player) {
        double jump = MovementModel.JUMP_VELOCITY;
        if (player != null && player.isPotionActive(Potion.jump)) {
            int amp = player.getActivePotionEffect(Potion.jump).getAmplifier() + 1;
            jump += amp * MovementModel.JUMP_POTION_BONUS;
        }
        return jump;
    }
}
