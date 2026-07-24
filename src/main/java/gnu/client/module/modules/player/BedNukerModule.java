package gnu.client.module.modules.player;

import gnu.client.mixin.impl.accessors.IAccessorPlayerControllerMP;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.player.scaffold.ScaffoldModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.PlayerUpdateHook;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.runtime.packet.PacketUtil;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;
import gnu.client.utility.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;

/**
 * BedWars bed breaker — settings and dig logic ported from wsamiaw {@code BedNuker}.
 */
public final class BedNukerModule extends Module implements PacketListener {

    public static final int ROTATION_PRIORITY = MoveFixUtil.BED_NUKER_MOVE_FIX_PRIORITY;

    private static final int MODE_LEGIT = 0;
    private static final int MODE_SWAP = 1;

    private static final int VELOCITY_NONE = 0;
    private static final int VELOCITY_CANCEL = 1;
    private static final int VELOCITY_DELAY = 2;

    private static final int MOVEFIX_NONE = 0;
    private static final int MOVEFIX_SILENT = 1;
    private static final int MOVEFIX_STRICT = 2;

    private static final int SHOW_NONE = 0;
    private static final int SHOW_DEFAULT = 1;
    private static final int SHOW_HUD = 2;

    private static final List<String> MODES = Arrays.asList("Legit", "Swap");
    private static final List<String> VELOCITY_MODES = Arrays.asList("None", "Cancel", "Delay");
    private static final List<String> MOVEFIX_MODES = Arrays.asList("None", "Silent", "Strict");
    private static final List<String> SHOW_MODES = Arrays.asList("None", "Default", "HUD");

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", MODE_LEGIT, MODES));
    private final SliderSetting range = addSetting(new SliderSetting("Range", 4.5f, 3.0f, 6.0f, 0.1f));
    private final SliderSetting speed = addSetting(new SliderSetting("Speed", 0f, 0f, 100f, 1f));
    private final BoolSetting groundSpoof = addSetting(new BoolSetting("Ground spoof", false));
    private final ModeSetting ignoreVelocity = addSetting(new ModeSetting("Ignore velocity", VELOCITY_NONE, VELOCITY_MODES));
    private final BoolSetting surroundings = addSetting(new BoolSetting("Surroundings", true));
    private final BoolSetting toolCheck = addSetting(new BoolSetting("Tool check", true));
    private final BoolSetting whitelist = addSetting(new BoolSetting("Whitelist", true));
    private final BoolSetting swing = addSetting(new BoolSetting("Swing", true));
    private final ModeSetting moveFix = addSetting(new ModeSetting("Move fix", MOVEFIX_SILENT, MOVEFIX_MODES));
    private final ModeSetting showTarget = addSetting(new ModeSetting("Show target", SHOW_DEFAULT, SHOW_MODES));
    private final ModeSetting showProgress = addSetting(new ModeSetting("Show progress", SHOW_DEFAULT, SHOW_MODES));

    private final Color colorRed = new Color(0xFF5555);
    private final Color colorYellow = new Color(0xFFFF55);
    private final Color colorGreen = new Color(0x55FF55);
    private final Color colorHud = new Color(0x55FFFF);

    private final ArrayList<BlockPos> bedWhitelist = new ArrayList<BlockPos>();
    private final Queue<Object> delayedVelocity = new ArrayDeque<Object>();

    private BlockPos targetBed;
    private int breakStage;
    private int tickCounter;
    private float breakProgress;
    private boolean isBed;
    private int savedSlot = -1;
    private boolean readyToBreak;
    private boolean breaking;
    private boolean waitingForStart;
    private long whitelistScanAt = -1L;
    private long retargetAt;

    public BedNukerModule() {
        super("BedNuker", "Breaks nearby enemy beds (wsamiaw BedNuker)", Category.MISC);
    }

    public static BedNukerModule activeInstance() {
        Module module = ModuleManager.instance().getModule("BedNuker");
        if (module instanceof BedNukerModule && module.isEnabled())
            return (BedNukerModule) module;
        return null;
    }

    public static boolean shouldCancelVanillaClick() {
        BedNukerModule bed = activeInstance();
        if (bed == null)
            return false;
        if (bed.isReady())
            return true;
        if (bed.targetBed == null)
            return false;
        MovingObjectPosition mop = Mc.objectMouseOver();
        return mop != null && mop.typeOfHit == MovingObjectType.BLOCK;
    }

    public static boolean shouldCancelSlotChange() {
        BedNukerModule bed = activeInstance();
        return bed != null && bed.savedSlot != -1;
    }

    public static void onPreUpdate(Object player) {
        BedNukerModule bed = activeInstance();
        if (bed != null)
            bed.preUpdate(player);
    }

    public static void patchMovementInput(Object movInput) {
        BedNukerModule bed = activeInstance();
        if (bed == null || movInput == null)
            return;
        if (bed.moveFix.getIndex() != MOVEFIX_SILENT)
            return;
        if (!MoveFixUtil.hasMoveFixPriority(ROTATION_PRIORITY) || !MoveFixUtil.isForwardPressed())
            return;
        net.minecraft.util.MovementInput input = (net.minecraft.util.MovementInput) movInput;
        float[] fixed = MoveFixUtil.fixStrafe(
                Mc.getYaw(), RotationState.getSmoothedYaw(), input.sneak);
        input.moveForward = fixed[0];
        input.moveStrafe = fixed[1];
    }

    public boolean isReady() {
        return targetBed != null && readyToBreak;
    }

    public boolean isBreaking() {
        return targetBed != null && breaking;
    }

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }

    @Override
    public void onEnable() {
        PacketEvents.register(this);
        resetBreaking();
        savedSlot = -1;
        flushDelayedVelocity();
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        restoreSlot();
        resetBreaking();
        savedSlot = -1;
        flushDelayedVelocity();
        clearRotationStateIfOwned();
    }

    @Override
    public void onTick() {
        if (!Mc.isInGame()) {
            restoreSlot();
            resetBreaking();
            clearRotationStateIfOwned();
            return;
        }
        if (isScaffoldActive()) {
            restoreSlot();
            resetBreaking();
            clearRotationStateIfOwned();
            return;
        }

        runWhitelistScanIfDue();

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null)
            return;

        if (targetBed != null) {
            if (world.isAirBlock(targetBed) || !canReach(targetBed, range.getValue())) {
                restoreSlot();
                resetBreaking();
            } else if (!isBed) {
                BlockPos nearestBed = findNearestBed();
                if (nearestBed != null && world.getBlockState(nearestBed).getBlock() instanceof BlockBed)
                    resetBreaking();
            }
        }

        if (targetBed != null) {
            digTick(player, world);
            if (targetBed != null)
                return;
        }

        if (player.capabilities.allowEdit && System.currentTimeMillis() >= retargetAt) {
            targetBed = findNearestBed();
            breakStage = 0;
            tickCounter = 0;
            breakProgress = 0.0f;
            isBed = targetBed != null && world.getBlockState(targetBed).getBlock() instanceof BlockBed;
            restoreSlot();
            if (targetBed != null)
                readyToBreak = true;
        }

        if (targetBed == null)
            flushDelayedVelocity();
    }

    @Override
    public void onOverlay(Object scaledResolution) {
        if (!(scaledResolution instanceof ScaledResolution) || targetBed == null)
            return;
        if (isBed && surroundings.getValue())
            return;
        if (showProgress.getIndex() == SHOW_NONE)
            return;

        ScaledResolution sr = (ScaledResolution) scaledResolution;
        FontRenderer fr = Mc.mc().fontRendererObj;
        if (fr == null)
            return;

        String text = String.format("%d%%", (int) (calcProgress() * 100.0f));
        int width = fr.getStringWidth(text);
        int color = getProgressColor(showProgress.getIndex()).getRGB() & 0xFFFFFF | 0xBF000000;
        fr.drawStringWithShadow(
                text,
                sr.getScaledWidth() / 2.0f - width / 2.0f,
                sr.getScaledHeight() / 5.0f * 2.0f,
                color);
    }

    @Override
    public void onRender(float partialTicks) {
        if (targetBed == null || !Mc.isInGame())
            return;
        WorldClient world = Mc.world();
        EntityPlayerSP player = Mc.player();
        if (world == null || player == null || world.isAirBlock(targetBed))
            return;

        world.sendBlockBreakProgress(player.getEntityId(), targetBed, (int) (calcProgress() * 10.0f) - 1);

        if (showTarget.getIndex() == SHOW_NONE)
            return;

        Color color = getProgressColor(showTarget.getIndex());
        double[] vp = Mc.getViewerPos(partialTicks);
        double minX = targetBed.getX() - vp[0];
        double minY = targetBed.getY() - vp[1];
        double minZ = targetBed.getZ() - vp[2];
        double height = isBed ? 0.5625 : 1.0;

        RenderHelper.begin();
        EspDraw.fill(
                minX, minY, minZ,
                minX + 1.0, minY + height, minZ + 1.0,
                color.getRed() / 255.0f,
                color.getGreen() / 255.0f,
                color.getBlue() / 255.0f,
                0.35f);
        RenderHelper.end();
    }

    @Override
    public boolean onSend(Object packet) {
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (packet instanceof S02PacketChat) {
            String text = ((S02PacketChat) packet).getChatComponent().getFormattedText();
            if (text.contains("§e§lProtect your bed and destroy the enemy bed")
                    || text.contains("§e§lDestroy the enemy bed and then eliminate them"))
                waitingForStart = true;
        }

        if (PacketHelper.isPlayerPosLook(packet) && waitingForStart) {
            waitingForStart = false;
            bedWhitelist.clear();
            whitelistScanAt = System.currentTimeMillis() + 1000L;
        }

        if (!isEnabled() || targetBed == null)
            return false;

        if (ignoreVelocity.getIndex() == VELOCITY_CANCEL)
            return shouldCancelVelocity(packet);

        if (ignoreVelocity.getIndex() == VELOCITY_DELAY && shouldCancelVelocity(packet)) {
            delayedVelocity.offer(packet);
            return true;
        }
        return false;
    }

    private void digTick(EntityPlayerSP player, WorldClient world) {
        int slot = findBestHotbarSlot(player, world.getBlockState(targetBed).getBlock());
        if (mode.getIndex() == MODE_LEGIT && savedSlot == -1) {
            savedSlot = Mc.getHotbarSlot(player);
            selectSlot(player, slot);
        }

        switch (breakStage) {
            case 0:
                if (!Mc.isUsingItem(player)) {
                    doSwing(player);
                    EnumFacing face = getHitFacing(targetBed);
                    Mc.addToSendQueue(new C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, targetBed, face));
                    doSwing(player);
                    Mc.mc().effectRenderer.addBlockHitEffects(targetBed, face);
                    breakStage = 1;
                }
                break;
            case 1:
                if (mode.getIndex() == MODE_SWAP)
                    readyToBreak = false;
                breaking = true;
                tickCounter++;
                breakProgress += getBreakDelta(
                        world.getBlockState(targetBed), targetBed, slot, player.onGround);

                boolean canBreak = player.onGround && groundSpoof.getValue();
                float delta = tickCounter * getBreakDelta(
                        world.getBlockState(targetBed), targetBed, slot, canBreak);
                float threshold = 1.0f - 0.3f * (speed.getValue() / 100.0f);
                EnumFacing face = getHitFacing(targetBed);
                Mc.mc().effectRenderer.addBlockHitEffects(targetBed, face);

                if (breakProgress >= threshold || delta >= threshold) {
                    if (mode.getIndex() == MODE_SWAP) {
                        readyToBreak = true;
                        savedSlot = Mc.getHotbarSlot(player);
                        selectSlot(player, slot);
                        if (Mc.isUsingItem(player)) {
                            savedSlot = Mc.getHotbarSlot(player);
                            selectSlot(player, (Mc.getHotbarSlot(player) + 1) % 9);
                        }
                    }
                    breaking = false;
                    Mc.addToSendQueue(new C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, targetBed, face));
                    doSwing(player);

                    IBlockState state = world.getBlockState(targetBed);
                    Block block = state.getBlock();
                    if (block.getMaterial() != Material.air) {
                        world.playAuxSFX(2001, targetBed, Block.getStateId(state));
                        world.setBlockToAir(targetBed);
                    }
                    if (block instanceof BlockBed)
                        retargetAt = System.currentTimeMillis() + 500L;
                    breakStage = 2;
                }
                break;
            case 2:
                restoreSlot();
                resetBreaking();
                break;
            default:
                break;
        }
    }

    private void preUpdate(Object playerObj) {
        if (isScaffoldActive()) {
            clearRotationStateIfOwned();
            return;
        }
        EntityPlayerSP player = playerObj instanceof EntityPlayerSP
                ? (EntityPlayerSP) playerObj : Mc.player();
        if (player == null) {
            clearRotationStateIfOwned();
            return;
        }

        if (isBreaking())
            doSwing(player);

        if (!isReady()) {
            clearRotationStateIfOwned();
            return;
        }

        double x = targetBed.getX() + 0.5 - player.posX;
        double y = targetBed.getY() + 0.5 - player.posY - player.getEyeHeight();
        double z = targetBed.getZ() + 0.5 - player.posZ;
        float baseYaw = PlayerUpdateHook.lastReportedYaw(player);
        float basePitch = PlayerUpdateHook.lastReportedPitch(player);
        float[] rotations = rotationsTo(x, y, z, baseYaw, basePitch);

        PlayerUpdateHook.requestRotation(rotations[0], rotations[1]);
        boolean moveFixOn = moveFix.getIndex() != MOVEFIX_NONE;
        float pervYaw = moveFixOn ? rotations[0] : Mc.getYaw();
        int priority = moveFixOn ? ROTATION_PRIORITY : -1;
        RotationState.applyState(true, rotations[0], rotations[1], pervYaw, priority);
    }

    private void resetBreaking() {
        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (targetBed != null && player != null && world != null)
            world.sendBlockBreakProgress(player.getEntityId(), targetBed, -1);
        targetBed = null;
        breakStage = 0;
        tickCounter = 0;
        breakProgress = 0.0f;
        isBed = false;
        readyToBreak = false;
        breaking = false;
    }

    private float calcProgress() {
        if (targetBed == null)
            return 0.0f;
        float progress = breakProgress;
        if (groundSpoof.getValue()) {
            EntityPlayerSP player = Mc.player();
            WorldClient world = Mc.world();
            if (player != null && world != null) {
                int slot = findBestHotbarSlot(player, world.getBlockState(targetBed).getBlock());
                progress = tickCounter * getBreakDelta(
                        world.getBlockState(targetBed), targetBed, slot, true);
            }
        }
        return Math.min(1.0f, progress / (1.0f - 0.3f * (speed.getValue() / 100.0f)));
    }

    private void restoreSlot() {
        EntityPlayerSP player = Mc.player();
        if (savedSlot != -1 && player != null) {
            selectSlot(player, savedSlot);
            savedSlot = -1;
        }
    }

    private void selectSlot(EntityPlayerSP player, int slot) {
        if (player == null || slot < 0 || slot > 8)
            return;
        if (Mc.getHotbarSlot(player) != slot)
            Mc.setHotbarSlot(player, slot);
        PlayerControllerMP controller = Mc.controller();
        if (controller instanceof IAccessorPlayerControllerMP) {
            IAccessorPlayerControllerMP acc = (IAccessorPlayerControllerMP) controller;
            if (Mc.getHotbarSlot(player) != acc.getCurrentPlayerItem()) {
                if (Mc.isUsingItem(player))
                    player.stopUsingItem();
                acc.invokeSyncCurrentPlayItem();
            }
        }
    }

    private boolean hasProperTool(Block block) {
        Material material = block.getMaterial();
        if (material != Material.iron && material != Material.anvil && material != Material.rock)
            return true;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemPickaxe)
                return true;
        }
        return false;
    }

    private EnumFacing getHitFacing(BlockPos blockPos) {
        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null)
            return EnumFacing.UP;
        double x = blockPos.getX() + 0.5 - player.posX;
        double y = blockPos.getY() + 0.25 - player.posY - player.getEyeHeight();
        double z = blockPos.getZ() + 0.5 - player.posZ;
        float[] rotations = rotationsTo(x, y, z, player.rotationYaw, player.rotationPitch);
        MovingObjectPosition mop = rayTrace(rotations[0], rotations[1], 8.0, 1.0f);
        return mop == null || mop.sideHit == null ? EnumFacing.UP : mop.sideHit;
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch, double distance, float partialTicks) {
        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null)
            return null;
        Vec3 eyePos = player.getPositionEyes(partialTicks);
        Vec3 look = RotationUtils.getVectorForRotation(pitch, yaw);
        Vec3 end = eyePos.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return world.rayTraceBlocks(eyePos, end);
    }

    private float getDigSpeed(IBlockState state, int slot, boolean onGround) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return 1.0f;
        ItemStack item = player.inventory.getStackInSlot(slot);
        float digSpeed = item == null ? 1.0f : item.getItem().getDigSpeed(item, state);
        if (digSpeed > 1.0f) {
            int enchant = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, item);
            if (enchant > 0)
                digSpeed += (float) (enchant * enchant + 1);
        }
        if (player.isPotionActive(Potion.digSpeed))
            digSpeed *= 1.0f + (player.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2f;
        if (player.isPotionActive(Potion.digSlowdown)) {
            switch (player.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
                case 0:
                    digSpeed *= 0.3f;
                    break;
                case 1:
                    digSpeed *= 0.09f;
                    break;
                case 2:
                    digSpeed *= 0.0027f;
                    break;
                default:
                    digSpeed *= 8.1E-4f;
                    break;
            }
        }
        if (player.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(player))
            digSpeed /= 5.0f;
        if (!onGround)
            digSpeed /= 5.0f;
        return digSpeed;
    }

    private boolean canHarvest(Block block, int slot) {
        if (block.getMaterial().isToolNotRequired())
            return true;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        ItemStack stack = player.inventory.getStackInSlot(slot);
        return stack != null && stack.canHarvestBlock(block);
    }

    private float getBreakDelta(IBlockState state, BlockPos pos, int slot, boolean onGround) {
        WorldClient world = Mc.world();
        if (world == null)
            return 0.0f;
        Block block = state.getBlock();
        float hardness = block.getBlockHardness(world, pos);
        float boost = canHarvest(block, slot) ? 30.0f : 100.0f;
        return hardness < 0.0f ? 0.0f : getDigSpeed(state, slot, onGround) / hardness / boost;
    }

    private float calcBlockStrength(BlockPos pos) {
        WorldClient world = Mc.world();
        EntityPlayerSP player = Mc.player();
        if (world == null || player == null)
            return 0.0f;
        IBlockState state = world.getBlockState(pos);
        int slot = findBestHotbarSlot(player, state.getBlock());
        return getBreakDelta(state, pos, slot, player.onGround);
    }

    private BlockPos validateBedPlacement(BlockPos bedPosition) {
        WorldClient world = Mc.world();
        EntityPlayerSP player = Mc.player();
        if (world == null || player == null)
            return null;
        IBlockState blockState = world.getBlockState(bedPosition);
        if (!(blockState.getBlock() instanceof BlockBed))
            return null;

        ArrayList<BlockPos> pos = new ArrayList<BlockPos>();
        EnumPartType partType = blockState.getValue(BlockBed.PART);
        EnumFacing facing = blockState.getValue(BlockBed.FACING);
        for (BlockPos blockPos : Arrays.asList(
                bedPosition,
                bedPosition.offset(partType == EnumPartType.HEAD ? facing.getOpposite() : facing))) {
            for (EnumFacing side : Arrays.asList(
                    EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST)) {
                Block block = world.getBlockState(blockPos.offset(side)).getBlock();
                if (isReplaceable(block))
                    return null;
                if (!(block instanceof BlockBed))
                    pos.add(blockPos.offset(side));
            }
        }
        if (pos.isEmpty())
            return null;

        pos.sort(new Comparator<BlockPos>() {
            @Override
            public int compare(BlockPos a, BlockPos b) {
                int o = Float.compare(calcBlockStrength(b), calcBlockStrength(a));
                if (o != 0)
                    return o;
                double eyeY = player.posY + player.getEyeHeight();
                return Double.compare(
                        a.distanceSqToCenter(player.posX, eyeY, player.posZ),
                        b.distanceSqToCenter(player.posX, eyeY, player.posZ));
            }
        });
        return pos.get(0);
    }

    private BlockPos findNearestBed() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return null;
        return findTargetBed(player.posX, player.posY + player.getEyeHeight(), player.posZ);
    }

    private BlockPos findTargetBed(double x, double y, double z) {
        WorldClient world = Mc.world();
        EntityPlayerSP player = Mc.player();
        if (world == null || player == null)
            return null;

        ArrayList<BlockPos> targets = new ArrayList<BlockPos>();
        int sX = MathHelper.floor_double(x);
        int sY = MathHelper.floor_double(y);
        int sZ = MathHelper.floor_double(z);
        double reach = range.getValue();

        for (int i = sX - 6; i <= sX + 6; i++) {
            for (int j = sY - 6; j <= sY + 6; j++) {
                for (int k = sZ - 6; k <= sZ + 6; k++) {
                    BlockPos newPos = new BlockPos(i, j, k);
                    if (whitelist.getValue() && bedWhitelist.contains(newPos))
                        continue;
                    Block block = world.getBlockState(newPos).getBlock();
                    if (block instanceof BlockBed && isBlockWithinReach(newPos, x, y, z, reach))
                        targets.add(newPos);
                }
            }
        }
        if (targets.isEmpty())
            return null;

        targets.sort(Comparator.comparingDouble(pos ->
                pos.distanceSqToCenter(player.posX, player.posY + player.getEyeHeight(), player.posZ)));

        for (BlockPos blockPos : targets) {
            if (surroundings.getValue()) {
                BlockPos cover = validateBedPlacement(blockPos);
                if (cover != null) {
                    Block block = world.getBlockState(cover).getBlock();
                    if (toolCheck.getValue() && !hasProperTool(block))
                        continue;
                    return cover;
                }
            }
            return blockPos;
        }
        return null;
    }

    private void doSwing(EntityPlayerSP player) {
        if (swing.getValue())
            player.swingItem();
        else
            Mc.addToSendQueue(new C0APacketAnimation());
    }

    private Color getProgressColor(int showMode) {
        switch (showMode) {
            case SHOW_DEFAULT: {
                float progress = calcProgress();
                if (progress <= 0.5f)
                    return interpolate(progress / 0.5f, colorRed, colorYellow);
                return interpolate((progress - 0.5f) / 0.5f, colorYellow, colorGreen);
            }
            case SHOW_HUD:
                return colorHud;
            default:
                return Color.WHITE;
        }
    }

    private static Color interpolate(float t, Color a, Color b) {
        t = MathHelper.clamp_float(t, 0.0f, 1.0f);
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl);
    }

    private static float[] rotationsTo(double x, double y, double z, float currentYaw, float currentPitch) {
        double horizontal = Math.sqrt(x * x + z * z);
        float yawDelta = MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(
                (float) (-Math.atan2(y, horizontal) * 180.0 / Math.PI) - currentPitch);
        return new float[] { currentYaw + yawDelta, MathHelper.clamp_float(currentPitch + pitchDelta, -90.0f, 90.0f) };
    }

    private static int findBestHotbarSlot(EntityPlayerSP player, Block block) {
        int best = Mc.findBestHotbarTool(block);
        return best >= 0 ? best : Mc.getHotbarSlot(player);
    }

    private static boolean isReplaceable(Block block) {
        return block != null && block.getMaterial().isReplaceable();
    }

    private static boolean canReach(BlockPos pos, double reach) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        return isBlockWithinReach(
                pos, player.posX, player.posY + player.getEyeHeight(), player.posZ, reach);
    }

    private static boolean isBlockWithinReach(BlockPos pos, double x, double y, double z, double reach) {
        return pos.distanceSqToCenter(x, y, z) < reach * reach;
    }

    private static boolean isScaffoldActive() {
        Module module = ModuleManager.instance().getModule("Scaffold");
        return module instanceof ScaffoldModule && module.isEnabled();
    }

    private boolean shouldCancelVelocity(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (PacketHelper.isEntityVelocity(packet)
                && PacketHelper.velocityEntityId(packet) == Mc.entityId(player)
                && PacketHelper.velocityMotionY(packet) > 0)
            return true;
        if (PacketHelper.isExplosion(packet)) {
            float mx = PacketHelper.explosionMotionX(packet);
            float my = PacketHelper.explosionMotionY(packet);
            float mz = PacketHelper.explosionMotionZ(packet);
            return mx != 0.0f || my != 0.0f || mz != 0.0f;
        }
        return false;
    }

    private void flushDelayedVelocity() {
        while (!delayedVelocity.isEmpty()) {
            Object packet = delayedVelocity.poll();
            if (packet != null)
                PacketUtil.processInbound(packet);
        }
    }

    private void runWhitelistScanIfDue() {
        if (whitelistScanAt < 0L || System.currentTimeMillis() < whitelistScanAt)
            return;
        whitelistScanAt = -1L;
        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null)
            return;
        int sX = MathHelper.floor_double(player.posX);
        int sY = MathHelper.floor_double(player.posY + player.getEyeHeight());
        int sZ = MathHelper.floor_double(player.posZ);
        for (int i = sX - 25; i <= sX + 25; i++) {
            for (int j = sY - 25; j <= sY + 25; j++) {
                for (int k = sZ - 25; k <= sZ + 25; k++) {
                    BlockPos pos = new BlockPos(i, j, k);
                    if (world.getBlockState(pos).getBlock() instanceof BlockBed)
                        bedWhitelist.add(pos);
                }
            }
        }
    }

    private void clearRotationStateIfOwned() {
        if ((int) RotationState.getPriority() == ROTATION_PRIORITY)
            RotationState.reset();
    }
}
