package gnu.client.mixin.impl.entity;

import com.google.common.collect.Maps;
import gnu.client.event.JumpEvent;
import gnu.client.event.PreMotionEvent;
import gnu.client.event.PrePlayerMovementInputEvent;
import gnu.client.event.StrafeEvent;
import gnu.client.module.impl.client.Settings;
import gnu.client.runtime.MoveFixHook;
import gnu.client.runtime.PlayerUpdateHook;
import gnu.client.utility.RotationUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity implements gnu.client.mixin.RealPosAccess {
    public MixinEntityLivingBase(World worldIn) {
        super(worldIn);
    }

    /**
     * Augustus-style server position. Updated from {@code S14PacketEntity} (relative
     * deltas) and {@code S18PacketEntityTeleport} (absolute) so the BackTrack module can
     * render an entity at its last known server position while inbound packets are held.
     */
    @Unique
    public double realPosX;
    @Unique
    public double realPosY;
    @Unique
    public double realPosZ;

    @Override
    public double getRealPosX() {
        return realPosX;
    }

    @Override
    public void setRealPosX(double value) {
        realPosX = value;
    }

    @Override
    public double getRealPosY() {
        return realPosY;
    }

    @Override
    public void setRealPosY(double value) {
        realPosY = value;
    }

    @Override
    public double getRealPosZ() {
        return realPosZ;
    }

    @Override
    public void setRealPosZ(double value) {
        realPosZ = value;
    }

    private final Map<Integer, PotionEffect> activePotionsMap = Maps.newHashMap();

    @Shadow
    public PotionEffect getActivePotionEffect(Potion potionIn) {
        return this.activePotionsMap.get(Integer.valueOf(potionIn.id));
    }

    @Shadow
    public boolean isPotionActive(Potion potionIn) {
        return this.activePotionsMap.containsKey(Integer.valueOf(potionIn.id));
    }

    @Shadow
    public float rotationYawHead;

    @Shadow
    public float renderYawOffset;

    @Shadow
    public float swingProgress;

    @Inject(method = { "updateDistance", "func_110146_f" }, at = @At("HEAD"), cancellable = true)
    protected void injectUpdateDistance(float p_110146_1_, float p_110146_2_, CallbackInfoReturnable<Float> cir) {
        float rotationYaw = this.rotationYaw;
        if (Settings.fullBody != null && Settings.rotateBody != null && !Settings.fullBody.isToggled() && Settings.rotateBody.isToggled() && (EntityLivingBase) (Object) this instanceof EntityPlayerSP && PreMotionEvent.setRenderYaw()) {
            if (this.swingProgress > 0F) {
                p_110146_1_ = RotationUtils.renderYaw;
            }
            rotationYaw = RotationUtils.renderYaw;
            rotationYawHead = RotationUtils.renderYaw;
        }

        float f = MathHelper.wrapAngleTo180_float(p_110146_1_ - this.renderYawOffset);
        this.renderYawOffset += f * 0.3F;
        float f1 = MathHelper.wrapAngleTo180_float(rotationYaw - this.renderYawOffset);
        boolean flag = f1 < 90.0F || f1 >= 90.0F;

        if (f1 < -75.0F) {
            f1 = -75.0F;
        }

        if (f1 >= 75.0F) {
            f1 = 75.0F;
        }

        this.renderYawOffset = rotationYaw - f1;

        if (f1 * f1 > 2500.0F) {
            this.renderYawOffset += f1 * 0.2F;
        }

        if (flag) {
            p_110146_2_ *= -1.0F;
        }

        cir.setReturnValue(p_110146_2_);
    }

    @Shadow
    protected float getJumpUpwardsMotion() {
        return 0.42F;
    }

    @Overwrite
    protected void jump() {
        float jumpYaw = this.rotationYaw;
        // OpenMyau: sprint-jump boost uses silent packet yaw while movefix is active.
        if ((Object) this instanceof EntityPlayerSP && MoveFixHook.shouldUseServerMoveYaw())
            jumpYaw = PlayerUpdateHook.silentYawForMoveFix();

        JumpEvent jumpEvent = new JumpEvent(this.getJumpUpwardsMotion(), jumpYaw, this.isSprinting());
        MinecraftForge.EVENT_BUS.post(jumpEvent);
        if (jumpEvent.isCanceled()) {
            return;
        }

        this.motionY = jumpEvent.getMotionY();

        if (this.isPotionActive(Potion.jump)) {
            this.motionY += (float) (this.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;
        }

        if (jumpEvent.applySprint()) {
            float f = jumpEvent.getYaw() * 0.017453292F;
            this.motionX -= MathHelper.sin(f) * 0.2F;
            this.motionZ += MathHelper.cos(f) * 0.2F;
        }

        this.isAirBorne = true;
        ForgeHooks.onLivingJump(((EntityLivingBase) (Object) this));
    }

    /**
     * OpenMyau {@code MixinEntityLivingBase.moveEntityWithHeading} — temporarily set
     * {@code rotationYaw} to silent packet yaw for {@code moveFlying} so remapped WASD
     * (fixStrafe) produces camera-relative world motion.
     */
    @Redirect(
        method = {"moveEntityWithHeading", "func_70612_e"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityLivingBase;moveFlying(FFF)V"
        )
    )
    private void redirectMoveFlying(EntityLivingBase self, float strafe, float forward, float friction) {
        if (!(self instanceof EntityPlayerSP)) {
            self.moveFlying(strafe, forward, friction);
            return;
        }

        StrafeEvent event = new StrafeEvent(strafe, forward, friction, self.rotationYaw);
        MinecraftForge.EVENT_BUS.post(event);
        strafe = event.getStrafe();
        forward = event.getForward();
        friction = event.getFriction();

        boolean swap = MoveFixHook.shouldUseServerMoveYaw();
        float savedYaw = self.rotationYaw;
        self.rotationYaw = swap ? PlayerUpdateHook.silentYawForMoveFix() : event.getYaw();
        self.moveFlying(strafe, forward, friction);
        self.rotationYaw = savedYaw;
    }

    @Inject(method = "isPotionActive(Lnet/minecraft/potion/Potion;)Z", at = @At("HEAD"), cancellable = true)
    private void isPotionActive(Potion p_isPotionActive_1_, final CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        // GNUClient has no anti-debuff module in this port.
    }

    @Redirect(method = "onLivingUpdate", at = @At(value  = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;moveEntityWithHeading(FF)V"))
    private void onMoveEntityWithHeadingRedirect(EntityLivingBase self, float originalStrafing, float originalForward) {
        if (self instanceof EntityPlayerSP) {
            PrePlayerMovementInputEvent event = new PrePlayerMovementInputEvent(originalForward, originalStrafing);

            MinecraftForge.EVENT_BUS.post(event);

            self.moveEntityWithHeading(event.strafe, event.forward);
        }
        else {
            self.moveEntityWithHeading(originalStrafing, originalForward);
        }
    }
}