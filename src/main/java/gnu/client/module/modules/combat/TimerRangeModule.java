package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.AxisAlignedBB;

/**
 * TimerRange — maximize timer-balance advantage (LB/FDP ideas + raven-safe freeze).
 *
 * <ul>
 *   <li><b>Passive farm</b> (LB-style): while a target is in scan range, periodically skip
 *       local {@code onUpdate} (timer stays 1.0) to bank C03 balance without freezing GUIs.</li>
 *   <li><b>Freeze</b> (raven Timer@0): full local-update skip for {@code Freeze Ticks}.</li>
 *   <li><b>Predicted trigger</b> (FDP): only dump when predicted self/enemy positions land
 *       in the activation band.</li>
 *   <li><b>Dump</b>: {@code boostTicks = freeze * Dump Ratio},
 *       {@code speed = totalBanked / boostTicks} for that many wall-clock ticks.</li>
 * </ul>
 */
public final class TimerRangeModule extends Module implements PacketListener {

    private static final int PHASE_IDLE = 0;
    private static final int PHASE_FREEZE = 1;
    private static final int PHASE_BOOST = 2;

    private static final long MS_PER_TICK = 50L;

    private final SliderSetting freezeTicks =
            addSetting(new SliderSetting("Freeze Ticks", 10f, 2f, 40f, 1f));
    /** Fraction of freeze length used as boost wall-time (0.5 → freeze10 → boost5 @ 2x). */
    private final SliderSetting dumpRatio =
            addSetting(new SliderSetting("Dump Ratio", 0.50f, 0.20f, 1.0f, 0.05f));
    private final SliderSetting maxBalance =
            addSetting(new SliderSetting("Max Balance", 30f, 5f, 60f, 1f));
    private final SliderSetting farmInterval =
            addSetting(new SliderSetting("Farm Interval", 2f, 1f, 8f, 1f));
    private final SliderSetting scanRange =
            addSetting(new SliderSetting("Scan Range", 8.0f, 3.0f, 16.0f, 0.1f));
    private final SliderSetting minRange =
            addSetting(new SliderSetting("Min Range", 2.5f, 1.0f, 6.0f, 0.1f));
    private final SliderSetting maxRange =
            addSetting(new SliderSetting("Max Range", 3.5f, 2.0f, 8.0f, 0.1f));
    private final SliderSetting predictEnemy =
            addSetting(new SliderSetting("Predict Enemy", 1.5f, 0.0f, 3.0f, 0.1f));
    private final SliderSetting predictSelf =
            addSetting(new SliderSetting("Predict Self", 2f, 0f, 5f, 1f));
    private final SliderSetting cooldownTicks =
            addSetting(new SliderSetting("Cooldown", 15f, 0f, 100f, 1f));
    private final BoolSetting passiveFarm =
            addSetting(new BoolSetting("Passive Farm", true));
    private final BoolSetting predictedOnly =
            addSetting(new BoolSetting("Predicted Only", true));
    private final BoolSetting onlyKillAura =
            addSetting(new BoolSetting("Only KillAura", true));
    private final BoolSetting onlyForward =
            addSetting(new BoolSetting("Only Forward", true));
    private final BoolSetting onlyOnGround =
            addSetting(new BoolSetting("Only On Ground", false));
    private final BoolSetting resetOnLagback =
            addSetting(new BoolSetting("Reset On Lagback", true));
    private final BoolSetting resetOnKnockback =
            addSetting(new BoolSetting("Reset On Knockback", true));

    private int phase = PHASE_IDLE;
    private int freezeTicksLeft;
    private int balance;
    private int farmCounter;
    private boolean farmSkipArmed;
    private int boostTicksTotal;
    private float boostSpeed;
    private long boostEndMs;
    private int cooldownLeft;
    private boolean owningTimer;

    public TimerRangeModule() {
        super("TimerRange", "Farm + freeze timer balance then burst to break spacing", Category.COMBAT);
    }

    /** raven Timer@0 / passive farm: skip local player onUpdate. */
    public static boolean shouldSkipLocalUpdate() {
        Module module = ModuleManager.instance().getModule("TimerRange");
        if (!(module instanceof TimerRangeModule) || !module.isEnabled())
            return false;
        TimerRangeModule tr = (TimerRangeModule) module;
        if (tr.phase == PHASE_FREEZE)
            return true;
        if (tr.farmSkipArmed) {
            tr.farmSkipArmed = false;
            return true;
        }
        return false;
    }

    public static boolean isControllingTimer() {
        Module module = ModuleManager.instance().getModule("TimerRange");
        return module instanceof TimerRangeModule
                && module.isEnabled()
                && ((TimerRangeModule) module).owningTimer;
    }

    @Override
    public void onEnable() {
        PacketEvents.register(this);
        resetCycle(true);
        balance = 0;
        farmCounter = 0;
        cooldownLeft = 0;
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        resetCycle(true);
        balance = 0;
        farmCounter = 0;
        farmSkipArmed = false;
        cooldownLeft = 0;
    }

    @Override
    public void onTickStart() {
        tickLogic();
    }

    @Override
    public void onTick() {
        if (owningTimer)
            Mc.setTimerSpeed(Math.max(1.01f, boostSpeed));
    }

    @Override
    public void onRender(float partialTicks) {
        if (phase == PHASE_BOOST)
            maybeFinishBoost();
    }

    private void tickLogic() {
        if (!Mc.isInGame()) {
            resetCycle(true);
            return;
        }
        if (isModuleEnabled("Timer")) {
            resetCycle(true);
            return;
        }

        EntityPlayerSP player = Mc.player();
        if (player == null || player.isDead) {
            resetCycle(true);
            return;
        }

        if (Mc.currentScreen() != null && phase == PHASE_BOOST) {
            finishCycle();
            return;
        }

        if (phase == PHASE_IDLE) {
            if (cooldownLeft > 0)
                cooldownLeft--;

            EntityPlayer target = resolveTarget(player);
            if (target == null)
                return;

            float dist = player.getDistanceToEntity(target);
            float scan = Math.max(scanRange.getValue(), maxRange.getValue());

            // LB-style passive banking while near but not dumping yet.
            if (passiveFarm.getValue() && dist <= scan && dist > minRange.getValue())
                tryFarmTick();

            if (cooldownLeft > 0)
                return;
            if (onlyOnGround.getValue() && !player.onGround)
                return;
            if (!isMovingOk(player))
                return;
            if (!inActivationBand(dist))
                return;
            if (predictedOnly.getValue() && !predictedInBand(player, target))
                return;

            beginFreeze();
            return;
        }

        if (phase == PHASE_FREEZE) {
            freezeTicksLeft--;
            if (freezeTicksLeft <= 0)
                beginBoost();
            return;
        }

        if (phase == PHASE_BOOST) {
            // LB distanceToPause: stop dumping if already on top of them.
            EntityPlayer target = resolveTarget(player);
            if (target != null && player.getDistanceToEntity(target) < minRange.getValue() * 0.55f) {
                finishCycle();
                return;
            }
            Mc.setTimerSpeed(Math.max(1.01f, boostSpeed));
            maybeFinishBoost();
        }
    }

    private void tryFarmTick() {
        int cap = Math.max(1, Math.round(maxBalance.getValue()));
        if (balance >= cap)
            return;
        int interval = Math.max(1, Math.round(farmInterval.getValue()));
        farmCounter++;
        if (farmCounter < interval)
            return;
        farmCounter = 0;
        farmSkipArmed = true;
        balance++;
    }

    private void beginFreeze() {
        int freeze = Math.max(2, Math.round(freezeTicks.getValue()));
        int cap = Math.max(freeze, Math.round(maxBalance.getValue()));
        balance = Math.min(cap, balance + freeze);
        float ratio = Math.max(0.2f, Math.min(1.0f, dumpRatio.getValue()));
        boostTicksTotal = Math.max(1, Math.round(freeze * ratio));
        boostSpeed = (float) balance / (float) boostTicksTotal;
        phase = PHASE_FREEZE;
        freezeTicksLeft = freeze;
        boostEndMs = 0L;
        farmSkipArmed = false;
        owningTimer = false;
        Mc.resetTimer();
    }

    private void beginBoost() {
        phase = PHASE_BOOST;
        freezeTicksLeft = 0;
        boostEndMs = System.currentTimeMillis() + (long) boostTicksTotal * MS_PER_TICK;
        owningTimer = true;
        Mc.setTimerSpeed(Math.max(1.01f, boostSpeed));
    }

    private void maybeFinishBoost() {
        if (phase != PHASE_BOOST)
            return;
        if (System.currentTimeMillis() >= boostEndMs)
            finishCycle();
    }

    private void finishCycle() {
        phase = PHASE_IDLE;
        freezeTicksLeft = 0;
        balance = 0;
        boostTicksTotal = 0;
        boostSpeed = 1.0f;
        boostEndMs = 0L;
        farmSkipArmed = false;
        cooldownLeft = Math.max(0, Math.round(cooldownTicks.getValue()));
        owningTimer = false;
        Mc.resetTimer();
    }

    private void resetCycle(boolean restoreTimer) {
        phase = PHASE_IDLE;
        freezeTicksLeft = 0;
        boostTicksTotal = 0;
        boostSpeed = 1.0f;
        boostEndMs = 0L;
        farmSkipArmed = false;
        owningTimer = false;
        if (restoreTimer)
            Mc.resetTimer();
    }

    private boolean inActivationBand(float dist) {
        float min = Math.min(minRange.getValue(), maxRange.getValue());
        float max = Math.max(minRange.getValue(), maxRange.getValue());
        return dist >= min && dist <= max;
    }

    /** FDP updateDistance — will we be in-band after short prediction? */
    private boolean predictedInBand(EntityPlayerSP player, EntityPlayer target) {
        double enemyScale = 2.0 + predictEnemy.getValue();
        double predX = (target.posX - target.prevPosX) * enemyScale;
        double predY = (target.posY - target.prevPosY) * enemyScale;
        double predZ = (target.posZ - target.prevPosZ) * enemyScale;
        AxisAlignedBB box = target.getEntityBoundingBox().offset(predX, predY, predZ);

        int selfTicks = Math.max(0, Math.round(predictSelf.getValue()));
        double cx = player.posX + player.motionX * selfTicks;
        double cy = player.posY + player.motionY * selfTicks;
        double cz = player.posZ + player.motionZ * selfTicks;

        double dx = Math.max(box.minX - cx, Math.max(0.0, cx - box.maxX));
        double dy = Math.max(box.minY - cy, Math.max(0.0, cy - box.maxY));
        double dz = Math.max(box.minZ - cz, Math.max(0.0, cz - box.maxZ));
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return inActivationBand(dist);
    }

    private EntityPlayer resolveTarget(EntityPlayerSP self) {
        if (onlyKillAura.getValue()) {
            Entity ka = KillAuraModule.getCurrentTarget();
            if (ka instanceof EntityPlayer && !(ka instanceof EntityPlayerSP) && !ka.isDead)
                return (EntityPlayer) ka;
            return null;
        }
        return nearestPlayer(self);
    }

    private static EntityPlayer nearestPlayer(EntityPlayerSP self) {
        if (Mc.world() == null)
            return null;
        EntityPlayer best = null;
        float bestDist = Float.MAX_VALUE;
        for (EntityPlayer other : Mc.world().playerEntities) {
            if (other == null || other == self || other.isDead || other instanceof EntityPlayerSP)
                continue;
            float d = self.getDistanceToEntity(other);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    private boolean isMovingOk(EntityPlayerSP player) {
        if (!onlyForward.getValue())
            return player.moveForward != 0.0f || player.moveStrafing != 0.0f;
        return player.moveForward > 0.0f && player.moveStrafing == 0.0f;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!isEnabled() || !Mc.isInGame())
            return false;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;

        if (resetOnLagback.getValue() && packet instanceof S08PacketPlayerPosLook) {
            resetCycle(true);
            balance = 0;
            cooldownLeft = Math.max(cooldownLeft, Math.round(cooldownTicks.getValue()));
            return false;
        }
        if (resetOnKnockback.getValue() && PacketHelper.isEntityVelocity(packet)
                && packet instanceof S12PacketEntityVelocity) {
            if (((S12PacketEntityVelocity) packet).getEntityID() == player.getEntityId()) {
                resetCycle(true);
                balance = 0;
                cooldownLeft = Math.max(cooldownLeft, Math.round(cooldownTicks.getValue()));
            }
        }
        return false;
    }

    @Override
    public boolean onSend(Object packet) {
        return false;
    }

    @Override
    public String[] getSuffix() {
        if (phase == PHASE_FREEZE)
            return new String[] { "Freeze " + freezeTicksLeft + " bal" + balance };
        if (phase == PHASE_BOOST) {
            long leftMs = Math.max(0L, boostEndMs - System.currentTimeMillis());
            return new String[] { String.format("Boost %.2fx", boostSpeed) };
        }
        if (cooldownLeft > 0)
            return new String[] { "CD " + cooldownLeft };
        if (balance > 0)
            return new String[] { "Bal " + balance };
        return new String[] { String.format("%.1f-%.1f", minRange.getValue(), maxRange.getValue()) };
    }

    private static boolean isModuleEnabled(String name) {
        Module module = ModuleManager.instance().getModule(name);
        return module != null && module.isEnabled();
    }
}
