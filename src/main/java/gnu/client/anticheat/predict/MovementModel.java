package gnu.client.anticheat.predict;

/**
 * 1.8.9 vanilla / Grim-aligned movement constants for ClientAC prediction.
 */
public final class MovementModel {
    private MovementModel() {}

    public enum Phase {
        IDLE,
        WALKING,
        JUMPING,
        FALLING,
        IN_VEHICLE
    }

    /** LivingEntity gravity (1.8). */
    public static final double GRAVITY = 0.08;
    /** Air drag on all axes after move (BlockProperties air drag ≈ 0.98). */
    public static final double AIR_DRAG = 0.98;
    /** Default block slipperiness (stone) * 0.91 → ground friction factor on XZ. */
    public static final float DEFAULT_SLIPPERINESS = 0.6F;
    public static final double JUMP_VELOCITY = 0.42;
    public static final double SPRINT_JUMP_BOOST = 0.2;
    public static final double MAX_FALL_SPEED = 3.92;

    /** 1.8 movement threshold (Grim applyMovementThreshold for ≤1.8). */
    public static final double MOVEMENT_THRESHOLD = 0.005;

    /** Base AI move speed before sprint / potions. */
    public static final float BASE_GROUND_SPEED = 0.1F;
    public static final float SPRINT_MULTIPLIER = 1.3F;
    public static final float AIR_MOVE_FACTOR = 0.02F;
    public static final float SNEAK_INPUT_MULTIPLIER = 0.3F;
    public static final float USING_ITEM_INPUT_MULTIPLIER = 0.2F;
    public static final float INPUT_SCALE = 0.98F;

    public static final double SPEED_POTION_MULTIPLIER = 0.2; // +20% per level on land speed
    public static final double JUMP_POTION_BONUS = 0.1;

    public static final double GROUND_CHECK_EPS = 0.04;
    public static final double TELEPORT_RESET = 8.0;

    /** Interpolation / entity lerp uncertainty (client-side only). */
    public static final double ENTITY_LERP_UNCERTAINTY = 0.03;
}
