package gnu.client.command;

import org.junit.Test;

import static org.junit.Assert.*;

public class HelpCommandTest {

    @Test
    public void helpTextContainsConfigAndBindUsage() {
        String help = HelpCommand.help();

        assertTrue(help, help.contains(".config save [name] | load <name>"));
        assertTrue(help, help.contains(".bind <module> <key>"));
    }

    @Test
    public void handlesHelpPrefixes() {
        assertTrue(HelpCommand.handles(".help"));
        assertTrue(HelpCommand.handles(".help "));
        assertTrue(HelpCommand.handles(".h"));
        assertTrue(HelpCommand.handles(".h "));
        assertFalse(HelpCommand.handles(".helpme"));
        assertFalse(HelpCommand.handles(".hook"));
        assertNull(HelpCommand.execute(".bind list"));
    }

    @Test
    public void executeReturnsHelpForHelpPrefixes() {
        assertEquals(HelpCommand.help(), HelpCommand.execute(".help"));
        assertEquals(HelpCommand.help(), HelpCommand.execute(".h"));
    }
}
