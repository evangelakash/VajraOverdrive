package vajra.world;

/** THE SEVEN LOKAS — one row each, top to bottom of Patala.
 *
 *  <p>Scripture: the Vishnu Purana puts the seven nether worlds 70,000 yojanas below the
 *  surface, each 10,000 deep, and names them Atala, Vitala, Nitala, Garbhastimat, Mahatala,
 *  Sutala, Patala. The Bhagavata and Padma Puranas — the ordering this game uses — call them
 *  Atala, Vitala, Sutala, Talatala, Mahitala, Rasatala, Patala. Below all seven lies Naraka.
 *  The Patalas rest on the head of Shesha.
 *
 *  <p>Adding a loka is a row here plus one MapGen cavern and one WorldGen placement pass.
 *  `atalaSeed` is the per-band worldgen seed base, so a loka's layout is reproducible.
 *
 *  <p>`hpScale` and `dmgScale` are the DIFFICULTY CURVE. They ride the enemy at construction,
 *  so a deeper loka's roster is meaner without any new behaviour code. The player answers
 *  them with the coin ladder (ship prices, tier upgrades) and the shards (the Astra)
 *  charges) — the two axes the hangar already owns.
 */
public final class LokaTable {
    public static final int COUNT = 7;

    /** One loka: its name, who holds its band of the Vajrayudha, what it pays in shards,
     *  how mean it is, which ships it opens, and the seed its bands generate from. */
    public static final class Loka {
        public final String name;
        public final String boss;        // the loka's warden, who holds its Vajrayudha band
        public final int[] shards;       // Pancha Tatva this loka pays (index into World.TATVA)
        public final float hpScale;      // enemy hull multiplier
        public final float dmgScale;     // enemy fire multiplier
        public final int unlockShip;     // ships 0..unlockShip are purchasable from here on
        public final int seedBase;

        Loka(String name, String boss, int[] shards, float hpScale, float dmgScale,
             int unlockShip, int seedBase) {
            this.name = name;
            this.boss = boss;
            this.shards = shards;
            this.hpScale = hpScale;
            this.dmgScale = dmgScale;
            this.unlockShip = unlockShip;
            this.seedBase = seedBase;
        }

        public boolean pays(int tatva) {
            for (int t : shards) if (t == tatva) return true;
            return false;
        }
    }

    // Pancha Tatva index order (== World.TATVA): 0 Akasha, 1 Vayu, 2 Agni, 3 Jala, 4 Prithvi.
    // Each loka pays its own element; Rasatala pays two and Patala pays all five (LOKA-LAWS §3).

    public static final Loka[] ALL = {
            // Atala — ruler Bala, son of Maya. The Vayu Purana puts the cities of the daitya
            // Namuchi and the naga Kaliya here. Sunless, jewel-walled, full of naga water.
            new Loka("Atala", "Bala", new int[] { 0 }, 1.0f, 1.0f, 1, 11),
            // Vitala — the ember deep: fire, serpents of flame, the first loka that hurts.
            new Loka("Vitala", "Agnihotra", new int[] { 2 }, 1.35f, 1.2f, 3, 21),
            // Sutala — the water-deep. Kaliya's lake-country; the dampest rock in Patala.
            new Loka("Sutala", "Takshaka", new int[] { 3 }, 1.75f, 1.45f, 5, 31),
            // Talatala — stone and weight. Heavy, slow, patient.
            new Loka("Talatala", "Kalanemi", new int[] { 4 }, 2.3f, 1.7f, 6, 41),
            // Mahitala — the great deep. Mahitala's serpent courts, the most crowded band.
            new Loka("Mahitala", "Hiranyaksha", new int[] { 1 }, 3.0f, 2.0f, 6, 51),
            // Rasatala — the pool of blood. Pays two elements: it is the first mixed harvest,
            // so the purity rule on the forge bites.
            new Loka("Rasatala", "Puloman", new int[] { 2, 4 }, 4.0f, 2.35f, 6, 61),
            // Patala — Naga-loka itself, the seventh and lowest, Vasuki's floor. It pays every
            // element at once, so any line can be finished here. Trivikrama waits below it.
            new Loka("Patala", "Trivikrama", new int[] { 0, 1, 2, 3, 4 }, 5.5f, 2.8f, 6, 71)
    };

    private LokaTable() {}

    public static Loka at(int i) {
        return ALL[Math.max(0, Math.min(COUNT - 1, i))];
    }

    /** True once this loka has opened ship `shipId` for purchase. */
    public static boolean shipUnlocked(int loka, int shipId) {
        return shipId <= at(loka).unlockShip;
    }
}