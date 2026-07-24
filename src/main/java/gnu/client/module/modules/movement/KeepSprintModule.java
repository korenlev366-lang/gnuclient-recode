package gnu.client.module.modules.movement;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Arrays;
import java.util.List;

/**
 * KeepSprint with Grim-aware modes.
 *
 * <ul>
 *   <li>{@code Legit} — vanilla {@code 0.6} slow; keep sprint state / re-sprint</li>
 *   <li>{@code Packet} — sprint-gap spoof: STOP → walk tick → hit with no AttackSlow → START</li>
 *   <li>{@code Full} — Packet gap on every combat hit while a target is live</li>
 *   <li>{@code Unsafe} — skip/soften {@code 0.6} with no spoof (Simulation bait)</li>
 * </ul>
 *
 * <p>Grim applies AttackSlow from {@code lastSprinting} (updated after flying), not from
 * same-tick {@code C0B}. Gap requires STOP on tick N and the hit on tick N+1.
 */
public final class KeepSprintModule extends Module {

    public static final int MODE_LEGIT = 0;
    public static final int MODE_PACKET = 1;
    public static final int MODE_FULL = 2;
    public static final int MODE_UNSAFE = 3;

    private static final int STATE_IDLE = 0;
    private static final int STATE_GAP = 1;
    private static final int STATE_HIT_READY = 2;

    private static final List<String> MODES = Arrays.asList("Legit", "Packet", "Full", "Unsafe");

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", MODE_PACKET, MODES));
    /** Unsafe retain: 100 = no slow, 60 = vanilla. */
    private final SliderSetting unsafeRetain = addSetting(
            new SliderSetting("Unsafe retain", 100f, 60f, 100f, 1f));
    private final BoolSetting groundOnly = addSetting(new BoolSetting("Ground only", false));
    private final BoolSetting reachOnly = addSetting(new BoolSetting("Reach only", false));

    private int state = STATE_IDLE;
    private int hitReadyTicks;

    public KeepSprintModule() {
        super("KeepSprint", "Keeps sprint momentum through hits (Grim packet-gap modes)", Category.MOVEMENT);
        unsafeRetain.visibleWhen(() -> mode.getIndex() == MODE_UNSAFE);
    }

    public static KeepSprintModule activeInstance() {
        Module module = ModuleManager.instance().getModule("KeepSprint");
        if (module instanceof KeepSprintModule && module.isEnabled())
            return (KeepSprintModule) module;
        return null;
    }

    /** SprintModule: release key during gap so vanilla does not re-send START. */
    public static boolean shouldSuppressSprintKey() {
        KeepSprintModule ks = activeInstance();
        return ks != null && ks.state == STATE_GAP;
    }

    /** KillAura: wait one tick after STOP so Grim clears {@code lastSprinting}. */
    public static boolean shouldDeferAttack() {
        KeepSprintModule ks = activeInstance();
        return ks != null && ks.gapModeActive() && ks.state == STATE_GAP;
    }

    /**
     * Multiplier for vanilla attack {@code 0.6} constant.
     * {@code 1.0} = no slow; {@code 0.6} = vanilla.
     */
    public static double attackSlowMultiplier() {
        KeepSprintModule ks = activeInstance();
        if (ks == null || !ks.shouldKeepSprint())
            return 0.6;
        switch (ks.mode.getIndex()) {
            case MODE_UNSAFE:
                return ks.unsafeRetain.getValue() / 100.0;
            case MODE_PACKET:
            case MODE_FULL:
                // Only skip slow after a deliberate STOP→flying gap (HIT_READY).
                // Do NOT skip whenever serverSprintState is false (blocking/walk) —
                // that removes vanilla attack slow unintentionally.
                if (ks.state == STATE_HIT_READY)
                    return 1.0;
                return 0.6;
            case MODE_LEGIT:
            default:
                return 0.6;
        }
    }

    /** When true, {@code setSprinting(false)} from attack slow is ignored. */
    public static boolean shouldKeepSprintFlag() {
        KeepSprintModule ks = activeInstance();
        return ks != null && ks.isEnabled() && ks.shouldKeepSprint();
    }

    public static void onAttackApplied() {
        KeepSprintModule ks = activeInstance();
        if (ks != null)
            ks.afterAttack();
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }

    @Override
    public void onTickStart() {
        if (!Mc.isInGame()) {
            resetState();
            return;
        }
        EntityPlayerSP player = Mc.player();
        if (player == null) {
            resetState();
            return;
        }

        if (state == STATE_GAP) {
            // Prior tick sent STOP + flying → Grim lastSprinting is clear.
            state = STATE_HIT_READY;
            hitReadyTicks = 0;
            return;
        }

        if (state == STATE_HIT_READY) {
            hitReadyTicks++;
            if (hitReadyTicks > 3 && !combatActive()) {
                restoreSprint(player);
                state = STATE_IDLE;
            }
        }

        if (!gapModeActive()) {
            if (state != STATE_IDLE)
                restoreSprint(player);
            state = STATE_IDLE;
            return;
        }

        if (state == STATE_IDLE && shouldArmGap(player))
            armGap(player);
    }

    private void afterAttack() {
        if (!gapModeActive())
            return;
        // Only consume a completed gap — ignore hits that used vanilla 0.6 (IDLE).
        if (state != STATE_HIT_READY)
            return;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        // Re-enable client sprint; living / SprintModule sends START before C03.
        Mc.setClientSprinting(player, true);
        Mc.setSprintKeyState(true);
        if (!Mc.getServerSprintState(player))
            Mc.sendSprintActionPacket(player, true);
        state = STATE_IDLE;
        hitReadyTicks = 0;

        // Full: immediately arm next gap if still fighting.
        if (mode.getIndex() == MODE_FULL && shouldArmGap(player))
            armGap(player);
    }

    private void armGap(EntityPlayerSP player) {
        // Must actually STOP while sprinting — walking/blocking must not become HIT_READY
        // or the next hit skips vanilla attack slow.
        if (!Mc.getServerSprintState(player) && !player.isSprinting())
            return;
        Mc.setSprintKeyState(false);
        Mc.setClientSprinting(player, false);
        if (Mc.getServerSprintState(player))
            Mc.sendSprintActionPacket(player, false);
        state = STATE_GAP;
        hitReadyTicks = 0;
    }

    private void restoreSprint(EntityPlayerSP player) {
        if (player == null)
            return;
        Mc.setSprintKeyState(true);
        Mc.setClientSprinting(player, true);
        if (!Mc.getServerSprintState(player))
            Mc.sendSprintActionPacket(player, true);
    }

    private boolean gapModeActive() {
        int m = mode.getIndex();
        if (m != MODE_PACKET && m != MODE_FULL)
            return false;
        if (isModuleEnabled("W Tap") || isModuleEnabled("MoreKB"))
            return false;
        return true;
    }

    private boolean shouldArmGap(EntityPlayerSP player) {
        if (!shouldKeepSprint())
            return false;
        // Only arm while actually sprinting — not while blocking/using item / walking.
        if (Mc.isUsingItem(player))
            return false;
        if (!player.isSprinting() && !Mc.getServerSprintState(player))
            return false;
        if (mode.getIndex() == MODE_FULL)
            return combatActive();
        return combatActive() && attackImminent();
    }

    private boolean combatActive() {
        if (KillAuraModule.getCurrentTarget() != null)
            return true;
        return Mc.isAttackKeyDown() && lookingAtEntity();
    }

    private boolean attackImminent() {
        if (KillAuraModule.getCurrentTarget() != null)
            return true;
        return Mc.isAttackKeyDown();
    }

    private boolean lookingAtEntity() {
        MovingObjectPosition mop = Mc.objectMouseOver();
        return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY;
    }

    public boolean shouldKeepSprint() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (groundOnly.getValue() && !player.onGround)
            return false;
        if (!reachOnly.getValue())
            return true;
        MovingObjectPosition mop = Mc.objectMouseOver();
        if (mop == null || mop.hitVec == null)
            return true;
        Vec3 eyes = player.getPositionEyes(1.0f);
        return eyes.distanceTo(mop.hitVec) > 3.0;
    }

    private void resetState() {
        state = STATE_IDLE;
        hitReadyTicks = 0;
    }

    private static boolean isModuleEnabled(String name) {
        Module module = ModuleManager.instance().getModule(name);
        return module != null && module.isEnabled();
    }
}
