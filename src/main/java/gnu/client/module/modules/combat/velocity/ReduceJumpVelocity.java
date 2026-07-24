package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.combat.VelocityModule;
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
 * <p>While hurt and crosshair is on an {@link EntityPlayer}, sends legitimate
 * {@code C0A}/{@code C02 ATTACK} packets and applies Polar {@code 0.59928}
 * sprint-attack slow (instead of vanilla {@code 0.6}). Requires attack key
 * held on a crosshair player, or KillAura with a valid player target whose
 * silent/camera look-ray hits the hitbox (same client can-hit gate as KA).
 * Without a knockback enchant on the held item, at most one reduce C02 per
 * tick (Simulation / AntiKB).</p>
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

        // Fresh hurt window — refill attack budget once per hit.
        if (hurt >= 9 && lastHurtTime < 9)
            attacksLeft = Math.max(0, (int) parent.reduceJumpAttacks.getValue().floatValue());
        lastHurtTime = hurt;

        if (attacksLeft <= 0)
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
            // Re-arm client sprint so each reduce applies Polar hit-slow.
            if (!player.isSprinting())
                Mc.setClientSprinting(player, true);
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
     * Manual: attack key + crosshair player.
     * KillAura: has a player target and look-ray actually hits their box (can hit).
     */
    private static EntityPlayer resolveAttackTarget() {
        EntityPlayer cross = crosshairPlayer();
        Entity ka = KillAuraModule.getCurrentTarget();
        EntityPlayer kaPlayer = (ka instanceof EntityPlayer && !(ka instanceof EntityPlayerSP))
                ? (EntityPlayer) ka : null;

        if (Mc.isAttackKeyDown()) {
            if (cross == null)
                return null;
            // Don't reduce into a different player than KA is locked on.
            if (kaPlayer != null && cross != kaPlayer)
                return null;
            return cross;
        }

        if (kaPlayer == null)
            return null;
        // Silent/camera look must raycast the hitbox (same gate as KA can-hit).
        // Visual objectMouseOver is ignored — silent rotations often leave it elsewhere.
        if (!KillAuraModule.lookRayHitsCurrentTarget())
            return null;
        return kaPlayer;
    }

    /**
     * Multi-C02 same tick needs KB enchant (knockback stick) or Polar Simulation / AntiKB
     * flags the extra attack slows. Plain weapons stay at 1/tick.
     */
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
