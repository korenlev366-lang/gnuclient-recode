package gnu.client.module.modules.combat.velocity;

import gnu.client.mixin.impl.accessors.IAccessorS12PacketEntityVelocity;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * wsamiaw GRIM parity — adaptive S12 scale, setback awareness, optional C0F hold.
 * Does not cancel velocity packets (unlike the old dig-cancel Grim).
 */
public final class GrimVelocity extends VelocityMode {

    private float grimCurrentH;
    private float grimCurrentV;
    private int grimAdaptTimer;
    private boolean grimSetback;
    private int grimTxTimer;
    private boolean pendingSetbackCheck;
    private final Deque<C0FPacketConfirmTransaction> delayedTransactions = new ArrayDeque<>();

    public GrimVelocity(VelocityModule parent) {
        super("Grim", parent);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        grimTxTimer = 0;
        flushDelayedTransactions();
        resetState();
    }

    @Override
    public void onUpdate(boolean pre) {
        if (!pre)
            return;

        grimAdaptTimer++;
        if (grimAdaptTimer >= Math.round(parent.grimAdaptTicks.getValue())) {
            grimAdaptTimer = 0;
            float step = parent.grimAdaptStep.getValue();
            if (grimSetback) {
                grimCurrentH = Math.min(parent.grimMaxH.getValue(), grimCurrentH + step);
                grimCurrentV = Math.min(1.0f, grimCurrentV + step);
                grimSetback = false;
            } else {
                grimCurrentH = Math.max(parent.grimMinH.getValue(), grimCurrentH - step);
                grimCurrentV = Math.max(0.0f, grimCurrentV - step);
            }
        }

        if (grimTxTimer > 0) {
            grimTxTimer--;
            if (grimTxTimer <= 0)
                flushDelayedTransactions();
        }
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null || mc.theWorld == null)
            return false;

        if (packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
            if (status.getEntity(mc.theWorld) == player && status.getOpCode() == 2)
                parent.allowNext = false;
            return false;
        }

        if (packet instanceof S08PacketPlayerPosLook) {
            S08PacketPlayerPosLook look = (S08PacketPlayerPosLook) packet;
            double dx = look.getX() - player.posX;
            double dy = look.getY() - player.posY;
            double dz = look.getZ() - player.posZ;
            if (dx * dx + dy * dy + dz * dz > 4.0) {
                grimSetback = true;
                pendingSetbackCheck = true;
            }
            return false;
        }

        if (!(packet instanceof S12PacketEntityVelocity))
            return false;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() != player.getEntityId())
            return false;

        // Match wsamiaw order: arm tx window first, then pending-setback recovery may override.
        grimSetback = false;
        grimAdaptTimer = 0;
        if (parent.grimTransactions.getValue())
            grimTxTimer = Math.round(parent.grimTxDelay.getValue());

        if (pendingSetbackCheck) {
            pendingSetbackCheck = false;
            grimSetback = true;
            grimTxTimer = 0;
            flushDelayedTransactions();
            grimCurrentH = parent.grimMaxH.getValue();
            grimCurrentV = 1.0f;
        }

        // Fake Check: only scale after real hurt (S19), unless Fake Check is off.
        if (parent.allowNext && parent.fakeCheck.getValue())
            return false;

        parent.allowNext = true;
        parent.chanceCounter = parent.chanceCounter % 100 + Math.round(parent.chance.getValue());
        if (parent.chanceCounter < 100)
            return false;

        double gh = grimCurrentH;
        double gv = grimCurrentV;
        IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) vel;
        if (gh > 0.0) {
            accessor.setMotionX((int) (vel.getMotionX() * gh));
            accessor.setMotionZ((int) (vel.getMotionZ() * gh));
        } else {
            accessor.setMotionX(0);
            accessor.setMotionZ(0);
        }
        if (gv > 0.0)
            accessor.setMotionY((int) (vel.getMotionY() * gv));
        else
            accessor.setMotionY(0);

        return false;
    }

    @Override
    public boolean onSend(Object packet) {
        if (!parent.grimTransactions.getValue() || grimTxTimer <= 0)
            return false;
        if (!(packet instanceof C0FPacketConfirmTransaction))
            return false;
        delayedTransactions.offer((C0FPacketConfirmTransaction) packet);
        return true;
    }

    private void resetState() {
        grimCurrentH = parent.grimStartH.getValue();
        grimCurrentV = parent.grimStartV.getValue();
        grimAdaptTimer = 0;
        grimSetback = false;
        grimTxTimer = 0;
        pendingSetbackCheck = false;
        delayedTransactions.clear();
    }

    private void flushDelayedTransactions() {
        while (!delayedTransactions.isEmpty()) {
            C0FPacketConfirmTransaction packet = delayedTransactions.poll();
            if (packet != null)
                Mc.addToSendQueue(packet);
        }
    }
}
