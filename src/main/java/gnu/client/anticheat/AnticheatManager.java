package gnu.client.anticheat;

import gnu.client.anticheat.checks.AutoBlockCheck;
import gnu.client.anticheat.checks.BacktrackCheck;
import gnu.client.anticheat.checks.BlinkCheck;
import gnu.client.anticheat.checks.FlightCheck;
import gnu.client.anticheat.checks.GroundSpoofCheck;
import gnu.client.anticheat.checks.KillAuraCheck;
import gnu.client.anticheat.checks.LagAbuseCheck;
import gnu.client.anticheat.checks.LagrangeCheck;
import gnu.client.anticheat.checks.MultiAuraCheck;
import gnu.client.anticheat.checks.NoSlowCheck;
import gnu.client.anticheat.checks.PredictionCheck;
import gnu.client.anticheat.checks.ReachCheck;
import gnu.client.anticheat.checks.ScaffoldCheck;
import gnu.client.anticheat.checks.SpeedCheck;
import gnu.client.anticheat.checks.VelocityCheck;
import gnu.client.common.GnuLog;
import gnu.client.module.modules.combat.RavenAntiBot;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Other-player ClientAC. Observes entities + inbound packets; never mutates outbound traffic.
 */
public final class AnticheatManager implements ClientAntiCheatContext, PacketListener {

    public static final AnticheatManager INSTANCE = new AnticheatManager();

    private final CheckDataManager checkDataManager = new CheckDataManager();
    private final ObservationQueue observations = new ObservationQueue();
    private final ScaffoldCheck scaffoldCheck = new ScaffoldCheck();
    private final KillAuraCheck killAuraCheck = new KillAuraCheck();
    private final AutoBlockCheck autoBlockCheck = new AutoBlockCheck();
    private final NoSlowCheck noSlowCheck = new NoSlowCheck();
    private final BlinkCheck blinkCheck = new BlinkCheck();
    private final ReachCheck reachCheck = new ReachCheck();
    private final VelocityCheck velocityCheck = new VelocityCheck();
    private final SpeedCheck speedCheck = new SpeedCheck(checkDataManager);
    private final FlightCheck flightCheck = new FlightCheck();
    private final GroundSpoofCheck groundCheck = new GroundSpoofCheck();
    private final MultiAuraCheck multiAuraCheck = new MultiAuraCheck();
    private final PredictionCheck predictionCheck = new PredictionCheck();
    private final LagrangeCheck lagrangeCheck = new LagrangeCheck();
    private final BacktrackCheck backtrackCheck = new BacktrackCheck();
    private final LagAbuseCheck lagAbuseCheck = new LagAbuseCheck();

    private final Map<String, int[]> flagMap = new HashMap<String, int[]>();
    private final Map<String, Integer> alertCooldowns = new HashMap<String, Integer>();
    private final Set<String> whitelist = new HashSet<String>();

    private boolean active;
    private boolean packetsRegistered;
    private boolean scaffold = true;
    private boolean killAura = true;
    private boolean autoBlock = true;
    private boolean noSlow = true;
    private boolean blink = true;
    private boolean reach = true;
    private boolean velocity = true;
    private boolean speed = true;
    private boolean flight = true;
    private boolean ground = true;
    private boolean multiAura = true;
    private boolean prediction = true;
    private boolean lagrange = true;
    private boolean backtrack = true;
    private boolean lagAbuse = true;
    private boolean sound = true;
    private boolean ignoreBots = true;

    private AnticheatManager() {}

    public static AnticheatManager instance() {
        return INSTANCE;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            if (!packetsRegistered) {
                PacketEvents.register(this);
                packetsRegistered = true;
            }
        } else {
            if (packetsRegistered) {
                PacketEvents.unregister(this);
                packetsRegistered = false;
            }
            reset();
        }
    }

    public boolean isActive() {
        return active;
    }

    public void configure(boolean scaffold, boolean killAura, boolean autoBlock, boolean noSlow,
                          boolean blink, boolean reach, boolean velocity, boolean speed,
                          boolean flight, boolean ground, boolean multiAura, boolean prediction,
                          boolean lagrange, boolean backtrack, boolean lagAbuse,
                          boolean sound, boolean ignoreBots) {
        this.scaffold = scaffold;
        this.killAura = killAura;
        this.autoBlock = autoBlock;
        this.noSlow = noSlow;
        this.blink = blink;
        this.reach = reach;
        this.velocity = velocity;
        this.speed = speed;
        this.flight = flight;
        this.ground = ground;
        this.multiAura = multiAura;
        this.prediction = prediction;
        this.lagrange = lagrange;
        this.backtrack = backtrack;
        this.lagAbuse = lagAbuse;
        this.sound = sound;
        this.ignoreBots = ignoreBots;
    }

    @Override
    public boolean onSend(Object packet) {
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!active || packet == null)
            return false;
        try {
            if (packet instanceof S0BPacketAnimation) {
                S0BPacketAnimation anim = (S0BPacketAnimation) packet;
                if (anim.getAnimationType() == 0)
                    observations.offerSwing(anim.getEntityID());
            } else if (packet instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
                EntityPlayerSP self = Mc.player();
                if (self != null && vel.getEntityID() == self.getEntityId())
                    return false;
                observations.offerVelocity(
                        vel.getEntityID(),
                        PacketHelper.velocityMotionX(vel),
                        PacketHelper.velocityMotionY(vel),
                        PacketHelper.velocityMotionZ(vel));
            }
        } catch (Throwable t) {
            GnuLog.log("ANTICHEAT_ packet observe error: " + t);
        }
        return false;
    }

    public void tick() {
        if (!active || !Mc.isInGame())
            return;
        EntityPlayerSP self = Mc.player();
        World world = Mc.world();
        if (self == null || world == null)
            return;

        // 1) Drain packet observations before state update (race-safe).
        applyObservations(world, self);
        // 2) Update per-player deltas from rendered entities.
        checkDataManager.update(world);

        long currentTick = world.getTotalWorldTime();
        Map<EntityPlayer, PlayerCheckData> snapshot = new HashMap<EntityPlayer, PlayerCheckData>();
        Set<String> aliveNames = new HashSet<String>();
        for (Object obj : new ArrayList<Object>(world.playerEntities)) {
            if (!(obj instanceof EntityPlayer))
                continue;
            EntityPlayer player = (EntityPlayer) obj;
            if (player == self || CombatContext.isInvalidSubject(player))
                continue;
            if (ignoreBots && RavenAntiBot.isBot(player))
                continue;
            PlayerCheckData data = checkDataManager.get(player);
            if (data != null) {
                snapshot.put(player, data);
                aliveNames.add(player.getName());
            }
        }

        for (Map.Entry<EntityPlayer, PlayerCheckData> entry : snapshot.entrySet()) {
            EntityPlayer player = entry.getKey();
            PlayerCheckData data = entry.getValue();
            try {
                if (prediction)
                    predictionCheck.check(player, world, data, this);
                if (scaffold)
                    scaffoldCheck.check(player, world, data, this);
                if (killAura)
                    killAuraCheck.check(player, world, data, currentTick, this);
                if (autoBlock)
                    autoBlockCheck.check(player, data, currentTick, this);
                if (noSlow)
                    noSlowCheck.check(player, data, this);
                if (blink)
                    blinkCheck.check(player, data, this);
                if (lagrange)
                    lagrangeCheck.check(player, world, data, currentTick, this);
                if (lagAbuse)
                    lagAbuseCheck.check(player, world, data, currentTick, this);
                if (backtrack)
                    backtrackCheck.tickTracker(player, data);
                if (reach)
                    reachCheck.check(player, world, data, this);
                if (velocity)
                    velocityCheck.check(player, data, this);
                if (speed)
                    speedCheck.check(player, data, this);
                if (flight)
                    flightCheck.check(player, data, this);
                if (ground)
                    groundCheck.check(player, world, data, this);
                if (multiAura)
                    multiAuraCheck.check(player, world, data, currentTick, this);
                if (data.justTookHit) {
                    if (reach)
                        reachCheck.onVictimHit(player, world, snapshot, this);
                    if (backtrack)
                        backtrackCheck.onVictimHit(player, world, snapshot, this);
                }
            } catch (Throwable t) {
                GnuLog.log("ANTICHEAT_ check error on " + player.getName() + ": " + t);
            }
        }
        predictionCheck.pruneMissing(aliveNames);
        pruneFlags(world);
    }

    private void applyObservations(World world, EntityPlayerSP self) {
        List<Integer> swings = observations.pollSwings();
        for (int i = 0; i < swings.size(); i++) {
            int id = swings.get(i);
            Entity entity = world.getEntityByID(id);
            if (!(entity instanceof EntityPlayer) || entity == self)
                continue;
            PlayerCheckData data = checkDataManager.ensure((EntityPlayer) entity);
            if (data != null)
                data.notePacketSwing();
        }
        List<ObservationQueue.VelocityObs> vels = observations.pollVelocities();
        for (int i = 0; i < vels.size(); i++) {
            ObservationQueue.VelocityObs obs = vels.get(i);
            Entity entity = world.getEntityByID(obs.entityId);
            if (!(entity instanceof EntityPlayer) || entity == self)
                continue;
            PlayerCheckData data = checkDataManager.ensure((EntityPlayer) entity);
            if (data != null)
                data.noteVelocityPacket(obs.mx, obs.my, obs.mz);
        }
    }

    @Override
    public void receiveSignal(String playerName, String cheatName) {
        if (playerName == null || playerName.isEmpty() || cheatName == null)
            return;
        EntityPlayerSP self = Mc.player();
        World world = Mc.world();
        if (self == null || world == null)
            return;
        if (playerName.equalsIgnoreCase(self.getName()))
            return;
        if (isWhitelisted(playerName))
            return;

        int currentTime = (int) (world.getTotalWorldTime() / 20L);
        String flagKey = flagKey(playerName, cheatName);
        int[] flagData = flagMap.get(flagKey);
        if (flagData == null)
            flagData = new int[] { 0, currentTime };
        if (currentTime - flagData[1] > CheckRules.FLAG_WINDOW_SECONDS)
            flagData[0] = 0;
        flagData[0] += 1;
        flagData[1] = currentTime;
        flagMap.put(flagKey, flagData);

        int maxFlagCount = CheckRules.alertSignalsFor(cheatName);
        Integer lastAlert = alertCooldowns.get(flagKey);
        int last = lastAlert == null ? -CheckRules.ALERT_COOLDOWN_SECONDS : lastAlert;
        if (flagData[0] >= maxFlagCount && currentTime - last >= CheckRules.ALERT_COOLDOWN_SECONDS) {
            alertChat(playerName, cheatName);
            if (sound) {
                try {
                    self.playSound("random.orb", 0.35F, 1.15F);
                } catch (Throwable ignored) {
                }
            }
            alertCooldowns.put(flagKey, currentTime);
            flagMap.remove(flagKey);
        }
    }

    private void alertChat(String playerName, String cheatName) {
        try {
            EntityPlayerSP self = Mc.player();
            if (self == null)
                return;
            // Plain text only — no untrusted formatting injection from names.
            String safeName = playerName.replace('\u00a7', '?');
            String safeCheck = cheatName.replace('\u00a7', '?');
            self.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.DARK_GRAY + "["
                            + EnumChatFormatting.AQUA + "ClientAC"
                            + EnumChatFormatting.DARK_GRAY + "] "
                            + EnumChatFormatting.RED + safeName
                            + EnumChatFormatting.GRAY + " failed "
                            + EnumChatFormatting.RED + safeCheck));
            GnuLog.log("ANTICHEAT_ " + safeName + " failed " + safeCheck);
        } catch (Throwable ignored) {
        }
    }

    private void pruneFlags(World world) {
        int currentTime = (int) (world.getTotalWorldTime() / 20L);
        Map<String, int[]> next = new HashMap<String, int[]>();
        for (Map.Entry<String, int[]> entry : flagMap.entrySet()) {
            int[] flagData = entry.getValue();
            if (currentTime - flagData[1] <= CheckRules.FLAG_WINDOW_SECONDS)
                next.put(entry.getKey(), flagData);
        }
        flagMap.clear();
        flagMap.putAll(next);

        java.util.Iterator<Map.Entry<String, Integer>> it = alertCooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            if (currentTime - entry.getValue() > CheckRules.ALERT_COOLDOWN_SECONDS)
                it.remove();
        }
    }

    private boolean isWhitelisted(String playerName) {
        for (String name : whitelist) {
            if (name.equalsIgnoreCase(playerName))
                return true;
        }
        return false;
    }

    private static String flagKey(String playerName, String cheatName) {
        return playerName.toLowerCase(Locale.ROOT) + ":" + cheatName;
    }

    public void reset() {
        scaffoldCheck.reset();
        killAuraCheck.reset();
        autoBlockCheck.reset();
        noSlowCheck.reset();
        blinkCheck.reset();
        reachCheck.reset();
        velocityCheck.reset();
        speedCheck.reset();
        flightCheck.reset();
        groundCheck.reset();
        multiAuraCheck.reset();
        predictionCheck.reset();
        lagrangeCheck.reset();
        backtrackCheck.reset();
        lagAbuseCheck.reset();
        checkDataManager.reset();
        observations.clear();
        flagMap.clear();
        alertCooldowns.clear();
    }
}
