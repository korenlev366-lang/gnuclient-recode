package gnu.client.runtime;

import gnu.client.GnuClientMod;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.runtime.mc.McAccess;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forge client helpers: LWJGL mouse/keyboard polling, ClickGUI open/close,
 * keybind rebinding, and the AutoClicker attack-click queue.
 */
public final class ClientBootstrap {

    private static volatile boolean initialized;
    private static volatile String rebindModule;
    private static final List<Module> GUI_MODULES = new ArrayList<>();
    /** Clicks queued by AutoClicker worker; drained on the Minecraft client thread. */
    private static final AtomicInteger PENDING_ATTACK_CLICKS = new AtomicInteger();

    private ClientBootstrap() {}

    public static void markInitialized() {
        initialized = true;
        refreshGuiModules();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void refreshGuiModules() {
        GUI_MODULES.clear();
        GUI_MODULES.addAll(ModuleManager.INSTANCE.all());
    }

    public static List<Module> guiModules() {
        return GUI_MODULES;
    }

    public static void toggleMenu() {
        GnuClientMod.openClickGui();
    }

    public static boolean isLeftMouseDown() {
        return Mouse.isButtonDown(0);
    }

    public static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    /**
     * Queue a synthetic left-click for the next client tick, consumed by
     * {@link McAccess#pressAttackKeyOnce()}. {@code holdMs} is ignored (no
     * OS-level press/release — Forge has no physical key state to hold).
     */
    public static void queueAttackClick(int holdMs) {
        PENDING_ATTACK_CLICKS.incrementAndGet();
    }

    /** Drain queued AutoClicker attacks on the Minecraft client thread. */
    public static void drainPendingAttackClicks() {
        int n = PENDING_ATTACK_CLICKS.getAndSet(0);
        if (n <= 0 || !McAccess.isInGame())
            return;
        // Cap burst if the worker got ahead of ticks.
        if (n > 4)
            n = 4;
        for (int i = 0; i < n; i++) {
            McAccess.pressAttackKeyOnce();
        }
    }

    public static boolean isRebindActive() {
        return rebindModule != null;
    }

    public static void beginRebind(String moduleName) {
        rebindModule = moduleName;
    }

    public static void setModuleKeyCode(String moduleName, int keyCode) {
        Module m = ModuleManager.INSTANCE.getModule(moduleName);
        if (m != null) {
            m.setKeyCode(keyCode);
        }
    }

    public static void cancelRebind() {
        rebindModule = null;
    }

    public static void handleRebindKeyboard() {
        if (rebindModule == null)
            return;
        for (int i = 0; i < 256; i++) {
            if (Keyboard.isKeyDown(i)) {
                Module m = ModuleManager.INSTANCE.get(rebindModule);
                if (m != null) {
                    m.setKeyCode(i);
                }
                rebindModule = null;
                return;
            }
        }
    }
}
