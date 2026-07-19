package gnu.client.mixin;

/**
 * Read/write access to the Augustus-style server position fields ({@code realPosX/Y/Z})
 * that {@code MixinEntityLivingBase} adds to every living entity. This is a plain
 * interface (not a mixin) so it can be referenced by name at runtime; the mixin
 * implements it, and callers cast the entity to this interface.
 */
public interface RealPosAccess {

    double getRealPosX();

    void setRealPosX(double value);

    double getRealPosY();

    void setRealPosY(double value);

    double getRealPosZ();

    void setRealPosZ(double value);
}
