package gnu.client.utility;

import gnu.client.common.GnuLog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.Random;

/** Minimal utility helpers for rotation/lag modules (Raven subset). */
public final class Utils implements IMinecraftInstance {

    private static final Random RAND = new Random();

    private Utils() {}

    public static boolean nullCheck() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    public static int randomizeInt(int min, int max) {
        return RAND.nextInt(max - min + 1) + min;
    }

    public static Vec3 getLookVec(float yaw, float pitch) {
        float cosYaw = MathHelper.cos(-yaw * ((float) Math.PI / 180F) - (float) Math.PI);
        float sinYaw = MathHelper.sin(-yaw * ((float) Math.PI / 180F) - (float) Math.PI);
        float cosPitch = -MathHelper.cos(-pitch * ((float) Math.PI / 180F));
        float sinPitch = MathHelper.sin(-pitch * ((float) Math.PI / 180F));
        return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    public static void sendDebugMessage(String message) {
        GnuLog.log("DEBUG " + message);
    }

    public static boolean isFriended(EntityPlayer player) {
        return false;
    }

    public static boolean isTeammate(Entity entity) {
        if (entity == null || mc.thePlayer == null) {
            return false;
        }
        try {
            if (entity instanceof net.minecraft.entity.EntityLivingBase) {
                return mc.thePlayer.isOnSameTeam((net.minecraft.entity.EntityLivingBase) entity);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
