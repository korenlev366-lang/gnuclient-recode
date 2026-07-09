package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.module.modules.network.LagrangeModule;
import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.AuraCombatPacketGuard;
import gnu.client.runtime.CombatAttackNotify;
import gnu.client.runtime.PlayerUpdateHook;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.mc.McAccess;
import net.minecraft.entity.EntityLivingBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OpenMyau-Plus {@code KillAura} skid, ported onto the GNUClient Forge hook
 * system (silent rotations via {@link PlayerUpdateHook} / {@link RotationState},
 * movefix via {@link MoveFixUtil}, attack packets via {@link McAccess#attackEntity}).
 *
 * <p>Target selection mirrors OpenMyau's {@code onTick} PRE block: gather
 * valid players in range, sort by the configured {@link #sort} mode, then
 * apply Single/Switch persistence with a switch-delay timer. Rotation prep
 * and the actual attack both run once per client tick from
 * {@code onPreUpdate} / {@code beforeWalkingAttack} (see
 * {@link PlayerUpdateHook}), matching OpenMyau's {@code UpdateEvent.PRE}
 * cadence closely enough for parity given this repo's coarser hook granularity.
 *
 * <p>Rotation modes:
 * <ul>
 *   <li><b>NONE</b> — no aiming; attacks whatever is already in range.</li>
 *   <li><b>Legit</b> — smoothly turns the real camera (visible) toward the target.</li>
 *   <li><b>Silent</b> — OpenMyau-style: camera stays put, only the C03 packet
 *       yaw/pitch (and optionally movement input) are spoofed.</li>
 *   <li><b>LockView</b> — instantly snaps the real camera to the raw target
 *       rotation every tick (no smoothing).</li>
 * </ul>
 * OpenMyau's LiquidBounce rotation mode and Blink-dependent auto-block modes
 * (VANILLA/SPOOF/HYPIXEL/BLINK/INTERACT/SWAP/LEGIT/FAKE) are intentionally
 * not ported — see class-level constraints in the porting task. Auto-block
 * here is a thin defer-to-{@link AutoBlockModule} toggle only.
 */
public final class KillAuraModule extends Module {

    // ── Rotation modes ──────────────────────────────────────────────────
    private static final int ROT_NONE = 0;
    private static final int ROT_LEGIT = 1;
    private static final int ROT_SILENT = 2;
    private static final int ROT_LOCKVIEW = 3;

    // ── Move-fix modes ──────────────────────────────────────────────────
    private static final int MOVEFIX_NONE = 0;
    private static final int MOVEFIX_SILENT = 1;
    private static final int MOVEFIX_STRICT = 2;

    // ── Target modes ─────────────────────────────────────────────────────
    private static final int MODE_SINGLE = 0;
    private static final int MODE_SWITCH = 1;

    // ── Sort modes ───────────────────────────────────────────────────────
    private static final int SORT_DISTANCE = 0;
    private static final int SORT_HEALTH = 1;
    private static final int SORT_HURTTIME = 2;
    private static final int SORT_FOV = 3;

    // ── CPS modes ────────────────────────────────────────────────────────
    private static final int CPS_NORMAL = 0;
    private static final int CPS_RECORD = 1;

    // ── Auto-block modes ─────────────────────────────────────────────────
    private static final int AUTOBLOCK_NONE = 0;
    private static final int AUTOBLOCK_DEFER = 1;

    private static final int ROTATION_PRIORITY = MoveFixUtil.KILLAURA_MOVE_FIX_PRIORITY;
    private static final double MULTIPOINT_OFFSET = 50.0;

    /** OpenMyau human-click delay pattern (ms), cycled in CPS Mode = Record. */
    private static final int[] CLICK_PATTERN = {16, 22, 14, 46, 18, 8, 8, 63, 25, 25, 12, 39, 26, 18, 6, 62, 26, 18, 21, 40, 26, 8, 16, 46, 26, 20, 15, 50, 25, 10, 11, 43, 25, 11, 37, 39, 25, 12, 18, 54, 25, 25, 15, 41, 27, 9, 1, 66, 26, 17, 21, 48, 27, 8, 6, 62, 28, 19, 13, 47, 26, 7, 14, 53, 27, 16, 29, 38, 27, 8, 6, 60, 27, 22, 19, 45, 26, 10, 10, 62, 25, 20, 28, 22, 26, 19, 11, 57, 26, 16, 32, 36, 26, 9, 9, 66, 27, 19, 27, 38, 26, 9, 10, 61, 26, 25, 15, 34, 26, 20, 10, 52, 26, 22, 28, 29, 27, 8, 3, 63, 26, 21, 27, 38, 26, 10, 11, 38, 27, 15, 31, 39, 25, 13, 10, 45, 27, 14, 27, 40, 26, 10, 6, 51, 26, 18, 31, 27, 27, 11, 14, 47, 26, 23, 21, 35, 26, 12, 13, 41, 26, 15, 31, 36, 27, 16, 9, 44, 27, 14, 30, 39, 25, 14, 10, 46, 28, 10, 24, 45, 26, 7, 5, 46, 26, 20, 6, 50, 26, 8, 6, 51, 26, 17, 20, 40, 27, 25, 1, 32, 26, 20, 9, 46, 25, 15, 12, 30, 26, 11, 25, 46, 27, 13, 10, 36, 27, 20, 15, 41, 26, 8, 6, 41, 26, 12, 29, 44, 26, 13, 11, 44, 26, 12, 27, 36, 26, 23, 4, 39, 26, 24, 12, 47, 26, 9, 2, 65, 26, 16, 27, 34, 26, 25, 0, 53, 26, 16, 3, 47, 27, 16, 10, 41, 26, 18, 25, 38, 26, 11, 10, 50, 27, 20, 20, 29, 26, 11, 7, 66, 26, 20, 18, 31, 26, 21, 21, 28, 26, 21, 29, 25, 27, 15, 12, 43, 28, 11, 31, 32, 27, 23, 0, 49, 27, 20, 30, 30, 25, 32, 0, 50, 26, 12, 25, 34, 27, 11, 11, 44, 27, 23, 26, 25, 27, 16, 11, 46, 26, 13, 32, 35, 28, 9, 5, 48, 26, 21, 29, 37, 26, 10, 7, 48, 27, 20, 21, 41, 24, 7, 18, 46, 25, 22, 22, 33, 25, 10, 5, 59, 26, 21, 19, 29, 26, 11, 10, 46, 25, 22, 29, 31, 25, 11, 12, 50, 24, 20, 28, 40, 25, 10, 4, 56, 25, 16, 36, 30, 24, 10, 9, 63, 25, 22, 22, 32, 25, 9, 8, 58, 27, 10, 43, 30, 26, 8, 3, 60, 26, 24, 14, 42, 26, 12, 9, 49, 25, 11, 32, 38, 27, 8, 8, 50, 26, 20, 26, 32, 25, 10, 4, 66, 25, 18, 28, 24, 26, 10, 8, 54, 25, 16, 32, 34, 24, 9, 12, 54, 25, 18, 18, 41, 28, 9, 16, 50, 28, 15, 21, 46, 27, 9, 8, 49, 26, 21, 18, 36, 26, 15, 10, 54, 27, 22, 27, 32, 25, 9, 15, 48, 28, 19, 26, 35, 27, 9, 13, 48, 25, 21, 23, 33, 27, 8, 3, 65, 26, 19, 23, 39, 25, 9, 13, 44, 26, 25, 19, 35, 26, 14, 6, 63, 27, 15, 23, 32, 28, 8, 2, 65, 26, 19, 24, 34, 27, 12, 0, 49, 26, 21, 34, 34, 26, 8, 9, 60, 26, 23, 19, 34, 26, 10, 5, 59, 26, 12, 36, 39, 26, 11, 11, 44, 26, 25, 5, 47, 25, 9, 10, 49, 27, 19, 24, 31, 26, 10, 4, 60, 27, 25, 9, 41, 26, 20, 7, 54, 24, 11, 35, 35, 26, 9, 5, 67, 26, 17, 19, 43, 26, 24, 17, 39, 25, 16, 11, 45, 25, 9, 3, 60, 25, 25, 16, 37, 28, 9, 5, 55, 26, 15, 12, 49, 25, 17, 8, 39, 25, 15, 16, 48, 25, 12, 9, 37, 25, 17, 31, 38, 27, 8, 8, 62, 26, 23, 14, 38, 27, 16, 10, 45, 26, 13, 25, 42, 25, 9, 8, 57, 27, 12, 36, 38, 27, 13, 11, 30, 27, 21, 24, 47, 25, 10, 6, 54, 26, 13, 28, 42, 25, 10, 5, 47, 26, 21, 22, 44, 26, 10, 8, 50, 28, 17, 26, 33, 26, 10, 14, 55, 27, 14, 30, 29, 25, 13, 1, 70, 26, 14, 30, 26, 27, 12, 14, 67, 25, 21, 4, 33, 25, 11, 5, 48, 26, 21, 21, 39, 25, 11, 1, 55, 26, 11, 29, 32, 26, 12, 10, 50, 27, 16, 26, 36, 27, 23, 3, 57, 27, 11, 23, 37, 26, 9, 16, 37, 26, 16, 38, 37, 26, 9, 2, 60, 27, 22, 16, 38, 27, 9, 5, 53, 26, 14, 33, 30, 25, 13, 11, 46, 25, 23, 22, 43, 24, 10, 13, 51, 25, 21, 25, 35, 27, 8, 16, 48, 25, 21, 19, 42, 25, 12, 12, 49, 26, 21, 18, 42, 25, 12, 13, 51, 27, 16, 25, 37, 26, 11, 12, 47, 27, 21, 13, 39, 27, 5, 9, 61, 25, 24, 11, 39, 26, 10, 9, 52, 26, 15, 33, 28, 38, 0, 9, 55, 26, 14, 39, 24, 25, 10, 9, 52, 27, 13, 29, 36, 25, 12, 9, 49, 25, 22, 30, 26, 26, 10, 2, 66, 27, 17, 30, 31, 26, 14, 7, 64, 28, 16, 31, 28, 24, 13, 14, 54, 25, 12, 29, 35, 27, 10, 8, 49, 27, 18, 26, 38, 25, 8, 14, 46, 26, 23, 15, 36, 26, 11, 5, 61, 27, 23, 8, 42, 25, 9, 10, 57, 26, 11, 29, 37, 25, 11, 9, 56, 27, 11, 32, 35, 26, 12, 6, 62, 27, 20, 33, 27, 27, 10, 14, 50, 27, 17, 28, 40, 25, 9, 8, 46, 26, 23, 16, 44, 26, 11, 13, 47, 28, 19, 19, 36, 26, 8, 7, 55, 26, 15, 24, 39, 26, 12, 9, 56, 26, 15, 28, 36, 25, 10, 10, 51, 25, 17, 32, 36, 25, 9, 7, 58, 26, 11, 31, 32, 26, 7, 14, 57, 26, 13, 22, 25, 24, 9, 14, 42, 26, 12, 27, 31, 25, 9, 2, 62, 27, 23, 12, 33, 26, 8, 18, 46, 25, 24, 14, 33, 24, 10, 14, 50, 25, 20, 21, 38, 26, 9, 1, 61, 25, 11, 30, 35, 26, 10, 10, 53, 25, 18, 22, 35, 25, 8, 4, 44, 25, 25, 21, 37, 24, 13, 6, 35, 27, 11, 34, 32, 25, 9, 10, 51, 26, 17, 18, 31, 24, 11, 8, 53, 26, 16, 30, 35, 26, 8, 10, 60, 25, 11, 32, 29, 25, 22, 2, 53, 26, 16, 30, 33, 27, 9, 11, 57, 25, 13, 32, 30, 25, 14, 10, 67, 24, 21, 29, 35, 27, 8, 12, 70, 26, 14, 19, 42, 27, 22, 0, 57, 27, 12, 31, 33, 25, 9, 12, 62, 27, 23, 14, 43, 25, 11, 2, 71, 28, 12, 33, 31, 27, 8, 12, 71, 26, 15, 23, 42, 28, 9, 8, 63, 26, 22, 22, 37, 27, 7, 4, 78, 27, 20, 26, 34, 25, 9, 15, 64, 27, 21, 23, 32, 26, 12, 11, 77, 25, 11, 32, 29, 26, 9, 15, 63, 27, 19, 23, 38, 26, 10, 15, 57, 26, 14, 37, 14, 26, 18, 6, 67, 26, 13, 31, 33, 26, 19, 1, 60, 27, 25, 22, 24, 27, 22, 2, 55, 26, 13, 25, 34, 26, 24, 0, 68, 25, 20, 22, 31, 25, 11, 4, 80, 24, 22, 22, 29, 26, 16, 8, 81, 25, 11, 22, 38, 27, 10, 11, 50, 27, 18, 35, 32, 26, 10, 5, 76, 26, 23, 22, 30, 24, 21, 8, 67, 27, 24, 16, 42, 27, 8, 3};

    // ── Settings ─────────────────────────────────────────────────────────

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", MODE_SWITCH,
        Arrays.asList("Single", "Switch")));
    private final ModeSetting sort = addSetting(new ModeSetting("Sort", SORT_HEALTH,
        Arrays.asList("Distance", "Health", "HurtTime", "FOV")));
    private final ModeSetting rotations = addSetting(new ModeSetting("Rotations", ROT_SILENT,
        Arrays.asList("NONE", "Legit", "Silent", "LockView")));
    private final ModeSetting moveFix = addSetting(new ModeSetting("Move-fix", MOVEFIX_SILENT,
        Arrays.asList("NONE", "Silent", "Strict")));
    private final ModeSetting cpsMode = addSetting(new ModeSetting("CPS Mode", CPS_NORMAL,
        Arrays.asList("Normal", "Record")));
    private final ModeSetting autoBlock = addSetting(new ModeSetting("Auto-block", AUTOBLOCK_NONE,
        Arrays.asList("NONE", "AUTO BLOCK")));

    private final SliderSetting minCps = addSetting(new SliderSetting("MinCPS", 10.0f, 1.0f, 20.0f));
    private final SliderSetting maxCps = addSetting(new SliderSetting("MaxCPS", 14.0f, 1.0f, 20.0f));
    private final SliderSetting attackRange = addSetting(new SliderSetting("AttackRange", 3.2f, 2.0f, 6.0f));
    private final SliderSetting swingRange = addSetting(new SliderSetting("SwingRange", 3.6f, 2.0f, 6.0f));
    private final SliderSetting fov = addSetting(new SliderSetting("FOV", 180.0f, 15.0f, 360.0f));
    private final SliderSetting switchDelayMs = addSetting(new SliderSetting("SwitchDelay", 150.0f, 0.0f, 1000.0f));
    private final SliderSetting horizontalSpeed = addSetting(new SliderSetting("Horizontal speed", 40.0f, 1.0f, 100.0f));
    private final SliderSetting verticalSpeed = addSetting(new SliderSetting("Vertical speed", 40.0f, 0.0f, 100.0f));

    private final BoolSetting multipoint = addSetting(new BoolSetting("Multipoint", false));
    private final BoolSetting teams = addSetting(new BoolSetting("Teams", true));
    private final BoolSetting botCheck = addSetting(new BoolSetting("BotCheck", true));
    private final BoolSetting requirePress = addSetting(new BoolSetting("RequirePress", false));
    private final BoolSetting weaponsOnly = addSetting(new BoolSetting("WeaponsOnly", true));
    private final BoolSetting inventoryCheck = addSetting(new BoolSetting("InventoryCheck", true));

    // ── State ─────────────────────────────────────────────────────────────

    private long lastAttackMs = 0L;
    private Object attackTarget;
    private float lastSentYaw = Float.MIN_VALUE;
    private float lastSentPitch = Float.MIN_VALUE;
    private float pendingSentYaw = Float.MIN_VALUE;
    private float pendingSentPitch = Float.MIN_VALUE;
    /** Blocks sprint restart same tick after aura attack (Grim BadPacketsX). */
    private boolean attackedThisTick;
    /** OpenMyau Switch mode: bump switchTick only after a hit lands, not every timeout. */
    private boolean hitRegisteredSinceSwitch;
    private int switchTick;
    private long lastSwitchMs;
    /** CPS Mode = Record: index into {@link #CLICK_PATTERN}. */
    private int patternIndex;

    public KillAuraModule() {
        super("KillAura", "Attacks players in range (OpenMyau-style)", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        resetRotationState();
        attackTarget = null;
        switchTick = 0;
        hitRegisteredSinceSwitch = false;
        lastSwitchMs = 0L;
        patternIndex = 0;
        AuraCombatPacketGuard.register();
    }

    @Override
    public void onDisable() {
        clearRotationStateIfOwned();
        attackTarget = null;
        resetRotationState();
        attackedThisTick = false;
        AuraCombatPacketGuard.unregister();
    }

    @Override
    public void onTick() {
        // Attacks run in onPreUpdate (OpenMyau UpdateEvent PRE) before living update.
    }

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }

    // ── Static hook API (kept for PlayerUpdateHook / AimAssist / AutoBlock / MovementInputHook) ──

    public static boolean shouldYieldAimAssist() {
        Module module = ModuleManager.instance().getModule("KillAura");
        if (!(module instanceof KillAuraModule) || !module.isEnabled())
            return false;
        KillAuraModule killAura = (KillAuraModule) module;
        return killAura.rotations.getValue() != ROT_NONE && killAura.attackTarget != null;
    }

    public static Object getCurrentTarget() {
        Module module = ModuleManager.instance().getModule("KillAura");
        if (!(module instanceof KillAuraModule) || !module.isEnabled())
            return null;
        return ((KillAuraModule) module).attackTarget;
    }

    public static void onPreUpdate(Object player) {
        Module module = ModuleManager.instance().getModule("KillAura");
        if (module instanceof KillAuraModule && module.isEnabled())
            ((KillAuraModule) module).preUpdate(player);
    }

    public static void onBeforeWalkingPrepare(Object player) {
        Module module = ModuleManager.instance().getModule("KillAura");
        if (module instanceof KillAuraModule && module.isEnabled())
            ((KillAuraModule) module).beforeWalkingPrepare(player);
    }

    public static void onBeforeWalkingAttack(Object player) {
        Module module = ModuleManager.instance().getModule("KillAura");
        if (module instanceof KillAuraModule && module.isEnabled())
            ((KillAuraModule) module).beforeWalkingAttack(player);
    }

    /** After aura attack — block START_SPRINTING until C03 is sent (BadPacketsX). */
    public static boolean shouldSuppressSprintRestart() {
        Module module = ModuleManager.instance().getModule("KillAura");
        if (!(module instanceof KillAuraModule) || !module.isEnabled())
            return false;
        return ((KillAuraModule) module).attackedThisTick;
    }

    /**
     * Hold sprint key off on the aura attack tick so living update / SprintModule cannot
     * re-enable sprint before STOP/C03 (BadPacketsX). Cleared in {@link #onPostAttackTick}.
     */
    public static boolean shouldSuppressSprintKey() {
        return shouldSuppressSprintRestart();
    }

    public static void onPostAttackTick(Object player) {
        Module module = ModuleManager.instance().getModule("KillAura");
        if (module instanceof KillAuraModule && module.isEnabled())
            ((KillAuraModule) module).clearPostAttackGuard();
    }

    public static void onAfterWalking(Object player) {
        Module module = ModuleManager.instance().getModule("KillAura");
        if (module instanceof KillAuraModule && module.isEnabled())
            ((KillAuraModule) module).afterWalking(player);
    }

    /**
     * OpenMyau {@code KillAura.onMove}: {@code moveFix != 0 && Silent && isActived
     * && priority == 1 && isForwardPressed} → {@code MoveUtil.fixStrafe(smoothedYaw)}.
     */
    public static void patchMovementInput(Object movInput) {
        if (movInput == null)
            return;
        Module module = ModuleManager.instance().getModule("KillAura");
        KillAuraModule killAura = module instanceof KillAuraModule && module.isEnabled()
            ? (KillAuraModule) module : null;
        if (killAura == null)
            return;

        if (killAura.rotations.getValue() != ROT_SILENT
            || killAura.moveFix.getValue() == MOVEFIX_NONE
            || !MoveFixUtil.hasMoveFixPriority(ROTATION_PRIORITY)
            || !MoveFixUtil.isForwardPressed())
            return;

        boolean sneak = McAccess.getBool(movInput, "field_78899_d");
        float[] fixed = MoveFixUtil.fixStrafe(
            McAccess.getYaw(), RotationState.getSmoothedYaw(), sneak);
        McAccess.setFloat(movInput, "field_78900_b", fixed[0]);
        McAccess.setFloat(movInput, "field_78902_a", fixed[1]);
    }

    // ── Per-tick flow ─────────────────────────────────────────────────────

    private void preUpdate(Object player) {
        pendingSentYaw = Float.MIN_VALUE;
        pendingSentPitch = Float.MIN_VALUE;

        if (player == null || !canRunCombat()) {
            attackTarget = null;
            clearRotationStateIfOwned();
            return;
        }

        updateTarget(player);
        if (attackTarget == null) {
            clearRotationStateIfOwned();
            return;
        }

        prepareRotation(player);
        // OpenMyau UpdateEvent PRE: attack before onLivingUpdate so AttackSlow sees slowed motion.
        // Still before C03 (Post animation 1.8 safe) — walking packet runs later this tick.
        if (canAttackThisTick(player))
            tryPerformAttack(player);
    }

    private void afterWalking(Object player) {
        // Attack is in preUpdate; post-C03 swings flag Grim Post animation v1.8.
    }

    private void beforeWalkingPrepare(Object player) {
        if (player == null || pendingSentYaw == Float.MIN_VALUE || rotations.getValue() != ROT_SILENT)
            return;
        if (PlayerUpdateHook.hasRotationOverride())
            return;
        PlayerUpdateHook.requestRotation(pendingSentYaw, pendingSentPitch);
    }

    private void beforeWalkingAttack(Object player) {
        // Legacy hook kept for PlayerUpdateHook call sites; attack runs in preUpdate.
    }

    // ── Target selection (OpenMyau onTick PRE: gather → sort → switch) ──

    private void updateTarget(Object player) {
        Object world = McAccess.theWorld();
        if (world == null) {
            attackTarget = null;
            return;
        }

        List<Object> candidates = new ArrayList<>();
        for (Object entity : McAccess.getWorldEntitiesFiltered(world)) {
            if (isValidCandidate(entity, player))
                candidates.add(entity);
        }
        if (candidates.isEmpty()) {
            attackTarget = null;
            return;
        }

        sortCandidates(candidates);

        long now = System.currentTimeMillis();
        boolean needsNewTarget = attackTarget == null
            || !candidates.contains(attackTarget)
            || (now - lastSwitchMs) >= (long) switchDelayMs.getValue().floatValue();
        if (!needsNewTarget)
            return;

        if (mode.getValue() == MODE_SWITCH && hitRegisteredSinceSwitch) {
            switchTick++;
            hitRegisteredSinceSwitch = false;
        }
        if (mode.getValue() == MODE_SINGLE || switchTick >= candidates.size())
            switchTick = 0;

        Object newTarget = candidates.get(switchTick);
        if (newTarget != attackTarget) {
            attackTarget = newTarget;
            lastSwitchMs = now;
        }
    }

    private boolean isValidCandidate(Object entity, Object player) {
        if (entity == null || entity == player)
            return false;
        if (!McAccess.isEntityPlayer(entity))
            return false;
        if (McAccess.isDead(entity) || McAccess.getDeathTime(entity) > 0)
            return false;
        if (teams.getValue() && isSameTeam(entity))
            return false;
        if (botCheck.getValue() && RavenAntiBot.isBot(entity))
            return false;

        double maxRange = swingRange.getValue();
        double distSq = RavenRotationUtils.distanceSqFromEyeToClosestOnAabb(entity);
        if (distSq > maxRange * maxRange)
            return false;

        float fovVal = fov.getValue();
        if (fovVal < 360.0f) {
            float cameraYaw = McAccess.getYaw();
            if (!RavenRotationUtils.isEyeInsideExpandedHitbox(entity)
                && !RavenRotationUtils.inFov(cameraYaw, fovVal, RavenRotationUtils.angleToEntity(entity)))
                return false;
        }
        return true;
    }

    private void sortCandidates(List<Object> candidates) {
        Comparator<Object> comparator;
        switch (sort.getValue()) {
            case SORT_HEALTH:
                comparator = Comparator.comparingDouble(e -> (double) McAccess.getHealth(e));
                break;
            case SORT_HURTTIME:
                comparator = Comparator.comparingInt(McAccess::getHurtTime);
                break;
            case SORT_FOV: {
                float cameraYaw = McAccess.getYaw();
                comparator = Comparator.comparingDouble(e -> fovDifference(cameraYaw, e));
                break;
            }
            case SORT_DISTANCE:
            default:
                comparator = Comparator.comparingDouble(RavenRotationUtils::distanceSqFromEyeToClosestOnAabb);
        }
        comparator = comparator.thenComparingDouble(RavenRotationUtils::distanceSqFromEyeToClosestOnAabb);
        candidates.sort(comparator);
    }

    private static double fovDifference(float cameraYaw, Object entity) {
        return Math.abs(RavenRotationUtils.wrapAngleTo180(cameraYaw - RavenRotationUtils.angleToEntity(entity)));
    }

    private static boolean isSameTeam(Object entity) {
        Object player = McAccess.thePlayer();
        if (player == null || entity == null)
            return false;
        try {
            Object team = McAccess.invoke(entity, "func_96124_cp", new Class<?>[0]);
            if (team == null)
                return false;
            Object ourTeam = McAccess.invoke(player, "func_96124_cp", new Class<?>[0]);
            if (ourTeam == null)
                return false;
            Object teamName = McAccess.invoke(team, "func_96661_b", new Class<?>[0]);
            Object ourName = McAccess.invoke(ourTeam, "func_96661_b", new Class<?>[0]);
            return teamName != null && ourName != null && teamName.equals(ourName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ── Rotation prep ─────────────────────────────────────────────────────

    private void prepareRotation(Object player) {
        int rot = rotations.getValue();
        if (rot == ROT_NONE) {
            clearRotationStateIfOwned();
            return;
        }

        if (rot == ROT_SILENT) {
            prepareSilentRotation(player);
            return;
        }

        // Legit / LockView: write directly to the real (visible) player rotation.
        double mp = multipoint.getValue() ? MULTIPOINT_OFFSET : 0.0;
        float[] rawTarget = RavenRotationUtils.getRawRotationsToTarget(
            attackTarget, mp, mp, false, swingRange.getValue(), true, false);
        if (rawTarget == null)
            return;

        EntityLivingBase entity = (EntityLivingBase) player;
        float newYaw;
        float newPitch;
        if (rot == ROT_LOCKVIEW) {
            newYaw = rawTarget[0];
            newPitch = rawTarget[1];
        } else { // Legit
            int hSpeed = Math.round(horizontalSpeed.getValue());
            int vSpeed = Math.round(verticalSpeed.getValue());
            float[] result = RavenRotationUtils.smoothRotationHv(
                entity.rotationYaw, entity.rotationPitch, rawTarget[0], rawTarget[1], hSpeed, vSpeed, 0.0f);
            newYaw = result[0];
            newPitch = result[1];
        }

        entity.rotationYaw = newYaw;
        entity.rotationPitch = newPitch;
        entity.rotationYawHead = newYaw;
        entity.renderYawOffset = newYaw;
        clearRotationStateIfOwned();
    }

    private void prepareSilentRotation(Object player) {
        if (player == null || attackTarget == null)
            return;

        if (MoveFixUtil.isForwardPressed() && moveFix.getValue() == MOVEFIX_NONE) {
            clearRotationStateIfOwned();
            return;
        }

        float baseYaw = lastSentYaw != Float.MIN_VALUE
            ? lastSentYaw
            : PlayerUpdateHook.lastReportedYaw(player);
        float basePitch = lastSentPitch != Float.MIN_VALUE
            ? lastSentPitch
            : PlayerUpdateHook.lastReportedPitch(player);

        double mp = multipoint.getValue() ? MULTIPOINT_OFFSET : 0.0;
        float[] rawTarget = RavenRotationUtils.getRawRotationsToTarget(
            attackTarget, mp, mp, false, swingRange.getValue(), true, false);
        if (rawTarget == null)
            return;

        int hSpeed = Math.round(horizontalSpeed.getValue());
        int vSpeed = Math.round(verticalSpeed.getValue());
        float[] result = RavenRotationUtils.smoothRotationHv(
            baseYaw, basePitch, rawTarget[0], rawTarget[1], hSpeed, vSpeed, 0.0f);

        pendingSentYaw = result[0];
        pendingSentPitch = result[1];
        sendSilentRotation(pendingSentYaw, pendingSentPitch);
    }

    private void sendSilentRotation(float sentYaw, float sentPitch) {
        PlayerUpdateHook.requestRotation(sentYaw, sentPitch);
        lastSentYaw = sentYaw;
        lastSentPitch = sentPitch;
        // OpenMyau setPervRotation only when MoveFix != NONE. Without it, smoothYaw must
        // not be the silent yaw or moveFlying (if ever gated on isActived alone) desyncs.
        if (moveFix.getValue() != MOVEFIX_NONE) {
            RotationState.applyState(true, sentYaw, sentPitch, sentYaw, ROTATION_PRIORITY);
        } else {
            clearRotationStateIfOwned();
        }
    }

    // ── Attack ────────────────────────────────────────────────────────────

    private boolean shouldSkipAttackForAutoBlockLag() {
        if (autoBlock.getValue() != AUTOBLOCK_DEFER)
            return false;
        Module module = ModuleManager.INSTANCE.getModule("Auto Block");
        if (!(module instanceof AutoBlockModule) || !module.isEnabled())
            return false;
        AutoBlockModule block = (AutoBlockModule) module;
        return block.isLagging() && block.isHoldThroughLag();
    }

    /** Steady sword block — attack only in the lag blink, not while holding block. */
    private boolean shouldSkipAttackForSteadyAutoBlock() {
        if (autoBlock.getValue() != AUTOBLOCK_DEFER)
            return false;
        Module module = ModuleManager.INSTANCE.getModule("Auto Block");
        if (!(module instanceof AutoBlockModule) || !module.isEnabled())
            return false;
        AutoBlockModule block = (AutoBlockModule) module;
        return block.isActive() && !block.isLagging();
    }

    private boolean shouldSkipAttackForItemUse(Object player) {
        if (player == null)
            return true;
        if (McAccess.isUsingItem(player) || McAccess.isBlocking(player))
            return true;
        // Manual sword block: RMB held — isUsingItem can lag a tick behind key state.
        if (McAccess.isUseItemKeyDown() && McAccess.isHoldingSword())
            return true;
        return false;
    }

    private boolean canAttackThisTick(Object player) {
        if (player == null || attackTarget == null || !canRunCombat())
            return false;
        if (requirePress.getValue() && !McAccess.isPhysicalLmbDown())
            return false;
        if (inventoryCheck.getValue() && McAccess.currentScreen() != null)
            return false;
        if (weaponsOnly.getValue() && !McAccess.isHoldingSword())
            return false;
        if (shouldSkipAttackForItemUse(player))
            return false;
        if (shouldSkipAttackForSteadyAutoBlock())
            return false;
        if (shouldSkipAttackForAutoBlockLag())
            return false;

        long now = System.currentTimeMillis();
        if (now - lastAttackMs < getAttackDelayMs())
            return false;

        double distSq = RavenRotationUtils.distanceSqFromEyeToClosestOnAabb(attackTarget);
        double range = attackRange.getValue();
        if (distSq > range * range)
            return false;

        if (HitSelectModule.shouldBlockClick())
            return false;
        return McAccess.playerController() != null;
    }

    private long getAttackDelayMs() {
        if (cpsMode.getValue() == CPS_RECORD) {
            if (patternIndex < 0 || patternIndex >= CLICK_PATTERN.length)
                patternIndex = 0;
            return CLICK_PATTERN[patternIndex];
        }
        int min = Math.max(1, Math.round(Math.min(minCps.getValue(), maxCps.getValue())));
        int max = Math.max(min, Math.round(Math.max(minCps.getValue(), maxCps.getValue())));
        int cps = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        return 1000L / cps;
    }

    private void tryPerformAttack(Object player) {
        if (!canAttackThisTick(player))
            return;

        notifyPreAttackHooks(attackTarget);

        boolean wasClientSprinting = McAccess.isClientSprinting(player);
        boolean wasServerSprinting = McAccess.getServerSprintState(player);

        float savedYaw = 0f;
        float savedPitch = 0f;
        boolean useSilentAim = rotations.getValue() == ROT_SILENT && pendingSentYaw != Float.MIN_VALUE;
        if (useSilentAim) {
            savedYaw = McAccess.getFloat(player, "field_70177_z");
            savedPitch = McAccess.getFloat(player, "field_70125_A");
            McAccess.setFloat(player, "field_70177_z", pendingSentYaw);
            McAccess.setFloat(player, "field_70125_A", pendingSentPitch);
        }

        boolean swing = !shouldDeferSwingToAutoBlock();
        boolean attacked = McAccess.attackEntity(attackTarget, swing);

        if (useSilentAim) {
            McAccess.setFloat(player, "field_70177_z", savedYaw);
            McAccess.setFloat(player, "field_70125_A", savedPitch);
        }

        if (!attacked)
            return;

        attackedThisTick = true;
        hitRegisteredSinceSwitch = true;
        // Do not force sprint key off here — fights SprintModule and causes BadPacketsX
        // (STOP/START churn) under knockback. AuraCombatPacketGuard + shouldSuppressSprintRestart
        // already block START until after C03; vanilla attack clears client sprint when needed.
        McAccess.reconcileVanillaAttackSlowdown(player, wasClientSprinting, wasServerSprinting);

        lastAttackMs = System.currentTimeMillis();
        AimAssistModule.lastClickMs = lastAttackMs;

        if (cpsMode.getValue() == CPS_RECORD)
            patternIndex = (patternIndex + 1) % CLICK_PATTERN.length;
    }

    private void clearPostAttackGuard() {
        attackedThisTick = false;
    }

    private static void notifyPreAttackHooks(Object target) {
        CombatAttackNotify.noteAttack(target);
        Module lagrange = ModuleManager.INSTANCE.getModule("Lagrange");
        if (lagrange instanceof LagrangeModule && lagrange.isEnabled())
            ((LagrangeModule) lagrange).noteForgeAttack(target);
    }

    private static boolean shouldDeferSwingToAutoBlock() {
        Module autoBlockModule = ModuleManager.INSTANCE.getModule("Auto Block");
        if (autoBlockModule instanceof AutoBlockModule && autoBlockModule.isEnabled())
            return ((AutoBlockModule) autoBlockModule).killAuraShouldDeferSwing();
        return false;
    }

    // ── Shared guards ─────────────────────────────────────────────────────

    private boolean canRunCombat() {
        Object player = McAccess.thePlayer();
        Object world = McAccess.theWorld();
        if (player == null || world == null)
            return false;

        Module autoClicker = ModuleManager.INSTANCE.getModule("AutoClicker");
        if (autoClicker != null && autoClicker.isEnabled())
            return false;

        return true;
    }

    private void clearRotationStateIfOwned() {
        if (RotationState.getPriority() == ROTATION_PRIORITY)
            RotationState.reset();
    }

    private void resetRotationState() {
        lastSentYaw = Float.MIN_VALUE;
        lastSentPitch = Float.MIN_VALUE;
        pendingSentYaw = Float.MIN_VALUE;
        pendingSentPitch = Float.MIN_VALUE;
    }
}
