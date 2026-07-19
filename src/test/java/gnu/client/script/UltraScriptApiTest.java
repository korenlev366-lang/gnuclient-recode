package gnu.client.script;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UltraScriptApiTest {

    private Module aura;
    private Module scriptOwner;
    private Modules modules;

    @Before
    public void setUp() {
        ModuleManager.INSTANCE.reset();
        aura = new DummyModule("KillAura");
        aura.addScriptSetting(new BoolSetting("Teams", false));
        aura.addScriptSetting(new SliderSetting("Range", 4.2f, 1f, 6f));
        aura.addScriptSetting(new ModeSetting("Mode", 0,
                java.util.Arrays.asList("Single", "Switch")));
        ModuleManager.INSTANCE.register(aura);

        scriptOwner = new DummyModule("DemoScript");
        ModuleManager.INSTANCE.register(scriptOwner);
        modules = new Modules("DemoScript", scriptOwner);
        modules.registerButton("Notify", true);
        modules.registerSlider("Value", 1.5f, 0f, 5f);
    }

    @After
    public void tearDown() {
        ModuleManager.INSTANCE.reset();
        Shared.INSTANCE.clear();
        Commands.clearAll();
    }

    @Test
    public void crossModuleEnableAndSettings() {
        assertTrue(modules.exists("KillAura"));
        assertFalse(modules.isEnabled("KillAura"));
        assertTrue(modules.enable("KillAura"));
        assertTrue(modules.isEnabled("killaura"));
        assertTrue(modules.setBool("KillAura", "Teams", true));
        assertTrue(modules.getBool("KillAura", "Teams"));
        assertTrue(modules.setSlider("KillAura", "Range", 5.0f));
        assertEquals(5.0f, modules.getSlider("KillAura", "Range"), 0.001f);
        assertTrue(modules.setMode("KillAura", "Mode", "Switch"));
        assertEquals("Switch", modules.getMode("KillAura", "Mode"));
        assertTrue(modules.names().contains("KillAura"));
        assertTrue(modules.settingNames("KillAura").contains("Range"));
    }

    @Test
    public void ownSettingWriters() {
        assertTrue(modules.getButton("Notify"));
        modules.setButton("Notify", false);
        assertFalse(modules.getButton("Notify"));
        modules.setSlider("Value", 3.25f);
        assertEquals(3.25f, modules.getSlider("Value"), 0.001f);
    }

    @Test
    public void sharedStoreAndEmit() {
        Shared.INSTANCE.put("x", 42);
        assertEquals(42, Shared.INSTANCE.getInt("x", 0));
        final AtomicReference<String> seen = new AtomicReference<String>();
        Shared.INSTANCE.addListener(new Shared.Listener() {
            @Override
            public void onShared(String channel, Object payload) {
                seen.set(channel + ":" + payload);
            }
        });
        Shared.INSTANCE.emit("combat", "hit");
        assertEquals("combat:hit", seen.get());
    }

    @Test
    public void commandsRegisterAndDispatch() {
        Commands commands = new Commands("DemoScript", scriptOwner);
        scriptOwner.setEnabled(true);
        commands.register("ping");
        // No onCommand on DummyModule → still cancels with empty reply
        assertEquals("", Commands.tryHandle(".ping"));
        assertEquals(null, Commands.tryHandle(".unknown"));
        commands.unregisterAll();
        assertEquals(null, Commands.tryHandle(".ping"));
    }

    @Test
    public void inventorySlotMappingStillWorks() {
        assertEquals(36, gnu.client.runtime.mc.Mc.inventoryToContainerSlot(0));
        assertEquals(5, gnu.client.runtime.mc.Mc.armorContainerSlot(0));
    }

    private static final class DummyModule extends Module {
        DummyModule(String name) {
            super(name, "test", Category.COMBAT);
        }

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}
    }
}
