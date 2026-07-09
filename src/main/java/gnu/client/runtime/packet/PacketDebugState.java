package gnu.client.runtime.packet;

import gnu.client.common.GnuLog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight packet-derived position cache for diagnostics visuals.
 */
public final class PacketDebugState {

    public static final class Vec3 {
        public final double x;
        public final double y;
        public final double z;

        public Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static volatile boolean selfValid;
    private static volatile double selfX;
    private static volatile double selfY;
    private static volatile double selfZ;
    private static volatile int activeTargetId = -1;

    private static final Map<Integer, Vec3> TARGET_REAL = new ConcurrentHashMap<>();
    private static final Map<Integer, Vec3> TARGET_BACKTRACKED = new ConcurrentHashMap<>();

    private PacketDebugState() {}

    public static void captureSentPlayerPacket(Object packet) {
        if (!PacketHelper.isPlayerMovement(packet))
            return;

        if (!PacketHelper.c03HasPosition(packet))
            return;

        selfX = PacketHelper.c03PosX(packet);
        selfY = PacketHelper.c03PosY(packet);
        selfZ = PacketHelper.c03PosZ(packet);
        selfValid = true;
        GnuLog.log("PKT_DEBUG self C03 captured x=" + selfX + " y=" + selfY + " z=" + selfZ);
    }

    public static boolean hasSelfPosition() {
        return selfValid;
    }

    public static Vec3 selfPosition() {
        if (!selfValid)
            return null;
        return new Vec3(selfX, selfY, selfZ);
    }

    public static void setActiveTarget(int entityId) {
        activeTargetId = entityId;
    }

    public static int activeTargetId() {
        return activeTargetId;
    }

    public static void clearTarget(int entityId) {
        TARGET_REAL.remove(entityId);
        TARGET_BACKTRACKED.remove(entityId);
        if (activeTargetId == entityId)
            activeTargetId = -1;
    }

    public static void clearAllTargets() {
        TARGET_REAL.clear();
        TARGET_BACKTRACKED.clear();
        activeTargetId = -1;
    }

    public static void updateTargetBacktracked(int entityId, double x, double y, double z) {
        if (entityId < 0)
            return;
        TARGET_BACKTRACKED.put(entityId, new Vec3(x, y, z));
    }

    public static void updateTargetReal(int entityId, double x, double y, double z) {
        if (entityId < 0)
            return;
        TARGET_REAL.put(entityId, new Vec3(x, y, z));
    }

    public static Vec3 targetBacktracked(int entityId) {
        return TARGET_BACKTRACKED.get(entityId);
    }

    public static Vec3 targetReal(int entityId) {
        return TARGET_REAL.get(entityId);
    }
}
