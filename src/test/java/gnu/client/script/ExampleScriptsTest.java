package gnu.client.script;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Structural checks for the bundled example scripts under {@code scripts/}.
 * Ensures bare-body conventions match what {@link ScriptManager} expects.
 */
public class ExampleScriptsTest {

    private static final Path SCRIPTS_DIR = resolveScriptsDir();
    private static final Pattern PACKAGE_OR_CLASS = Pattern.compile(
            "(?m)^\\s*(package\\s+|public\\s+class\\s+|class\\s+)");
    private static final Pattern REGISTER_CALL = Pattern.compile(
            "modules\\.register(Button|Slider|Mode)\\s*\\(");
    private static final Pattern ON_LOAD = Pattern.compile(
            "(?s)void\\s+onLoad\\s*\\(\\s*\\)\\s*\\{.*?\\}");
    private static final Pattern OVERLAY_OR_RENDER = Pattern.compile(
            "(?m)^\\s*(public\\s+)?void\\s+(onOverlay|onRender)\\s*\\(");

    private static Path resolveScriptsDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path direct = cwd.resolve("scripts");
        if (Files.isDirectory(direct))
            return direct;
        Path parent = cwd.resolve("..").resolve("scripts").normalize();
        if (Files.isDirectory(parent))
            return parent;
        return direct;
    }

    @Test
    public void exampleScriptsDirectoryExists() {
        assertTrue("Expected scripts/ at " + SCRIPTS_DIR.toAbsolutePath(),
                Files.isDirectory(SCRIPTS_DIR));
    }

    @Test
    public void fiftyExampleScriptsPresent() throws IOException {
        List<Path> files = listJavaScripts();
        assertTrue("Expected at least 50 example .java scripts, found " + files.size(),
                files.size() >= 50);
    }

    @Test
    public void scriptsAreBareBodiesWithoutPackageOrClass() throws IOException {
        for (Path file : listJavaScripts()) {
            String body = read(file);
            assertFalse(file.getFileName() + " must not declare package/class",
                    PACKAGE_OR_CLASS.matcher(body).find());
        }
    }

    @Test
    public void settingsRegistrationLivesInOnLoad() throws IOException {
        for (Path file : listJavaScripts()) {
            String body = read(file);
            Matcher reg = REGISTER_CALL.matcher(body);
            if (!reg.find())
                continue;
            Matcher load = ON_LOAD.matcher(body);
            assertTrue(file.getFileName() + " registers settings but has no onLoad()",
                    load.find());
            String onLoadBody = load.group();
            Matcher inLoad = REGISTER_CALL.matcher(onLoadBody);
            assertTrue(file.getFileName() + " must call modules.register* inside onLoad()",
                    inLoad.find());
            String withoutLoad = body.replace(onLoadBody, "");
            assertFalse(file.getFileName() + " has modules.register* outside onLoad()",
                    REGISTER_CALL.matcher(withoutLoad).find());
        }
    }

    @Test
    public void overlayAndRenderHooksArePublic() throws IOException {
        for (Path file : listJavaScripts()) {
            String body = read(file);
            Matcher m = OVERLAY_OR_RENDER.matcher(body);
            while (m.find()) {
                assertTrue(file.getFileName() + " " + m.group(2)
                                + " must be declared public to override Module",
                        m.group(1) != null && m.group(1).contains("public"));
            }
        }
    }

    private static List<Path> listJavaScripts() throws IOException {
        List<Path> out = new ArrayList<Path>();
        if (!Files.isDirectory(SCRIPTS_DIR))
            return out;
        DirectoryStream<Path> stream = Files.newDirectoryStream(SCRIPTS_DIR, "*.java");
        try {
            for (Path p : stream)
                out.add(p);
        } finally {
            stream.close();
        }
        java.util.Collections.sort(out);
        return out;
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
