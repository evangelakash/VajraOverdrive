package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vajra.ship.Hangar;
import vajra.ship.ShipDef;
import vajra.weapon.WeaponDef;

/** THE CHARTS TELL THE TRUTH. Every figure the hangar draws comes from {@link ShopStats}, so
 *  these pin it against the numbers {@code Ship} and {@code Weapon} really act on. */
class ShopStatsTest {
    private static final float EPS = 0.001f;

    @Test
    void everyAxisFitsInsideTheOuterRing() {
        for (ShipDef d : ShipDef.ALL)
            for (boolean t1 : new boolean[] { false, true })
                for (boolean t2 : new boolean[] { false, true })
                    for (float f : ShopStats.fractions(d, t1, t2))
                        assertTrue(f > 0f && f <= 1f, d.name + " fraction " + f);
    }

    @Test
    void someHullReachesTheRingOnEveryAxis() {
        for (int k = 0; k < ShopStats.AXES; k++) {
            float top = 0;
            for (ShipDef d : ShipDef.ALL) top = Math.max(top, ShopStats.fractions(d, true, true)[k]);
            assertEquals(1f, top, EPS, ShopStats.AXIS_NAMES[k] + " ring is the fleet's best");
        }
    }

    @Test
    void anUpgradeNeverShrinksAnAxis() {
        for (ShipDef d : ShipDef.ALL) {
            float[] base = ShopStats.fractions(d, false, false);
            float[] full = ShopStats.fractions(d, true, true);
            for (int k = 0; k < ShopStats.AXES; k++)
                assertTrue(full[k] >= base[k] - EPS, d.name + " " + ShopStats.AXIS_NAMES[k]);
        }
    }

    /** Aegis takes 8% less damage: its hull axis must count that, as Ship.damage does. */
    @Test
    void armourCountsTowardTheHull() {
        ShipDef aegis = ShipDef.ALL[2];
        assertEquals(aegis.maxHp / (1f - aegis.damageReduction),
                ShopStats.raw(aegis, false, false)[ShopStats.HULL], EPS);
    }

    /** Aegis Plate on the starter is +40 hull and +15 shield (Ship.shieldCap = 50 + bonus). */
    @Test
    void upgradesLandOnTheRightAxis() {
        ShipDef mk1 = ShipDef.ALL[0];
        float[] base = ShopStats.raw(mk1, false, false);
        float[] full = ShopStats.raw(mk1, true, true);
        assertEquals(100f, base[ShopStats.HULL], EPS);
        assertEquals(140f, full[ShopStats.HULL], EPS);
        assertEquals(50f, base[ShopStats.SHIELD], EPS);
        assertEquals(65f, full[ShopStats.SHIELD], EPS);
        assertEquals(1f / 0.82f, full[ShopStats.GUN], EPS, "Rapid Cycler: 0.82 cooldown");
    }

    @Test
    void theHangarReadsTheTiersItOwns() {
        Hangar h = new Hangar();
        ShipDef mk1 = ShipDef.ALL[0];
        assertArrayEquals(ShopStats.fractions(mk1, false, false), ShopStats.fractions(h, mk1), EPS);
        h.owned[0][1] = true;
        assertArrayEquals(ShopStats.fractions(mk1, true, false), ShopStats.fractions(h, mk1), EPS);
    }

    @Test
    void aSingleBoltIsWhatTheNumbersSay() {
        ShopStats.Shot s = ShopStats.shot(WeaponDef.SINGLE);
        assertEquals(WeaponDef.SINGLE.damage / WeaponDef.SINGLE.cooldown, s.dps, EPS);
        assertEquals(WeaponDef.SINGLE.speed * WeaponDef.SINGLE.life, s.reach, EPS);
        assertEquals(0f, s.spread, EPS);
    }

    /** Three shots in 0.18 s still wait for the 0.5 s cooldown; a burst cannot cycle faster. */
    @Test
    void aBurstWaitsOutItsCooldown() {
        ShopStats.Shot s = ShopStats.shot(WeaponDef.BURST1);
        assertEquals(0.5f, s.cycle, EPS);
        assertEquals(3 * WeaponDef.BURST1.damage, s.perPull, EPS);
    }

    /** Held to the full a charge pays double, and it cannot cycle faster than it charges. */
    @Test
    void aChargeShotPaysDoubleAndCyclesAtItsChargeTime() {
        ShopStats.Shot s = ShopStats.shot(WeaponDef.CHARGE);
        assertEquals(2f * WeaponDef.CHARGE.damage, s.perPull, EPS);
        assertEquals(WeaponDef.CHARGE.chargeTime, s.cycle, EPS);
    }

    @Test
    void aBeamReachesItsRangeAndAFanShowsItsSpread() {
        assertEquals(WeaponDef.LASER.range, ShopStats.shot(WeaponDef.LASER).reach, EPS);
        assertEquals(0f, ShopStats.shot(WeaponDef.LASER).speed, EPS, "a beam has no travel time");
        ShopStats.Shot fan = ShopStats.shot(WeaponDef.SPREAD);
        assertEquals(5, fan.rays);
        assertEquals(45f, fan.spread, EPS);
        assertEquals(5 * WeaponDef.SPREAD.damage, fan.perPull, EPS);
    }

    @Test
    void theHullThatFiresItScalesThePattern() {
        ShopStats.Shot base = ShopStats.shot(WeaponDef.SINGLE);
        ShopStats.Shot fast = ShopStats.shot(WeaponDef.SINGLE, 1.25f, 0.8f);
        assertEquals(base.dps * 1.25f / 0.8f, fast.dps, 0.01f);
        Hangar h = new Hangar();
        assertEquals(base.dps, ShopStats.shot(h, WeaponDef.SINGLE).dps, EPS, "Mk-1 bare changes nothing");
    }

    @Test
    void barsStayOnTheirTrack() {
        for (WeaponDef w : WeaponDef.ALL)
            for (int m = 0; m < 4; m++) {
                float b = ShopStats.bar(ShopStats.shot(w), m);
                assertTrue(b > 0f && b <= 1f, w.name + " metric " + m + " = " + b);
            }
        float over = ShopStats.bar(ShopStats.shot(WeaponDef.LIGHT, 5f, 0.2f), ShopStats.DPS);
        assertEquals(1f, over, EPS, "an upgraded hull may beat the best as shipped; the bar clamps");
    }

    /** The price ladder promises that nothing above 200c is a raw downgrade: the charts must agree. */
    @Test
    void dearerPatternsDoNotLoseOnOutputToTheStarter() {
        float starter = ShopStats.shot(WeaponDef.SINGLE).dps;
        for (WeaponDef w : WeaponDef.ALL)
            if (w.cost > 200)
                assertTrue(ShopStats.shot(w).dps >= starter, w.name + " dps vs the starter");
    }

    @Test
    void versusReadsAsARatio() {
        assertEquals("x1.50", ShopStats.versus(15f, 10f));
        assertEquals("x0.80", ShopStats.versus(8f, 10f));
        assertEquals(50, ShopStats.versusPct(15f, 10f));
        assertEquals(-20, ShopStats.versusPct(8f, 10f));
        assertEquals("same", ShopStats.versus(10f, 10f));
        assertEquals("new", ShopStats.versus(5f, 0f));
    }

    @Test
    void everyPatternAndHullHasAWordOnWhatItIsFor() {
        for (WeaponDef w : WeaponDef.ALL)
            assertFalse(ShopStats.howItPlays(w).isEmpty(), w.name + " has a description");
        for (ShipDef d : ShipDef.ALL)
            assertTrue(ShopStats.role(d).startsWith("STRONGEST: "), d.name);
    }

    /** The HUD face (courbe-sans) has no + - or % glyph: text drawn with them silently loses the
     *  sign, so "hull +25" read as "hull 25". Every string the armoury builds must avoid them. */
    @Test
    void shopTextOnlyUsesGlyphsTheHudFaceDraws() {
        java.util.List<String> all = new java.util.ArrayList<>();
        for (ShipDef d : ShipDef.ALL) {
            for (int t = 0; t < 3; t++) all.add(ShopUi.tierEffect(d, t));
            all.add(ShopStats.role(d));
            for (boolean t1 : new boolean[] { false, true })
                for (int k = 0; k < ShopStats.AXES; k++)
                    all.add(ShopStats.axisText(k, ShopStats.raw(d, t1, true)[k]));
        }
        for (WeaponDef w : WeaponDef.ALL) {
            all.add(ShopStats.howItPlays(w));
            for (int m = 0; m < 4; m++) all.add(ShopStats.metricText(ShopStats.shot(w), m));
            all.add(ShopStats.versus(ShopStats.shot(w).dps, ShopStats.shot(WeaponDef.SINGLE).dps));
        }
        for (String s : all)
            for (char c : "+%\u00B7\u00B0".toCharArray())
                assertEquals(-1, s.indexOf(c), "'" + s + "' contains '" + c + "'");
        // a hyphen is the third missing glyph, but only prose can carry one by accident
        for (WeaponDef w : WeaponDef.ALL)
            assertEquals(-1, ShopStats.howItPlays(w).indexOf('-'), w.name);
    }
}
