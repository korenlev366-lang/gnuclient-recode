package gnu.client.mixin.impl.accessors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin(EntityPlayer.class)
public interface IAccessorEntityPlayer {
    @Accessor("itemInUse")
    ItemStack getItemInUse();

    @Accessor("itemInUse")
    void setItemInUse(ItemStack stack);

    @Accessor("itemInUseCount")
    int getItemInUseCount();

    @Accessor("itemInUseCount")
    void setItemInUseCount(int count);
}
