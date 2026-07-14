package gnu.client.module.modules.player;

import gnu.client.event.PostUpdateEvent;
import gnu.client.event.PreUpdateEvent;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.player.FastPlaceModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

/**
 * Raven-APlus AutoPlace — place held blocks against non-horizontal faces.
 */
public final class AutoPlaceModule extends Module {

    private final SliderSetting frameDelay = addSetting(new SliderSetting("Frame delay", 8.0f, 0.0f, 30.0f, 1.0f));
    private final SliderSetting minPlaceDelay = addSetting(new SliderSetting("Min place delay", 60.0f, 1.0f, 500.0f, 5.0f));
    private final BoolSetting disableLeft = addSetting(new BoolSetting("Disable left", false));
    private final BoolSetting holdRight = addSetting(new BoolSetting("Hold right", true));
    private final BoolSetting fastPlaceJump = addSetting(new BoolSetting("Fast place on jump", true));
    private final BoolSetting pitchCheck = addSetting(new BoolSetting("Pitch check", false));
    private final BoolSetting postPlace = addSetting(new BoolSetting("Post place", false));

    private double frameDelayCache;
    private long lastPlaceTime;
    private int frameTicks;
    private MovingObjectPosition lastMouseOver;
    private BlockPos lastPos;

    public AutoPlaceModule() {
        super("AutoPlace", "Auto-place held blocks against non-horizontal faces", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        resetVariables();
        frameDelayCache = frameDelay.getInput();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        resetVariables();
    }

    @Override
    public void guiUpdate() {
        if (frameDelayCache != frameDelay.getInput())
            resetVariables();
        frameDelayCache = frameDelay.getInput();
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        if (!postPlace.getValue())
            action();
    }

    @SubscribeEvent
    public void onPostUpdate(PostUpdateEvent event) {
        if (postPlace.getValue())
            action();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        if (!canUseAutoPlace())
            return;

        ItemStack stack = Mc.player().getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemBlock))
            return;

        MovingObjectPosition hit = Mc.objectMouseOver();
        if (hit == null || hit.typeOfHit != MovingObjectType.BLOCK)
            return;
        if (disableLeft.getValue() && Mouse.isButtonDown(0))
            return;
        if (hit.sideHit == EnumFacing.UP || hit.sideHit == EnumFacing.DOWN)
            return;

        if (lastMouseOver != null && frameTicks < frameDelay.getInput()) {
            frameTicks++;
            return;
        }

        lastMouseOver = hit;
        BlockPos pos = hit.getBlockPos();
        if (lastPos != null && sameBlockPos(pos, lastPos))
            return;

        Block block = Mc.world().getBlockState(pos).getBlock();
        if (block == null || block == Blocks.air || block instanceof BlockLiquid)
            return;
        if (holdRight.getValue() && !Mouse.isButtonDown(1))
            return;

        long now = System.currentTimeMillis();
        if (now - lastPlaceTime < minPlaceDelay.getInput())
            return;

        lastPlaceTime = now;
        if (placeBlock(Mc.player(), Mc.world(), stack, pos, hit.sideHit, hit.hitVec)) {
            Mc.player().swingItem();
            Mc.mc().getItemRenderer().resetEquippedProgress();
            lastPos = pos;
            frameTicks = 0;
        }
    }

    private void action() {
        if (!canUseAutoPlace())
            return;

        ItemStack stack = Mc.player().getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemBlock))
            return;

        if (fastPlaceJump.getValue() && holdRight.getValue() && !fastPlaceEnabled() && Mouse.isButtonDown(1)) {
            if (Mc.player().motionY > 0.0D) {
                Mc.setRightClickDelay(1);
            } else if (!pitchCheck.getValue()) {
                Mc.setRightClickDelay(1000);
            }
        }
    }

    private boolean canUseAutoPlace() {
        return Mc.isInGame() && !Mc.player().capabilities.isFlying;
    }

    private boolean fastPlaceEnabled() {
        Module module = ModuleManager.instance().getModule("FastPlace");
        return module instanceof FastPlaceModule && module.isEnabled();
    }

    private static boolean placeBlock(EntityPlayerSP player, WorldClient world, ItemStack stack, BlockPos pos, EnumFacing side, Vec3 hitVec) {
        return Mc.controller().onPlayerRightClick(player, world, stack, pos, side, hitVec);
    }

    private static boolean sameBlockPos(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    private void resetVariables() {
        lastPos = null;
        lastMouseOver = null;
        frameTicks = 0;
    }
}
