package vajra.ship;

import vajra.weapon.WeaponDef;
import vajra.world.World;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HangarTest {
    @Test
    void onlyTheStartingHullComesWithTheRun() {
        Hangar h = new Hangar();
        assertTrue(h.owned[0][0], "Mk-1 is the hull a run starts in");
        for (int i = 1; i < ShipDef.ALL.length; i++) {
            assertFalse(h.ownsShip(i), ShipDef.ALL[i].name + " has to be bought");
            assertFalse(h.owned[i][1]);
            assertFalse(h.owned[i][2]);
        }
    }

    @Test
    void hullsAreBoughtAtAPriceAndGatedByTheLoka() {
        Hangar h = new Hangar();
        h.coins = 9999;
        ShipDef k2 = ShipDef.ALL[1], mk3 = ShipDef.ALL[2], seven = ShipDef.ALL[6];
        assertEquals(k2.cost, h.buyShip(1, 0), "Kite opens in Atala and is bought outright");
        assertTrue(h.ownsShip(1));
        assertEquals(9999 - k2.cost, h.coins);
        assertEquals(0, h.buyShip(2, 0), "Mk-3 stays shut in Atala");
        assertEquals(mk3.cost, h.buyShip(2, 1), "and opens the moment you reach Vitala");
        assertEquals(0, h.buyShip(1, 0), "no rebuy");
        h.coins = 0;
        assertEquals(0, h.buyShip(6, 3), "broke buys nothing");
        assertFalse(h.ownsShip(seven.id));
    }

    @Test
    void theCycleOnlyEverLandsOnAHullYouBought() {
        Hangar h = new Hangar();
        h.coins = 9999;
        h.buyShip(2, 1);
        h.buyShip(4, 2);
        // three hulls owned: 0, 2 and 4. A full lap forward is three steps and returns home.
        assertEquals(2, h.cycleShip(0, 1));
        assertEquals(4, h.cycleShip(2, 1));
        assertEquals(0, h.cycleShip(4, 1), "wraps round to the start");
        assertEquals(4, h.cycleShip(0, -1), "and backwards");
    }

    @Test
    void tierBuysNeedTheHullFirst() {
        Hangar h = new Hangar();
        h.coins = 9999;
        assertFalse(h.buy(ShipDef.ALL[3], 1), "you cannot tier a hull you have not bought");
        h.buyShip(3, 1);
        assertTrue(h.buy(ShipDef.ALL[3], 1));
    }

    @Test
    void buyDeductsCoinsAndApplies() {
        Hangar h = new Hangar();
        h.coins = 9999; // test purse: the yard ships with 0
        ShipDef d = ShipDef.ALL[0];
        int cost = d.upgrades[1].cost;
        int coins0 = h.coins;
        assertTrue(h.buy(d, 1));
        assertEquals(coins0 - cost, h.coins);
        assertTrue(h.owned[0][1]);
        assertEquals(d.upgrades[1].cooldownMult, h.cooldownMult(d), 0.001f);
    }

    @Test
    void cannotRebuyOrBuyBase() {
        Hangar h = new Hangar();
        h.coins = 9999;
        ShipDef d = ShipDef.ALL[0];
        assertTrue(h.buy(d, 1));
        assertFalse(h.buy(d, 1), "no double buy");
        assertFalse(h.buy(d, 0), "base tier is not purchasable");
    }

    @Test
    void poorShipsCannotBuy() {
        Hangar h = new Hangar();
        h.coins = 0;
        assertFalse(h.buy(ShipDef.ALL[0], 1));
    }

    @Test
    void upgradesArePerShip() {
        Hangar h = new Hangar();
        h.coins = 9999;
        ShipDef seven = ShipDef.ALL[6];
        ShipDef one = ShipDef.ALL[0];
        h.buyShip(6, 3);
        h.shards[0] = Hangar.SHARD_COST; // x.3 wants shards on top of coins
        h.buy(seven, 2); // 7.3 Seventh Halo: hull +50
        assertEquals(50f, h.hullBonus(seven), 0.001f);
        assertEquals(0f, h.hullBonus(one), 0.001f, "another ship's upgrades don't leak");
    }

    @Test
    void x3TiersWantShardsOnTopOfCoins() {
        Hangar h = new Hangar();
        h.coins = 9999;
        ShipDef d = ShipDef.ALL[0];
        int cost = d.upgrades[2].cost;
        int coins0 = h.coins;
        assertFalse(h.buy(d, 2), "no shards: the top tier waits");
        h.shards[0] = 1;
        h.shards[3] = 2; // mixed stock still funds it
        assertTrue(h.buy(d, 2));
        assertEquals(coins0 - cost, h.coins, "the coin price still lands");
        assertEquals(0, h.shardCount(), "all three shards spent, deepest stack first");
        assertTrue(h.owned[0][2]);
    }

    @Test
    void sellShardsPaysTwentyEach() {
        Hangar h = new Hangar();
        h.shards[1] = 7;
        int coins0 = h.coins;
        assertEquals(140, h.sellShards(), "flat rate: five went for 100c, so 20c each");
        assertEquals(coins0 + 140, h.coins);
        assertEquals(0, h.shardCount(), "the stock is dumped");
    }

    @Test
    void magnetStartsAtFifteen() {
        Hangar h = new Hangar();
        assertEquals(15f, h.magnet, 0.0001f, "fixed at 15 for dev builds");
    }

    /** Shards have exactly one use now: forging the loka's Astra. Every one this loka pays
     *  banks it, and nothing else can be spent on anything. */
    @Test
    void everyShardYouHoldIsForgingTheAstra() {
        Hangar h = new Hangar();
        int need = h.forgeNeed(0);
        assertTrue(need > 0, "there is always a next thing to bank toward");
        for (int i = 0; i < need - 1; i++) {
            h.addShard(Hangar.AKASHA, 0);
            assertFalse(h.isForged(0), "not forged at " + h.shards[Hangar.AKASHA]);
        }
        h.addShard(Hangar.AKASHA, 0);
        assertTrue(h.isForged(0), "the last one forges it");
    }

    /** The purity rule survives the removal of tempering: shards of an element this loka does
     *  NOT pay stock up and never forge anything. */
    @Test
    void shardsThisLokaDoesNotPayNeverForge() {
        Hangar h = new Hangar();
        for (int i = 0; i < 40; i++) h.addShard(Hangar.AGNI, 0);   // Atala pays Akasha alone
        assertEquals(40, h.shards[Hangar.AGNI], "they still stock up, to sell");
        assertFalse(h.isForged(0), "and never forge the Astra");
        assertEquals(0, h.forgeCharges[0]);
    }

    @Test
    void shipStatsFollowPurchases() {
        World w = new World();
        w.hangar.coins = 9999;
        Ship s = w.ship;
        float hp0 = s.maxHp();
        w.hangar.buy(s.def, 1); // 1.2 Rapid Cycler: cooldown x0.82
        assertEquals(hp0, s.maxHp(), 0.001f);
        assertEquals(0.82f, s.cooldownMult(), 0.001f);
    }

    @Test
    void everyShipHasThreeTiersAndEveryWeaponExists() {
        for (ShipDef d : ShipDef.ALL) assertEquals(3, d.upgrades.length, d.name);
        assertEquals(15, WeaponDef.ALL.length);
    }

    @Test
    void buyWeaponDeductsAndRefuses() {
        Hangar h = new Hangar();
        assertTrue(h.weaponOwned[0], "the Single Bolt comes with the hull");
        assertFalse(h.weaponOwned[8], "the Laser waits in the shop");
        h.coins = 9999;
        assertTrue(h.buyWeapon(8));
        assertEquals(9999 - WeaponDef.LASER.cost, h.coins, "the price lands");
        assertTrue(h.weaponOwned[8]);
        assertFalse(h.buyWeapon(8), "no double buy");
        h.coins = 0;
        assertFalse(h.buyWeapon(9), "broke: no pattern unlocks");
        assertFalse(h.weaponOwned[9]);
    }

    @Test
    void cycleWalksOwnedPatternsOnly() {
        Hangar h = new Hangar(); // only the starter is owned
        assertEquals(0, h.cycleWeapon(0, 1), "nothing else to reach");
        assertEquals(0, h.cycleWeapon(0, -1), "and nothing behind it");
        h.weaponOwned[2] = true;
        assertEquals(2, h.cycleWeapon(0, 1), "forward lands on the next owned");
        assertEquals(0, h.cycleWeapon(2, 1), "wraps past the end back to the starter");
        assertEquals(0, h.cycleWeapon(2, -1), "and backward too");
    }
}
