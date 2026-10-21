package vajra.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import vajra.VajraGame;

public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Vajra Overdrive");
        // the mk1 base ship flies on the window and taskbar
        config.setWindowIcon("vajra_ships/mk_1/ship_1.png");
        config.setWindowedMode(1920, 1080); // HD home; F12 swaps 1280x720, F11 fullscreen
        config.useVsync(true);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4); // 4x MSAA: the bazaar is vector, and smooth
        config.setForegroundFPS(60);
        new Lwjgl3Application(new VajraGame(), config);
    }
}
