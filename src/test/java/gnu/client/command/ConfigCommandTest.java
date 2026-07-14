package gnu.client.command;

import gnu.client.config.ConfigManager;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.*;

public class ConfigCommandTest {

    @Test
    public void currentReportsActiveConfigAndPath() {
        String previous = ConfigManager.instance().getActiveConfigName();
        ConfigManager.instance().setActiveConfigName("default");
        try {
            String result = ConfigCommand.execute(".config current");

            assertTrue(result, result.contains("active config default"));
            assertTrue(result, result.endsWith(" at " + ConfigManager.instance().getConfigPath()));
        } finally {
            ConfigManager.instance().setActiveConfigName(previous);
        }
    }

    @Test
    public void listShortcutWithNoNameCallsList() {
        String result = ConfigCommand.execute(".config l");

        assertTrue(result, result.startsWith("config: "));
    }

    @Test
    public void noArgSubcommandsRejectTrailingArguments() {
        assertUsage(ConfigCommand.execute(".config list foo"));
        assertUsage(ConfigCommand.execute(".config current foo"));
        assertUsage(ConfigCommand.execute(".config l foo"));
    }

    @Test
    public void importWithoutNameReturnsUsage() {
        assertUsage(ConfigCommand.execute(".config import"));
    }

    @Test
    public void loadMissingProfileReportsMissing() {
        String name = "foo";
        Assume.assumeFalse("foo config exists in this environment", Files.exists(ConfigManager.instance().getConfigPath(name)));

        assertEquals("config: config " + name + " not found", ConfigCommand.execute(".config load " + name));
    }

    @Test
    public void importMissingProfileReportsMissing() {
        String name = "foo";
        Assume.assumeFalse("foo config exists in this environment", Files.exists(ConfigManager.instance().getConfigPath(name)));

        assertEquals("config: config " + name + " not found", ConfigCommand.execute(".config import " + name));
    }

    @Test
    public void handlesConfigPrefixesAndShortcuts() {
        assertTrue(ConfigCommand.handles(".config"));
        assertTrue(ConfigCommand.handles(".cfg list"));
        assertTrue(ConfigCommand.handles(".c current"));
        assertFalse(ConfigCommand.handles(".bind list"));
        assertFalse(ConfigCommand.handles(".configuration"));
        assertFalse(ConfigCommand.handles(".configfoo"));
        assertTrue(ConfigCommand.handles(".config list"));
        assertNull(ConfigCommand.execute(".bind list"));
    }

    @Test
    public void noArgsReturnsUsage() {
        assertEquals(
                "config: usage .config save [name] | load <name> | export <name> | import <name> | list | current | folder | default",
                ConfigCommand.execute(".config"));
    }

    @Test
    public void invalidSubcommandIsReported() {
        String result = ConfigCommand.execute(".config invalid");

        assertTrue(result, result.contains("invalid argument"));
        assertTrue(result, result.contains("invalid"));
    }

    @Test
    public void prefixBoundariesRejectLongerCommands() {
        assertFalse(ConfigCommand.handles(".configuration"));
        assertFalse(ConfigCommand.handles(".configfoo"));
        assertFalse(ConfigCommand.handles(".configlist"));
        assertTrue(ConfigCommand.handles(".config"));
        assertTrue(ConfigCommand.handles(".config list"));
    }

    @Test
    public void loadWithoutNameReturnsUsage() {
        assertUsage(ConfigCommand.execute(".config load"));
        assertUsage(ConfigCommand.execute(".cfg import"));
    }

    private static void assertUsage(String result) {
        assertEquals(
                "config: usage .config save [name] | load <name> | export <name> | import <name> | list | current | folder | default",
                result);
    }
}
