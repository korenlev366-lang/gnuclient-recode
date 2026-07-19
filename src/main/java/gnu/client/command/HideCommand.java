package gnu.client.command;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Player-facing toggle for the per-module "hidden" flag (excludes a module from the
 * on-screen ArrayList HUD). Usage:
 *   .hide <module>          hide one module from the array
 *   .unhide <module>        show one module in the array
 *   .hide all               hide every module (except HUD/settings)
 *   .unhide all             show every module
 */
public final class HideCommand {

    private static final String PREFIX = ".hide";
    private static final String SHORT_PREFIX = ".h";

    private HideCommand() {}

    public static boolean handles(String message) {
        return matchPrefix(message) != null;
    }

    public static String execute(String message) {
        String prefix = matchPrefix(message);
        if (prefix == null)
            return null;

        String args = argsAfterPrefix(message, prefix).trim();
        if (args.isEmpty())
            return usage();

        boolean hide = !args.toLowerCase(Locale.ROOT).startsWith("un");
        String target = hide ? args : args.substring("un".length()).trim();
        if (target.isEmpty())
            return usage();

        if (target.equalsIgnoreCase("all")) {
            int n = 0;
            for (Module m : ModuleManager.INSTANCE.all()) {
                if (m instanceof gnu.client.module.modules.visual.HudModule)
                    continue;
                if (m.getCategory() == gnu.client.module.Category.SETTINGS)
                    continue;
                m.setHidden(hide);
                n++;
            }
            return "hide: " + (hide ? "hid" : "showed") + " " + n + " modules from the array";
        }

        Module m = ModuleManager.INSTANCE.getModule(target);
        if (m == null)
            return "hide: no module named \"" + target + "\"";
        m.setHidden(hide);
        return "hide: " + (hide ? "hid" : "showed") + " " + m.getName() + " from the array";
    }

    private static String usage() {
        return "hide: usage .hide <module|all> | .unhide <module|all>";
    }

    private static String argsAfterPrefix(String message, String prefix) {
        String trimmed = message.trim();
        return trimmed.length() > prefix.length()
                ? trimmed.substring(prefix.length()).trim()
                : "";
    }

    private static String matchPrefix(String message) {
        if (message == null)
            return null;
        String trimmed = message.trim();
        if (matches(trimmed, PREFIX))
            return PREFIX;
        if (matches(trimmed, SHORT_PREFIX))
            return SHORT_PREFIX;
        return null;
    }

    private static boolean matches(String trimmed, String prefix) {
        if (!trimmed.regionMatches(true, 0, prefix, 0, prefix.length()))
            return false;
        if (trimmed.length() == prefix.length())
            return true;
        return Character.isWhitespace(trimmed.charAt(prefix.length()));
    }
}
