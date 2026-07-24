package gnu.client.anticheat.predict;

import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

/** Per-player dead-reckoning state for the predictive engine. */
public final class PredictedPlayerState {
    public double x;
    public double y;
    public double z;
    public double vx;
    public double vy;
    public double vz;
    public boolean onGround;
    public boolean initialized;
    public int ticksTracked;
    public MovementModel.Phase phase = MovementModel.Phase.IDLE;

    public void syncFromObservation(PlayerCheckData data) {
        if (data == null)
            return;
        x = data.x;
        y = data.y;
        z = data.z;
        vx = data.deltaX;
        vy = data.deltaY;
        vz = data.deltaZ;
        onGround = data.onGround;
        initialized = true;
        ticksTracked++;
        phase = resolvePhase(data);
    }

    public void syncFromPlayer(EntityPlayer player, PlayerCheckData data) {
        if (player == null)
            return;
        if (data != null) {
            syncFromObservation(data);
            return;
        }
        x = player.posX;
        y = player.posY;
        z = player.posZ;
        vx = player.posX - player.lastTickPosX;
        vy = player.posY - player.lastTickPosY;
        vz = player.posZ - player.lastTickPosZ;
        onGround = player.onGround;
        initialized = true;
        ticksTracked++;
        phase = onGround ? MovementModel.Phase.WALKING : MovementModel.Phase.FALLING;
    }

    public static MovementModel.Phase resolvePhase(PlayerCheckData data) {
        if (data == null)
            return MovementModel.Phase.IDLE;
        if (data.horizontalDelta < 0.003 && Math.abs(data.deltaY) < 0.003)
            return MovementModel.Phase.IDLE;
        if (data.onGround)
            return MovementModel.Phase.WALKING;
        if (data.deltaY > 0.05)
            return MovementModel.Phase.JUMPING;
        return MovementModel.Phase.FALLING;
    }

    public PredictedPlayerState copy() {
        PredictedPlayerState c = new PredictedPlayerState();
        c.x = x;
        c.y = y;
        c.z = z;
        c.vx = vx;
        c.vy = vy;
        c.vz = vz;
        c.onGround = onGround;
        c.initialized = initialized;
        c.ticksTracked = ticksTracked;
        c.phase = phase;
        return c;
    }
}
