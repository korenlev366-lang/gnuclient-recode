package gnu.client.module.modules.movement;

import gnu.client.event.JumpEvent;
import gnu.client.mixin.impl.accessors.IAccessorMinecraft;
import gnu.client.mixin.impl.accessors.IAccessorTimer;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.Timer;

import java.util.Collections;
import java.util.List;

/**
 * LiquidBounce nextgen {@code SpeedCustom} port — single mode {@code Custom}.
 *
 * <p>Auto-hop (SpeedBHopBase), horizontal accel / jump-off boost, jump height,
 * pulldown, strafe, timer, and velocity-timeout / strafe-knock.</p>
 */
public final class SpeedModule extends Module implements PacketListener {

    private static final List<String> MODES = Collections.singletonList("Custom");
    private static final float VANILLA_JUMP = 0.42f;
    private static final double FALL_DAMAGE_MIN_STRAFE = 0.2857671997172534;

    private final ModeSetting mode =
            addSetting(new ModeSetting("Mode", 0, MODES));

    // ── HorizontalModification ────────────────────────────────────────────
    private final BoolSetting horizontalModification =
            addSetting(new BoolSetting("Horizontal Modification", true));
    private final SliderSetting horizontalAcceleration =
            addSetting(new SliderSetting("Horizontal Acceleration", 0.0f, -0.1f, 0.2f, 0.01f)
                    .visibleWhen(horizontalModification::isToggled));
    private final SliderSetting horizontalJumpOff =
            addSetting(new SliderSetting("Horizontal Jump Off", 0.0f, -0.5f, 1.0f, 0.01f)
                    .visibleWhen(horizontalModification::isToggled));
    private final SliderSetting ticksToBoostOff =
            addSetting(new SliderSetting("Ticks To Boost Off", 0.0f, 0.0f, 20.0f, 1.0f)
                    .visibleWhen(horizontalModification::isToggled));

    // ── VerticalModification ──────────────────────────────────────────────
    private final BoolSetting verticalModification =
            addSetting(new BoolSetting("Vertical Modification", true));
    private final SliderSetting jumpHeight =
            addSetting(new SliderSetting("Jump Height", VANILLA_JUMP, 0.0f, 3.0f, 0.01f)
                    .visibleWhen(verticalModification::isToggled));
    private final SliderSetting pulldown =
            addSetting(new SliderSetting("Pulldown", 0.0f, 0.0f, 1.0f, 0.01f)
                    .visibleWhen(verticalModification::isToggled));
    private final SliderSetting pullDownDuringFall =
            addSetting(new SliderSetting("Pull Down During Fall", 0.0f, 0.0f, 1.0f, 0.01f)
                    .visibleWhen(verticalModification::isToggled));

    private final SliderSetting timerSpeed =
            addSetting(new SliderSetting("Timer Speed", 1.0f, 0.1f, 10.0f, 0.01f));

    // ── Strafe ────────────────────────────────────────────────────────────
    private final BoolSetting strafe =
            addSetting(new BoolSetting("Strafe", true));
    private final SliderSetting strength =
            addSetting(new SliderSetting("Strength", 1.0f, 0.1f, 1.0f, 0.01f)
                    .visibleWhen(strafe::isToggled));
    private final BoolSetting customSpeed =
            addSetting(new BoolSetting("Custom Speed", false)
                    .visibleWhen(strafe::isToggled));
    private final SliderSetting speed =
            addSetting(new SliderSetting("Speed", 1.0f, 0.1f, 10.0f, 0.01f)
                    .visibleWhen(() -> strafe.isToggled() && customSpeed.isToggled()));
    private final SliderSetting velocityTimeout =
            addSetting(new SliderSetting("Velocity Timeout", 0.0f, 0.0f, 20.0f, 1.0f)
                    .visibleWhen(strafe::isToggled));
    private final BoolSetting strafeKnock =
            addSetting(new BoolSetting("Strafe Knock", false)
                    .visibleWhen(strafe::isToggled));

    private int ticksTimeout;
    private int jumpOffBoostTicks = -1;
    private boolean pendingStrafeKnock;
    private boolean pendingFallDamageStrafe;
    private boolean owningTimer;

    public SpeedModule() {
        super("Speed", "Move faster (LiquidBounce Custom)", Category.MOVEMENT);
    }

    /** True while Speed owns {@code Timer.timerSpeed} (Timer / maintain yield). */
    public static boolean isControllingTimer() {
        Module module = ModuleManager.INSTANCE.getModule("Speed");
        return module instanceof SpeedModule
                && module.isEnabled()
                && ((SpeedModule) module).owningTimer;
    }

    public static void patchJump(JumpEvent event) {
        Module mod = ModuleManager.INSTANCE.getModule("Speed");
        if (!(mod instanceof SpeedModule) || !mod.isEnabled() || event == null)
            return;
        ((SpeedModule) mod).onJump(event);
    }

    @Override
    public void onEnable() {
        resetState();
        PacketEvents.register(this);
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        resetState();
        if (owningTimer) {
            owningTimer = false;
            if (!TimerRangeModuleOwns() && !timerModuleEnabled())
                Mc.resetTimer();
        }
    }

    @Override
    public void onTickStart() {
        if (!isEnabled() || !isCustom())
            return;
        EntityPlayerSP player = Mc.player();
        if (player == null || !Mc.isInGame())
            return;

        // SpeedBHopBase — force jump while moving on ground
        if (player.onGround && isMoving(player) && player.movementInput != null)
            player.movementInput.jump = true;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || !isCustom())
            return;
        EntityPlayerSP player = Mc.player();
        if (player == null || !Mc.isInGame())
            return;

        if (pendingStrafeKnock) {
            pendingStrafeKnock = false;
            applyStrafeKnock(player);
        }

        if (jumpOffBoostTicks >= 0) {
            if (jumpOffBoostTicks == 0 && horizontalModification.getValue()) {
                float mod = horizontalJumpOff.getValue();
                if (mod != 0.0f) {
                    player.motionX *= (1.0 + mod);
                    player.motionZ *= (1.0 + mod);
                }
            }
            jumpOffBoostTicks--;
        }

        if (!isMoving(player)) {
            releaseTimerIfNeeded();
            return;
        }

        if (horizontalModification.getValue()) {
            float accel = horizontalAcceleration.getValue();
            if (accel != 0.0f) {
                player.motionX *= (1.0 + accel);
                player.motionZ *= (1.0 + accel);
            }
        }

        if (verticalModification.getValue()) {
            float pull = player.motionY <= 0.0
                    ? pullDownDuringFall.getValue()
                    : pulldown.getValue();
            if (pull != 0.0f)
                player.motionY -= pull;
        }

        if (strafe.getValue()) {
            if (ticksTimeout > 0) {
                ticksTimeout--;
            } else {
                float str = strength.getValue();
                if (customSpeed.getValue())
                    withStrafe(player, speed.getValue(), str);
                else
                    withStrafe(player, horizontalSpeed(player), str);
            }
        }

        float ts = timerSpeed.getValue();
        if (ts != 1.0f && !TimerRangeModuleOwns()) {
            owningTimer = true;
            Timer timer = ((IAccessorMinecraft) Mc.mc()).getTimer();
            if (timer != null)
                ((IAccessorTimer) timer).setTimerSpeed(ts);
        } else {
            releaseTimerIfNeeded();
        }
    }

    private void onJump(JumpEvent event) {
        if (!isCustom())
            return;

        if (verticalModification.getValue()) {
            float h = jumpHeight.getValue();
            if (h != VANILLA_JUMP)
                event.setMotionY(h);
        }

        if (horizontalModification.getValue() && horizontalJumpOff.getValue() != 0.0f)
            jumpOffBoostTicks = Math.round(ticksToBoostOff.getValue());
    }

    @Override
    public boolean onSend(Object packet) {
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!isEnabled() || !isCustom() || !strafe.getValue())
            return false;
        if (!(packet instanceof S12PacketEntityVelocity))
            return false;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() != player.getEntityId())
            return false;

        int mx = vel.getMotionX();
        int mz = vel.getMotionZ();
        int my = vel.getMotionY();

        ticksTimeout = Math.round(velocityTimeout.getValue());

        if (strafeKnock.getValue()) {
            pendingStrafeKnock = true;
            // Fall-damage style: zero horiz, downward Y (LB isMovementYFallDamage)
            pendingFallDamageStrafe = mx == 0 && mz == 0 && my < 0;
        }
        return false;
    }

    private void applyStrafeKnock(EntityPlayerSP player) {
        double spd = horizontalSpeed(player);
        if (pendingFallDamageStrafe)
            spd = Math.max(spd, FALL_DAMAGE_MIN_STRAFE);
        pendingFallDamageStrafe = false;
        withStrafe(player, spd, 1.0);
    }

    private void withStrafe(EntityPlayerSP player, double speed, double strength) {
        if (!isMoving(player)) {
            player.motionX = 0.0;
            player.motionZ = 0.0;
            return;
        }
        float yaw = moveYaw(player);
        double oneMinus = 1.0 - strength;
        double prevX = player.motionX * oneMinus;
        double prevZ = player.motionZ * oneMinus;
        double used = speed * strength;
        double angle = Math.toRadians(yaw);
        player.motionX = prevX - Math.sin(angle) * used;
        player.motionZ = prevZ + Math.cos(angle) * used;
    }

    private static float moveYaw(EntityPlayerSP player) {
        float yaw = player.rotationYaw;
        float forward = player.movementInput != null ? player.movementInput.moveForward : player.moveForward;
        float strafeIn = player.movementInput != null ? player.movementInput.moveStrafe : player.moveStrafing;
        if (forward < 0.0f)
            yaw += 180.0f;
        if (strafeIn != 0.0f) {
            float multiplier = forward == 0.0f ? 1.0f : 0.5f * Math.signum(forward);
            yaw += -90.0f * multiplier * Math.signum(strafeIn);
        }
        return yaw;
    }

    private static double horizontalSpeed(EntityPlayerSP player) {
        return Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
    }

    private static boolean isMoving(EntityPlayerSP player) {
        if (player.movementInput != null)
            return player.movementInput.moveForward != 0.0f || player.movementInput.moveStrafe != 0.0f;
        return player.moveForward != 0.0f || player.moveStrafing != 0.0f;
    }

    private boolean isCustom() {
        return "Custom".equalsIgnoreCase(mode.getCurrentMode());
    }

    private void resetState() {
        ticksTimeout = 0;
        jumpOffBoostTicks = -1;
        pendingStrafeKnock = false;
        pendingFallDamageStrafe = false;
    }

    private void releaseTimerIfNeeded() {
        if (!owningTimer)
            return;
        owningTimer = false;
        if (!TimerRangeModuleOwns() && !timerModuleEnabled())
            Mc.resetTimer();
    }

    private static boolean TimerRangeModuleOwns() {
        return gnu.client.module.modules.combat.TimerRangeModule.isControllingTimer();
    }

    private static boolean timerModuleEnabled() {
        Module m = ModuleManager.INSTANCE.getModule("Timer");
        return m != null && m.isEnabled();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getCurrentMode()};
    }
}
