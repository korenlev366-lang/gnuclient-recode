package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;

import java.util.Arrays;
import java.util.List;

/**
 * MoreKB — increases knockback via sprint resets (OpenMiau port).
 */
public final class MoreKBModule extends Module implements PacketListener {

    private static final float FORWARD_SPRINT_THRESHOLD = 0.8f;

    private static final List<String> MODE_NAMES = Arrays.asList(
            "Legit", "LegitFast", "LessPacket", "Packet", "DoublePacket",
            "WTap", "SprintTap", "Silent", "SneakPacket", "SpamS");

    private final ModeSetting mode = addSetting(
            new ModeSetting("Mode", 0, MODE_NAMES));
    private final BoolSetting intelligent = addSetting(
            new BoolSetting("Intelligent", false));
    private final BoolSetting onlyGround = addSetting(
            new BoolSetting("Only Ground", true));
    private final SliderSetting spamSDistance = addSetting(
            new SliderSetting("SpamS Distance", 3.0f, 0.0f, 6.0f));
    private final SliderSetting spamSTick = addSetting(
            new SliderSetting("SpamS Tick", 2.0f, 0.0f, 10.0f));

    private EntityLivingBase target;
    private int ticks;
    private int spamSActiveTicks;
    private int wTapTicks;

    public MoreKBModule() {
        super("MoreKB", "Increases knockback via sprint resets (OpenMiau)", Category.COMBAT);
        spamSDistance.visibleWhen(() -> mode.getIndex() == 9);
        spamSTick.visibleWhen(() -> mode.getIndex() == 9);
    }

    /**
     * WTap mode: zero {@code moveForward} while the post-attack window is active
     * (called from {@link gnu.client.runtime.MovementInputHook}).
     */
    public static void patchMovementInput(Object movInput) {
        Module mod = ModuleManager.INSTANCE.getModule("MoreKB");
        if (!(mod instanceof MoreKBModule) || !mod.isEnabled() || movInput == null)
            return;
        MoreKBModule moreKb = (MoreKBModule) mod;
        if (moreKb.mode.getIndex() != 5 || moreKb.wTapTicks <= 0)
            return;
        MovementInput input = (MovementInput) movInput;
        input.moveForward = 0.0f;
    }

    @Override
    public void onEnable() {
        clearState();
        PacketEvents.register(this);

        Module wTap = ModuleManager.INSTANCE.getModule("W Tap");
        if (wTap != null && wTap.isEnabled())
            wTap.setEnabled(false);
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        clearState();
        Mc.setBackKeyState(false);
    }

    private void clearState() {
        target = null;
        ticks = 0;
        spamSActiveTicks = 0;
        wTapTicks = 0;
    }

    /**
     * Called from {@link gnu.client.runtime.ClientEventListener} and
     * {@link gnu.client.runtime.CombatAttackNotify} on Forge attack.
     */
    public void noteForgeAttack(Entity targetEntity) {
        if (!isEnabled() || targetEntity == null)
            return;
        if (!(targetEntity instanceof EntityLivingBase))
            return;

        this.target = (EntityLivingBase) targetEntity;

        int currentMode = mode.getIndex();
        if (currentMode < 5)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        if (onlyGround.getValue() && !player.onGround)
            return;

        double distance = player.getDistanceToEntity(this.target);

        switch (currentMode) {
            case 5:
                if (Mc.isClientSprinting(player))
                    wTapTicks = 2;
                break;
            case 6:
            case 7:
                if (Mc.isClientSprinting(player))
                    ticks = 2;
                break;
            case 8:
                Mc.sendSprintActionPacket(player, false);
                Mc.sendSneakActionPacket(player, true);
                Mc.sendSprintActionPacket(player, true);
                Mc.sendSneakActionPacket(player, false);
                break;
            case 9:
                if (distance <= spamSDistance.getValue())
                    spamSActiveTicks = spamSTick.getValue().intValue();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onSend(Object packet) {
        if (!isEnabled())
            return false;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;

        if (!(packet instanceof C03PacketPlayer) && !PacketHelper.isPlayerMovement(packet))
            return false;

        if (mode.getIndex() == 7) {
            if (ticks == 2) {
                Mc.sendSprintActionPacket(player, false);
                ticks--;
            } else if (ticks == 1 && Mc.isClientSprinting(player)) {
                Mc.sendSprintActionPacket(player, true);
                ticks--;
            }
        }
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    @Override
    public void onTick() {
        if (!isEnabled())
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        int currentMode = mode.getIndex();

        if (currentMode >= 5) {
            tickHighModes(player, currentMode);
            return;
        }

        if (currentMode == 1) {
            if (target != null && isMoving(player)) {
                if ((onlyGround.getValue() && player.onGround) || !onlyGround.getValue())
                    player.sprintingTicksLeft = 0;
                target = null;
            }
            return;
        }

        EntityLivingBase entity = raycastLivingTarget();
        if (entity == null)
            return;

        double x = player.posX - entity.posX;
        double z = player.posZ - entity.posZ;
        float calcYaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI - 90.0);
        float diffY = Math.abs(MathHelper.wrapAngleTo180_float(calcYaw - entity.rotationYawHead));

        if (intelligent.getValue() && diffY > 120.0f)
            return;

        if (entity.hurtTime != 10)
            return;

        switch (currentMode) {
            case 0:
                if (Mc.isClientSprinting(player)) {
                    Mc.setClientSprinting(player, false);
                    Mc.setClientSprinting(player, true);
                }
                break;
            case 2:
                if (Mc.isClientSprinting(player))
                    Mc.setClientSprinting(player, false);
                Mc.sendSprintActionPacket(player, true);
                Mc.setClientSprinting(player, true);
                break;
            case 3:
                Mc.sendSprintActionPacket(player, false);
                Mc.sendSprintActionPacket(player, true);
                Mc.setClientSprinting(player, true);
                break;
            case 4:
                Mc.sendSprintActionPacket(player, false);
                Mc.sendSprintActionPacket(player, true);
                Mc.sendSprintActionPacket(player, false);
                Mc.sendSprintActionPacket(player, true);
                Mc.setClientSprinting(player, true);
                break;
            default:
                break;
        }
    }

    private void tickHighModes(EntityPlayerSP player, int currentMode) {
        switch (currentMode) {
            case 5:
                if (wTapTicks > 0) {
                    Mc.setClientSprinting(player, false);
                    if (player.movementInput != null)
                        player.movementInput.moveForward = 0.0f;
                    wTapTicks--;
                }
                break;
            case 6:
                if (ticks == 2) {
                    Mc.setClientSprinting(player, false);
                    ticks--;
                } else if (ticks == 1) {
                    if (player.movementInput != null
                            && player.movementInput.moveForward > FORWARD_SPRINT_THRESHOLD) {
                        Mc.setClientSprinting(player, true);
                    }
                    ticks--;
                }
                break;
            case 9:
                if (spamSActiveTicks > 0) {
                    Mc.setBackKeyState(true);
                    spamSActiveTicks--;
                }
                break;
            default:
                break;
        }
    }

    private static EntityLivingBase raycastLivingTarget() {
        MovingObjectPosition mouseOver = Mc.objectMouseOver();
        if (mouseOver == null
                || mouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY
                || !(mouseOver.entityHit instanceof EntityLivingBase)) {
            return null;
        }
        return (EntityLivingBase) mouseOver.entityHit;
    }

    private static boolean isMoving(EntityPlayerSP player) {
        return player.moveForward != 0.0f || player.moveStrafing != 0.0f;
    }

    @Override
    public String[] getSuffix() {
        int idx = mode.getIndex();
        if (idx < 0 || idx >= MODE_NAMES.size())
            return new String[0];
        return new String[]{MODE_NAMES.get(idx)};
    }
}
