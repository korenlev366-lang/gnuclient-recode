package gnu.client.script;

import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;

/**
 * Script-facing {@code client} accessor — a stateless singleton facade over
 * {@link Mc}. Exposes the local player, wall-clock time, and a player-eye
 * raycast to script bodies.
 *
 * <p>This class holds NO game-object state across calls (no cached player/world
 * fields). Every method re-resolves through {@code Mc} so the script class
 * remains unloadable when its module is disabled.
 */
public final class Client {

    public static final Client INSTANCE = new Client();

    private Client() {}

    /** The local {@code EntityPlayerSP}, or {@code null} if not in-game. */
    public Object getPlayer() {
        return Mc.player();
    }

    /** Wall-clock millis ({@code System.currentTimeMillis()}). */
    public long time() {
        return System.currentTimeMillis();
    }

    /**
     * Ray-cast from the local player's eyes along the given yaw/pitch, returning
     * the {@code MovingObjectPosition} (or {@code null} on miss / no world).
     */
    public Object raycastBlock(double distance, float yaw, float pitch) {
        MovingObjectPosition hit = Mc.raycastBlocks(distance, yaw, pitch);
        return hit;
    }

    public double getMotionX() {
        return Mc.getMotionX();
    }

    public double getMotionY() {
        return Mc.getMotionY();
    }

    public double getMotionZ() {
        return Mc.getMotionZ();
    }

    public float getYaw() {
        return Mc.getYaw();
    }

    public float getPitch() {
        return Mc.getPitch();
    }

    public boolean isOnGround() {
        return Mc.isOnGround();
    }

    public boolean isSneaking() {
        return Mc.isSneaking();
    }

    public boolean isSprinting() {
        return Mc.isClientSprinting();
    }

    public float getTimerSpeed() {
        return Mc.getTimerSpeed();
    }

    public void setTimerSpeed(float speed) {
        Mc.setTimerSpeed(speed);
    }

    public void resetTimer() {
        Mc.resetTimer();
    }

    public void setRotation(float yaw, float pitch) {
        Mc.setRotation(yaw, pitch);
    }

    public void setMotion(double x, double y, double z) {
        Mc.setMotion(x, y, z);
    }

    public double getPosX() {
        return Mc.entityPosX(Mc.player());
    }

    public double getPosY() {
        return Mc.entityPosY(Mc.player());
    }

    public double getPosZ() {
        return Mc.entityPosZ(Mc.player());
    }

    /** Drive vanilla jump input (MovementInput.jump + keyBindJump state). */
    public void setJump(boolean jump) {
        Mc.setJumpInput(Mc.player(), jump);
    }

    public boolean isRiding() {
        return Mc.isRiding();
    }

    /** Riding entity ({@code Entity}) or null. */
    public Object getRidingEntity() {
        return Mc.getRidingEntity(Mc.player());
    }

    public void setRidingMotion(double x, double y, double z) {
        Mc.setEntityMotion(asEntity(getRidingEntity()), x, y, z);
    }

    public double entityPosX(Object entity) {
        return Mc.entityPosX(asEntity(entity));
    }

    public double entityPosY(Object entity) {
        return Mc.entityPosY(asEntity(entity));
    }

    public double entityPosZ(Object entity) {
        return Mc.entityPosZ(asEntity(entity));
    }

    public void setEntityPosition(Object entity, double x, double y, double z) {
        Mc.setEntityPosition(asEntity(entity), x, y, z);
    }

    public void setEntityVelocity(Object entity, double x, double y, double z) {
        Mc.setEntityVelocity(asEntity(entity), x, y, z);
    }

    public void setEntityYaw(Object entity, float yaw) {
        Mc.setEntityYaw(asEntity(entity), yaw);
    }

    /** {@code C0C} steer packet for mounted vehicles (boat/horse/pig/minecart). */
    public void sendSteer(float strafe, float forward, boolean jump, boolean unmount) {
        Mc.sendSteerVehicle(strafe, forward, jump, unmount);
    }

    /** {@code C07 RELEASE_USE_ITEM} — clears server item-use slow while eating/blocking. */
    public void releaseUseItem() {
        Mc.sendReleaseUseItem(Mc.player());
    }

    /** {@code C09} hotbar slot flick (brief slot swap to reset use state). */
    public void heldItemChangeFlicker() {
        Mc.sendHeldItemChangeFlicker();
    }

    public void setSprintKey(boolean pressed) {
        Mc.setSprintKeyState(pressed);
    }

    public void setForwardKey(boolean pressed) {
        Mc.setForwardKeyState(pressed);
    }

    public void setBackKey(boolean pressed) {
        Mc.setBackKeyState(pressed);
    }

    public void setLeftKey(boolean pressed) {
        Mc.setLeftKeyState(pressed);
    }

    public void setRightKey(boolean pressed) {
        Mc.setRightKeyState(pressed);
    }

    /** Override {@code MovementInput} after vanilla key read (script {@code patchMovementInput} hook). */
    public void setMovementInput(Object movInput, float moveForward, float moveStrafe, boolean jump) {
        if (movInput instanceof net.minecraft.util.MovementInput) {
            Mc.setMovementInput((net.minecraft.util.MovementInput) movInput,
                    moveForward, moveStrafe, jump);
        }
    }

    /** {@code EntityLivingBase.setSprinting} — pairs with forward movement for bhop speed. */
    public void setSprinting(boolean sprinting) {
        Mc.setClientSprinting(Mc.player(), sprinting);
    }

    /** Force local onGround after setback snap (pairs with C03 ground sync). */
    public void setOnGround(boolean onGround) {
        Mc.setOnGround(Mc.player(), onGround);
    }

    /** Teleport local player to world coords (micro-step / blink fly). */
    public void setPlayerPosition(double x, double y, double z) {
        Mc.setEntityPosition(Mc.player(), x, y, z);
    }

    /** Packet sprint state the server last ack'd ({@code serverSprintState}). */
    public boolean isServerSprinting() {
        return Mc.getServerSprintState();
    }

    /** {@code C0B START_SPRINTING} when server thinks we are not sprinting. */
    public void sendSprintStart() {
        Mc.sendSprintActionPacket(Mc.player(), true);
    }

    /** {@code C0B STOP_SPRINTING} when server thinks we are sprinting. */
    public void sendSprintStop() {
        Mc.sendSprintActionPacket(Mc.player(), false);
    }

    /** {@code PlayerControllerMP.attackEntity} — real C02 to server. */
    public boolean attackEntity(Object entity) {
        return Mc.attackEntity(asEntity(entity));
    }

    public boolean hasScreen() {
        return Mc.hasScreen();
    }

    public boolean isInventoryScreen() {
        return Mc.isInventoryScreen();
    }

    public Object getCurrentScreen() {
        return Mc.currentScreen();
    }

    public void rememberServer() {
        Mc.rememberServer();
    }

    public boolean hasLastServer() {
        return Mc.hasLastServer();
    }

    public String getLastServerIp() {
        return Mc.getLastServerIp();
    }

    public boolean reconnectToLastServer() {
        return Mc.reconnectToLastServer();
    }

    /** Block pos {x,y,z} from eye raycast, or {@code null}. */
    public int[] raycastBlockPos(double distance, float yaw, float pitch) {
        return Mc.raycastBlockPos(distance, yaw, pitch);
    }

    public boolean raycastHitBlock(Object mop) {
        return mop instanceof MovingObjectPosition
                && ((MovingObjectPosition) mop).typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    public void sendConfirmTransaction(int windowId, short uid, boolean accepted) {
        Mc.sendConfirmTransaction(windowId, uid, accepted);
    }

    /**
     * Send a C07 dig packet. {@code actionName} is an enum name such as
     * {@code START_DESTROY_BLOCK}, {@code STOP_DESTROY_BLOCK}, {@code RELEASE_USE_ITEM}.
     */
    public void sendDig(String actionName, int x, int y, int z, int face) {
        if (actionName == null)
            return;
        try {
            net.minecraft.network.play.client.C07PacketPlayerDigging.Action action =
                    net.minecraft.network.play.client.C07PacketPlayerDigging.Action.valueOf(actionName);
            Mc.sendDig(action, x, y, z, face);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static Entity asEntity(Object entity) {
        return entity instanceof Entity ? (Entity) entity : null;
    }
}
