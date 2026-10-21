package vajra.audio;

/** Every sound the game makes, by what happened, not by what it sounds like.
 *
 *  <p>Each carries its base gain and the shortest gap between two plays: a volley of five pellets
 *  is one report, not five, and a burst of coins is a patter, not a wall. The sounds themselves
 *  are made by {@link Synth}; a file named {@code sfx/<name>.ogg} or {@code .wav} in the assets
 *  replaces any of them. */
public enum Sfx {
    // ---- the menus and the bazaar ---------------------------------------------------------
    UI_MOVE(0.40f, 35),          // the cursor steps onto another leaf
    UI_SELECT(0.55f, 60),        // a choice is made
    UI_BACK(0.45f, 60),          // a screen is left
    UI_TAB(0.45f, 60),           // a section of the shop turns
    UI_DENY(0.50f, 150),         // that cannot be done
    UI_CONFIRM(0.55f, 120),      // the question is asked before a purchase
    PURCHASE(0.75f, 200),        // coins change hands
    SELL(0.65f, 200),            // shards are sold
    SHOP_OPEN(0.70f, 400),       // the hangar opens
    SHOP_CLOSE(0.55f, 400),      // and shuts
    DOCK(0.50f, 600),            // the bay locks the hull

    // ---- the guns: each is a real firearm's report, by what the weapon is ----------------
    SHOT_MAGNUM(0.90f, 60),      // single, double, aim: a Glock 17 (9 mm)
    SHOT_RIFLE(0.75f, 50),       // burst and twin burst: a 5.56 rifle on three-round burst
    SHOT_MG(0.55f, 40),          // the light MG: a submachine gun, near-dry so it can rattle
    SHOT_SHELL(0.95f, 100),      // the heavy shell: a .50 / cannon, deep, long rumble
    SHOT_SCATTER(0.90f, 100),    // buckshot: a 12-gauge, and the pump racked after
    SHOT_SNIPER(0.85f, 80),      // piercer and wallbore: a high-velocity rifle
    SHOT_LASER(0.45f, 90),       // the beam (an energy weapon: no firearm to copy)
    SHOT_MISSILE(0.70f, 100),    // a rocket leaves the rail
    SHOT_BOMB(0.80f, 140),       // the grenade launcher's thunk
    SHOT_CHARGED(0.62f, 140),    // a held charge lets go (energy)
    ENEMY_SHOT(0.34f, 70),       // a foe fires: a small pistol

    // ---- the fight ------------------------------------------------------------------------
    HIT_ENEMY(0.38f, 45),        // a round lands on a foe
    IMPACT(0.34f, 45),           // a round strikes rock and whines away
    EXPLODE_S(0.55f, 70),        // a small foe falls
    EXPLODE_M(0.65f, 90),        // a stronger one, or a bomb
    EXPLODE_L(0.75f, 120),       // a large body
    BOSS_AWAKEN(0.85f, 2000),    // the seal lifts: Bala
    BOSS_PHASE(0.75f, 800),      // a phase of the duel turns
    BOSS_DEATH(0.90f, 2000),     // Bala falls

    // ---- the ship -------------------------------------------------------------------------
    HULL_HIT(0.70f, 90),         // the hull is struck
    SHIELD_HIT(0.55f, 70),       // the shield soaks it
    SHIELD_ON(0.40f, 120),       // the shield rises
    SHIELD_OFF(0.30f, 120),      // and is let go
    SHIELD_DOWN(0.55f, 400),     // and runs dry
    DASH(0.55f, 150),            // the dash
    BOOST(0.50f, 300),           // the boost lights
    LOW_HULL(0.50f, 900),        // the hull is failing
    SHIP_DESTROYED(0.85f, 600),  // a life is lost
    RESPAWN(0.55f, 600),         // and the hull is whole again at the anchor
    GAME_OVER(0.80f, 3000),      // the last life
    THRUST(0.50f, 0),            // the engine's loop (not a one-shot)

    // ---- what the world gives -------------------------------------------------------------
    COIN(0.50f, 30),             // a coin is taken (climbs when taken in a run)
    SHARD(0.55f, 80),            // a shard
    HEALTH(0.55f, 120),          // a mend
    PIECE(0.80f, 1500),          // a band of the Vajra
    DROP(0.28f, 70),             // a coin leaves a body
    DROP_RARE(0.40f, 120),       // a shard or a band does
    CHECKPOINT(0.65f, 800),      // an anchor taken
    CACHE_WAKE(0.65f, 800),      // the cache fights back
    CACHE_OPEN(0.65f, 800),      // and pays
    LIFE_UP(0.65f, 600),         // a life gained
    REGION(0.50f, 1500),         // a gate crossed into a new band
    FORGE(0.75f, 800),           // the lance forges or charges
    ASTRA_WIND(0.80f, 0),        // the Astra gathers
    ASTRA_RELEASE(0.90f, 0),     // and lets go
    VICTORY(0.80f, 3000);        // Atala conquered

    /** Base gain, 0..1, before the pilot's volume and any distance. */
    public final float gain;
    /** The shortest gap between two plays of this sound, in milliseconds. */
    public final int minGapMs;

    Sfx(float gain, int minGapMs) {
        this.gain = gain;
        this.minGapMs = minGapMs;
    }

    /** The sound of a player's weapon firing, read from what the weapon IS: a beam, a charge, a
     *  launcher, a rocket, a shotgun's spread, a heavy round, a rapid-fire gun, a high-velocity
     *  piercing round, a burst, and otherwise the sidearm, a Glock. */
    public static Sfx forWeapon(vajra.weapon.WeaponDef d, float power) {
        if (d.beam) return SHOT_LASER;
        if (d.chargeTime > 0 || power > 1.05f) return SHOT_CHARGED;
        if (d.aoeRadius > 0) return SHOT_BOMB;
        if (d.homingTurn > 0) return SHOT_MISSILE;
        if (d.count >= 4) return SHOT_SCATTER;
        if (d.bulletSize >= 9) return SHOT_SHELL;
        if (d.cooldown <= 0.09f) return SHOT_MG;
        if (d.pierceEnemies || d.pierceWalls) return SHOT_SNIPER;
        if (d.burst > 1) return SHOT_RIFLE;
        return SHOT_MAGNUM;
    }
}
