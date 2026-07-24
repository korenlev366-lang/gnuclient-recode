package gnu.client.anticheat;

/**
 * Single source of truth for ClientAC thresholds (mapped from the validation-rules plan).
 * Every check imports constants from here — no inline magic numbers for policy.
 *
 * <p>Audit catalog:</p>
 * <ul>
 *   <li>Reach — combat swing / hit-correlation distance</li>
 *   <li>KillAura — swing rate + aim error + snap</li>
 *   <li>AutoBlock — attack while blocking / sprint-block / rapid toggle</li>
 *   <li>Noslow — sprint/speed while using slow items (not blocks)</li>
 *   <li>Blink — freeze then catch-up burst</li>
 *   <li>Scaffold — bridge rotation / pitch / edge patterns</li>
 *   <li>Velocity — missing knockback after S12 / hurt</li>
 *   <li>Speed — horizontal delta vs predicted limit</li>
 *   <li>Flight — sustained ascent / hover</li>
 *   <li>Ground — onGround without support</li>
 *   <li>MultiAura — multi-target aim window</li>
 *   <li>Prediction — physics dead-reckoning engine</li>
 *   <li>Lagrange — combat outbound freeze→burst lag</li>
 *   <li>Backtrack — inbound rewind hits (far + bad aim at current pos)</li>
 *   <li>LagAbuse — repeated short freeze pulses near players</li>
 * </ul>
 */
public final class CheckRules {
    private CheckRules() {}

    // --- Alert aggregation (AnticheatManager) ---
    public static final int FLAG_WINDOW_SECONDS = 6;
    public static final int ALERT_COOLDOWN_SECONDS = 3;

    // --- Shared geometry / combat ---
    public static final double MAX_COMBAT_RANGE = 6.0;
    public static final double HITBOX_EXPAND_XZ = 0.12;
    public static final double HITBOX_EXPAND_Y = 0.10;
    public static final float AIM_YAW_COMBAT = 22.0F;
    public static final float AIM_PITCH_COMBAT = 26.0F;
    public static final float AIM_YAW_HIT_CORRELATE = 28.0F;
    public static final float AIM_PITCH_HIT_CORRELATE = 32.0F;
    public static final float NON_COMBAT_AXE_PITCH = 50.0F;
    public static final float NON_COMBAT_OTHER_PITCH = 60.0F;

    // --- Reach ---
    public static final double REACH_BASE = 3.1;
    public static final double REACH_MOVE_TOLERANCE_CAP = 0.35;
    public static final double REACH_HURT_BONUS = 0.15;
    public static final double REACH_SWING_BUFFER_THRESHOLD = 4.25;
    public static final double REACH_HIT_BUFFER_THRESHOLD = 3.75;
    public static final int REACH_ALERT_SIGNALS = 2;

    // --- KillAura ---
    public static final double KA_RATE_FLAG = 1.1;
    public static final double KA_AIM_FLAG = 1.25;
    public static final double KA_SNAP_FLAG = 1.15;
    public static final double KA_RATE_NEED = 3.75;
    public static final double KA_AIM_NEED = 2.0;
    public static final double KA_SNAP_NEED = 2.75;
    public static final float KA_AIM_YAW_ERR = 32.0F;
    public static final float KA_AIM_PITCH_ERR = 26.0F;
    public static final float KA_SNAP_YAW_DELTA = 85.0F;
    public static final float KA_SNAP_YAW_ACCEL = 58.0F;
    public static final float KA_SNAP_YAW_ERR_MAX = 9.0F;
    public static final int KA_ALERT_SIGNALS = 2;

    // --- AutoBlock ---
    public static final double AB_ATTACK_THRESHOLD = 3.25;
    public static final double AB_SPRINT_THRESHOLD = 4.25;
    public static final double AB_RAPID_THRESHOLD = 3.0;
    public static final double AB_IMPOSSIBLE_THRESHOLD = 3.5;
    public static final double AB_SPRINT_SPEED = 0.17;
    public static final int AB_ALERT_SIGNALS = 2;

    // --- Noslow ---
    public static final double NS_GROUND_EXPECTED = 0.165;
    public static final double NS_AIR_EXPECTED = 0.265;
    public static final double NS_SPEED_POT_BONUS = 0.04;
    public static final double NS_SPRINT_THRESHOLD = 2.5;
    public static final double NS_SPEED_THRESHOLD = 3.75;
    public static final int NS_MIN_USE_TICKS_SPRINT = 5;
    public static final int NS_MIN_USE_TICKS_SPEED = 6;
    public static final int NS_ALERT_SIGNALS = 2;

    // --- Blink ---
    public static final int BLINK_MIN_FROZEN = 8;
    public static final double BLINK_BURST_MIN = 0.65;
    public static final double BLINK_PULSE_MIN = 1.15;
    public static final double BLINK_BURST_THRESHOLD = 3.0;
    public static final double BLINK_PULSE_THRESHOLD = 3.5;
    public static final int BLINK_ALERT_SIGNALS = 2;

    // --- Scaffold ---
    public static final double SC_SUPPORT_NEED = 5.0;
    public static final double SC_ROTATION_NEED = 2.25;
    public static final double SC_PITCH_NEED = 5.0;
    public static final double SC_EDGE_NEED = 7.5;
    public static final long SC_FLAG_COOLDOWN_MS = 2500L;
    public static final int SC_ALERT_SIGNALS = 2;

    // --- Velocity ---
    public static final double VEL_H_THRESHOLD = 2.75;
    public static final double VEL_V_THRESHOLD = 2.5;
    public static final int VEL_ALERT_SIGNALS = 2;

    // --- Speed ---
    public static final double SPEED_LIMIT_SLACK = 1.12;
    public static final double SPEED_BUFFER_THRESHOLD = 4.5;
    public static final int SPEED_ALERT_SIGNALS = 2;

    // --- Flight ---
    public static final double FLIGHT_MAX_UP = 0.52;
    public static final double FLIGHT_BUFFER_THRESHOLD = 4.25;
    public static final int FLIGHT_MIN_ASCEND = 8;
    public static final int FLIGHT_ALERT_SIGNALS = 2;

    // --- Ground ---
    public static final double GROUND_BUFFER_THRESHOLD = 3.75;
    public static final int GROUND_ALERT_SIGNALS = 2;

    // --- MultiAura ---
    public static final int MA_MIN_TARGETS = 3;
    public static final double MA_BUFFER_THRESHOLD = 2.75;
    public static final float MA_AIM_YAW = 30.0F;
    public static final int MA_ALERT_SIGNALS = 2;

    // --- Prediction engine (Grim-style possibilities; keep latency slack) ---
    public static final double PRED_MAX_POSITION_ERROR = 0.38;
    public static final double PRED_MAX_VERTICAL_ERROR = 0.48;
    public static final double PRED_MAX_SPEED_ERROR = 0.16;
    public static final double PRED_MAX_ACCEL_ERROR = 0.30;
    public static final double PRED_LATENCY_SLACK = 0.22;
    public static final double PRED_HARD_SPEED_CAP = 0.78;
    public static final double PRED_BUFFER_THRESHOLD = 3.75;
    public static final int PRED_ALERT_SIGNALS = 2;

    // --- Lagrange / LagRange (outbound combat lag) ---
    public static final int LAGRANGE_MIN_FROZEN = 3;
    public static final int LAGRANGE_MAX_FROZEN = 12;
    public static final double LAGRANGE_BURST_MIN = 0.40;
    public static final double LAGRANGE_BUFFER_THRESHOLD = 3.5;
    public static final int LAGRANGE_PULSE_COUNT = 3;
    public static final long LAGRANGE_PULSE_WINDOW_TICKS = 40L;
    public static final int LAGRANGE_ALERT_SIGNALS = 2;

    // --- Backtrack (inbound target lag) ---
    public static final double BACKTRACK_MIN_HIT_DISTANCE = 3.35;
    public static final float BACKTRACK_MIN_AIM_ERROR = 28.0F;
    public static final double BACKTRACK_BUFFER_THRESHOLD = 3.25;
    public static final int BACKTRACK_ALERT_SIGNALS = 2;

    // --- General lag abuse ---
    public static final int LAGABUSE_MIN_FREEZE = 2;
    public static final int LAGABUSE_MAX_FREEZE = 10;
    public static final double LAGABUSE_BURST_MIN = 0.35;
    public static final int LAGABUSE_EVENT_COUNT = 4;
    public static final long LAGABUSE_WINDOW_TICKS = 60L;
    public static final double LAGABUSE_BUFFER_THRESHOLD = 3.5;
    public static final int LAGABUSE_ALERT_SIGNALS = 2;

    public static int alertSignalsFor(String cheatName) {
        if (cheatName == null)
            return 2;
        switch (cheatName) {
            case "Reach":
                return REACH_ALERT_SIGNALS;
            case "KillAura":
                return KA_ALERT_SIGNALS;
            case "AutoBlock":
                return AB_ALERT_SIGNALS;
            case "Noslow":
                return NS_ALERT_SIGNALS;
            case "Blink":
                return BLINK_ALERT_SIGNALS;
            case "Scaffold":
                return SC_ALERT_SIGNALS;
            case "Velocity":
                return VEL_ALERT_SIGNALS;
            case "Speed":
                return SPEED_ALERT_SIGNALS;
            case "Flight":
                return FLIGHT_ALERT_SIGNALS;
            case "Ground":
                return GROUND_ALERT_SIGNALS;
            case "MultiAura":
                return MA_ALERT_SIGNALS;
            case "Prediction":
                return PRED_ALERT_SIGNALS;
            case "Lagrange":
                return LAGRANGE_ALERT_SIGNALS;
            case "Backtrack":
                return BACKTRACK_ALERT_SIGNALS;
            case "LagAbuse":
                return LAGABUSE_ALERT_SIGNALS;
            default:
                return 2;
        }
    }
}
