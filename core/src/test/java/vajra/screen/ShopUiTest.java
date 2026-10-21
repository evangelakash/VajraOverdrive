package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vajra.ship.Hangar;
import vajra.ship.ShipDef;
import vajra.weapon.WeaponDef;
import vajra.world.LokaTable;
import vajra.world.World;

/** THE HANGAR LAYOUT. These are numbers the old split could not check: before, the row geometry
 *  lived in the draw code and a second copy lived in the click handler, and the only way to know
 *  they agreed was to open the game and try.
 *
 *  <p>The screen is now tabbed, so the things worth pinning are: the tab strip does not run off
 *  screen, the footer stays under the body, and the label builders tell the truth. */
class ShopUiTest {

    @Test
    void theSectionsAreSixAndNumberedInOrder() {
        assertEquals(6, ShopUi.TAB_NAMES.length);
        assertEquals(ShopUi.TAB_BAY, ShopUi.TAB_NAMES.length - 6, "BAY first");
        assertEquals(5, ShopUi.TAB_VAULT, "VAULT last");
        assertEquals(ShopUi.TAB_FLEET, ShopUi.TAB_HULL, "the old HULL name points at FLEET");
        assertEquals(ShopUi.TAB_PATTERNS, ShopUi.TAB_PATTERN, "PATTERN at PATTERNS");
        assertEquals(ShopUi.TAB_FORGE, ShopUi.TAB_ASTRA, "ASTRA at FORGE");
    }

    /** The vertical order of the chrome: heading, body, answer line, footer. */
    @Test
    void theChromeStacksTopToBottom() {
        assertTrue(ShopUi.BODY_Y < ShopUi.HEAD_Y, "the body sits under the heading");
        assertTrue(ShopUi.ANSWER_Y < ShopUi.BODY_Y, "the answer line is under the body");
        assertTrue(ShopUi.FOOTER_Y < ShopUi.ANSWER_Y, "the footer hint is lowest");
        assertTrue(ShopUi.FOOTER_Y > 0, "the footer is on screen");
    }

    /** The wallet row is right-anchored INSIDE the frame rails; the SELL cell has to start clear
     *  of the left rail or the row walks off the frame. */
    @Test
    void theWalletStaysInsideTheRails() {
        assertTrue(ShopUi.WALLET_X1 <= ShopUi.M1, "right edge inside the right rail");
        assertTrue(ShopUi.WALLET_SELL_X > ShopUi.M0, "left edge clear of the left rail");
        assertTrue(ShopUi.WALLET_COINS_W > 0 && ShopUi.WALLET_SHARDS_W > 0
                && ShopUi.WALLET_SELL_W > 0, "every cell has width");
    }

    /** A tier's price line has to say what the money actually buys. Tier 0 IS the hull. */
    @Test
    void aTierSaysWhatItBuys() {
        for (ShipDef d : ShipDef.ALL)
            for (int t = 1; t < 3; t++)
                assertNotEquals("the hull itself", ShopUi.tierEffect(d, t),
                        d.name + " tier " + t + " should change something");
        assertEquals("the hull itself", ShopUi.tierEffect(ShipDef.ALL[0], 0));
        assertTrue(ShopUi.tierEffect(ShipDef.ALL[0], 1).contains("CD"),
                "Rapid Cycler cools down: " + ShopUi.tierEffect(ShipDef.ALL[0], 1));
        assertTrue(ShopUi.tierEffect(ShipDef.ALL[3], 2).contains("dmg"),
                "Seeker Chips adds damage: " + ShopUi.tierEffect(ShipDef.ALL[3], 2));
        assertTrue(ShopUi.tierEffect(ShipDef.ALL[2], 2).contains("shield"),
                "Bulwark adds shield: " + ShopUi.tierEffect(ShipDef.ALL[2], 2));
    }

    /** The Astra page must say what a release actually does, or it is just a progress bar. */
    @Test
    void theAstraSaysWhatAReleaseDoes() {
        String what = ShopUi.astraEffect();
        assertTrue(what.contains("20"), "the cube count is the headline: " + what);
        assertTrue(what.contains("through rock"), "and the one rule that makes it special: " + what);
    }

    /** A hull row must say which of the four things it is. */
    @Test
    void aHullRowSaysWhichStateItIsIn() {
        Hangar h = new Hangar();
        h.coins = 9999;
        ShipDef mk1 = ShipDef.ALL[0], kite = ShipDef.ALL[1], seven = ShipDef.ALL[6];
        assertEquals("FLYING", ShopUi.hullBuyLine(h, mk1, 0));
        assertTrue(ShopUi.hullBuyLine(h, kite, 0).startsWith(kite.cost + "c"), "open and priced");
        assertEquals("OPENS VITALA", ShopUi.hullBuyLine(h, ShipDef.ALL[2], 0), "closed until its loka");
        assertEquals("OPENS TALATALA", ShopUi.hullBuyLine(h, seven, 0));
        h.buyShip(1, 0);
        assertEquals("OWNED", ShopUi.hullBuyLine(h, kite, 0), "bought but not flown");
    }

    @Test
    void aPatternRowSaysWhichStateItIsIn() {
        Hangar h = new Hangar();
        assertEquals("FITTED", ShopUi.weaponState(h, WeaponDef.SINGLE), "the free one you fly");
        assertEquals(WeaponDef.BURST1.cost + "c", ShopUi.weaponState(h, WeaponDef.BURST1), "priced");
        h.weaponOwned[2] = true;
        assertEquals("OWNED", ShopUi.weaponState(h, WeaponDef.BURST1), "bought, not fitted");
    }

    /** The forge has to say where you are between "nothing yet" and "ready to fire". */
    @Test
    void theForgeSaysWhereYouAre() {
        Hangar h = new Hangar();
        assertTrue(ShopUi.forgeLabel(h, 0).contains("TO FORGE"), "nothing banked yet");
        for (int i = 0; i < Hangar.FORGE_COST; i++) h.addShard(Hangar.AKASHA, 0);
        assertTrue(ShopUi.forgeLabel(h, 0).contains("CHARGES 1"), "forged: " + ShopUi.forgeLabel(h, 0));
        for (int i = 0; i < Hangar.CHARGE_COST; i++) h.addShard(Hangar.AKASHA, 0);
        assertTrue(ShopUi.forgeLabel(h, 0).contains("CHARGES 2"), "charged: " + ShopUi.forgeLabel(h, 0));
        // forging is permanent: spending the last release must not read as "not forged yet"
        assertTrue(h.spendForgeCharge(0));
        assertTrue(h.spendForgeCharge(0));
        assertTrue(ShopUi.forgeLabel(h, 0).contains("FORGED"), "still forged: " + ShopUi.forgeLabel(h, 0));
        assertFalse(ShopUi.forgeLabel(h, 0).contains("TO FORGE"), "and not back to square one");
    }

    /** Every pattern knows its row, so the hangar can label rows by number. */
    @Test
    void everyPatternKnowsItsRow() {
        for (int i = 0; i < WeaponDef.ALL.length; i++)
            assertEquals(i, WeaponDef.ALL[i].idx, WeaponDef.ALL[i].name + " row index");
    }

    @Test
    void theLokaGateNamesItsLoka() {
        assertEquals("VITALA", LokaTable.at(ShipDef.ALL[2].minLoka).name.toUpperCase());
        assertTrue(LokaTable.shipUnlocked(0, 1));
        assertFalse(LokaTable.shipUnlocked(0, 6));
    }

    /** ZOOM: libGDX builds its projection as zoom x viewportWidth, so zoom > 1 is the direction
     *  that shows MORE world. Getting this backwards magnifies when you asked to pull back. */
    @Test
    void pullingBackMeansAZoomAboveOne() {
        assertEquals(1f, vajra.screen.GameScreen.ZOOM, 0.0001f, "rest is 1:1");
        assertTrue(vajra.screen.GameScreen.ZOOM_OUT > vajra.screen.GameScreen.ZOOM,
                "the far end of the pull must be ABOVE 1 to show more world");
    }
}