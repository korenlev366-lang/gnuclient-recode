package gnu.client.anticheat.predict;

import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.List;

/**
 * Grim-style "best matching possibility" search for remote players.
 * Enumerates inputs and returns the minimum position error vs observation.
 */
public final class PossibilityEngine {
    private PossibilityEngine() {}

    public static final class Match {
        public final double bestPositionError;
        public final double bestHorizontalError;
        public final double bestVerticalError;
        public final double bestSpeedError;
        public final PhysicsSimulation.StepOutcome best;

        public Match(double bestPositionError, double bestHorizontalError, double bestVerticalError,
                     double bestSpeedError, PhysicsSimulation.StepOutcome best) {
            this.bestPositionError = bestPositionError;
            this.bestHorizontalError = bestHorizontalError;
            this.bestVerticalError = bestVerticalError;
            this.bestSpeedError = bestSpeedError;
            this.best = best;
        }
    }

    public static Match findBest(PredictedPlayerState prior, EntityPlayer player, World world,
                                 PlayerCheckData observed) {
        boolean wasOnGround = prior.onGround;
        boolean combat = observed != null && (observed.recentlyHurt() || observed.recentlySwung());
        List<InputPossibilities.Sample> samples = InputPossibilities.enumerate(wasOnGround, combat);

        // Inject knockback start vector if S12 just applied.
        PredictedPlayerState base = prior.copy();
        if (observed != null && observed.velocityPacketTicks == 0 && observed.expectedVelH > 0.0) {
            // Blend KB into start velocity for this tick's possibilities.
            double h = Math.hypot(base.vx, base.vz);
            if (h > 1.0e-4) {
                base.vx = base.vx / h * observed.expectedVelH;
                base.vz = base.vz / h * observed.expectedVelH;
            } else {
                base.vx = observed.expectedVelH;
            }
            base.vy = Math.max(base.vy, observed.expectedVelY);
        }

        double bestPos = Double.MAX_VALUE;
        double bestH = Double.MAX_VALUE;
        double bestV = Double.MAX_VALUE;
        double bestSp = Double.MAX_VALUE;
        PhysicsSimulation.StepOutcome bestOut = null;

        double obsSpeed = observed != null ? observed.horizontalDelta : 0.0;

        for (int i = 0; i < samples.size(); i++) {
            InputPossibilities.Sample sample = samples.get(i);
            PhysicsSimulation.StepOutcome out = PhysicsSimulation.simulateSample(base, player, world, sample);
            if (observed == null)
                continue;

            double dx = observed.x - out.x;
            double dy = observed.y - out.y;
            double dz = observed.z - out.z;
            double pos = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double horiz = Math.hypot(dx, dz);
            double vert = Math.abs(dy);
            double predSpeed = Math.hypot(out.x - prior.x, out.z - prior.z);
            double speedErr = Math.abs(obsSpeed - predSpeed);

            if (pos < bestPos) {
                bestPos = pos;
                bestH = horiz;
                bestV = vert;
                bestSp = speedErr;
                bestOut = out;
            }
        }

        if (bestOut == null) {
            bestOut = PhysicsSimulation.simulateSample(base, player, world,
                    new InputPossibilities.Sample(0, 0, false, false));
            bestPos = 0;
            bestH = 0;
            bestV = 0;
            bestSp = 0;
        }

        return new Match(bestPos, bestH, bestV, bestSp, bestOut);
    }
}
