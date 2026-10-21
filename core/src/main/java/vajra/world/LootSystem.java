package vajra.world;

import vajra.audio.Sfx;

import java.util.Random;
import vajra.enemy.Enemy;
import vajra.ship.Hangar;

/** Loot: what a death pays and what the hull sweeps up. Extracted from World so the simulation
 *  no longer owns drop tables. The RNG is passed in, so tests can reseed World's instance. */
public final class LootSystem {
    private static final int[] LOOT_COINS = { 2, 4, 6 };
    private static final int BOSS_LOOT_COINS = 25;
    private static final float[] LOOT_HEALTH = { 0.08f, 0.15f, 0.30f, 1f };
    private static final float HEALTH_HEAL = 0.25f;
    private static final float[] LOOT_SHARD = { 0.06f, 0.14f, 0.28f };

    private float dropAngle; // fan cursor: each kill's burst is rotated so two deaths never twin

    /** Coin income scales with loka difficulty (sqrt of hpScale) so deeper lokas
     *  pay more per kill and the run can afford its tiers. */
    private static int lootCoins(int[] table, int tier, float hpScale) {
        return Math.max(1, Math.round(table[tier] * (float) Math.sqrt(hpScale)));
    }

    public void drop(World world, Random rng, Enemy e) {
        int tier = e.boss ? 3 : e.elite ? 2 : e.rank == 1 ? 1 : 0;
        float hpScale = LokaTable.at(world.lokaIndex).hpScale;
        int coinN = e.boss ? Math.round(BOSS_LOOT_COINS * (float) Math.sqrt(hpScale))
                : lootCoins(LOOT_COINS, tier, hpScale);
        boolean mend = rng.nextFloat() < LOOT_HEALTH[tier];
        boolean piece = e.boss;
        boolean shard = !piece && rng.nextFloat() < LOOT_SHARD[tier];
        int total = coinN + (mend ? 1 : 0) + (piece || shard ? 1 : 0);

        float base = (dropAngle += 2.3999632f); // golden turn
        float stride = 6.2831853f / total;
        int slot = 0;
        for (int i = 0; i < coinN; i++)
            pop(world, rng, e, Pickup.COIN, -1, base + slot++ * stride);
        if (mend) pop(world, rng, e, Pickup.HEALTH, -1, base + slot++ * stride);
        if (piece) {
            pop(world, rng, e, Pickup.PIECE, world.lokaIndex, base + slot * stride);
        } else if (shard) {
            // which element pays is the LOKA's business, not the foe's (LOKA-LAWS §3)
            int[] pay = LokaTable.at(world.lokaIndex).shards;
            pop(world, rng, e, Pickup.SHARD, pay[rng.nextInt(pay.length)], base + slot * stride);
        }
    }

    private void pop(World world, Random rng, Enemy e, int type, int variant, float a) {
        float ux = (float) Math.cos(a), uy = (float) Math.sin(a);
        float d = e.w * (0.5f + 0.5f * rng.nextFloat());
        d = Math.max(16, Math.min(d, world.wallDist(e.x, e.y, ux, uy, d) - 8));
        Pickup p = new Pickup();
        p.netId = world.dropSeq++; // the host's ledger ids each drop so a sweep can retire it
        p.type = type;
        p.variant = variant >= 0 ? variant : rng.nextInt(Pickup.VARIANTS);
        p.x = e.x;
        p.y = e.y;
        p.vx = ux * d * Pickup.DRAG;
        p.vy = uy * d * Pickup.DRAG;
        p.phase = rng.nextFloat() * 6.2831853f;
        world.pickups.add(p);
        world.sound(type == Pickup.COIN ? Sfx.DROP : Sfx.DROP_RARE, e.x, e.y); // a drop leaves the body
    }

    /** Sweep it up: coins pay the purse, shards stock the hangar, pieces complete the blade. */
    public void collect(World world, Pickup p) {
        if (p.type == Pickup.COIN) {
            world.hangar.coins++;
            world.sound(Sfx.COIN);
            world.addEffect(Effect.ring(p.x, p.y, 12, 1f, 0.84f, 0.3f));
        } else if (p.type == Pickup.SHARD) {
            // feeds the tatva stock AND the loka's forge: only its own element pays both
            int charged = world.hangar.addShard(p.variant, world.lokaIndex);
            world.sound(charged > 0 ? Sfx.FORGE : Sfx.SHARD);
            float[] c = World.TATVA_RGB[p.variant];
            world.addEffect(Effect.ring(p.x, p.y, 16, c[0], c[1], c[2]));
            if (charged > 0)
                world.popups.push(new Popups.Spec()
                        .text(world.hangar.forgeCharges[world.lokaIndex] == 1
                                ? "THE LANCE IS FORGED" : "THE LANCE CHARGES")
                        .img("taptam").at(world.ship.x, world.ship.y + 34)
                        .color(c[0], c[1], c[2]).scale(1.25f).life(1.8f));
        } else if (p.type == Pickup.PIECE) {
            world.hangar.pieces[p.variant] = true;
            world.sound(Sfx.PIECE);
            world.popups.push(new Popups.Spec().text("THE VAJRA ANSWERS").img("vajra")
                    .at(world.ship.x, world.ship.y + 40).color(1f, 215 / 255f, 0).scale(1.5f).life(2.4f));
            world.addEffect(Effect.ring(p.x, p.y, 40, 1f, 0.84f, 0.3f));
            world.addEffect(Effect.ring(p.x, p.y, 24, 1f, 1f, 1f));
            for (boolean have : world.hangar.pieces) if (!have) return;
            world.vajraDone = true;
        } else {
            world.sound(Sfx.HEALTH);
            world.ship.hp = Math.min(world.ship.maxHp(),
                    world.ship.hp + world.ship.maxHp() * HEALTH_HEAL);
            world.addEffect(Effect.ring(p.x, p.y, 16, 0.4f, 1f, 0.55f));
        }
    }
}
