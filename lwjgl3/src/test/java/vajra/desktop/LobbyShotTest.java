package vajra.desktop;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vajra.flow.Flow;
import vajra.net.Net;
import vajra.net.Session;
import vajra.net.Transport;
import vajra.screen.LobbyScreen;
import vajra.screen.SeatColors;
import vajra.screen.Ui;

/** Screenshot autotest for the pilots table: a real four-pilot session off an in-memory
 *  fan wire renders the lobby into an off-screen frame. The sampled points assert the
 *  seat swatches wear the four co-op colours (pink, cyan, yellow, white — join order),
 *  and the READY column counts green where the roster said ready, red where not. Needs
 *  a real GL context, so it lives in lwjgl3, not headless core. */
class LobbyShotTest {
    private static final int W = 1280, H = 720;
    private static final float[] CLEAR = {13, 13, 26};
    private static final int ROSTER_Y = 412, PITCH = 28; // LobbyScreen's table geometry
    private static final int SWATCH_X = 640 - 238, READY_X = 640 + 10; // swatch + status columns

    /** One socket in a shared wire: sends land in the addressed port's queue, from-addressed. */
    static final class Fan implements Transport {
        private static final Map<Integer, ArrayDeque<Transport.Received>> wire = new HashMap<>();

        private final int port;

        static Fan at(int p) {
            wire.putIfAbsent(p, new ArrayDeque<>());
            return new Fan(p);
        }

        static void reset() { wire.clear(); }

        private Fan(int p) { port = p; }

        @Override
        public void send(byte[] data, SocketAddress to) {
            ArrayDeque<Transport.Received> dest = wire.get(((InetSocketAddress) to).getPort());
            if (dest != null) dest.add(new Transport.Received(data, new InetSocketAddress(port)));
        }

        @Override
        public Transport.Received poll() { return wire.get(port).pollFirst(); }

        @Override
        public void close() { wire.get(port).clear(); }
    }

    @Test
    void fourPilotsRenderASeatColouredTable() {
        List<String> failures = new ArrayList<>();
        AtomicReference<Throwable> boom = new AtomicReference<>();

        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setWindowedMode(W, H);
        cfg.setInitialVisible(false);
        cfg.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter() {
            Ui ui;
            SpriteBatch batch;
            FrameBuffer fbo;
            Session host;

            @Override
            public void create() {
                try {
                    ui = new Ui();
                    batch = new SpriteBatch();
                    fbo = new FrameBuffer(Pixmap.Format.RGBA8888, W, H, false);

                    Fan.reset();
                    host = Session.host(Fan.at(26000), "AGNI");
                    Session[] guests = new Session[3];
                    for (int i = 0; i < 3; i++)
                        guests[i] = Session.join(Fan.at(26001 + i), "PILOT" + (i + 1),
                                new InetSocketAddress(26000));

                    pumpAll(60, host, guests); // the mimicked handshakes land
                    guests[0].toggleReady();
                    guests[1].toggleReady();
                    pumpAll(60, host, guests);

                    assertEquals(4, host.count(), "four pilots sit in the room");

                    Net.session = host; // the lobby reads its seat off the global Net
                    Flow flow = new Flow().ui(ui);
                    LobbyScreen lobby = new LobbyScreen(flow, ui, batch);
                    try {
                        int[] px = shot(lobby);
                        writePng(px, "build/lobby-table.png");
                        check(px, failures);
                    } finally {
                        Net.session = null; // leave no global behind
                    }

                    ui.dispose();
                    batch.dispose();
                    fbo.dispose();
                } catch (Throwable t) {
                    boom.set(t);
                } finally {
                    Gdx.app.exit();
                }
            }

            /** Clear the bound frame buffer, draw the lobby, read the pixels back. */
            int[] shot(LobbyScreen lobby) {
                fbo.begin();
                Gdx.gl.glClearColor(CLEAR[0] / 255f, CLEAR[1] / 255f, CLEAR[2] / 255f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                lobby.render();
                IntBuffer buf = BufferUtils.newIntBuffer(W * H);
                Gdx.gl.glReadPixels(0, 0, W, H, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);
                fbo.end();
                int[] px = new int[W * H];
                buf.get(px);
                return px;
            }

            private void check(int[] px, List<String> f) {
                // every row's swatch wears exactly its seat colour
                int[][] want = {
                    {255, 82, 191},  // pink   - the host
                    {77, 255, 255},  // cyan   - pilot 2
                    {255, 235, 64},  // yellow - pilot 3
                    {255, 255, 255}, // white  - pilot 4
                };
                for (int i = 0; i < 4; i++) {
                    int y = ROSTER_Y - i * PITCH; // the swatch's centre, its rect spans y-6..y+6
                    expectNear(px, SWATCH_X + 6, y, want[i][0], want[i][1], want[i][2], 40,
                            "row " + i + " swatch colour", f);
                }
                // rows 1 and 2 are READY: green presence in the status column band
                band(px, READY_X, READY_X + 130, ROSTER_Y - PITCH - 12, ROSTER_Y - PITCH + 2,
                        SeatColors.GO, "row 1 READY green", f);
                band(px, READY_X, READY_X + 130, ROSTER_Y - 2 * PITCH - 12, ROSTER_Y - 2 * PITCH + 2,
                        SeatColors.GO, "row 2 READY green", f);
                // the host sat out: its status band carries the red
                band(px, READY_X, READY_X + 130, ROSTER_Y - 12, ROSTER_Y + 2,
                        SeatColors.WAIT, "host NOT READY red", f);
            }
        }, cfg);

        if (boom.get() != null) throw new AssertionError("lobby render crashed", boom.get());
        assertTrue(failures.isEmpty(), () -> "lobby table checks failed:\n  "
                + String.join("\n  ", failures));
    }

    private static void pumpAll(int steps, Session host, Session[] guests) {
        // the public pump: the fan wire is instant, so a few turns converge the roster
        for (int i = 0; i < steps; i++) {
            host.update();
            for (Session g : guests) g.update();
        }
    }

    // --- pixel plumbing, shared with HudScreenshotTest's idiom ------------------------------

    private static int at(int[] px, int x, int y) { // one int per pixel: R in the low byte
        return y * W + x;
    }

    private static int ch(int[] px, int x, int y, int k) {
        return (px[at(px, x, y)] >> (k * 8)) & 0xFF;
    }

    private static void expectNear(int[] px, int x, int y, int r, int g, int b, int tol,
                                   String what, List<String> f) {
        if (Math.abs(ch(px, x, y, 0) - r) > tol || Math.abs(ch(px, x, y, 1) - g) > tol
                || Math.abs(ch(px, x, y, 2) - b) > tol)
            f.add(what + ": got " + ch(px, x, y, 0) + "," + ch(px, x, y, 1) + ","
                    + ch(px, x, y, 2) + " at " + x + "," + y);
    }

    /** Pixels in the band roughly matching the colour (each channel) en masse. */
    private static void band(int[] px, int x0, int x1, int y0, int y1,
                             com.badlogic.gdx.graphics.Color c, String what, List<String> f) {
        int n = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                if (Math.abs(ch(px, x, y, 0) - Math.round(c.r * 255)) < 50
                        && Math.abs(ch(px, x, y, 1) - Math.round(c.g * 255)) < 50
                        && Math.abs(ch(px, x, y, 2) - Math.round(c.b * 255)) < 50) n++;
            }
        if (n <= 40) f.add(what + ": only " + n + " px");
    }

    private static void writePng(int[] px, String path) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int i = at(px, x, y);
                img.setRGB(x, H - 1 - y, (px[i] & 0xFF) << 16 | ((px[i] >> 8) & 0xFF) << 8
                        | (px[i] >> 16) & 0xFF);
            }
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
}
