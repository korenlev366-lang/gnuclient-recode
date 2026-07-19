package gnu.client.script;

import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Script-facing player/entity status facade over {@link Mc}. */
public final class Status {

    public static final Status INSTANCE = new Status();

    private Status() {}

    public boolean isUsingItem() {
        return Mc.isUsingItem();
    }

    public boolean isUsingItem(Object player) {
        return Mc.isUsingItem(asPlayer(player));
    }

    public boolean isBlocking() {
        return Mc.isBlocking();
    }

    public boolean isBlocking(Object player) {
        return Mc.isBlocking(asPlayer(player));
    }

    public int getHurtTime() {
        return Mc.getHurtTime();
    }

    public int getHurtTime(Object entity) {
        return Mc.getHurtTime(asLiving(entity));
    }

    public int getMaxHurtTime(Object entity) {
        return Mc.getMaxHurtTime(asLiving(entity));
    }

    public int getDeathTime(Object entity) {
        return Mc.getDeathTime(asLiving(entity));
    }

    public float getHealth() {
        return Mc.getHealth();
    }

    public float getHealth(Object entity) {
        return Mc.getHealth(asLiving(entity));
    }

    public float getMaxHealth() {
        return Mc.getMaxHealth();
    }

    public float getMaxHealth(Object entity) {
        return Mc.getMaxHealth(asLiving(entity));
    }

    public float getAbsorption() {
        return Mc.getAbsorption();
    }

    public float getAbsorption(Object entity) {
        return Mc.getAbsorption(asLiving(entity));
    }

    public boolean isDead() {
        return Mc.isDead(Mc.player());
    }

    public boolean isDead(Object entity) {
        return Mc.isDead(asEntity(entity));
    }

    public boolean isAlive() {
        return Mc.isAlive();
    }

    public boolean isAlive(Object entity) {
        return Mc.isAlive(asEntity(entity));
    }

    public boolean isSwingInProgress() {
        return Mc.isSwingInProgress();
    }

    public boolean isSwingInProgress(Object player) {
        return Mc.isSwingInProgress(asLiving(player));
    }

    public Object getHeldItemStack() {
        return Mc.getHeldItemStack();
    }

    public Object getHeldItemStack(Object player) {
        ItemStack stack = Mc.getHeldItemStack(asPlayer(player));
        return stack;
    }

    public boolean isHoldingSword() {
        return Mc.isHoldingSword();
    }

    public boolean isHoldingBlock() {
        return Mc.isHoldingBlock();
    }

    public boolean isHoldingBow() {
        return Mc.isHoldingBow();
    }

    public boolean isHoldingConsumable() {
        return Mc.isHoldingConsumable();
    }

    public boolean isInWater() {
        return Mc.isInWater();
    }

    public boolean isInWater(Object entity) {
        return Mc.isInWater(asEntity(entity));
    }

    public boolean isSoup(Object stack) {
        return Mc.isSoup(asStack(stack));
    }

    public boolean isFood(Object stack) {
        return Mc.isFood(asStack(stack));
    }

    public String getItemName(Object stack) {
        return Mc.getItemName(asStack(stack));
    }

    private static Entity asEntity(Object entity) {
        return entity instanceof Entity ? (Entity) entity : null;
    }

    private static EntityLivingBase asLiving(Object entity) {
        return entity instanceof EntityLivingBase ? (EntityLivingBase) entity : null;
    }

    private static EntityPlayer asPlayer(Object player) {
        return player instanceof EntityPlayer ? (EntityPlayer) player : null;
    }

    private static ItemStack asStack(Object stack) {
        return stack instanceof ItemStack ? (ItemStack) stack : null;
    }
}
