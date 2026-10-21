package vajra.update;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import com.badlogic.gdx.Gdx;

/** The home screen's UPDATE row: pulls the newest setup exe from a source the
 *  install carries — "updates.txt" beside the game, one line, an http(s) URL or a
 *  plain file path (this dev tree's own setup exe is a valid source). It asks the
 *  downloaded wizard for its build stamp (--version) and compares it with the
 *  installed gamever.txt: same build stops there, a different one installs silently
 *  over this very install and relaunches, an exe that answers nothing is refused —
 *  the closed game never reopens into something unvouched-for. */
public final class Updater {

    public enum State { IDLE, UNCONFIGURED, DOWNLOADING, CURRENT, RUNNING, FAILED }

    private volatile State state = State.IDLE;
    private volatile String message = "";

    public State state() { return state; }
    public String message() { return message; }

    static final String NEW_EXE = "update-new.exe";

    /** The update source: one line in updates.txt beside the game. */
    static String source() {
        try {
            java.nio.file.Path f = Paths.get("updates.txt");
            if (Files.exists(f)) {
                String s = Files.readString(f).strip();
                if (!s.isEmpty()) return s;
            }
        } catch (Exception gone) { // unreadable reads as unconfigured
        }
        return null;
    }

    static String localVersion() {
        try {
            java.nio.file.Path f = Paths.get("gamever.txt");
            if (Files.exists(f)) return Files.readString(f).strip();
        } catch (Exception gone) { // a dev tree has no stamp
        }
        return "unknown";
    }

    /** The downloaded wizard's own stamp, via the --version mode. An empty answer
     *  is the caller's refusal signal: a winexe that can't say what it carries
     *  gets nothing installed. */
    static String probeVersion(File exe) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(exe.getAbsolutePath(), "--version")
                .redirectErrorStream(true).start();
        try {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String s = r.readLine();
            return s == null ? "" : s.strip();
        } finally {
            p.waitFor(20, TimeUnit.SECONDS);
            p.destroyForcibly();
        }
    }

    /** The newest setup exe, downloaded over the source line. */
    static void fetch(String src) throws IOException {
        URLConnection c = new java.net.URL(src).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(120000); // a full payload over a slow link
        try (InputStream in = c.getInputStream()) {
            Files.copy(in, Paths.get(NEW_EXE), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void begin() {
        if (state == State.DOWNLOADING || state == State.RUNNING) return;
        if (!new File("play.bat").isFile()) { // the updater serves the install, not a dev tree
            state = State.FAILED;
            message = "UPDATING NEEDS THE INSTALLED GAME (play.bat BESIDE IT)";
            return;
        }
        String src = source();
        if (src == null) {
            state = State.UNCONFIGURED;
            message = "PUT updates.txt BESIDE THE GAME — ONE LINE: A URL OR PATH TO THE NEW SETUP EXE";
            return;
        }
        state = State.DOWNLOADING;
        message = "FETCHING THE NEWEST BUILD ...";
        Thread t = new Thread(() -> work(src), "vajra-update");
        t.setDaemon(true);
        t.start();
    }

    private void work(String src) {
        try {
            fetch(src);
            String fresh = probeVersion(new File(NEW_EXE));
            if (fresh.isEmpty()) { // it did not answer: refuse and clean up
                Files.deleteIfExists(Paths.get(NEW_EXE));
                state = State.FAILED;
                message = "THE DOWNLOADED BUILD DIDN'T ANSWER — UPDATE CANCELLED";
                return;
            }
            if (fresh.equals(localVersion())) {
                Files.deleteIfExists(Paths.get(NEW_EXE));
                state = State.CURRENT;
                message = "ALREADY AT THE LATEST BUILD — " + fresh;
                return;
            }
            String dest = new File("").getAbsolutePath();
            new ProcessBuilder(dest + File.separator + NEW_EXE,
                    "--silent", dest, "-", "launch").start();
            state = State.RUNNING;
            message = "BUILD " + fresh + " READY — THE INSTALLER TAKES OVER AND RESTARTS THE GAME";
            try {
                Gdx.app.postRunnable(Gdx.app::exit); // the installer stops a clinging process anyway
            } catch (Throwable headless) {
                Thread.sleep(8000); // no app to exit through: linger while the swap happens
            }
        } catch (Exception gone) {
            state = State.FAILED;
            message = "UPDATE FAILED — CHECK updates.txt AND THE CONNECTION";
        }
    }
}
