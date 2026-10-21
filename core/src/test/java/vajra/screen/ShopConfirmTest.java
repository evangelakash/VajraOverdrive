package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import vajra.ship.Hangar;
import vajra.ship.ShipDef;
import vajra.weapon.WeaponDef;
import vajra.world.World;

/** EVERY PURCHASE IS ASKED FOR. B and N and a click on a price raise a question; coins and
 *  stock move only on a yes, and ESC says no without closing the hangar. */
class ShopConfirmTest {

    private static ShopController shop(World w) {
        ShopController c = new ShopController(w, new ShopUi(null)); // the keys never touch GL
        c.open();
        return c;
    }

    @Test
    void buyingAHullAsksFirstAndSpendsOnlyOnYes() {
        World w = new World();
        w.hangar.coins = 400;
        ShopController c = shop(w);
        c.ui().setTab(ShopUi.TAB_REFIT);
        c.ui().setShip(1);
        c.ui().setTier(0);

        c.keyDown(Input.Keys.B);
        assertTrue(c.confirming(), "B raises the question");
        assertEquals(400, w.hangar.coins, "and nothing is spent yet");
        assertFalse(w.hangar.ownsShip(1));
        assertEquals(ShopUi.Confirm.HULL, c.ui().confirm().kind);
        assertEquals(ShipDef.ALL[1].cost, c.ui().confirm().coins);
        assertEquals(400 - ShipDef.ALL[1].cost, c.ui().confirm().coinsLeft);

        c.keyDown(Input.Keys.ENTER);
        assertFalse(c.confirming());
        assertTrue(w.hangar.ownsShip(1), "yes buys it");
        assertEquals(400 - ShipDef.ALL[1].cost, w.hangar.coins);
        assertEquals(1, w.hangar.shipIndex, "and fits it, as before");
    }

    @Test
    void escCancelsTheQuestionAndLeavesTheShopOpen() {
        World w = new World();
        w.hangar.coins = 400;
        ShopController c = shop(w);
        c.ui().setShip(1);
        c.ui().setTier(0);
        c.keyDown(Input.Keys.B);
        assertTrue(c.confirming());

        c.keyDown(Input.Keys.ESCAPE);
        assertFalse(c.confirming());
        assertTrue(c.isOpen(), "ESC cancelled the purchase, not the hangar");
        assertEquals(400, w.hangar.coins);
        assertFalse(w.hangar.ownsShip(1));
    }

    @Test
    void otherKeysAreSwallowedWhileTheQuestionIsUp() {
        World w = new World();
        w.hangar.coins = 400;
        w.hangar.shards[0] = 3;
        ShopController c = shop(w);
        c.ui().setShip(1);
        c.ui().setTier(0);
        c.keyDown(Input.Keys.B);
        int tab = c.ui().tab();

        c.keyDown(Input.Keys.TAB);
        c.keyDown(Input.Keys.X); // would sell every shard
        c.keyDown(Input.Keys.B); // would buy twice
        assertEquals(tab, c.ui().tab(), "the page does not move behind the modal");
        assertEquals(3, w.hangar.shardCount(), "nothing is sold behind it");
        assertEquals(400, w.hangar.coins);
        assertTrue(c.confirming());
    }

    @Test
    void aPurchaseThatCouldNotGoThroughAsksNothing() {
        Hangar h = new Hangar();
        h.coins = 10;
        assertNull(ShopController.confirmTier(h, 0, 1, 0), "too dear");
        h.coins = 9999;
        assertNull(ShopController.confirmTier(h, 0, 2, 0), "Aegis opens at Vitala");
        assertNull(ShopController.confirmTier(h, 0, 0, 0), "the starter is already owned");
        assertNull(ShopController.confirmTier(h, 0, 1, 1), "an upgrade needs the hull first");
        assertNull(ShopController.confirmWeapon(h, 0), "the Single Bolt is owned");
        h.coins = 5;
        assertNull(ShopController.confirmWeapon(h, 1), "a pattern out of reach");
    }

    @Test
    void theThirdTierWantsShardsAndSaysSo() {
        Hangar h = new Hangar();
        h.coins = 1000;
        assertNull(ShopController.confirmTier(h, 0, 0, 2), "no shards, no tier 3");
        h.shards[0] = 4;
        ShopUi.Confirm c = ShopController.confirmTier(h, 0, 0, 2);
        assertNotNull(c);
        assertEquals(Hangar.SHARD_COST, c.shards);
        assertEquals(4 - Hangar.SHARD_COST, c.shardsLeft);
        assertEquals(1000 - ShipDef.ALL[0].upgrades[2].cost, c.coinsLeft);
        assertEquals(ShopUi.Confirm.TIER, c.kind);
    }

    @Test
    void buyingAWeaponAsksAndOwnedOnesFitAtOnce() {
        World w = new World();
        w.hangar.coins = 500;
        ShopController c = shop(w);
        c.ui().setTab(ShopUi.TAB_PATTERNS);
        c.ui().setWeapon(1);

        c.keyDown(Input.Keys.N);
        assertTrue(c.confirming());
        assertFalse(w.hangar.weaponOwned[1]);
        assertEquals(500, w.hangar.coins);
        c.keyDown(Input.Keys.N); // swallowed: N is also "no" inside the modal
        assertFalse(c.confirming(), "N answers no");
        assertFalse(w.hangar.weaponOwned[1]);

        c.keyDown(Input.Keys.N);
        c.keyDown(Input.Keys.Y);
        assertTrue(w.hangar.weaponOwned[1], "Y answers yes");
        assertEquals(500 - WeaponDef.DOUBLE.cost, w.hangar.coins);
        assertEquals(1, w.hangar.weaponIndex);

        c.keyDown(Input.Keys.N); // owned now: fits, asks nothing
        assertFalse(c.confirming());
    }

    @Test
    void closingTheShopDropsAWaitingQuestion() {
        World w = new World();
        w.hangar.coins = 400;
        ShopController c = shop(w);
        c.ui().setShip(1);
        c.ui().setTier(0);
        c.keyDown(Input.Keys.B);
        c.close();
        assertFalse(c.confirming());
        assertNull(c.ui().confirm());
        assertEquals(400, w.hangar.coins);
    }
}
