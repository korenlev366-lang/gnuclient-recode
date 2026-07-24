package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public final class ScaffoldCheck {
    private final Map<String, CheckBuffer> supportBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> rotationBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> pitchBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> edgeBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, Long> lastFlag = new HashMap<String, Long>();

    public void check(EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null || world == null)
            return;
        String name = player.getName();

        CheckBuffer supportBuffer = buffer(supportBuffers, name);
        CheckBuffer rotationBuffer = buffer(rotationBuffers, name);
        CheckBuffer pitchBuffer = buffer(pitchBuffers, name);
        CheckBuffer edgeBuffer = buffer(edgeBuffers, name);

        ItemStack held = player.getHeldItem();
        boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
        if (!holdingBlock || CombatContext.isMovementEnvironmentExempt(player) || data.recentlyTeleported()) {
            supportBuffer.decay(0.5);
            rotationBuffer.decay(0.5);
            pitchBuffer.decay(0.5);
            edgeBuffer.decay(0.5);
            return;
        }

        boolean moving = data.horizontalDelta > 0.12;
        boolean hasBlockBelow = hasSolidBelow(player, world, 1.0);
        boolean hasRecentSupport = hasSolidBelow(player, world, 1.35);
        boolean nearEdge = player.onGround && !hasSolidBelowOffset(player, world, data.deltaX, data.deltaZ);
        boolean bridgeContext = moving && (nearEdge || !hasBlockBelow || (hasRecentSupport && data.pitch > 55.0F));

        if (bridgeContext && data.horizontalDelta > 0.18 && hasRecentSupport)
            supportBuffer.flag(1.0, 999.0);
        else
            supportBuffer.decay(0.25);

        if (bridgeContext && (data.yawAcceleration > 45.0F || data.pitchAcceleration > 18.0F || data.yawDelta > 110.0F))
            rotationBuffer.flag(1.25, 999.0);
        else
            rotationBuffer.decay(0.3);

        if (bridgeContext && data.pitch > 63.0F && data.pitchDelta < 0.35F && data.yawDelta < 3.5F)
            pitchBuffer.flag(0.9, 999.0);
        else
            pitchBuffer.decay(0.2);

        // Legit bridging often walks edge without sneak — need pitch lock too.
        if (nearEdge && moving && !player.isSneaking() && data.groundTicks > 5 && data.pitch > 50.0F)
            edgeBuffer.flag(1.0, 999.0);
        else
            edgeBuffer.decay(0.25);

        boolean failed = (supportBuffer.get() > CheckRules.SC_SUPPORT_NEED
                && rotationBuffer.get() > CheckRules.SC_ROTATION_NEED)
                || (supportBuffer.get() > CheckRules.SC_SUPPORT_NEED + 1.0
                && pitchBuffer.get() > CheckRules.SC_PITCH_NEED)
                || (edgeBuffer.get() > CheckRules.SC_EDGE_NEED
                && pitchBuffer.get() > 3.0);
        if (failed) {
            long now = System.currentTimeMillis();
            Long last = lastFlag.get(name);
            if (last == null || now - last > CheckRules.SC_FLAG_COOLDOWN_MS) {
                context.receiveSignal(name, "Scaffold");
                lastFlag.put(name, now);
                supportBuffer.reset();
                rotationBuffer.reset();
                pitchBuffer.reset();
                edgeBuffer.reset();
            }
        }
    }

    private static boolean hasSolidBelow(EntityPlayer player, World world, double below) {
        for (double xOffset = -0.3; xOffset <= 0.3; xOffset += 0.3) {
            for (double zOffset = -0.3; zOffset <= 0.3; zOffset += 0.3) {
                BlockPos pos = new BlockPos(
                        MathHelper.floor_double(player.posX + xOffset),
                        MathHelper.floor_double(player.posY - below),
                        MathHelper.floor_double(player.posZ + zOffset));
                if (!world.isAirBlock(pos))
                    return true;
            }
        }
        return false;
    }

    private static boolean hasSolidBelowOffset(EntityPlayer player, World world, double motionX, double motionZ) {
        BlockPos pos = new BlockPos(
                MathHelper.floor_double(player.posX + motionX * 2.0),
                MathHelper.floor_double(player.posY - 1.0),
                MathHelper.floor_double(player.posZ + motionZ * 2.0));
        return !world.isAirBlock(pos);
    }

    private static CheckBuffer buffer(Map<String, CheckBuffer> map, String name) {
        CheckBuffer buffer = map.get(name);
        if (buffer == null) {
            buffer = new CheckBuffer();
            map.put(name, buffer);
        }
        return buffer;
    }

    public void reset() {
        supportBuffers.clear();
        rotationBuffers.clear();
        pitchBuffers.clear();
        edgeBuffers.clear();
        lastFlag.clear();
    }
}
