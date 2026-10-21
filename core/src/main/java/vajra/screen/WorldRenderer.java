package vajra.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.graphics.OrthographicCamera;
import vajra.world.Checkpoint;
import vajra.world.Effect;
import vajra.enemy.Enemy;
import vajra.world.Pickup;
import vajra.world.Popups;
import vajra.world.TileMap;
import vajra.world.World;
import vajra.world.AstraCube;
import vajra.world.LokaTable;
import vajra.ship.Ship;

/** Every world-space draw pass in one place, in fixed order: nebula, backdrop, plants, tiles,
 *  torches, enemies, loot, signs, bullets, sparks, hp bars, boss bar, hull, effects, shield, lights.
 *  Owns all image resources for the world (one instance per play session). The camera is
 *  positioned by PlayState before this runs; lights close the frame so overlays can dim it. */
public final class WorldRenderer {
    private final SpriteBatch batch;
    private final Ui ui;
    private final ShapeRenderer shapes;
    private final TileImages tiles;
    private final OrbImages orbs;
    private final PickupImages loot;
    private final CheckpointImages cps;
    private final ShopImages shopSign;
    private final NebulaImages nebula;
    private final EnemyImages foes;
    private final BulletImages shots;
    private final FxImages fx;
    private final ShipRenderer shipR;
    private final Backdrop backdrop;
    private final Lights lights;
    private final PopupArt popupArt; // the pre-rendered Devanagari phrases

    public WorldRenderer(SpriteBatch batch, Ui ui, World world) {
        this.batch = batch;
        this.ui = ui;
        this.shapes = ui.shapes;
        this.tiles = new TileImages();
        this.orbs = new OrbImages();
        this.loot = new PickupImages();
        this.cps = new CheckpointImages();
        this.shopSign = new ShopImages();
        this.nebula = new NebulaImages();
        this.foes = new EnemyImages();
        this.shots = new BulletImages();
        this.fx = new FxImages();
        this.shipR = new ShipRenderer(fx);
        this.backdrop = new Backdrop(world.width, world.height);
        this.lights = new Lights();
        this.popupArt = new PopupArt();
    }

    /** The ship art, borrowed by the hangar for its hull preview. */
    public ShipRenderer ships() {
        return shipR;
    }

    public void render(World world, OrthographicCamera camera, FitViewport viewport) {
        float viewW = camera.viewportWidth * camera.zoom;
        float viewH = camera.viewportHeight * camera.zoom;
        float l = camera.position.x - viewW / 2;
        float b = camera.position.y - viewH / 2;

        nebulaLayer(world, camera, l, b, viewW, viewH);
        backdropLayer(world, camera, l, b, viewW, viewH);
        worldSpriteLayer(world, camera, l, b, viewW, viewH);
        sparkAndBarLayer(world, camera, l, b, viewW, viewH);
        lights.render(batch, ui.camera, viewport, world, l, b, viewW, viewH);
        shipLayer(world, camera);
        shieldLayer(world, camera);
        foregroundLayer(world, camera);
    }

    private void nebulaLayer(World world, OrthographicCamera camera,
                            float l, float b, float viewW, float viewH) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(1, 1, 1, 0.1f);
        nebula.draw(batch, l, b, viewW, viewH, camera.position.x, camera.position.y, world.time());
        batch.setColor(1, 1, 1, 1);
        batch.end();
    }

    private void backdropLayer(World world, OrthographicCamera camera,
                              float l, float b, float viewW, float viewH) {
        shapes.setProjectionMatrix(camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        backdrop.draw(shapes, camera.position.x, camera.position.y, l, b, l + viewW, b + viewH, false);
        for (int i = 0; i < world.checkpoints.length; i++) { // behind the stack, never over it
            Checkpoint c = world.checkpoints[i];
            if (c.x + 90 > l && c.x - 90 < l + viewW && c.y + 90 > b && c.y - 90 < b + viewH) {
                cps.drawParticles(shapes, c, world.time());
            }
        }
        shapes.end();
    }

    private void worldSpriteLayer(World world, OrthographicCamera camera,
                                 float l, float b, float viewW, float viewH) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        // plants first: the wall tiles draw over their tucked roots — they grow from behind
        for (float[] p : world.plants) {
            if (p[0] + 80 > l && p[0] - 80 < l + viewW && p[1] + 80 > b && p[1] - 80 < b + viewH)
                fx.drawPlant(batch, p[0], p[1], (int) p[2], world.time(), p[3], p[4], p[5]);
        }
        for (TileMap m : world.tileMaps()) {
            tiles.draw(batch, m, l, b, l + viewW, b + viewH);
        }
        for (int i = 0; i < world.torches.length; i++) { // wall-mounted props, behind everything
            float[] t = world.torches[i];
            if (t[0] + 40 > l && t[0] - 40 < l + viewW && t[1] + 40 > b && t[1] - 40 < b + viewH)
                fx.drawFlame(batch, t[0], t[1], t[2], world.time(), i);
        }
        for (int i = 0; i < world.enemies.size(); i++) {
            Enemy e = world.enemies.get(i);
            if (e.bossHidden) continue; // mid-blink: the body is elsewhere
            if (e.x + e.w / 2 > l && e.x - e.w / 2 < l + viewW && e.y + e.h / 2 > b && e.y - e.h / 2 < b + viewH) {
                foes.draw(batch, e, world.time());
            }
        }
        for (int i = 0; i < world.orbs.length; i++) {
            if (world.orbs[i].visible) orbs.draw(batch, world.orbs[i]);
        }
        for (int i = 0; i < world.pickups.size(); i++) {
            Pickup p = world.pickups.get(i);
            if (p.x + 32 > l && p.x - 32 < l + viewW && p.y + 32 > b && p.y - 32 < b + viewH)
                loot.draw(batch, p, world.time());
        }
        for (int i = 0; i < world.shops.length; i++) { // the hanging SHOP sign + bay, behind the checkpoint stack
            float[] s = world.shops[i];
            if (s[0] + 70 > l && s[0] - 70 < l + viewW && s[1] + 110 > b && s[1] - 70 < b + viewH) {
                shopSign.draw(batch, s[0], s[1], world.shopState[i], world.shopTimer[i]);
            }
        }
        for (int i = 0; i < world.checkpoints.length; i++) {
            Checkpoint c = world.checkpoints[i];
            if (c.x + 80 > l && c.x - 80 < l + viewW && c.y + 80 > b && c.y - 80 < b + viewH) {
                cps.draw(batch, c, world.time());
            }
        }
        for (int i = 0; i < world.bullets.size(); i++) shots.draw(batch, world.bullets.get(i));
        batch.end();
    }

    private void sparkAndBarLayer(World world, OrthographicCamera camera,
                                 float l, float b, float viewW, float viewH) {
        shapes.setProjectionMatrix(camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        for (Effect e : world.effects) {
            if (e.type == Effect.SURGE) { surgeLayer(shapes, e); continue; }
            if (e.type != Effect.SPARK) continue;
            shapes.setColor(e.r, e.g, e.b, e.alpha());
            shapes.circle(e.x, e.y, e.radius);
        }
        astraLayer(shapes, world, l, b, viewW, viewH);
        for (int i = 0; i < world.enemies.size(); i++) {
            Enemy foe = world.enemies.get(i);
            if (foe.dead || foe.boss) continue; // the commander has his own, bigger bar
            if (foe.x + foe.w < l || foe.x - foe.w > l + viewW || foe.y + foe.h < b || foe.y - foe.h > b + viewH) continue;
            hpBar(shapes, foe);
        }
        // Bala's bar rides above his hull like everyone else's — bigger, segmented, phase-tinted,
        // notched at both thresholds. Appears the moment the ship enters the boss region.
        if (world.currentRegionIndex() == 5 && world.boss != null && !world.boss.dead
                && !world.boss.bossHidden) {
            Enemy bal = world.boss;
            float bw = 240, bh = 16, bx = bal.x - bw / 2, by = bal.y + bal.h / 2 + 10;
            if (bx + bw > l && bx < l + viewW && by < b + viewH && by + bh > b) {
                float frac = Math.max(0f, bal.dispHp / bal.maxHp);
                shapes.setColor(1f, 0.78f, 0.2f, 1f);          // gold frame
                shapes.rect(bx - 3, by - 3, bw + 6, bh + 6);
                shapes.setColor(0.07f, 0.05f, 0.05f, 0.95f);   // dark backing
                shapes.rect(bx, by, bw, bh);
                if (bal.bossPhase == 1) shapes.setColor(1f, 0.78f, 0.2f, 1f);       // gold
                else if (bal.bossPhase == 2) shapes.setColor(1f, 0.5f, 0.12f, 1f);  // ember
                else shapes.setColor(0.95f, 0.22f, 0.1f, 1f);                      // blood
                float seg = bw / 20f; // segmented fill: an honor bar, not a plain strip
                for (int s = 0; s < 20; s++) {
                    float left = frac * bw - s * seg;
                    if (left <= 0) break;
                    shapes.rect(bx + s * seg, by + 2, Math.min(seg - 2, left), bh - 4);
                }
                shapes.setColor(1, 1, 1, 0.7f);                // threshold notches
                shapes.rect(bx + bw / 3f - 1, by, 2, bh);
                shapes.rect(bx + 2 * bw / 3f - 1, by, 2, bh);
            }
        }
        if (!shipR.hasArt(world.ship)) shipR.drawHull(shapes, world.ship);
        shapes.end();
    }

    /** SURGE: two stacked discs — a wide faint body and a tighter, faster inner front. Overlapping
     *  a slow swell with a quick one is what stops an expanding circle reading as a flat halo:
     *  the eye sees a body with a crest running through it. Drawn in the shape pass AFTER the
     *  enemies, so the release washes OVER them.
     *
     *  <p>The release is drawn in the loka's own tatva, so nothing here may bias the hue: the
     *  body is darkened and the crest lifted by UNIFORM factors. Shading a single channel - as
     *  the old water version did, holding blue at 0.8 - turned Atala's violet Akasha into blue. */
    private void surgeLayer(ShapeRenderer s, Effect e) {
        float reach = e.surgeReach();
        if (reach <= 0.001f) return;
        float rad = e.radius * reach;
        float a = e.alpha();
        s.setColor(e.r * 0.62f, e.g * 0.62f, e.b * 0.68f, e.bodyAlpha * a);
        s.circle(e.x, e.y, rad);
        float front = 1f - (1f - reach) * (1f - reach); // leads the body
        s.setColor(e.r + (1f - e.r) * 0.6f, e.g + (1f - e.g) * 0.6f, e.b + (1f - e.b) * 0.6f,
                0.5f * a * Math.min(1f, front * 1.6f));
        s.circle(e.x, e.y, rad * 0.82f);
    }

    /** THE AKASHA ASTRA's cubes, drawn as real projected solids rather than sprites.
     *
     *  <p>A cube's eight corners are rotated in 3D (yaw on world time, pitch a fraction behind
     *  it), projected with a mild perspective, and its six faces are drawn back-to-front with
     *  brightness taken from each face's rotated normal. That is what makes it read as a
     *  spinning solid rather than a spinning square, and it needs no art - which suits a weapon
     *  made of the element itself.
     *
     *  <p>Painter's order does the occlusion, so no backface culling is needed and the winding
     *  order of the quads does not matter. */
    // Corner v has bits x=1, y=2, z=4, so each face is exactly the four corners sharing one bit -
    // and they must be in CYCLIC order around that face. Listing the same four corners in index
    // order gives a bowtie, whose signed area cancels to zero: every face vanished and the cube
    // rendered as a few stray slivers. These are hand-derived per face, not generated.
    /** Cube half-size in world px. 14 px across reads as a solid at 1x and still leaves
     *  the 3.6 px gap between neighbours when the shell closes at WIND_R_IN. */
    public static final float CUBE_HALF = 7f;
    private static final int[][] ASTRA_FACE = {
            { 4, 5, 7, 6 }, // bit 2 set: +z, toward the viewer
            { 0, 1, 3, 2 }, // bit 2 clear: -z
            { 1, 5, 7, 3 }, // bit 0 set: +x
            { 0, 4, 6, 2 }, // bit 0 clear: -x
            { 3, 7, 6, 2 }, // bit 1 set: +y, the top
            { 0, 2, 5, 4 }, // bit 1 clear: -y
    };
    private static final float[][] ASTRA_N = {
            { 0, 0, 1 }, { 0, 0, -1 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 },
    };
    private final int[] astraOrder = new int[6];
    private final float[] astraDepth = new float[6];
    private final float[] astraPx = new float[8], astraPy = new float[8], astraPz = new float[8];
    private final float[] astraQuad = new float[8];

    /** Project a cube's eight corners at `spin` into world x/y. Pure and static so the geometry
     *  can be pinned headless - a cube that degenerates to a line, or flips inside out as it
     *  turns, is exactly the sort of thing that only shows up as "it looks wrong".
     *
     *  <p>Corner i is the cube vertex with bits x=1, y=2, z=4 set. Writes 8 x into {@code px}
     *  and 8 y into {@code py}. Depth (the rotated z) goes into {@code pz} when given.
     *
     *  <p>{@code PERSPECTIVE} is a divisor, not a zoom: k = P / (P + z). A corner's rotated z
     *  reaches 1.73, so at P = 3.2 the near corner magnified 4x and the cube rendered as a
     *  fisheye wedge rather than a solid. P = 12 holds k to 0.83..1.25 - enough to read the
     *  near/far ordering, little enough to stay a cube. */
    public static final float PERSPECTIVE = 12f;

    public static void projectCube(float cx, float cy, float spin, float half,
            float[] px, float[] py, float[] pz) {
        float yaw = spin, pitch = spin * 0.62f;
        float cyaw = (float) Math.cos(yaw), syaw = (float) Math.sin(yaw);
        float cpit = (float) Math.cos(pitch), spit = (float) Math.sin(pitch);
        for (int v = 0; v < 8; v++) {
            float vx = (v & 1) == 0 ? -1 : 1, vy = (v & 2) == 0 ? -1 : 1,
                    vz = (v & 4) == 0 ? -1 : 1;
            float rx = vx * cyaw - vz * syaw, rz = vx * syaw + vz * cyaw;
            float ry = vy * cpit - rz * spit, rzz = vy * spit + rz * cpit;
            float k = PERSPECTIVE / (PERSPECTIVE + rzz * 1.4f);
            px[v] = cx + rx * half * k;
            py[v] = cy + ry * half * k;
            if (pz != null) pz[v] = rzz;
        }
    }

    private void astraLayer(ShapeRenderer s, World w, float l, float b, float viewW, float viewH) {
        if (w.astra.isEmpty()) return;
        float[] el = World.TATVA_RGB[LokaTable.at(w.lokaIndex).shards[0]];

        for (AstraCube c : w.astra) {
            if (c.x < l - 40 || c.x > l + viewW + 40 || c.y < b - 40 || c.y > b + viewH + 40) {
                continue;
            }
            projectCube(c.x, c.y, c.spin, CUBE_HALF, astraPx, astraPy, astraPz);
            float spin = c.spin, pitch = c.spin * 0.62f;
            float cyaw = (float) Math.cos(spin), syaw = (float) Math.sin(spin);
            float cpit = (float) Math.cos(pitch), spit = (float) Math.sin(pitch);

            // sort the six faces far to near: insertion sort over six items, no allocation
            for (int i = 0; i < 6; i++) {
                astraOrder[i] = i;
                astraDepth[i] = faceDepth(i);
            }
            for (int i = 1; i < 6; i++) {
                int f = astraOrder[i];
                float d = astraDepth[i];
                int j = i - 1;
                while (j >= 0 && astraDepth[j] < d) {
                    astraDepth[j + 1] = astraDepth[j];
                    astraOrder[j + 1] = astraOrder[j];
                    j--;
                }
                astraDepth[j + 1] = d;
                astraOrder[j + 1] = f;
            }
            for (int i = 0; i < 6; i++) drawAstraFace(s, astraOrder[i], el, cyaw, syaw, cpit, spit);
        }
    }

    private float faceDepth(int face) {
        int[] q = ASTRA_FACE[face];
        return (astraPz[q[0]] + astraPz[q[1]] + astraPz[q[2]] + astraPz[q[3]]) * 0.25f;
    }

    /** Brightness from the face's rotated normal: faces turned toward the viewer are lit, faces
     *  turned away fall away, so the tumble reads even on a flat fill.
     *
     *  <p>Two triangles, not {@code polygon()}: ShapeRenderer.polygon is Line-mode only and this
     *  pass is Filled. Opaque, so the shared diagonal does not seam. */
    private void drawAstraFace(ShapeRenderer s, int face, float[] el,
            float cyaw, float syaw, float cpit, float spit) {
        float[] n = ASTRA_N[face];
        float rx = n[0] * cyaw - n[2] * syaw, rz = n[0] * syaw + n[2] * cyaw;
        float ry = n[1] * cpit - rz * spit;
        float rz2 = n[1] * spit + rz * cpit;
        // floor 0.70, top 1.35: even the darkest face stays unmistakably the element, and
        // the face turned to the viewer blooms past it. A 0.42 floor greyed the violet out.
        float lit = 0.70f + 0.65f * (rz2 * 0.5f + 0.5f);
        s.setColor(el[0] * lit, el[1] * lit, el[2] * lit, 1f);
        int[] q = ASTRA_FACE[face];
        for (int i = 0; i < 4; i++) {
            astraQuad[i * 2] = astraPx[q[i]];
            astraQuad[i * 2 + 1] = astraPy[q[i]];
        }
        s.triangle(astraQuad[0], astraQuad[1], astraQuad[2], astraQuad[3],
                astraQuad[4], astraQuad[5]);
        s.triangle(astraQuad[0], astraQuad[1], astraQuad[6], astraQuad[7],
                astraQuad[2], astraQuad[3]);
    }


    private void shipLayer(World world, OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        // post-lights visibility pass: the hull (or, if art is missing, its fallback) must
        // stay bright on top of the dim frame alongside the sprite.
        if (!shipR.hasArt(world.ship)) shipR.drawHull(shapes, world.ship);
        // no decorative halo / rim circle around the hull (was obscuring the ship, and the
        // state ring it replaced): state now reads through the hull's dip and its flicker
        shipR.drawCharge(shapes, world.ship);
        if (world.partner != null) { // co-op: the other hull reads the same way
            if (!shipR.hasArt(world.partner)) shipR.drawHull(shapes, world.partner);
            shipR.drawCharge(shapes, world.partner);
        }
        for (World.SeatHull gh : world.extras) { // the extra hulls read like the partner's
            if (gh.ship == null) continue;
            if (!shipR.hasArt(gh.ship)) shipR.drawHull(shapes, gh.ship);
            shipR.drawCharge(shapes, gh.ship);
        }
        // every hull wears its pilot's colour bar below it: who is who at a glance
        if (vajra.net.Net.active()) {
            vajra.net.Session s = vajra.net.Net.session;
            seatBar(shapes, world.ship, s.myId()); // my own seat, not seat 0's
            if (world.partner != null) seatBar(shapes, world.partner, world.partnerSeat);
            for (World.SeatHull gh : world.extras) if (gh.ship != null) seatBar(shapes, gh.ship, gh.seat);
        }
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        shipR.draw(batch, world.ship, world.time());
        if (world.partner != null) shipR.draw(batch, world.partner, world.time()); // co-op: the other hull
        for (World.SeatHull gh : world.extras) // co-op: the extra hulls
            if (gh.ship != null) shipR.draw(batch, gh.ship, world.time());
        for (Effect e : world.effects) fx.draw(batch, e);
        batch.end();
    }

    private void shieldLayer(World world, OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Line);
        if (world.ship.shieldOn && world.ship.shieldEnergy > 0) {
            shapes.setColor(0.3f, 0.6f, 1, 0.3f + 0.4f * (world.ship.shieldEnergy / world.ship.shieldCap()));
            shapes.circle(world.ship.x, world.ship.y, 30 + 2 * (float) Math.sin(world.time() * 6));
        }
        shapes.end();
    }

    private void foregroundLayer(World world, OrthographicCamera camera) {
        // bullets again, over the dimmed frame: shots must read in the dark
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (int i = 0; i < world.bullets.size(); i++) shots.draw(batch, world.bullets.get(i));
        drawPopups(world);
        batch.end();
    }

    /** A hull's seat bar: their colour, right under the hull. */
    private void seatBar(ShapeRenderer sh, Ship h, int seat) {
        sh.setColor(SeatColors.of(seat));
        sh.rect(h.x - 14, h.y - 30, 28, 5);
    }

    /** Floating popups (Popups): world-space, topmost, punch-in scale and end-of-life fade.
     *  ONE LANGUAGE per popup, both small: with an `img` the popup is the pre-rendered
     *  Devanagari phrase alone — centred, tinted, drop-shadowed — with no English caption;
     *  without one it is the plain English text (centred, outline shadow for legibility over
     *  any backdrop). The settings' popup scale stretches both.
     *  The logic lives in Popups, this only paints. */
    private static final float TEXT_SCALE = 0.62f;  // words render small
    private static final float VERSE_SCALE = 0.45f; // verses smaller still: 66px art lands ~30px tall

    private final com.badlogic.gdx.graphics.g2d.GlyphLayout popLayout =
            new com.badlogic.gdx.graphics.g2d.GlyphLayout();

    private void drawPopups(World world) {
        float pScale = vajra.profile.Profiles.active().settings.popupScale;
        for (Popups.Spec s : world.popups.slots()) {
            if (s.text == null) continue;
            float fade = world.popups.alphaOf(s);
            com.badlogic.gdx.graphics.g2d.TextureRegion img = popupArt.get(s.img);
            float scale = world.popups.punchOf(s) * pScale
                    * (img != null ? VERSE_SCALE : TEXT_SCALE);
            if (img != null) { // the verse: drop-shadowed, tinted, centred on the push point
                float w = img.getRegionWidth() * scale, h = img.getRegionHeight() * scale;
                batch.setColor(0, 0, 0, fade * 0.8f);
                batch.draw(img, s.x - w / 2 + 1f, s.y - h / 2 - 1f, w, h);
                batch.setColor(s.r, s.g, s.b, fade);
                batch.draw(img, s.x - w / 2, s.y - h / 2, w, h);
            } else if (!s.text.isEmpty()) {
                com.badlogic.gdx.graphics.g2d.BitmapFont f = ui.hud;
                f.getData().setScale(scale);
                popLayout.setText(f, s.text);
                float tx = s.x - popLayout.width / 2;
                f.setColor(0, 0, 0, fade * 0.8f);
                f.draw(batch, s.text, tx + 1f, s.y - 1f);
                f.setColor(s.r, s.g, s.b, fade);
                f.draw(batch, s.text, tx, s.y);
                f.getData().setScale(1f);
            }
        }
        ui.hud.setColor(1, 1, 1, 1);
    }

    /** Thin bar above a hull: green→red with real hp, drawn to the eased dispHp, so a hit
     *  burns a white-hot slice at the leading edge that cools away as the bar drains onto hp. */
    private void hpBar(ShapeRenderer s, Enemy e) {
        float bw = e.w * 0.9f, bh = 2f;
        float x = e.x - bw / 2, y = e.y + e.h / 2 + 5;
        float frac = Math.max(0, e.hp / e.maxHp);
        float disp = Math.max(0, Math.min(1, e.dispHp / e.maxHp));
        s.setColor(0.05f, 0.05f, 0.08f, 0.6f);
        s.rect(x - 1, y - 1, bw + 2, bh + 2);
        s.setColor(1f - 0.75f * frac, 0.3f + 0.7f * frac, 0.2f, 0.95f);
        s.rect(x, y, bw * disp, bh);
        if (disp > frac) {
            s.setColor(1, 1, 1, 0.9f);
            s.rect(x + bw * frac, y, bw * (disp - frac), bh);
        }
    }

    public void dispose() {
        foes.dispose();
        nebula.dispose();
        orbs.dispose();
        loot.dispose();
        cps.dispose();
        shopSign.dispose();
        shots.dispose();
        fx.dispose();
        shipR.dispose();
        tiles.dispose();
        lights.dispose();
        popupArt.dispose();
    }
}
