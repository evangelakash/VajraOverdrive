package vajra.audio;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vajra.Settings;
import vajra.screen.ShopController;
import vajra.screen.ShopUi;
import vajra.screen.Template;
import vajra.screen.UiMenu;
import vajra.world.World;

/** THE MENUS AND THE BAZAAR SPEAK. Every step of a menu, every question and answer in the
 *  hangar, and the pilot's volume setting. */
class SfxUiTest {
    private final List<Sfx> played = new ArrayList<>();

    @BeforeEach
    void listen() {
        played.clear();
        Audio.use((s, v, p) -> played.add(s));
    }

    @AfterEach
    void hush() {
        Audio.use(null);
    }

    private ShopController shop(World w) {
        ShopController c = new ShopController(w, new ShopUi(null));
        c.open();
        played.clear();
        return c;
    }

    // ---- menus --------------------------------------------------------------------------------
    @Test
    void steppingAMenuTicksOncePerStep() {
        UiMenu m = new UiMenu(new String[] { "A", "B", "C" }, 400, 40, 100, 20);
        m.keyDown(Input.Keys.S);
        m.keyDown(Input.Keys.S);
        assertEquals(List.of(Sfx.UI_MOVE, Sfx.UI_MOVE), played);
    }

    @Test
    void hoveringTicksOnlyWhenTheCursorReallyMoves() {
        UiMenu m = new UiMenu(new String[] { "A", "B", "C" }, 400, 40, 100, 20);
        Vector2 onB = new Vector2(Template.COL, 360);
        m.hover(onB);
        assertEquals(1, played.size(), "arriving on a leaf ticks");
        m.hover(onB);
        m.hover(onB);
        assertEquals(1, played.size(), "resting on it does not tick every frame");
        m.hover(new Vector2(0, 0));
        assertEquals(1, played.size(), "leaving to empty space is silent");
    }

    // ---- the hangar ---------------------------------------------------------------------------
    @Test
    void openingAndClosingTheHangarHaveTheirOwnSounds() {
        World w = new World();
        ShopController c = new ShopController(w, new ShopUi(null));
        c.open();
        assertTrue(played.contains(Sfx.SHOP_OPEN));
        c.close();
        assertTrue(played.contains(Sfx.SHOP_CLOSE));
    }

    @Test
    void theTabsTurnWithALeafSound() {
        ShopController c = shop(new World());
        c.keyDown(Input.Keys.TAB);
        assertEquals(List.of(Sfx.UI_TAB), played);
    }

    @Test
    void aPurchaseIsAskedThenPaidWithCoins() {
        World w = new World();
        w.hangar.coins = 400;
        ShopController c = shop(w);
        c.ui().setTab(ShopUi.TAB_REFIT);
        c.ui().setShip(1);
        c.ui().setTier(0);
        c.keyDown(Input.Keys.B);
        assertEquals(List.of(Sfx.UI_CONFIRM), played, "the question is asked");
        c.keyDown(Input.Keys.ENTER);
        assertEquals(List.of(Sfx.UI_CONFIRM, Sfx.PURCHASE), played, "and the coins change hands");
    }

    @Test
    void decliningIsAGentleBack() {
        World w = new World();
        w.hangar.coins = 400;
        ShopController c = shop(w);
        c.ui().setShip(1);
        c.ui().setTier(0);
        c.keyDown(Input.Keys.B);
        played.clear();
        c.keyDown(Input.Keys.ESCAPE);
        assertEquals(List.of(Sfx.UI_BACK), played);
        assertEquals(400, w.hangar.coins);
    }

    @Test
    void whatYouCannotAffordIsRefusedWithABuzz() {
        World w = new World();
        w.hangar.coins = 5;
        ShopController c = shop(w);
        c.ui().setShip(1);
        c.ui().setTier(0);
        c.keyDown(Input.Keys.B);
        assertEquals(List.of(Sfx.UI_DENY), played, "too dear: a buzz, and no question");
    }

    @Test
    void whatYouAlreadyOwnIsNotRefused() {
        World w = new World();
        ShopController c = shop(w);
        c.ui().setShip(0);
        c.ui().setTier(0);
        c.keyDown(Input.Keys.B); // the starter hull, already owned
        assertTrue(played.isEmpty(), "owning it is not an error");
    }

    @Test
    void fittingAnOwnedWeaponChimesAndSpendsNothing() {
        World w = new World();
        ShopController c = shop(w);
        c.ui().setTab(ShopUi.TAB_PATTERNS);
        c.ui().setWeapon(0);
        c.keyDown(Input.Keys.N);
        assertEquals(List.of(Sfx.UI_SELECT), played);
    }

    @Test
    void sellingShardsPaysInCoinsAndNothingToSellIsRefused() {
        World w = new World();
        ShopController c = shop(w);
        c.keyDown(Input.Keys.X);
        assertEquals(List.of(Sfx.UI_DENY), played, "nothing to sell");
        played.clear();
        w.hangar.addShard(0, 0);
        c.keyDown(Input.Keys.X);
        assertEquals(List.of(Sfx.SELL), played);
    }

    // ---- the volume setting -------------------------------------------------------------------
    @Test
    void theVolumeIsASettingThatSurvivesTheSaveAndIsClamped() {
        Settings s = new Settings();
        assertEquals(0.8f, s.sfxVolume, 0.0001f, "a sensible default, not full blast");
        s.sfxVolume = 0.4f;
        Settings back = new Settings();
        Settings.deserialize(back, Settings.serialize(s));
        assertEquals(0.4f, back.sfxVolume, 0.0001f);
        Settings wild = new Settings();
        Settings.deserialize(wild, "{\"sfxVolume\":7}");
        assertEquals(1f, wild.sfxVolume, 0.0001f, "a hand-edited file cannot blow the speakers");
        Settings old = new Settings();
        Settings.deserialize(old, "{\"shake\":0.5,\"popupScale\":1}"); // a save from before sound
        assertEquals(0.8f, old.sfxVolume, 0.0001f, "an old save gets the default");
    }
}
