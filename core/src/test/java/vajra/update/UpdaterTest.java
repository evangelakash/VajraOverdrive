package vajra.update;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** The updater's pure half: the dev-tree guard names the problem instead of
 *  writing anywhere, and a file:// fetch lands byte-exact — that source shape is
 *  what this tree's own setup exe makes possible. What the process spawn does is
 *  verified live, not in CI. */
class UpdaterTest {

    @Test
    void aDevTreeCannotUpdateItself() {
        Updater u = new Updater(); // the test cwd is no install: play.bat lives beside the real one
        u.begin();
        assertTrue(u.message().contains("INSTALLED"), "the guard names the install tree: " + u.message());
    }

    @Test
    void aFileUrlFetchesByteExact() throws Exception {
        File src = File.createTempFile("vajra-src", ".bin");
        byte[] body = "the newest build".getBytes(StandardCharsets.UTF_8);
        Files.write(src.toPath(), body);
        try {
            Updater.fetch(src.toURI().toString());
            assertArrayEquals(body, Files.readAllBytes(Paths.get(Updater.NEW_EXE)));
        } finally {
            Files.deleteIfExists(Paths.get(Updater.NEW_EXE));
            Files.deleteIfExists(src.toPath());
        }
    }
}
