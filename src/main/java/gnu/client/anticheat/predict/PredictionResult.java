package gnu.client.anticheat.predict;

/** Deviation metrics between predicted and observed movement for one tick. */
public final class PredictionResult {
    public final double positionError;
    public final double horizontalError;
    public final double verticalError;
    public final double speedError;
    public final double accelerationError;
    public final boolean exceeded;

    public PredictionResult(double positionError, double horizontalError, double verticalError,
                            double speedError, double accelerationError, boolean exceeded) {
        this.positionError = positionError;
        this.horizontalError = horizontalError;
        this.verticalError = verticalError;
        this.speedError = speedError;
        this.accelerationError = accelerationError;
        this.exceeded = exceeded;
    }
}
