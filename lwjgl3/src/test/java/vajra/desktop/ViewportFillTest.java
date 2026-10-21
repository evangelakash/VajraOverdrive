package vajra.desktop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vajra.flow.Flow;
import vajra.screen.HomeScreen;
import vajra.screen.SplashScreen;
import vajra.screen.Ui;

/** The UI-only screens have no viewport of their own — only GameScreen did — so after the
 *  boot-time switch to fullscreen they kept whatever glViewport the last world render left
 *  behind, and drew short of the top of the frame by a black strip. It cleared itself the
 *  moment PLAY pushed GameScreen, which applies its own viewport every frame; that is what
 *  made it look like a splash-only bug.
 *
 *  <p>This resizes the window while a UI-only state is on top and checks the frame is filled to
 *  all four edges. Needs a GL context, so it lives in lwjgl3. */
class ViewportFillTest {
    private static final int W = 1280, H = 720;

    @Test
    void uiOnlyScreensFillTheFrameAfterAResize() {
        List<String> failures = new ArrayList<>();
        AtomicReference<Throwable> boom = new AtomicReference<>();

        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setWindowedMode(W, H);
        cfg.setInitialVisible(false);
        cfg.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter() {
            Ui ui;
            SpriteBatch batch;
            Flow flow;

            @Override
            public void create() {
                try {
                    ui = new Ui();
                    batch = new SpriteBatch();
                    flow = new Flow().ui(ui);

                    flow.set(new HomeScreen(flow, ui, batch));
                    checkEdges(frame(), "title at the launch size", failures);

                    // the same thing the boot-time fullscreen switch does: the framebuffer
                    // changes size under a UI-only state that has no viewport of its own
                    Gdx.graphics.setWindowedMode(1600, 900);
                    waitForSize(1600, 900);
                    checkEdges(frame(), "title after the framebuffer changed", failures);

                    // and the studio card, which is what actually boots
                    flow.set(new SplashScreen(flow, ui, batch));
                    checkEdges(frame(), "studio card after the framebuffer changed", failures);

                    flow.dispose();
                    ui.dispose();
                    batch.dispose();
                } catch (Throwable t) {
                    boom.set(t);
                } finally {
                    Gdx.app.exit();
                }
            }

            /** LWJGL applies a mode change on the next window update, so spin until it lands. */
            void waitForSize(int w, int h) {
                for (int i = 0; i < 600; i++) {
                    if (Gdx.graphics.getWidth() == w && Gdx.graphics.getHeight() == h) return;
                    try { Thread.sleep(5); } catch (InterruptedException ignored) { }
                }
            }

            int[] frame() {
                Gdx.gl.glClearColor(0, 0, 0, 1);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                flow.paint();
                int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
                IntBuffer buf = BufferUtils.newIntBuffer(w * h);
                Gdx.gl.glReadPixels(0, 0, w, h, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);
                int[] px = new int[w * h];
                buf.get(px);
                return px;
            }

            /** The four edges of the frame must all carry the screen's field. A viewport that is short or
             *  offset leaves a run of untouched clear colour down one side - and here it was along
             *  the TOP. Only the outer few pixels are sampled: the middle of the frame is where
             *  the logo and the menu rows legitimately are. */
            void checkEdges(int[] px, String what, List<String> f) {
                int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
                // reference: the left edge at mid height - always field, never ornament (the
                // corner brackets are inset 28 px in)
                int ref = px[(h / 2) * w + 1];
                for (int x = 0; x < w; x++) {
                    if (!same(px[x], ref)) { // bottom row, y = h-1
                        f.add(what + ": bottom edge not filled at x " + x
                                + " (" + hex(px[x]) + " vs field " + hex(ref) + ")");
                        return;
                    }
                    if (!same(px[(h - 1) * w + x], ref)) { // top row
                        f.add(what + ": TOP edge not filled at x " + x
                                + " (" + hex(px[(h - 1) * w + x]) + " vs field " + hex(ref) + ")");
                        return;
                    }
                }
                for (int y = 0; y < h; y++) {
                    if (!same(px[y * w], ref)) {
                        f.add(what + ": left edge not filled at y " + y
                                + " (" + hex(px[y * w]) + " vs field " + hex(ref) + ")");
                        return;
                    }
                    if (!same(px[y * w + w - 1], ref)) {
                        f.add(what + ": right edge not filled at y " + y
                                + " (" + hex(px[y * w + w - 1]) + " vs field " + hex(ref) + ")");
                        return;
                    }
                }
            }

            String hex(int p) { return String.format("#%02x%02x%02x", r(p), g(p), b(p)); }

            /** The field is drawn flat, so only a genuinely different pixel counts as a hole. */
            boolean same(int p, int ref) {
                return Math.abs(r(p) - r(ref)) <= 2 && Math.abs(g(p) - g(ref)) <= 2
                        && Math.abs(b(p) - b(ref)) <= 2;
            }

            static int r(int p) { return p & 0xff; }
            static int g(int p) { return (p >> 8) & 0xff; }
            static int b(int p) { return (p >> 16) & 0xff; }

            void check(boolean ok, String what, List<String> f) { if (!ok) f.add(what); }
        }, cfg);

        if (boom.get() != null) throw new AssertionError("viewport fill render crashed", boom.get());
        assertTrue(failures.isEmpty(), () -> "viewport checks failed:\n  "
                + String.join("\n  ", failures));
    }
}