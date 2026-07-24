package gnu.client.anticheat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;

/** Per-player observation state (OpenMyAU PlayerCheckData + packet swing/velocity hooks). */
public final class PlayerCheckData {
    private static final double TELEPORT_DISTANCE_THRESHOLD = 8.0;
    private static final int TELEPORT_EXEMPT_TICKS = 4;
    private static final int MAX_SINCE_HURT_TICKS = 999;
    private static final int RECENT_HURT_TICKS = 10;
    private static final int INITIAL_EXEMPT_TICKS = 5;
    private static final double GROUND_HORIZONTAL_LIMIT = 0.34;
    private static final double AIR_HORIZONTAL_LIMIT = 0.58;
    private static final double SPRINT_LIMIT_BONUS = 0.08;
    private static final double SNEAK_LIMIT_BONUS = 0.02;
    private static final double USING_ITEM_LIMIT_BONUS = 0.02;
    private static final double SPEED_POTION_LIMIT_BONUS = 0.075;
    private static final double JUMP_POTION_LIMIT_BONUS = 0.04;
    private static final double RECENT_HURT_LIMIT_BONUS = 0.35;

    public final String name;

    public double lastX;
    public double lastY;
    public double lastZ;
    public double x;
    public double y;
    public double z;
    public double deltaX;
    public double deltaY;
    public double deltaZ;
    public double horizontalDelta;
    public double lastHorizontalDelta;
    public double totalDelta;

    public float yaw;
    public float pitch;
    public float lastYaw;
    public float lastPitch;
    public float yawDelta;
    public float pitchDelta;
    public float lastYawDelta;
    public float lastPitchDelta;
    public float yawAcceleration;
    public float pitchAcceleration;

    public boolean onGround;
    public boolean lastOnGround;
    public boolean lastUsingItem;
    public boolean usingItem;
    public boolean lastSwinging;
    public boolean swinging;
    public boolean packetSwingPending;
    public int swingAge = 999;
    public int groundTicks;
    public int airTicks;
    public int hurtTicks;
    public int lastHurtTime;
    public int sinceHurtTicks = 999;
    public boolean justTookHit;
    public int teleportTicks;
    public int existedTicks;
    public int stillTicks;
    public int burstTicks;
    public int usingItemTicks;
    public int swingTicks;
    public float observedFallDistance;

    /** Server-applied knockback window from S12. */
    public int velocityPacketTicks = -1;
    public double expectedVelH;
    public double expectedVelY;

    public PlayerCheckData(EntityPlayer player) {
        this.name = player.getName();
        this.x = this.lastX = player.posX;
        this.y = this.lastY = player.posY;
        this.z = this.lastZ = player.posZ;
        // Head look is what combat modules aim with for remote players.
        this.yaw = this.lastYaw = player.rotationYawHead;
        this.pitch = this.lastPitch = player.rotationPitch;
        this.onGround = this.lastOnGround = player.onGround;
        this.usingItem = this.lastUsingItem = player.isUsingItem() || player.isBlocking() || player.isEating();
        this.swinging = this.lastSwinging = player.isSwingInProgress || player.swingProgress > 0.0F;
        this.lastHurtTime = player.hurtTime;
    }

    public void notePacketSwing() {
        packetSwingPending = true;
        swingAge = 0;
    }

    public void noteVelocityPacket(double motionX, double motionY, double motionZ) {
        // S12 values are /8000 in vanilla application; packet ints are already raw.
        expectedVelH = Math.hypot(motionX / 8000.0, motionZ / 8000.0);
        expectedVelY = motionY / 8000.0;
        velocityPacketTicks = 0;
    }

    public void update(EntityPlayer player) {
        existedTicks++;
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastPitch = pitch;
        lastOnGround = onGround;
        lastHorizontalDelta = horizontalDelta;
        lastYawDelta = yawDelta;
        lastPitchDelta = pitchDelta;
        lastUsingItem = usingItem;
        lastSwinging = swinging;

        x = player.posX;
        y = player.posY;
        z = player.posZ;
        yaw = player.rotationYawHead;
        pitch = player.rotationPitch;
        onGround = player.onGround;
        usingItem = player.isUsingItem() || player.isBlocking() || player.isEating();

        boolean entitySwing = player.isSwingInProgress || player.swingProgress > 0.0F;
        swinging = entitySwing || packetSwingPending;
        packetSwingPending = false;
        if (swinging && !lastSwinging)
            swingAge = 0;
        else
            swingAge = Math.min(999, swingAge + 1);

        deltaX = x - lastX;
        deltaY = y - lastY;
        deltaZ = z - lastZ;
        horizontalDelta = Math.hypot(deltaX, deltaZ);
        totalDelta = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(yaw - lastYaw));
        pitchDelta = Math.abs(pitch - lastPitch);
        yawAcceleration = Math.abs(yawDelta - lastYawDelta);
        pitchAcceleration = Math.abs(pitchDelta - lastPitchDelta);

        teleportTicks = totalDelta > TELEPORT_DISTANCE_THRESHOLD ? TELEPORT_EXEMPT_TICKS : Math.max(0, teleportTicks - 1);
        groundTicks = onGround ? groundTicks + 1 : 0;
        airTicks = onGround ? 0 : airTicks + 1;

        justTookHit = player.hurtTime > lastHurtTime || (lastHurtTime == 0 && player.hurtTime >= 8);
        lastHurtTime = player.hurtTime;
        hurtTicks = player.hurtTime > 0 ? player.hurtTime : Math.max(0, hurtTicks - 1);
        sinceHurtTicks = player.hurtTime > 0 ? 0 : Math.min(MAX_SINCE_HURT_TICKS, sinceHurtTicks + 1);
        usingItemTicks = usingItem ? usingItemTicks + 1 : 0;
        swingTicks = swinging ? swingTicks + 1 : 0;
        stillTicks = totalDelta < 0.003 && yawDelta < 0.05F && pitchDelta < 0.05F ? stillTicks + 1 : 0;
        burstTicks = totalDelta > 0.8 && totalDelta < TELEPORT_DISTANCE_THRESHOLD ? burstTicks + 1 : 0;

        if (velocityPacketTicks >= 0)
            velocityPacketTicks++;

        if (!onGround && deltaY < 0.0) {
            observedFallDistance += (float) -deltaY;
        } else if (onGround) {
            observedFallDistance = 0.0F;
        }
    }

    public boolean recentlyTeleported() {
        return teleportTicks > 0 || existedTicks < INITIAL_EXEMPT_TICKS;
    }

    public boolean recentlyHurt() {
        return sinceHurtTicks <= RECENT_HURT_TICKS || hurtTicks > 0;
    }

    public boolean startedSwinging() {
        return swinging && !lastSwinging;
    }

    public boolean recentlySwung() {
        return swingAge <= 3;
    }

    public boolean startedUsingItem() {
        return usingItem && !lastUsingItem;
    }

    public double predictedHorizontalLimit(EntityPlayer player) {
        double limit = onGround ? GROUND_HORIZONTAL_LIMIT : AIR_HORIZONTAL_LIMIT;
        if (player.isSprinting())
            limit += SPRINT_LIMIT_BONUS;
        if (player.isSneaking())
            limit += SNEAK_LIMIT_BONUS;
        if (player.isUsingItem())
            limit += USING_ITEM_LIMIT_BONUS;
        if (player.isPotionActive(Potion.moveSpeed)) {
            int amplifier = player.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1;
            limit += amplifier * SPEED_POTION_LIMIT_BONUS;
        }
        if (player.isPotionActive(Potion.jump))
            limit += JUMP_POTION_LIMIT_BONUS;
        if (recentlyHurt())
            limit += RECENT_HURT_LIMIT_BONUS;
        return limit;
    }
}
