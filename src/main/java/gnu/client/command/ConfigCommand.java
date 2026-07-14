package gnu.client.command;

import gnu.client.config.ConfigManager;
import gnu.client.config.ConfigManager.ProfileLoadResult;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Client-only configuration command. ChatCommandHandler prints returned strings.
 */
public final class ConfigCommand {

    private static final String PREFIX = ".config";
    private static final String SHORT_PREFIX = ".cfg";
    private static final String TINY_PREFIX = ".c";
    private static final String DEFAULT_CONFIG = "default";

    private ConfigCommand() {}

    public static boolean handles(String message) {
        return matchPrefix(message) != null;
    }

    /**
     * @return concise client-facing result, or {@code null} if this is not a config command
     */
    public static String execute(String message) {
        String prefix = matchPrefix(message);
        if (prefix == null)
            return null;

        String args = argsAfterPrefix(message, prefix);
        if (args.isEmpty())
            return usage();

        String[] parts = args.trim().split("\\s+", 2);
        String subcommand = parts[0].toLowerCase(Locale.ROOT);

        if (isListShortcut(subcommand)) {
            if (parts.length == 1)
                return list();
            return usage();
        }

        if (hasTrailingArgs(parts)) {
            if (subcommand.equals("list") || subcommand.equals("current") || isFolderSubcommand(subcommand) || subcommand.equals("default"))
                return usage();
        }

        if (parts.length == 1) {
            if (subcommand.equals("save") || subcommand.equals("s"))
                return saveActive();
            if (subcommand.equals("load") || subcommand.equals("import"))
                return load(null);
            if (subcommand.equals("list"))
                return list();
            if (subcommand.equals("current"))
                return current();
            if (isFolderSubcommand(subcommand))
                return folder();
            if (subcommand.equals("default"))
                return setDefault();
            return "config: invalid argument \"" + parts[0] + "\"";
        }

        String name = parts[1].trim();
        if (subcommand.equals("save") || subcommand.equals("s") || subcommand.equals("export"))
            return save(name);
        if (subcommand.equals("load") || subcommand.equals("import"))
            return load(name);
        if (subcommand.equals("list") || subcommand.equals("l"))
            return list();
        if (subcommand.equals("current"))
            return current();
        if (isFolderSubcommand(subcommand))
            return folder();
        if (subcommand.equals("default"))
            return setDefault();

        return "config: invalid argument \"" + parts[0] + "\"";
    }

    private static boolean hasTrailingArgs(String[] parts) {
        return parts.length > 1;
    }

    private static String usage() {
        return "config: usage .config save [name] | load <name> | export <name> | import <name> | list | current | folder | default";
    }

    private static String argsAfterPrefix(String message, String prefix) {
        String trimmed = message.trim();
        return trimmed.length() > prefix.length()
                ? trimmed.substring(prefix.length()).trim()
                : "";
    }

    private static boolean isListShortcut(String subcommand) {
        return subcommand.equals("l");
    }

    private static boolean isFolderSubcommand(String subcommand) {
        return subcommand.equals("folder") || subcommand.equals("dir") || subcommand.equals("directory");
    }

    private static String saveActive() {
        try {
            ConfigManager.instance().flush();
            return "config: saved active config";
        } catch (Exception e) {
            return "config: save failed: " + e.getMessage();
        }
    }

    private static String save(String name) {
        try {
            if (name == null || name.trim().isEmpty())
                return saveActive();

            ConfigManager.instance().saveProfile(name);
            return "config: exported " + sanitizeProfileName(name);
        } catch (Exception e) {
            return "config: save failed: " + e.getMessage();
        }
    }

    private static String load(String name) {
        if (name == null || name.trim().isEmpty())
            return usage();

        String sanitized = sanitizeProfileName(name);
        ProfileLoadResult result = ConfigManager.instance().loadProfile(name);
        switch (result) {
            case LOADED:
                return "config: loaded " + sanitized;
            case MISSING:
                return "config: config " + sanitized + " not found";
            case INVALID_JSON:
                return "config: invalid JSON in " + sanitized;
            case FAILED:
            default:
                String detail = ConfigManager.instance().getLastProfileLoadError();
                if (detail == null || detail.trim().isEmpty())
                    detail = sanitized;
                return "config: load failed: " + detail;
        }
    }

    private static String list() {
        try {
            List<String> profiles = ConfigManager.instance().listProfiles();
            if (profiles.isEmpty())
                return "config: no configs found";

            return "config: " + String.join(", ", profiles);
        } catch (Exception e) {
            return "config: list failed: " + e.getMessage();
        }
    }

    private static String current() {
        String name = ConfigManager.instance().getActiveConfigName();
        return "config: active config " + name + " at " + ConfigManager.instance().getConfigPath();
    }

    private static String folder() {
        try {
            Path configsDirectory = ConfigManager.instance().getConfigsDirectory();
            Files.createDirectories(configsDirectory);
            Desktop.getDesktop().open(configsDirectory.toFile());
            return "config: opened " + configsDirectory;
        } catch (Exception e) {
            return "config: folder failed: " + e.getMessage();
        }
    }

    private static String setDefault() {
        ProfileLoadResult result = ConfigManager.instance().loadProfile(DEFAULT_CONFIG);
        if (result == ProfileLoadResult.LOADED)
            return "config: active config set to default";
        if (result == ProfileLoadResult.MISSING)
            return "config: default config not found";
        if (result == ProfileLoadResult.INVALID_JSON)
            return "config: invalid JSON in default";
        return "config: load failed: " + ConfigManager.instance().getLastProfileLoadError();
    }

    private static String matchPrefix(String message) {
        if (message == null)
            return null;

        String trimmed = message.trim();
        if (matchesCommand(trimmed, PREFIX))
            return PREFIX;
        if (matchesCommand(trimmed, SHORT_PREFIX))
            return SHORT_PREFIX;
        if (matchesCommand(trimmed, TINY_PREFIX))
            return TINY_PREFIX;
        return null;
    }

    private static boolean matchesCommand(String trimmed, String prefix) {
        if (!trimmed.regionMatches(true, 0, prefix, 0, prefix.length()))
            return false;
        if (trimmed.length() == prefix.length())
            return true;
        return Character.isWhitespace(trimmed.charAt(prefix.length()));
    }

    private static String sanitizeProfileName(String requested) {
        if (requested == null || requested.trim().isEmpty())
            return DEFAULT_CONFIG;
        if (DEFAULT_CONFIG.equalsIgnoreCase(requested) || "!".equals(requested))
            return DEFAULT_CONFIG;

        String raw = requested.replaceAll("[^A-Za-z0-9._ -]", "_");
        String sanitized = raw.replaceAll("[. ]+", " ").trim();
        if (sanitized.isEmpty() || ".".equals(sanitized) || "..".equals(sanitized))
            return DEFAULT_CONFIG;
        return sanitized;
    }
}
