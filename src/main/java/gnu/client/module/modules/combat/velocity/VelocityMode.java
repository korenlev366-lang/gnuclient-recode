package gnu.client.module.modules.combat.velocity;

import gnu.client.event.JumpEvent;
import gnu.client.event.StrafeEvent;
import gnu.client.module.modules.combat.VelocityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovementInput;

/**
 * OpenMiau-style Velocity strategy. Hooks are no-ops by default.
 * Packet cancel: return true from {@link #onReceive}/{@link #onSend}.
 */
public abstract class VelocityMode {

    protected static final Minecraft mc = Minecraft.getMinecraft();

    protected final String name;
    protected final VelocityModule parent;

    protected VelocityMode(String name, VelocityModule parent) {
        this.name = name;
        this.parent = parent;
    }

    public final String getName() {
        return name;
    }

    public void onEnable() {}

    public void onDisable() {}

    /** @param pre true = early tick / PRE; false = late tick / POST */
    public void onUpdate(boolean pre) {}

    /** @return true to cancel receive */
    public boolean onReceive(Object packet) {
        return false;
    }

    /** @return true to cancel send */
    public boolean onSend(Object packet) {
        return false;
    }

    public void onAttack(Object target) {}

    public void onMoveInput(MovementInput input) {}

    public void onStrafe(StrafeEvent event) {}

    public void onJump(JumpEvent event) {}
}
