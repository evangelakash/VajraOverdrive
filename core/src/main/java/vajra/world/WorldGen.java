package vajra.world;

import vajra.enemy.Enemy;

import java.util.ArrayList;
import java.util.Random;

/** Build-time world construction: the static maps, the garrison order of battle, and the
 *  dressing (shops, checkpoints, caches, torches, plants). Runs exactly once, from the
 *  World constructor; nothing in here is touched after the run starts. */
final class WorldGen {
    private static final float[] GATE_Y = { 1600, 4100, 7600, 12000, 17600 };
    private static final int[] GATE_DOOR = { 7, 4, 4, 4, 6 }; // half-door tiles: matches gateWall
    static final int PLANT_GAP = 23; // run step AND no-clump floor: 32 px grass overlaps 9

    private final World w;

    WorldGen(World w) {
        this.w = w;
    }

    void build() {
        w.tileMap = MapGen.svarnaDvara(24, 1600, 161, 79);
        w.tileMapR2 = MapGen.mayaVana(24, 4100, 161, 110);
        w.tileMapR3 = MapGen.svapnaMandala(24, 7600, 161, 138);
        w.tileMapR4 = MapGen.balaMukha(24, 12000, 161, 175);
        w.tileMapBoss = MapGen.balaArena(24, 17600, 161, 94);
        w.maps = new TileMap[5 + GATE_Y.length];
        w.maps[0] = w.tileMap;
        w.maps[1] = w.tileMapR2;
        w.maps[2] = w.tileMapR3;
        w.maps[3] = w.tileMapR4;
        w.maps[4] = w.tileMapBoss;
        // GATES: one wall per band boundary, doorway on the centre mouth of both
        // neighbours. Grand walls mark the two thresholds: Bhu -> Atala, Atala -> Bala.
        for (int i = 0; i < GATE_Y.length; i++)
            w.maps[5 + i] = MapGen.gateWall(GATE_Y[i], GATE_DOOR[i], i == 0 || i == GATE_Y.length - 1);
        spawnAtala();
        // Bala is not placed at all until the seal lifts — the arena starts empty of him.
        // the next loka's gate: gold across the arena's north corridor, opened only by the kill
        for (int c = 72; c <= 88; c++) w.tileMapBoss.set(c, 93, TileMap.GOLD);
        for (int i = 0; i < w.orbs.length; i++) {
            float[] spot = w.randomOpenSpot(w.orbRng);
            w.orbs[i] = new Orb(spot[0], spot[1], 0.5f + i * 0.7f);
        }
        for (int i = 0; i < w.checkpoints.length; i++) { // one per Atala band, deeper than its shop
            int r = i + 1;
            float cy = w.regionStart[r] + 0.6f * (w.regionEnd[r] - w.regionStart[r]);
            float[] spot = openNear(w.width / 2, cy, w.regionStart[r] + 40, w.regionEnd[r] - 40);
            w.checkpoints[i] = new Checkpoint(spot[0], spot[1]);
        }
        for (int i = 0; i < w.caches.length; i++) { // a cache just inside each band's entrance gate
            int r = i + 1;
            float cy = w.regionStart[r] + 400;
            float[] spot = openNear(w.width / 2, cy, w.regionStart[r] + 40, w.regionEnd[r] - 40);
            w.caches[i][0] = spot[0];
            w.caches[i][1] = spot[1];
        }
        for (int i = 0; i < w.shops.length; i++) { // SHOPS: bands 1-4, alternating right/left of centre
            int r = i + 1;
            float cx = (i % 2 == 0) ? 3700 : 1500;
            float cy = w.regionStart[r] + 0.40f * (w.regionEnd[r] - w.regionStart[r]);
            float[] spot = openNearShop(cx, cy, w.regionStart[r] + 300, w.regionEnd[r] - 300);
            w.shops[i][0] = spot[0];
            w.shops[i][1] = spot[1];
        }
        // TORCHES + PLANTS are placed AFTER buildCompounds and the guards, because both carve
        // the rock — a torch or plant may only stand on geometry that will still be there.
        buildCompounds(); // clean courtyard + rock compound around each shop: ring wall, door under the bay
        int posted = w.enemies.size();
        for (int i = 0; i < w.checkpoints.length; i++)
            guards(w.checkpoints[i].x, w.checkpoints[i].y, i + 1);
        for (int i = 0; i < w.shops.length; i++)
            guards(w.shops[i][0], w.shops[i][1], i + 1);
        for (int j = posted; j < w.enemies.size(); j++) // shop/checkpoint guards hold their site:
            w.enemies.get(j).siteGuard = true;         // a cache trigger must never pull them off
        // TORCHES: each landmark offers candidates; a torch only exists where a wall face is
        // within reach — no wall, no light (the base always has something to sit against).
        // Angle: flame points away from its rock — 0 up, -90 right, 90 left, 180 down.
        ArrayList<float[]> tl = new ArrayList<>();
        for (int i = 0; i < GATE_Y.length; i++) {
            float dx = GATE_DOOR[i] * TileMap.TILE - 16; // inside the doorway, next to the jamb
            mount(tl, w.width / 2 - dx, GATE_Y[i]);
            mount(tl, w.width / 2 + dx, GATE_Y[i]);
        }
        for (Checkpoint c : w.checkpoints) {
            mount(tl, c.x - 46, c.y);
            mount(tl, c.x + 46, c.y);
        }
        for (float[] s : w.shops) {
            mount(tl, s[0] - 46, s[1] - 24);
            mount(tl, s[0] + 46, s[1] - 24);
        }
        mount(tl, w.width / 2 - 200, 20544); // the sealed north gate
        mount(tl, w.width / 2 + 200, 20544);
        w.torches = tl.toArray(new float[0][]);

        // PLANTS: seeded scatter stuck to any rock face — floor grows up, ceiling hangs down,
        // walls grow sideways — roots tucked 4 px BEHIND the face (the wall tiles draw over
        // them), never on a corner block, all 1x; grass (variant 3) lands in side-by-side runs
        // of 1-3 tufts, everything exactly PLANT_GAP apart where they touch.
        ArrayList<float[]> pl = new ArrayList<>();
        Random prng = new Random(90218);
        float[] gap = { 370, 230, 4, 110, 110, 230 }; // smaller = thicker band (Maya Vana 5x denser)
        float[] ANGLE = { 0, 180, -90, 90 }; // rock below/above/left/right: grows up/down/right/left
        for (int r = 0; r < w.regionStart.length; r++) {
            int tries = (int) ((w.regionEnd[r] - w.regionStart[r]) / gap[r] * 68);
            for (int k = 0; k < tries; k++) {
                float px = 40 + prng.nextFloat() * (w.width - 80);
                float py = w.regionStart[r] + 40 + prng.nextFloat() * (w.regionEnd[r] - w.regionStart[r] - 80);
                if (w.blockedPx(px, py)) continue;
                // nearest rock on any of the four sides, directions tried in a per-plant order
                int rot = prng.nextInt(4), dir = -1;
                float nx = 0, ny = 0, rx = 0, ry = 0;
                for (float d = 8; d <= 96 && dir < 0; d += 8)
                    for (int i = 0; i < 4 && dir < 0; i++) {
                        int c = (rot + i) & 3; // 0 rock below, 1 above, 2 left, 3 right
                        float cx = c == 2 ? -1 : c == 3 ? 1 : 0, cy = c == 0 ? -1 : c == 1 ? 1 : 0;
                        if (w.blockedPx(px + cx * d, py + cy * d)) {
                            dir = c;
                            nx = cx;
                            ny = cy;
                            rx = px + cx * d;
                            ry = py + cy * d;
                        }
                    }
                if (dir < 0) continue;           // no rock within reach
                if (!faceRunsOn(rx, ry, nx, ny)) continue; // not a corner block
                float bx = rx + nx * 4, by = ry + ny * 4;   // root tucked behind the face
                if (!growsClear(bx, by, nx, ny)) continue;  // room to grow along its own normal
                if (!clearOfLandmarks(bx, by)) continue;
                if (clumped(pl, pl.size(), bx, by)) continue;
                boolean grass = prng.nextBoolean();
                int variant = grass ? 3 : prng.nextInt(3); // half the scatter is grass
                pl.add(new float[] { bx, by, variant, prng.nextFloat(), ANGLE[dir], 1f, rx, ry });
                if (!grass) continue;
                // single, double or triple: 0-2 more tufts, PLANT_GAP apart, each on live rock
                int run = prng.nextInt(3);
                float sgn = prng.nextBoolean() ? 1 : -1;
                float ux = -ny * PLANT_GAP * sgn, uy = nx * PLANT_GAP * sgn; // along the face
                for (int m = 1; m <= run; m++) {
                    float fx = rx + ux * m, fy = ry + uy * m;
                    if (!w.blockedPx(fx, fy) || !faceRunsOn(fx, fy, nx, ny)) break;
                    float mx = fx + nx * 4, my = fy + ny * 4;
                    if (!growsClear(mx, my, nx, ny) || !clearOfLandmarks(mx, my)) break;
                    if (clumped(pl, pl.size(), mx, my)) break;
                    pl.add(new float[] { mx, my, variant, prng.nextFloat(), ANGLE[dir], 1f, fx, fy });
                }
            }
        }
        w.plants = pl.toArray(new float[0][]);
        // THE SEAL: a per-kind kill quota sized off the full roster, so it is always obtainable
        int[] placed = new int[Enemy.Kind.values().length];
        for (Enemy e : w.enemies) placed[e.kind.ordinal()]++;
        for (Enemy.Kind k : Enemy.Kind.values())
            if (k != Enemy.Kind.BALA)
                w.quota[k.ordinal()] = Math.min(placed[k.ordinal()], Math.max(3, placed[k.ordinal()] / 8));
    }

    /** Spiral outward from the aim point until a spot is clear of rock for a ship-sized box.
     *  Clamped to the region so a checkpoint never lands in a neighbour's band. */
    private float[] openNear(float cx, float cy, float minY, float maxY) {
        for (int ring = 0; ring <= 30; ring++) {
            for (int a = -ring; a <= ring; a++) {
                for (int b = -ring; b <= ring; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != ring) continue;
                    float px = cx + a * TileMap.TILE;
                    float py = Math.max(minY, Math.min(maxY, cy + b * TileMap.TILE));
                    if (w.spotClear(px, py)) return new float[] { px, py };
                }
            }
        }
        return new float[] { cx, Math.max(minY, Math.min(maxY, cy)) };
    }

    /** openNear, plus the compound door approach must be open so the bay can be reached. */
    private float[] openNearShop(float cx, float cy, float minY, float maxY) {
        for (int ring = 0; ring <= 30; ring++) {
            for (int a = -ring; a <= ring; a++) {
                for (int b = -ring; b <= ring; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != ring) continue;
                    float px = cx + a * TileMap.TILE;
                    float py = Math.max(minY, Math.min(maxY, cy + b * TileMap.TILE));
                    if (w.spotClear(px, py) && shopFloorClear(px, py)) return new float[] { px, py };
                }
            }
        }
        return new float[] { cx, Math.max(minY, Math.min(maxY, cy)) };
    }

    /** The column under the sign: bay path, door, and the open approach below the compound. */
    private boolean shopFloorClear(float x, float y) {
        for (float dy : new float[] { -96, -160, -224 })
            for (float dx : new float[] { -48, 48 })
                if (w.blockedPx(x + dx, y + dy)) return false;
        return true;
    }

    /** Fleet court: 1 commander + N ring + M backup. ponytail: one method places every set. */
    private void court(Enemy.Kind cmd, Enemy.Kind ring, float ax, float ay, int n, int backups,
                       float patrol, float drift, float wake) {
        float room = openRoom(ax, ay, 200);
        if (room < 0.3f) backups = 0;
        else if (room < 0.5f) backups = backups / 2;
        Enemy c = new Enemy(cmd, ax, ay);
        c.rank = 1; // a court commander leads: its followers can regroup under it
        c.patrolAmp = 25;
        c.orbitRate = patrol / 100f;
        c.wakeRange = wake;
        w.add(c);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + Math.PI / n;
            Enemy e = new Enemy(ring, ax + (float) Math.cos(a) * 110, ay + (float) Math.sin(a) * 110);
            e.commander = c;
            e.patrolAmp = drift;
            e.orbitRate = patrol / 100f;
            e.wakeRange = wake;
            w.add(e);
        }
        for (int i = 0; i < backups; i++) {
            Enemy e = new Enemy(ring, ax + (i - (backups - 1) / 2f) * 60, ay - 200);
            e.commander = c;
            e.patrolAmp = drift; // same court motion as the ring; they still join the ring slot
            e.orbitRate = patrol / 100f;
            e.wakeRange = wake;
            w.add(e);
        }
    }

    private void sentryPair(float x, float y) {
        for (float sx : new float[] { x - 60, x + 60 }) {
            Enemy e = new Enemy(Enemy.Kind.LANCE, sx, y);
            e.sentry = true;
            e.wakeRange = 700;
            w.add(e);
        }
    }

    private void hatakaPair(float x, float y) {
        for (float sx : new float[] { x - 200, x + 200 }) {
            Enemy e = new Enemy(Enemy.Kind.HATAKA, sx, y - 100);
            e.wakeRange = 900;
            w.add(e);
        }
    }

    /** Region-graded shop/checkpoint guards: a rank-1 leader on the post with two sentries
     *  flanking below it. Region 0 stays open — that is where the player spawns. */
    private void guards(float x, float y, int region) {
        Enemy lead = new Enemy(World.GUARD[region][0], x, y + 90);
        lead.rank = 1;
        lead.sentry = true;
        lead.wakeRange = 700;
        lead.guard = true;
        w.add(lead);
        for (float dx : new float[] { -90, 90 }) {
            Enemy e = new Enemy(World.GUARD[region][1], x + dx, y - 50);
            e.commander = lead;
            e.sentry = true;
            e.wakeRange = 700;
            e.guard = true;
            w.add(e);
        }
    }

    /** A rock compound around each shop: ring wall with gold corners and a lintel over the
     *  door under the bay. The interior floor is carved clean out of whatever rock sits behind
     *  it first; a wall cell that would bury a posted enemy is left open instead. */
    private void buildCompounds() {
        for (float[] s : w.shops) { // clean courtyard: carve the interior out of region/gate rock
            float wx0 = s[0] - 5 * TileMap.TILE, wx1 = s[0] + 5 * TileMap.TILE;
            float wy0 = s[1] - 4.5f * TileMap.TILE, wy1 = s[1] + 4.5f * TileMap.TILE;
            for (TileMap m : w.maps) {
                for (int ty = (int) Math.floor((wy0 - m.oy) / TileMap.TILE); ty <= (int) Math.floor((wy1 - m.oy) / TileMap.TILE); ty++)
                    for (int tx = (int) Math.floor((wx0 - m.ox) / TileMap.TILE); tx <= (int) Math.floor((wx1 - m.ox) / TileMap.TILE); tx++)
                        m.set(tx, ty, TileMap.EMPTY);
            }
        }
        TileMap[] all = new TileMap[w.maps.length + w.shops.length];
        System.arraycopy(w.maps, 0, all, 0, w.maps.length);
        for (int i = 0; i < w.shops.length; i++) {
            int ox = (int) (w.shops[i][0] - 6 * TileMap.TILE), oy = (int) (w.shops[i][1] - 5.5f * TileMap.TILE);
            TileMap c = new TileMap(ox, oy, 12, 11);
            for (int ty = 0; ty < 11; ty++) {
                for (int tx = 0; tx < 12; tx++) {
                    if (tx != 0 && tx != 11 && ty != 0 && ty != 10) continue;
                    if (ty == 0 && tx >= 4 && tx <= 7) continue; // the door: you rise up through it
                    float wx = ox + tx * TileMap.TILE + 16, wy = oy + ty * TileMap.TILE + 16;
                    if (overlapsHome(wx, wy)) continue;
                    boolean corner = (tx == 0 || tx == 11) && (ty == 0 || ty == 10);
                    boolean lintel = ty == 10 && tx >= 4 && tx <= 7;
                    c.set(tx, ty, corner || lintel ? TileMap.GOLD : TileMap.ROCK);
                }
            }
            all[w.maps.length + i] = c;
        }
        w.maps = all;
    }

    private boolean overlapsHome(float wx, float wy) {
        for (Enemy e : w.enemies)
            if (Math.abs(e.homeX - wx) < e.w / 2 + 16 && Math.abs(e.homeY - wy) < e.h / 2 + 16)
                return true;
        return false;
    }

    private float openRoom(float x, float y, float r) {
        float best = -1;
        for (TileMap m : w.tileMaps()) best = Math.max(best, m.openFraction(x, y, r));
        return best < 0 ? 1 : best;
    }

    /** Full Atala order of battle: honor guard in the grand gateway, fodder at the mouth of
     *  band 1, each band heavier than the last, stationed guards on every gate, arena honor
     *  guard at the top. Mirrored about x = 2600 (gate guards excepted: they are stationed). */
    private void spawnAtala() {
        // GRAND ENTRY: LANCE sentries and HATAKA escorts hovering in the doorway under the arch
        for (float dx : new float[] { -64, 64 }) {
            Enemy e = new Enemy(Enemy.Kind.LANCE, 2600 + dx, 1790);
            e.sentry = true;
            e.wakeRange = 700;
            w.add(e);
        }
        for (float dx : new float[] { -150, 150 }) {
            Enemy e = new Enemy(Enemy.Kind.HATAKA, 2600 + dx, 1790);
            e.wakeRange = 900;
            w.add(e);
        }
        // band 1 - Svarna Dvara: the outer wall of Atala, AURIS/VAKRA fodder
        sentryPair(2600, 2150);
        court(Enemy.Kind.VAKRA, Enemy.Kind.AURIS, 2600, 3000, 6, 4, 60, 60, 900);
        court(Enemy.Kind.VAKRA, Enemy.Kind.AURIS, 1500, 3550, 6, 4, 70, 55, 900);
        court(Enemy.Kind.VAKRA, Enemy.Kind.AURIS, 3700, 3550, 6, 4, 70, 55, 900);

        // gate 4100: stationed above the doorway
        guards(2600, 4300, 2);
        // band 2 - Maya Vana: VAKRA courts under BHAVI command, gold seam liars
        sentryPair(2600, 4650);
        court(Enemy.Kind.BHAVI, Enemy.Kind.VAKRA, 2600, 5200, 4, 2, 80, 25, 900);
        hatakaPair(2600, 5200);
        court(Enemy.Kind.VAKRA, Enemy.Kind.AURIS, 1500, 6100, 6, 4, 90, 60, 900);
        court(Enemy.Kind.VAKRA, Enemy.Kind.AURIS, 3700, 6100, 6, 4, 90, 60, 900);
        court(Enemy.Kind.VAKRA, Enemy.Kind.AURIS, 2600, 6800, 4, 2, 90, 55, 900);
        sentryPair(2600, 7100);

        // gate 7600
        guards(2600, 7800, 3);
        // band 3 - Svapna Mandala: MAYAVI illusion tier
        court(Enemy.Kind.MAYAVI, Enemy.Kind.BHAVI, 2600, 8500, 6, 4, 100, 30, 900);
        hatakaPair(2600, 8500);
        court(Enemy.Kind.VAKRA, Enemy.Kind.AURIS, 1500, 9600, 6, 4, 100, 70, 900);
        court(Enemy.Kind.VAKRA, Enemy.Kind.AURIS, 3700, 9600, 6, 4, 100, 70, 900);
        court(Enemy.Kind.MAYAVI, Enemy.Kind.BHAVI, 2600, 10800, 4, 2, 110, 25, 900);
        sentryPair(2600, 11500);

        // gate 12000
        guards(2600, 12200, 4);
        // band 4 - Bala Mukha: the heavy approach, elites holding the side roads
        sentryPair(2600, 12550);
        court(Enemy.Kind.MAYAVI, Enemy.Kind.BHAVI, 2600, 13300, 6, 4, 110, 30, 900);
        hatakaPair(2600, 13300);
        court(Enemy.Kind.BHAVI, Enemy.Kind.VAKRA, 1500, 14400, 6, 4, 120, 45, 900);
        court(Enemy.Kind.BHAVI, Enemy.Kind.VAKRA, 3700, 14400, 6, 4, 120, 45, 900);
        court(Enemy.Kind.MAYAVI, Enemy.Kind.BHAVI, 2600, 15400, 4, 2, 120, 30, 900);
        for (float ex : new float[] { 1200, 4000 }) {
            Enemy e = new Enemy(Enemy.Kind.VAKRA, ex, 15400);
            e.elite = true;
            e.rank = 1; // an elite counts as senior too: strays can join its formation
            e.hp = e.maxHp = e.maxHp * 2; // elite means tougher, not just gold
            e.wakeRange = 700;
            w.add(e);
        }
        court(Enemy.Kind.MAYAVI, Enemy.Kind.BHAVI, 2600, 16300, 4, 2, 120, 30, 900);
        hatakaPair(2600, 16300);
        sentryPair(2600, 17200);

        // gate 17600: the boss gate
        guards(2600, 17800, 5);
        // band 5 - Boss: Bala: honor guard around the pit
        sentryPair(2600, 18100);
        for (float[] p : new float[][] { { 3000, 19084 }, { 2200, 19084 }, { 2600, 19584 }, { 2600, 18584 } }) {
            Enemy e = new Enemy(Enemy.Kind.MAYAVI, p[0], p[1]);
            e.wakeRange = 1200;
            w.add(e);
        }
        for (float[] p : new float[][] { { 3512, 19084 }, { 1688, 19084 }, { 2600, 19900 }, { 2600, 18300 } }) {
            Enemy e = new Enemy(Enemy.Kind.LANCE, p[0], p[1]);
            e.sentry = true;
            e.wakeRange = 1200;
            w.add(e);
        }
    }

    /** Nearest wall face around an open point: pushes {anchorX, anchorY, angle} — the flame
     *  points away from its rock (0 up, -90 right, 90 left, 180 down) — or nothing at all when
     *  there is no wall within reach. The candidate itself must be in open air. */
    private void mount(ArrayList<float[]> out, float cx, float cy) {
        if (w.blockedPx(cx, cy)) return;
        float[][] dirs = { { 0, -1, 0 }, { -1, 0, -90 }, { 1, 0, 90 }, { 0, 1, 180 } };
        for (float[] d : dirs)
            for (float r = 8; r <= 160; r += 8)
            if (w.blockedPx(cx + d[0] * r, cy + d[1] * r)) {
                out.add(new float[] { cx + d[0] * (r - 4), cy + d[1] * (r - 4), d[2],
                        cx + d[0] * r, cy + d[1] * r });
                return;
            }
    }

    /** Room to grow straight out of its own rock face: nothing blocks the 32 px it draws. */
    private boolean growsClear(float bx, float by, float nx, float ny) {
        for (float s = 8; s <= 32; s += 8)
            if (w.blockedPx(bx - nx * s, by - ny * s)) return false;
        return true;
    }

    /** The no-clump rule: a plant may not stand closer than PLANT_GAP to an earlier one. */
    private boolean clumped(ArrayList<float[]> pl, int until, float x, float y) {
        for (int i = 0; i < until; i++) {
            float[] q = pl.get(i);
            if ((q[0] - x) * (q[0] - x) + (q[1] - y) * (q[1] - y) < PLANT_GAP * PLANT_GAP) return true;
        }
        return false;
    }

    /** Roots never sit on a corner block: the face carries rock a tile either side of them. */
    private boolean faceRunsOn(float fx, float fy, float nx, float ny) {
        float tx = -ny * TileMap.TILE, ty = nx * TileMap.TILE;
        return solidAt(fx + tx, fy + ty, nx, ny) && solidAt(fx - tx, fy - ty, nx, ny);
    }

    /** The neighbour tile is solid at this row, or a little deeper — stepped edges count. */
    private boolean solidAt(float x, float y, float nx, float ny) {
        return w.blockedPx(x, y) || w.blockedPx(x + nx * 8, y + ny * 8);
    }

    /** Plants keep clear of gates, caches, shops, checkpoints, torches and the spawn meadow. */
    private boolean clearOfLandmarks(float x, float y) {
        if (Math.abs(x - w.width / 2) < 320)
            for (float gy : GATE_Y)
                if (Math.abs(y - gy) < 160) return false;
        if (Math.hypot(x - w.width / 2, y - 800) < 180) return false;
        for (Checkpoint c : w.checkpoints) if (Math.hypot(x - c.x, y - c.y) < 84) return false;
        for (float[] s : w.shops) if (Math.hypot(x - s[0], y - s[1]) < 84) return false;
        for (float[] c : w.caches) if (Math.hypot(x - c[0], y - c[1]) < 84) return false;
        for (float[] t : w.torches) if (Math.hypot(x - t[0], y - t[1]) < 56) return false;
        return true;
    }
}



