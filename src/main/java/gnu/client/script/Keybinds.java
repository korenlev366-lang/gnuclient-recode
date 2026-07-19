package gnu.client.script;

import gnu.client.command.KeyNames;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.runtime.mc.Mc;
import org.lwjgl.input.Keyboard;

/**
 * Script-facing {@code keybinds} accessor — mouse / movement / arbitrary LWJGL keys.
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

    /** Raw LWJGL keyboard state by key code ({@link Keyboard} constants). */
    public boolean isKeyDown(int keyCode) {
        if (keyCode <= 0)
            return false;
        try {
            return Keyboard.isKeyDown(keyCode);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Resolve a key name like {@code "R"} / {@code "LSHIFT"} to a key code, or {@code -1}. */
    public int keyCode(String keyName) {
        return KeyNames.parse(keyName);
    }
}
