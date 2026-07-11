package gnu.client.module.modules.player.scaffold;

import net.minecraft.util.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * LiquidBounce scaffold {@code AngleSmooth} port — Linear / Sigmoid / Acceleration.
 * Acceleration matches LB {@code AccelerationAngleSmooth.computeTurnSpeed}.
 */
public final class ScaffoldAngleSmooth {
  public static final int LINEAR = 0;
  public static final int SIGMOID = 1;
  public static final int ACCELERATION = 2;

  private float prevDeltaYaw;
  private float prevDeltaPitch;

  public void reset() {
    prevDeltaYaw = 0.0f;
    prevDeltaPitch = 0.0f;
  }

  /** Acceleration subtree options (LB AccelerationAngleSmooth). */
  public static final class AccelOptions {
    public boolean accelerationError = true;
    public float yawAccelError = 0.1f;
    public float pitchAccelError = 0.1f;
    public boolean constantError = true;
    public float yawConstantError = 0.1f;
    public float pitchConstantError = 0.1f;
    public boolean sigmoidDeceleration = false;
    public float sigmoidSteepness = 10.0f;
    public float sigmoidMidpoint = 0.3f;
    public boolean dynamicAccel = false;
    public float coefDistance = -1.393f;
    public float yawCrosshairAccelMin = 17.0f;
    public float yawCrosshairAccelMax = 20.0f;
    public float pitchCrosshairAccelMin = 17.0f;
    public float pitchCrosshairAccelMax = 20.0f;
    /** Scaffold default: no entity crosshair. */
    public boolean crosshair = false;
    public double distance = 0.0;
  }

  /**
   * One tick toward target. Speeds are degrees/tick (LB turn-speed ranges).
   *
   * @return {@code float[]{yaw, pitch}} already GCD-normalized from {@code cur*}
   */
  public float[] step(int mode, float curYaw, float curPitch, float targetYaw, float targetPitch,
                      float hSpeedMin, float hSpeedMax, float vSpeedMin, float vSpeedMax,
                      float sigmoidSteepness, float sigmoidMidpoint,
                      float yawAccelMin, float yawAccelMax, float pitchAccelMin, float pitchAccelMax) {
    return step(mode, curYaw, curPitch, targetYaw, targetPitch,
        hSpeedMin, hSpeedMax, vSpeedMin, vSpeedMax,
        sigmoidSteepness, sigmoidMidpoint,
        yawAccelMin, yawAccelMax, pitchAccelMin, pitchAccelMax, null);
  }

  public float[] step(int mode, float curYaw, float curPitch, float targetYaw, float targetPitch,
                      float hSpeedMin, float hSpeedMax, float vSpeedMin, float vSpeedMax,
                      float sigmoidSteepness, float sigmoidMidpoint,
                      float yawAccelMin, float yawAccelMax, float pitchAccelMin, float pitchAccelMax,
                      AccelOptions accel) {
    float hSpeed = randomIn(hSpeedMin, hSpeedMax);
    float vSpeed = randomIn(vSpeedMin, vSpeedMax);
    float nextYaw;
    float nextPitch;
    switch (mode) {
      case SIGMOID: {
        float diffLen = hypotDelta(curYaw, curPitch, targetYaw, targetPitch);
        hSpeed = sigmoidFactor(diffLen, hSpeed, sigmoidSteepness, sigmoidMidpoint);
        vSpeed = sigmoidFactor(diffLen, vSpeed, sigmoidSteepness, sigmoidMidpoint);
        float[] stepped = towardsLinear(curYaw, curPitch, targetYaw, targetPitch, hSpeed, vSpeed);
        nextYaw = stepped[0];
        nextPitch = stepped[1];
        break;
      }
      case ACCELERATION: {
        AccelOptions opts = accel != null ? accel : new AccelOptions();
        float[] stepped = accelerationStep(curYaw, curPitch, targetYaw, targetPitch,
            yawAccelMin, yawAccelMax, pitchAccelMin, pitchAccelMax, opts);
        // Cap by turn-speed so horizontal/vertical sliders still limit Acceleration.
        float dYaw = ScaffoldMath.wrapAngle(stepped[0] - curYaw);
        float dPitch = stepped[1] - curPitch;
        dYaw = coerce(dYaw, -hSpeed, hSpeed);
        dPitch = coerce(dPitch, -vSpeed, vSpeed);
        nextYaw = curYaw + dYaw;
        nextPitch = ScaffoldMath.clampPitch(curPitch + dPitch);
        break;
      }
      case LINEAR:
      default: {
        float[] stepped = towardsLinear(curYaw, curPitch, targetYaw, targetPitch, hSpeed, vSpeed);
        nextYaw = stepped[0];
        nextPitch = stepped[1];
        break;
      }
    }
    float dYaw = ScaffoldMath.wrapAngle(nextYaw - curYaw);
    float dPitch = nextPitch - curPitch;
    prevDeltaYaw = dYaw;
    prevDeltaPitch = dPitch;
    return ScaffoldMath.normalizeFrom(curYaw, curPitch, nextYaw, nextPitch);
  }

  /**
   * True only when the turn-speed range is always a full snap (both min and max ≥ 179.5).
   * Checking max alone made default max=180 instant-snap even when min was lowered.
   */
  public static boolean canFullSnap(float hMin, float hMax, float vMin, float vMax) {
    return Math.min(hMin, hMax) >= 179.5f && Math.min(vMin, vMax) >= 179.5f;
  }

  /** @deprecated use {@link #canFullSnap(float, float, float, float)} */
  public static boolean canFullSnap(float hMax, float vMax) {
    return hMax >= 179.5f && vMax >= 179.5f;
  }

  /**
   * LB {@code AccelerationAngleSmooth.computeTurnSpeed} + apply to current look.
   */
  private float[] accelerationStep(float curYaw, float curPitch, float targetYaw, float targetPitch,
                                   float yawAccelMin, float yawAccelMax, float pitchAccelMin,
                                   float pitchAccelMax, AccelOptions opts) {
    float deltaYaw = ScaffoldMath.wrapAngle(targetYaw - curYaw);
    float deltaPitch = targetPitch - curPitch;
    float diffLen = (float) Math.hypot(deltaYaw, deltaPitch);

    float decelerationFactor = 1.0f;
    if (opts.sigmoidDeceleration)
      decelerationFactor = sigmoidDecelerationFactor(diffLen, opts.sigmoidSteepness, opts.sigmoidMidpoint);

    boolean crosshairCheck = opts.dynamicAccel && opts.crosshair;
    float distanceFactor = opts.dynamicAccel
        ? (float) (opts.coefDistance * opts.distance) : 0.0f;

    float aYawMin;
    float aYawMax;
    float aPitchMin;
    float aPitchMax;
    if (crosshairCheck) {
      aYawMin = opts.yawCrosshairAccelMin;
      aYawMax = opts.yawCrosshairAccelMax;
      aPitchMin = opts.pitchCrosshairAccelMin;
      aPitchMax = opts.pitchCrosshairAccelMax;
    } else {
      aYawMin = yawAccelMin;
      aYawMax = yawAccelMax;
      aPitchMin = pitchAccelMin;
      aPitchMax = pitchAccelMax;
    }

    float yawBound = randomIn(aYawMin, aYawMax) + distanceFactor;
    float pitchBound = randomIn(aPitchMin, aPitchMax) + distanceFactor;
    // Second independent sample for the opposite end of the coerce range (LB aYaw.random() twice).
    float yawBoundNeg = -(randomIn(aYawMin, aYawMax)) + distanceFactor;
    float pitchBoundNeg = -(randomIn(aPitchMin, aPitchMax)) + distanceFactor;
    float yawLo = Math.min(yawBoundNeg, yawBound);
    float yawHi = Math.max(yawBoundNeg, yawBound);
    float pitchLo = Math.min(pitchBoundNeg, pitchBound);
    float pitchHi = Math.max(pitchBoundNeg, pitchBound);

    float yawAccel = calculateAcceleration(deltaYaw, prevDeltaYaw, yawLo, yawHi, decelerationFactor);
    float pitchAccel = calculateAcceleration(deltaPitch, prevDeltaPitch, pitchLo, pitchHi, decelerationFactor);

    float yawErr = errorFor(yawAccel, opts.accelerationError, opts.yawAccelError,
        opts.constantError, opts.yawConstantError);
    float pitchErr = errorFor(pitchAccel, opts.accelerationError, opts.pitchAccelError,
        opts.constantError, opts.pitchConstantError);

    float stepYaw = prevDeltaYaw + yawAccel + yawErr;
    float stepPitch = prevDeltaPitch + pitchAccel + pitchErr;
    return new float[] {curYaw + stepYaw, ScaffoldMath.clampPitch(curPitch + stepPitch)};
  }

  /** LB {@code RotationUtil.angleDifference(diff, prev).coerceIn(range) * deceleration}. */
  private static float calculateAcceleration(float diff, float prevDiff,
                                             float rangeLo, float rangeHi,
                                             float decelerationFactor) {
    float angleDiff = ScaffoldMath.wrapAngle(diff - prevDiff);
    return coerce(angleDiff, rangeLo, rangeHi) * decelerationFactor;
  }

  private static float errorFor(float acceleration, boolean accelErrOn, float accelErrMag,
                                boolean constErrOn, float constErrMag) {
    float accelErr = 0.0f;
    float constErr = 0.0f;
    if (accelErrOn && accelErrMag > 0.0f)
      accelErr = randomIn(-accelErrMag, accelErrMag);
    if (constErrOn && constErrMag > 0.0f)
      constErr = randomIn(-constErrMag, constErrMag);
    return acceleration * accelErr + constErr;
  }

  private static float sigmoidDecelerationFactor(float rotationDifference,
                                                 float steepness, float midpoint) {
    float scaled = rotationDifference / 120.0f;
    double sigmoid = 1.0 / (1.0 + Math.exp(-steepness * (scaled - midpoint)));
    return MathHelper.clamp_float((float) sigmoid, 0.0f, 180.0f);
  }

  /** LB {@code Rotation.towardsLinear}. */
  public static float[] towardsLinear(float curYaw, float curPitch, float targetYaw, float targetPitch,
                                      float horizontalFactor, float verticalFactor) {
    float deltaYaw = ScaffoldMath.wrapAngle(targetYaw - curYaw);
    float deltaPitch = targetPitch - curPitch;
    float rotationDifference = (float) Math.hypot(deltaYaw, deltaPitch);
    if (rotationDifference < 1.0e-4f)
      return new float[] {curYaw, curPitch};
    float straightLineYaw = Math.abs(deltaYaw / rotationDifference) * horizontalFactor;
    float straightLinePitch = Math.abs(deltaPitch / rotationDifference) * verticalFactor;
    float addYaw = coerce(deltaYaw, -straightLineYaw, straightLineYaw);
    float addPitch = coerce(deltaPitch, -straightLinePitch, straightLinePitch);
    return new float[] {curYaw + addYaw, ScaffoldMath.clampPitch(curPitch + addPitch)};
  }

  private static float sigmoidFactor(float rotationDifference, float turnSpeed,
                                     float steepness, float midpoint) {
    float scaled = rotationDifference / 120.0f;
    double sigmoid = 1.0 / (1.0 + Math.exp(-steepness * (scaled - midpoint)));
    return MathHelper.clamp_float((float) (sigmoid * turnSpeed), 0.0f, 180.0f);
  }

  private static float hypotDelta(float curYaw, float curPitch, float targetYaw, float targetPitch) {
    float dy = ScaffoldMath.wrapAngle(targetYaw - curYaw);
    float dp = targetPitch - curPitch;
    return MathHelper.clamp_float((float) Math.hypot(dy, dp), 0.0f, 180.0f);
  }

  private static float randomIn(float min, float max) {
    float lo = Math.min(min, max);
    float hi = Math.max(min, max);
    if (hi - lo < 1.0e-4f)
      return lo;
    return (float) ThreadLocalRandom.current().nextDouble(lo, hi);
  }

  private static float coerce(float v, float min, float max) {
    return MathHelper.clamp_float(v, min, max);
  }
}
