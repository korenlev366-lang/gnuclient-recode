package gnu.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import gnu.client.common.GnuLog;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.ui.clickgui.ClickGuiLayout;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long SAVE_DEBOUNCE_MS = 500L;
    private static final Clock SYSTEM_CLOCK = System::currentTimeMillis;
    private static final ConfigWriter FILE_WRITER = (path, root) -> {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(root, writer);
        }
    };

    public static final ConfigManager INSTANCE = new ConfigManager();

    private final Path configPath;
    private final Clock clock;
    private final ConfigWriter writer;
    private boolean loading;
    private boolean dirty;
    private long lastWriteAttemptAtMs;
    private ClickGuiLayout clickGuiLayout = ClickGuiLayout.defaults();

    private ConfigManager() {
        this(defaultConfigPath(), SYSTEM_CLOCK, FILE_WRITER);
    }

    ConfigManager(Path configPath, Clock clock, ConfigWriter writer) {
        if (configPath == null || clock == null || writer == null) {
            throw new IllegalArgumentException("config path, clock, and writer are required");
        }
        this.configPath = configPath;
        this.clock = clock;
        this.writer = writer;
        this.lastWriteAttemptAtMs = clock.nowMs();
    }

    private static Path defaultConfigPath() {
        String home = System.getProperty("user.home");
        if (home != null)
            return Paths.get(home, ".config", "gnuclient", "config.json");
        return Paths.get("/tmp/gnuclient_config.json");
    }

    public static ConfigManager instance() {
        return INSTANCE;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public synchronized boolean isLoading() {
        return loading;
    }

    public static void setLoading(boolean loading) {
        synchronized (INSTANCE) {
            INSTANCE.loading = loading;
        }
    }

    public synchronized ClickGuiLayout getClickGuiLayout() {
        return clickGuiLayout.copy();
    }

    public synchronized void setClickGuiLayout(ClickGuiLayout layout) {
        clickGuiLayout = layout == null ? ClickGuiLayout.defaults() : layout.copy();
        requestSave();
    }

    public synchronized void load() {
        if (!Files.isRegularFile(configPath)) {
            GnuLog.log("config: no file at " + configPath);
            return;
        }

        loading = true;
        try (Reader reader = Files.newBufferedReader(configPath)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null)
                return;

            clickGuiLayout = ClickGuiLayout.fromJson(asJsonObject(root.get("clickGui")));

            JsonObject modulesJson = asJsonObject(root.get("modules"));
            if (modulesJson != null) {
                for (Module module : ModuleManager.INSTANCE.all()) {
                    JsonObject moduleJson = asJsonObject(modulesJson.get(module.getName()));
                    if (moduleJson != null)
                        module.deserialize(moduleJson);
                }
            }
            GnuLog.log("config: loaded from " + configPath);
        } catch (Exception e) {
            GnuLog.log("config: load failed " + e);
        } finally {
            loading = false;
        }
    }

    public synchronized void requestSave() {
        if (loading)
            return;
        dirty = true;
    }

    public synchronized void save() {
        requestSave();
        flushIfDue();
    }

    public synchronized void flushIfDue() {
        if (!dirty)
            return;
        long now = clock.nowMs();
        if (now - lastWriteAttemptAtMs < SAVE_DEBOUNCE_MS)
            return;
        writeNow(now);
    }

    public synchronized void flush() {
        if (dirty)
            writeNow(clock.nowMs());
    }

    private void writeNow(long now) {
        lastWriteAttemptAtMs = now;
        try {
            JsonObject root = new JsonObject();
            JsonObject modulesJson = new JsonObject();
            for (Module module : ModuleManager.INSTANCE.all()) {
                modulesJson.add(module.getName(), module.serialize());
            }
            root.add("modules", modulesJson);
            root.add("clickGui", clickGuiLayout.toJson());
            writer.write(configPath, root);
            dirty = false;
            GnuLog.log("config: saved to " + configPath);
        } catch (Exception e) {
            GnuLog.log("config: save failed " + e);
        }
    }

    private static JsonObject asJsonObject(com.google.gson.JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    interface Clock {
        long nowMs();
    }

    interface ConfigWriter {
        void write(Path path, JsonObject root) throws IOException;
    }
}
