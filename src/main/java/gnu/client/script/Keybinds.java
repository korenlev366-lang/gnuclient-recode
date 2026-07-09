package gnu.client.script;

import gnu.client.runtime.ClientBootstrap;
import gnu.client.runtime.mc.Mc;

/**
 * Script-facing {@code keybinds} accessor — stateless singleton facade over
 * {@link ClientBootstrap} (LWJGL LMB) and {@link Mc} (LWJGL RMB, vanilla
 * {@code sendUseItem}).
 */
public final class Keybinds {

    public static final Keybinds INSTANCE = new Keybinds();

    private Keybinds() {}

    /**
     * Physical mouse-button state. {@code button} follows LWJGL conventions
     * (0 = left, 1 = right). Returns {@code false} for unknown indices.
     */
    public boolean isMouseDown(int button) {
        if (button == 0)
            return ClientBootstrap.isLeftMouseDown();
        if (button == 1)
            return Mc.isPhysicalRmbDown();
        return false;
    }

    public boolean isForwardDown() {
        return Mc.isForwardKeyHeld();
    }

    public boolean isBackDown() {
        return Mc.isBackKeyHeld();
    }

    public boolean isLeftDown() {
        return Mc.isLeftKeyHeld();
    }

    public boolean isRightDown() {
        return Mc.isRightKeyHeld();
    }

    public boolean isMovementDown() {
        return Mc.isMovementKeyHeld();
    }

    public boolean isJumpDown() {
        return Mc.isJumpKeyHeld();
    }

    public boolean isSneakDown() {
        return Mc.isSneakKeyHeld();
    }

    /** Synthesize a vanilla right-click via {@code PlayerControllerMP.sendUseItem}. */
    public boolean rightClick() {
        return Mc.sendUseItem();
    }
}
