package gnu.client.anticheat.predict;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.List;

/**
 * Grim-inspired physics step: start velocity → optional jump/attack-slow →
 * input (moveFlying) → collide → end-of-tick gravity/friction.
 */
public final class PhysicsSimulation {
    private PhysicsSimulation() {}

    public static final class StepOutcome {
        public final double x;
        public final double y;
        public final double z;
        public final double vx;
        public final double vy;
        public final double vz;
        public final boolean onGround;

        public StepOutcome(double x, double y, double z, double vx, double vy, double vz, boolean onGround) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.onGround = onGround;
        }
    }

    /**
     * Simulate one tick for a single Grim-style input sample.
     */
    public static StepOutcome simulateSample(PredictedPlayerState start, EntityPlayer player, World world,
                                             InputPossibilities.Sample sample) {
        double vx = start.vx;
        double vy = start.vy;
        double vz = start.vz;
        boolean onGround = start.onGround;

        if (sample.attackSlow) {
            vx *= 0.6;
            vz *= 0.6;
        }

        // 1.8 movement threshold
        if (Math.abs(vx) < MovementModel.MOVEMENT_THRESHOLD)
            vx = 0.0;
        if (Math.abs(vy) < MovementModel.MOVEMENT_THRESHOLD)
            vy = 0.0;
        if (Math.abs(vz) < MovementModel.MOVEMENT_THRESHOLD)
            vz = 0.0;

        if (sample.jump && onGround) {
            vy = InputPossibilities.jumpVelocity(player);
            if (player != null && player.isSprinting()) {
                float yawRad = startYaw(player, start) * (float) Math.PI / 180.0F;
                vx -= MathHelper.sin(yawRad) * MovementModel.SPRINT_JUMP_BOOST;
                vz += MathHelper.cos(yawRad) * MovementModel.SPRINT_JUMP_BOOST;
            }
            onGround = false;
        }

        float yaw = startYaw(player, start);
        double[] add = InputPossibilities.movementFromInput(player, sample.forward, sample.strafe, onGround, yaw);
        vx += add[0];
        vz += add[1];

        PredictedPlayerState moving = start.copy();
        moving.vx = vx;
        moving.vy = vy;
        moving.vz = vz;
        moving.onGround = onGround;

        if (world != null && player != null)
            moveWithCollision(moving, player, world);
        else {
            moving.x += moving.vx;
            moving.y += moving.vy;
            moving.z += moving.vz;
        }

        // End of tick (Grim PredictionEngineNormal.staticVectorEndOfTick-ish)
        endOfTick(moving, player);

        return new StepOutcome(moving.x, moving.y, moving.z, moving.vx, moving.vy, moving.vz, moving.onGround);
    }

    /**
     * Legacy single-path step used by unit tests (no input = coast + gravity).
     */
    public static void simulateTick(PredictedPlayerState state, EntityPlayer player, World world) {
        if (state == null || !state.initialized)
            return;
        InputPossibilities.Sample idle = new InputPossibilities.Sample(0.0F, 0.0F, false, false);
        StepOutcome out = simulateSample(state, player, world, idle);
        state.x = out.x;
        state.y = out.y;
        state.z = out.z;
        state.vx = out.vx;
        state.vy = out.vy;
        state.vz = out.vz;
        state.onGround = out.onGround;
        if (state.onGround)
            state.phase = Math.hypot(state.vx, state.vz) > 0.01
                    ? MovementModel.Phase.WALKING : MovementModel.Phase.IDLE;
        else if (state.vy > 0.05)
            state.phase = MovementModel.Phase.JUMPING;
        else
            state.phase = MovementModel.Phase.FALLING;
    }

    public static void simulateTickAir(PredictedPlayerState state) {
        simulateTick(state, null, null);
    }

    private static float startYaw(EntityPlayer player, PredictedPlayerState start) {
        if (player != null)
            return player.rotationYawHead;
        return 0.0F;
    }

    private static void endOfTick(PredictedPlayerState state, EntityPlayer player) {
        boolean inLiquid = player != null && (player.isInWater() || player.isInLava());
        if (!inLiquid) {
            state.vy -= MovementModel.GRAVITY;
            if (state.vy < -MovementModel.MAX_FALL_SPEED)
                state.vy = -MovementModel.MAX_FALL_SPEED;
        } else {
            state.vy *= 0.8;
            state.vy -= MovementModel.GRAVITY * 0.25;
        }

        // Ground friction factor = slipperiness * 0.91; air uses 0.91 on XZ after move in some paths.
        // Grim applies player.friction on XZ and air drag on Y.
        double friction = state.onGround
                ? (MovementModel.DEFAULT_SLIPPERINESS * 0.91)
                : 0.91;
        state.vx *= friction;
        state.vz *= friction;
        state.vy *= MovementModel.AIR_DRAG;

        if (state.onGround && state.vy < 0.0)
            state.vy = 0.0;
    }

    @SuppressWarnings("unchecked")
    private static void moveWithCollision(PredictedPlayerState state, EntityPlayer player, World world) {
        double dx = state.vx;
        double dy = state.vy;
        double dz = state.vz;

        AxisAlignedBB box = player.getEntityBoundingBox().offset(
                state.x - player.posX,
                state.y - player.posY,
                state.z - player.posZ);

        if (dy != 0.0) {
            List<AxisAlignedBB> list = world.getCollidingBoundingBoxes(player, box.addCoord(0.0, dy, 0.0));
            for (int i = 0; i < list.size(); i++)
                dy = list.get(i).calculateYOffset(box, dy);
            box = box.offset(0.0, dy, 0.0);
        }
        if (dx != 0.0) {
            List<AxisAlignedBB> list = world.getCollidingBoundingBoxes(player, box.addCoord(dx, 0.0, 0.0));
            for (int i = 0; i < list.size(); i++)
                dx = list.get(i).calculateXOffset(box, dx);
            box = box.offset(dx, 0.0, 0.0);
        }
        if (dz != 0.0) {
            List<AxisAlignedBB> list = world.getCollidingBoundingBoxes(player, box.addCoord(0.0, 0.0, dz));
            for (int i = 0; i < list.size(); i++)
                dz = list.get(i).calculateZOffset(box, dz);
            box = box.offset(0.0, 0.0, dz);
        }

        state.x += dx;
        state.y += dy;
        state.z += dz;
        // Keep pre-collision intent for next-tick friction basis (Grim separates these);
        // for remote AC we store post-collision travel as velocity estimate.
        state.vx = dx;
        state.vy = dy;
        state.vz = dz;

        AxisAlignedBB below = box.offset(0.0, -MovementModel.GROUND_CHECK_EPS, 0.0);
        state.onGround = !world.getCollidingBoundingBoxes(player, below).isEmpty() && state.vy <= 0.08;
    }
}
