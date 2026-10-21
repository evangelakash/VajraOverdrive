package vajra;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vajra.ship.Hangar;
import vajra.ship.ShipDef;

/** The save is ONE number now: the best run score. Everything else a run builds — coins,
 *  tiers, patterns, pieces, forged charges — is deliberately NOT persisted, because
 *  a new run starts broke and a new game resets everything to its defaults. */
class SaveTest {

    /** A hangar mid-run, loaded with everything a good run would have bought. */
    private static Hangar rich() {
        Hangar h = new Hangar();
        h.best = 4321;
        h.coins = 1234;
        h.shipIndex = 4;
        h.weaponIndex = 7;
        h.magnet = 0.45f;
        h.shards[Hangar.AGNI] = 3;
        h.shards[Hangar.JALA] = 1;
        h.forgeCharges[0] = 2;
        h.pieces[3] = true;
        h.owned[2][0] = true;
        h.owned[2][1] = true;
        h.owned[6][2] = true;
        h.forgeCharges[0] = 2;
        h.weaponOwned[5] = true;
        h.weaponOwned[14] = true;
        return h;
    }

    /** A fresh run wearing the save: best score lands, everything else is back at defaults. */
    private static Hangar freshRun(Hangar previous) {
        Hangar h = new Hangar();
        SaveGame.deserialize(h, SaveGame.serialize(previous));
        return h;
    }

    @Test
    void onlyTheBestScoreSurvivesARun() {
        Hangar fresh = freshRun(rich());
        assertEquals(4321, fresh.best, "the one thing that persists");
        assertEquals(0, fresh.coins, "a new run starts broke");
        assertEquals(0, fresh.shipIndex, "no hull carried over");
        assertEquals(0, fresh.weaponIndex, "no pattern carried over");
        assertEquals(15f, fresh.magnet, 0.0001f, "magnet back to its start");
        assertEquals(0, fresh.shards[Hangar.AGNI]);
        assertEquals(0, fresh.forgeCharges[0]);
        assertFalse(fresh.pieces[3], "no Vajrayudha band carried over");
        assertEquals(0, fresh.forgeCharges[0], "no forged charges carried over");
        for (int i = 0; i < ShipDef.ALL.length; i++)
            for (int t = 0; t < 3; t++)
                assertEquals(i == 0 && t == 0, fresh.owned[i][t],
                        "only the starting hull is owned in a new run");
        assertFalse(fresh.weaponOwned[5]);
        assertTrue(fresh.weaponOwned[0], "the starter pattern stays");
    }

    @Test
    void aCorruptOrForeignSaveLeavesTheRecordAlone() {
        Hangar h = new Hangar();
        h.best = 50;
        SaveGame.deserialize(h, "{ this is not json");
        assertEquals(50, h.best, "corrupt file ignored");
        SaveGame.Data foreign = new SaveGame.Data();
        foreign.version = SaveGame.VERSION + 1;
        foreign.best = 777;
        SaveGame.deserialize(h, new com.badlogic.gdx.utils.Json().toJson(foreign));
        assertEquals(50, h.best, "foreign version ignored");
    }

    @Test
    void theBestScoreClampsToTheTenDigits() {
        Hangar rich = rich();
        rich.best = 4321;
        Hangar fresh = freshRun(rich);
        assertEquals(4321, fresh.best, "the best score survives the roundtrip");

        rich.best = Long.MAX_VALUE;
        SaveGame.deserialize(fresh, SaveGame.serialize(rich));
        assertEquals(vajra.world.Score.MAX, fresh.best, "an absurd best clamps to the ten digits");

        rich.best = -7;
        SaveGame.deserialize(fresh, SaveGame.serialize(rich));
        assertEquals(0, fresh.best, "a negative best lands on zero");
    }

    @Test
    void anAbandonedRunStillSetsTheRecord() {
        // Quitting from the pause screen ends a run the same way dying does, so it has to fold
        // the score in the same way. It did not: PauseScreen.quit stored a hangar whose `best`
        // still held the OLD record, so beating it and quitting silently threw the new one away.
        Hangar h = new Hangar();
        h.best = 4321;

        assertFalse(SaveGame.record(h, 4000), "a worse score does not touch the record");
        assertEquals(4321, h.best, "and leaves it standing");

        assertTrue(SaveGame.record(h, 5000), "a better score is a new record");
        assertEquals(5000, h.best, "and lands");

        assertFalse(SaveGame.record(h, 5000), "matching the record exactly is not beating it");
        assertEquals(5000, h.best, "and leaves it alone");

        assertTrue(SaveGame.record(h, 9001), "and it keeps moving after that");
        assertEquals(9001, h.best, "upward");

        // what gets persisted is the folded hangar, so the abandoned run's score reaches disk
        Hangar next = freshRun(h);
        assertEquals(9001, next.best, "the abandoned run's score survives the roundtrip");
    }

    @Test
    void anAbsurdAbandonedScoreClampsLikeAnyOther() {
        Hangar h = new Hangar();
        assertTrue(SaveGame.record(h, Long.MAX_VALUE), "an absurd score is still a record");
        assertEquals(vajra.world.Score.MAX, h.best, "clamped to the ten digits the HUD shows");
    }

    @Test
    void aHandEditedRecordCannotSmuggleProgressIn() {
        // the old save shape had coins/shipIndex/weaponIndex; a file still carrying them is
        // the same version only if it says so, and even then nothing but `best` is read
        String json = "{\"version\":" + SaveGame.VERSION + ",\"best\":900,\"coins\":99999,"
                + "\"shipIndex\":6,\"weaponIndex\":14}";
        Hangar h = new Hangar();
        SaveGame.deserialize(h, json);
        assertEquals(900, h.best);
        assertEquals(0, h.coins, "a smuggled purse is ignored");
        assertEquals(0, h.shipIndex, "a smuggled hull is ignored");
        assertEquals(0, h.weaponIndex, "a smuggled pattern is ignored");
    }
}