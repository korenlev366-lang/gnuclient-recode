package gnu.client.script;

import gnu.client.module.Module;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-script chat command registry. Scripts call {@code commands.register("name")}
 * in {@code onLoad}, then implement {@code boolean onCommand(String name, String[] args)}.
 * Registered as client-only {@code .name} commands (packet cancelled).
 */
public final class Commands {

    private static final Map<String, Commands> BY_NAME = new ConcurrentHashMap<String, Commands>();

    private final String scriptName;
    private final Module owner;
    private final Set<String> mine = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    public Commands(String scriptName, Module owner) {
        this.scriptName = scriptName;
        this.owner = owner;
    }

    public String getScriptName() {
        return scriptName;
    }

    /** Register {@code .name} (leading dot optional). */
    public void register(String name) {
        String key = normalize(name);
        if (key.isEmpty() || owner == null)
            return;
        BY_NAME.put(key, this);
        mine.add(key);
    }

    public void unregister(String name) {
        String key = normalize(name);
        if (key.isEmpty())
            return;
        BY_NAME.remove(key, this);
        mine.remove(key);
    }

    public void unregisterAll() {
        for (String key : mine)
            BY_NAME.remove(key, this);
        mine.clear();
    }

    /**
     * Try to handle a chat command. Returns a reply string to print, or {@code null}
     * if unhandled. Returning non-null from the script cancels the outbound packet.
     */
    public static String tryHandle(String rawMessage) {
        if (rawMessage == null || !rawMessage.startsWith("."))
            return null;
        String body = rawMessage.substring(1).trim();
        if (body.isEmpty())
            return null;
        String[] parts = body.split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);
        Commands target = BY_NAME.get(name);
        if (target == null || target.owner == null || !target.owner.isEnabled())
            return null;
        String[] args = new String[Math.max(0, parts.length - 1)];
        System.arraycopy(parts, 1, args, 0, args.length);
        Object result = ScriptManager.instance().invokeScriptCommand(target.owner, name, args);
        if (result instanceof String)
            return (String) result;
        if (result instanceof Boolean && (Boolean) result)
            return ""; // handled, no message
        return null;
    }

    public static void clearAll() {
        BY_NAME.clear();
    }

    private static String normalize(String name) {
        if (name == null)
            return "";
        String n = name.trim();
        if (n.startsWith("."))
            n = n.substring(1);
        return n.toLowerCase(Locale.ROOT);
    }
}
