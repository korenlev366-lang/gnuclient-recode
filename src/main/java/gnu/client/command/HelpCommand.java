package gnu.client.command;

/**
 * Client-only help command. ChatCommandHandler prints returned strings.
 */
public final class HelpCommand {

    private static final String PREFIX = ".help";
    private static final String SHORT_PREFIX = ".h";

    private HelpCommand() {}

    public static boolean handles(String message) {
        return matchPrefix(message) != null;
    }

    public static String execute(String message) {
        return matchPrefix(message) == null ? null : help();
    }

    public static String help() {
        return "GNUClient commands:\n"
                + ".config save [name] | load <name> | export <name> | import <name> | list | current | folder | default\n"
                + ".bind <module> <key> | .bind list | key=none to unbind\n"
                + "script commands: enable a Scripts module that registered .name (see scripts/)";
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
}
