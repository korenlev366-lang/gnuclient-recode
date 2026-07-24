package gnu.client.module.modules.combat.velocity;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.AuraCombatPacketGuard;
import gnu.client.runtime.mc.Mc;
import gnu.client.utility.PacketUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovementInput;

/**
 * Jump-reset + Polar hit-slow abuse with capped extra attack packets.
 *
 * <p>Simple jump-reset (same as {@link JumpResetVelocity}). Extra {@code C02}s are
 * skipped while WTap/MoreKB, KillAura autoblock, item-use/block, or a same-tick
 * {@code C07} release would make the attack PacketOrder / MultiActions unsafe.
 * Without KB stick, hard-capped at 1 C02/tick.</p>
 */
public final class ReduceJumpVelocity extends VelocityMode {

    private int attacksLeft;
    private int lastHurtTime;

    public ReduceJumpVelocity(VelocityModule parent) {
        super("ReduceJump", parent);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onMoveInput(MovementInput input) {
        if (!parent.reduceJumpDoJump.getValue())
            return;
        EntityPlayerSP player = Mc.player();
        if (player == null || input == null)
            return;
        if (!player.onGround || player.hurtTime < 9 || parent.isInLiquidOrWeb())
            return;

        int roll = parent.random.nextInt(100) + 1;
        if (roll > (int) parent.chance.getValue().floatValue())
            return;

        input.jump = true;
    }

    @Override
    public void onUpdate(boolean pre) {
        if (!pre)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        int hurt = player.hurtTime;
        if (hurt <= 0) {
            reset();
            return;
        }

        if (hurt >= 9 && lastHurtTime < 9)
            attacksLeft = Math.max(0, (int) parent.reduceJumpAttacks.getValue().floatValue());
        lastHurtTime = hurt;

        if (attacksLeft <= 0)
            return;
        if (sprintResetModuleActive())
            return;
        if (unsafeForExtraAttack(player))
            return;
        if (parent.isInLiquidOrWeb())
            return;
        if (!player.isSprinting())
            return;
        if (player.motionX == 0.0D && player.motionZ == 0.0D)
            return;

        EntityPlayer target = resolveAttackTarget();
        if (target == null)
            return;

        int perTick = effectiveAttacksPerTick(player);
        boolean swing = parent.reduceJumpSwing.getValue();
        for (int i = 0; i < perTick && attacksLeft > 0; i++) {
            if (resolveAttackTarget() != target)
                break;
            if (!player.isSprinting())
                break;
            if (unsafeForExtraAttack(player))
                break;
            if (swing)
                PacketUtils.sendPacketNoEvent(new C0APacketAnimation());
            PacketUtils.sendPacketNoEvent(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
            player.motionX *= PolarVelocity.POLAR_HIT_SLOW;
            player.motionZ *= PolarVelocity.POLAR_HIT_SLOW;
            player.setSprinting(false);
            attacksLeft--;
        }
    }

    /**
     * Autoblock / use-item / PacketOrderI release window — do not inject C02.
     * Jump-reset still runs; only the extra attack packets are gated.
     */
    private static boolean unsafeForExtraAttack(EntityPlayerSP player) {
        if (KillAuraModule.blocksExternalAttackPackets())
            return true;
        if (AuraCombatPacketGuard.shouldSkipAttackForReleaseOrder())
            return true;
        if (Mc.isUsingItem(player) || Mc.isBlocking(player))
            return true;
        return false;
    }

    private static boolean sprintResetModuleActive() {
        return isModuleEnabled("W Tap") || isModuleEnabled("MoreKB");
    }

    private static boolean isModuleEnabled(String name) {
        Module module = ModuleManager.instance().getModule(name);
        return module != null && module.isEnabled();
    }

    private static EntityPlayer resolveAttackTarget() {
        EntityPlayer cross = crosshairPlayer();
        Entity ka = KillAuraModule.getCurrentTarget();
        EntityPlayer kaPlayer = (ka instanceof EntityPlayer && !(ka instanceof EntityPlayerSP))
                ? (EntityPlayer) ka : null;

        if (Mc.isAttackKeyDown()) {
            if (cross == null)
                return null;
            if (kaPlayer != null && cross != kaPlayer)
                return null;
            return cross;
        }

        if (kaPlayer == null)
            return null;
        if (!KillAuraModule.lookRayHitsCurrentTarget())
            return null;
        return kaPlayer;
    }

    private int effectiveAttacksPerTick(EntityPlayerSP player) {
        int wanted = Math.max(1, (int) parent.reduceJumpPerTick.getValue().floatValue());
        if (EnchantmentHelper.getKnockbackModifier(player) > 0)
            return wanted;
        return 1;
    }

    private static EntityPlayer crosshairPlayer() {
        MovingObjectPosition mop = Mc.objectMouseOver();
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY)
            return null;
        Entity entity = mop.entityHit;
        if (!(entity instanceof EntityPlayer) || entity instanceof EntityPlayerSP)
            return null;
        return (EntityPlayer) entity;
    }

    private void reset() {
        attacksLeft = 0;
        lastHurtTime = 0;
    }
}
