package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import gnu.client.anticheat.predict.MovementModel;
import gnu.client.anticheat.predict.PossibilityEngine;
import gnu.client.anticheat.predict.PredictedPlayerState;
import gnu.client.anticheat.predict.PredictionResult;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Grim-inspired possibility prediction for remote players.
 * Scores the minimum error across plausible WASD/jump/hit-slow samples.
 */
public final class PredictionCheck {
    private final Map<String, PredictedPlayerState> states = new HashMap<String, PredictedPlayerState>();
    private final Map<String, CheckBuffer> buffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null || world == null)
            return;
        String name = player.getName();
        CheckBuffer buffer = buffer(name);

        if (CombatContext.isMovementEnvironmentExempt(player)
                || CombatContext.isCreativeOrSpectator(player)
                || data.recentlyTeleported()
                || data.existedTicks < 12
                || player.isOnLadder()) {
            states.remove(name);
            buffer.decay(0.6);
            return;
        }

        // During active knockback window, only soft-score (Grim still predicts KB, but
        // remote S12 timing vs entity lerp is noisy).
        boolean kbWindow = data.velocityPacketTicks >= 0 && data.velocityPacketTicks <= 8;

        PredictedPlayerState prior = states.get(name);
        if (prior == null || !prior.initialized) {
            PredictedPlayerState fresh = new PredictedPlayerState();
            fresh.syncFromObservation(data);
            states.put(name, fresh);
            buffer.decay(0.3);
            return;
        }

        double jump = Math.sqrt(
                (data.x - prior.x) * (data.x - prior.x)
                        + (data.y - prior.y) * (data.y - prior.y)
                        + (data.z - prior.z) * (data.z - prior.z));
        if (jump > MovementModel.TELEPORT_RESET) {
            prior.syncFromObservation(data);
            buffer.decay(0.5);
            return;
        }

        PossibilityEngine.Match match = PossibilityEngine.findBest(prior, player, world, data);
        PredictionResult result = score(match, data, kbWindow);

        if (result.exceeded) {
            double add = 1.05
                    + Math.min(2.2, result.positionError * 2.2)
                    + Math.min(1.2, result.speedError * 2.5);
            if (buffer.flag(add, CheckRules.PRED_BUFFER_THRESHOLD)) {
                context.receiveSignal(name, "Prediction");
                buffer.reset();
            }
        } else {
            buffer.decay(0.45);
        }

        prior.syncFromObservation(data);
    }

    private static PredictionResult score(PossibilityEngine.Match match, PlayerCheckData observed,
                                          boolean kbWindow) {
        double positionError = match.bestPositionError;
        double horizontalError = match.bestHorizontalError;
        double verticalError = match.bestVerticalError;
        double speedError = match.bestSpeedError;

        // Latency + entity interpolation uncertainty (Grim has transactions; we use slack).
        double posTol = CheckRules.PRED_MAX_POSITION_ERROR
                + CheckRules.PRED_LATENCY_SLACK
                + MovementModel.ENTITY_LERP_UNCERTAINTY;
        double vertTol = CheckRules.PRED_MAX_VERTICAL_ERROR + CheckRules.PRED_LATENCY_SLACK;
        double speedTol = CheckRules.PRED_MAX_SPEED_ERROR + CheckRules.PRED_LATENCY_SLACK * 0.4;

        if (kbWindow) {
            posTol += 0.35;
            vertTol += 0.45;
            speedTol += 0.2;
        }
        if (observed.recentlyHurt()) {
            posTol += 0.15;
            speedTol += 0.1;
        }

        boolean exceeded = horizontalError > posTol
                || (verticalError > vertTol && observed.deltaY > 0.12 && !kbWindow)
                || (speedError > speedTol && observed.horizontalDelta > CheckRules.PRED_MAX_SPEED_ERROR * 1.5)
                || observed.horizontalDelta > CheckRules.PRED_HARD_SPEED_CAP;

        return new PredictionResult(positionError, horizontalError, verticalError,
                speedError, 0.0, exceeded);
    }

    public void pruneMissing(java.util.Set<String> aliveNames) {
        Iterator<Map.Entry<String, PredictedPlayerState>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PredictedPlayerState> e = it.next();
            if (!aliveNames.contains(e.getKey())) {
                it.remove();
                buffers.remove(e.getKey());
            }
        }
    }

    private CheckBuffer buffer(String name) {
        CheckBuffer buffer = buffers.get(name);
        if (buffer == null) {
            buffer = new CheckBuffer();
            buffers.put(name, buffer);
        }
        return buffer;
    }

    public void reset() {
        states.clear();
        buffers.clear();
    }
}
