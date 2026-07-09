package gnu.client.command;

import gnu.client.module.KeybindAction;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.settings.ClickGuiModule;
import gnu.client.runtime.ClientBootstrap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code .bind <module> <key>} — client-only; packet is cancelled before send.
 *
 * <p>Module names may contain spaces ({@code .bind Aim Assist R}). Prefer the
 * longest matching module name; aliases strip spaces ({@code aimassist}).
 */
public final class BindCommand {

    private static final String PREFIX = ".bind";
    private static final String SHORT_PREFIX = ".b";

    private static final Map<String, String> MODULE_ALIASES = new HashMap<>();

    static {
        MODULE_ALIASES.put("gui", ClickGuiModule.NAME);
        MODULE_ALIASES.put("clickgui", ClickGuiModule.NAME);
        MODULE_ALIASES.put("menu", ClickGuiModule.NAME);
        MODULE_ALIASES.put("aimassist", "Aim Assist");
        MODULE_ALIASES.put("wtap", "W Tap");
        MODULE_ALIASES.put("autoblock", "Auto Block");
        MODULE_ALIASES.put("bridgeassist", "Bridge Assist");
        MODULE_ALIASES.put("backtrack", "Back Track");
        MODULE_ALIASES.put("hitselect", "Hit Select");
        MODULE_ALIASES.put("instantstop", "Instant Stop");
        MODULE_ALIASES.put("delayremover", "Delay Remover");
        MODULE_ALIASES.put("killaura", "KillAura");
        MODULE_ALIASES.put("ka", "KillAura");
    }

    private BindCommand() {}

    public static boolean handles(String message) {
        return matchPrefix(message) != null;
    }

    /**
     * @return log/chat line, or {@code null} if not a bind command
     */
    public static String execute(String message) {
        String prefix = matchPrefix(message);
        if (prefix == null)
            return null;

        String trimmed = message.trim();
        String args = trimmed.length() > prefix.length()
                ? trimmed.substring(prefix.length()).trim()
                : "";

        if (args.isEmpty())
            return "bind: usage .bind <module> <key> | .bind list | key=none to unbind";

        if (args.equalsIgnoreCase("list") || args.equalsIgnoreCase("l"))
            return formatBindList();

        ParsedBind parsed = parseModuleAndKey(args);
        if (parsed == null)
            return "bind: usage .bind <module> <key>";

        Module module = resolveModule(parsed.moduleToken);
        if (module == null)
            return "bind: unknown module \"" + parsed.moduleToken + "\"";

        try {
            int keyCode = KeyNames.parse(parsed.keyToken);
            ClientBootstrap.setModuleKeyCode(module.getName(), keyCode);
            if (keyCode < 0)
                return "bind: unbound " + module.getName();
            String label = KeyNames.format(keyCode);
            if (module.getKeybindAction() == KeybindAction.MENU)
                return "bind: " + module.getName() + " menu key -> [" + label + "]";
            return "bind: " + module.getName() + " -> [" + label + "]";
        } catch (IllegalArgumentException e) {
            return "bind: " + e.getMessage();
        }
    }

    private static String matchPrefix(String message) {
        if (message == null)
            return null;
        String trimmed = message.trim();
        if (matchesCommand(trimmed, PREFIX))
            return PREFIX;
        if (matchesCommand(trimmed, SHORT_PREFIX))
            return SHORT_PREFIX;
        return null;
    }

    private static boolean matchesCommand(String trimmed, String prefix) {
        if (!trimmed.regionMatches(true, 0, prefix, 0, prefix.length()))
            return false;
        if (trimmed.length() == prefix.length())
            return true;
        return Character.isWhitespace(trimmed.charAt(prefix.length()));
    }

    private static String formatBindList() {
        List<String> lines = new ArrayList<>();
        for (Module module : ModuleManager.INSTANCE.all()) {
            int code = module.getKeyCode();
            if (code > 0)
                lines.add(module.getName() + " [" + KeyNames.format(code) + "]");
        }
        if (lines.isEmpty())
            return "bind: no binds set";
        return "bind: " + String.join(", ", lines);
    }

    /**
     * Split {@code <module...> <key>} preferring longest module-name match,
     * then quoted names, then last-token-as-key.
     */
    private static ParsedBind parseModuleAndKey(String args) {
        if (args.startsWith("\"")) {
            int end = args.indexOf('"', 1);
            if (end > 1) {
                String module = args.substring(1, end).trim();
                String key = args.substring(end + 1).trim();
                if (!module.isEmpty() && !key.isEmpty())
                    return new ParsedBind(module, key);
            }
        }

        Module best = null;
        String bestKey = null;
        for (Module module : ModuleManager.INSTANCE.all()) {
            String name = module.getName();
            if (args.length() <= name.length())
                continue;
            if (!args.regionMatches(true, 0, name, 0, name.length()))
                continue;
            if (!Character.isWhitespace(args.charAt(name.length())))
                continue;
            String key = args.substring(name.length()).trim();
            if (key.isEmpty())
                continue;
            if (best == null || name.length() > best.getName().length()) {
                best = module;
                bestKey = key;
            }
        }
        if (best != null)
            return new ParsedBind(best.getName(), bestKey);

        int split = args.lastIndexOf(' ');
        if (split < 0)
            return null;
        String moduleToken = args.substring(0, split).trim();
        String keyToken = args.substring(split + 1).trim();
        if (moduleToken.isEmpty() || keyToken.isEmpty())
            return null;
        return new ParsedBind(moduleToken, keyToken);
    }

    private static Module resolveModule(String token) {
        String compact = token.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "").replace("_", "");
        String alias = MODULE_ALIASES.get(compact);
        if (alias == null)
            alias = MODULE_ALIASES.get(token.toLowerCase(Locale.ROOT));
        if (alias != null)
            return ModuleManager.INSTANCE.getModule(alias);
        Module exact = ModuleManager.INSTANCE.getModule(token);
        if (exact != null)
            return exact;
        for (Module module : ModuleManager.INSTANCE.all()) {
            String name = module.getName().toLowerCase(Locale.ROOT).replace(" ", "");
            if (name.equals(compact))
                return module;
        }
        return null;
    }

    private static final class ParsedBind {
        final String moduleToken;
        final String keyToken;

        ParsedBind(String moduleToken, String keyToken) {
            this.moduleToken = moduleToken;
            this.keyToken = keyToken;
        }
    }
}
