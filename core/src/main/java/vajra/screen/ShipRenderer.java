package vajra.screen;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import vajra.ship.Ship;

/** Ship art. The sprite (vajra_ships, one variation per upgrade tier) is the real hull; the
 *  24x24 polygon with its nose wedge is the fallback when a file is missing. Thrust plume goes
 *  under the hull and the charge glow over it, so they are drawn separately from both. */
public class ShipRenderer {
    private static final float HALF = Ship.W / 2f;   // COLLISION half — not where the art ends
    private static final float SIZE = 40;             // render scale; collision stays 24x24
    private static final float ART_HALF = SIZE / 2f;  // where the hull ART actually ends
    private static final float PLUME_SCALE = 2;
    private static final float DASH_STRETCH = 1.6f, DASH_SQUASH = 0.7f; // smear into the push
    public static final float DIP_PX = 5;  // how far the hull sinks when hit / shielding / dashing
    /** How far back along the exhaust axis the plume is anchored: just past the hull ART's
     *  edge (ART_HALF = 20) plus a gap. This used to be HALF + 6 = 18 — which is INSIDE a 40 px
     *  sprite, so the flame was drawn under the hull instead of off its tail. */
    public static final float PLUME_ANCHOR = ART_HALF + 3f;
    private static final Matrix4 IDENT = new Matrix4(); // setTransformMatrix copies it, never mutates it

    private final ShipImages art = new ShipImages();
    private final FxImages fx;
    private final Matrix4 deform = new Matrix4();
    private final TextureRegion fallbackWhite;  // 1x1 white pixel fallback for when sprite art is missing

    public ShipRenderer(FxImages fx) {
        this.fx = fx;
        // 1x1 white pixel fallback for when sprite art is missing at runtime
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1, 1, 1, 1);
        pm.fillRectangle(0, 0, 1, 1);
        Texture t = new Texture(pm);
        pm.dispose();
        fallbackWhite = new TextureRegion(t);
    }

    /** The sprite's variation follows the ship's highest bought tier: 1, 2 or 3. */
    public static int variation(Ship s) {
        boolean[][] owned = s.hangar.owned;
        return owned[s.def.id][2] ? 3 : owned[s.def.id][1] ? 2 : 1;
    }

    public boolean hasArt(Ship s) {
        return art.get(s.def.id, variation(s)) != null;
    }

    /** Bare hull art for the hangar preview: variation 1..3, null when the file is missing. */
    public TextureRegion hull(int shipId, int variation1to3) {
        return art.get(shipId, variation1to3);
    }

    /** Sprite hull, nose up in the art, so it turns to facing - 90°. Dashing ghosts the hull:
     *  half transparent and smeared along the push key's world axis — W grows up, S down,
     *  A left, D right (a bare burst follows the nose), squeezed across it.
     *
     *  <p>The hull is drawn {@code Ship.dip} px LOW while it is hit, shielding or bursting and
     *  eases back up — the press-down that replaced the state ring. There is no halo. */
    public void draw(SpriteBatch batch, Ship s, float time) {
        if (blinking(s)) return;
        boolean dash = s.dashTime > 0;
        float y = drawnY(s);
        // the smear is a world-axis scale, so it rides a transform matrix: batch scale would
        // apply before the hull's rotation and smear along the nose instead of the push
        if (dash) batch.setTransformMatrix(dashDeform(s));
        drawPlume(batch, s, time);
        TextureRegion r = art.get(s.def.id, variation(s));
        if (r != null) {
            float deg = (float) Math.toDegrees(s.facing) - 90f;
            batch.setColor(1, 1, 1, dash ? 0.5f : 1f);
            batch.draw(r, s.x - SIZE / 2, y - SIZE / 2, SIZE / 2, SIZE / 2, SIZE, SIZE, 1, 1, deg);
            batch.setColor(1, 1, 1, 1);
        } else {
            // fallback: the polygon hull so the ship is never invisible when art is missing
            // Note: WorldRenderer.shipLayer() also draws this, but having it here ensures
            // the sprite batch pass also shows something when art is missing.
            float deg = (float) Math.toDegrees(s.facing) - 90f;
            batch.setColor(1, 1, 1, 0.8f);
            batch.draw(fallbackWhite, s.x - SIZE / 2, y - SIZE / 2, SIZE / 2, SIZE / 2, SIZE, SIZE, 1, 1, deg);
            batch.setColor(1, 1, 1, 1);
        }
        if (dash) batch.setTransformMatrix(IDENT);
    }

    /** Scale about the trailing edge, so the hull grows into the push direction. The smear rides
     *  the same curve as the burst: full from the first frame, eased out over the back half. */
    private Matrix4 dashDeform(Ship s) {
        float e = Ship.dashEase(s.dashTime);
        float stretch = 1 + (DASH_STRETCH - 1) * e;
        float squash = 1 - (1 - DASH_SQUASH) * e;
        float dx = s.dashDirX, dy = s.dashDirY;
        boolean vertical = Math.abs(dy) >= Math.abs(dx);
        float kx = vertical ? squash : stretch;
        float ky = vertical ? stretch : squash;
        float ax = s.x, ay = drawnY(s);
        if (vertical) ay += dy >= 0 ? -SIZE / 2 : SIZE / 2; // W up / S down
        else ax += dx >= 0 ? -SIZE / 2 : SIZE / 2;          // D right / A left
        return deform.idt().translate(ax, ay, 0).scale(kx, ky, 1).translate(-ax, -ay, 0);
    }

    /** Where the hull is actually DRAWN, which is not its centre while it is dipped. Everything
 *  that has to stay glued to the hull — the plume, the dash smear — reads this. */
    public static float drawnY(Ship s) {
        return s.y - DIP_PX * s.dip;
    }

    /** The point the plume is anchored to: on the hull's tail, at {@link #PLUME_ANCHOR} back
     *  along the exhaust axis, and lowered by the dip.
     *
     *  <p>The offset is applied INSIDE the rotation term — `centre - (cos, sin) * anchor` — so it
     *  turns with the ship. A separate screen-space nudge was the bug this replaced: subtracted
     *  outside the rotation it pushed the flame DOWN no matter which way the nose pointed, so on
     *  a downward-facing ship it shoved the plume back up into the hull instead of holding its
     *  place on the tail.
     *
     *  <p>Static and allocation-free so a headless test can pin it. Writes {x, y} into `out`. */
    public static void tailPoint(Ship s, float[] out) {
        float y = drawnY(s);
        out[0] = s.x - (float) Math.cos(s.facing) * PLUME_ANCHOR;
        out[1] = y - (float) Math.sin(s.facing) * PLUME_ANCHOR;
    }

    /** Engine plume from the pack's thrust frames: one of 4 power levels (thin on the tap, fat
     *  once the key is held) animated over 4 frames, tinted in the ship's colour — hue-shifted
     *  crimson and doubled in length while boosting. It hangs off the hull's tail at
     *  {@link #PLUME_ANCHOR} and turns with the nose, whatever the heading. */
    private void drawPlume(SpriteBatch batch, Ship s, float time) {
        if (s.thrustX == 0 && s.thrustY == 0) return;
        TextureRegion r = fx.thrust(level(s), (int) (time * 12) % 4);
        if (r == null) return;
        tailPoint(s, TAIL);
        float bx = TAIL[0], by = TAIL[1];
        float w = r.getRegionWidth() * PLUME_SCALE, h = r.getRegionHeight() * PLUME_SCALE * stretch(s);
        float deg = (float) Math.toDegrees(s.facing) - 90; // same turn as the hull, tip off the back
        float k = s.boosting ? 0.75f : 0f; // hue shift toward crimson, so boost reads as heat
        batch.setColor(mix(s.def.r, 0.92f, k), mix(s.def.g, 0.16f, k), mix(s.def.b, 0.22f, k), 0.9f);
        batch.draw(r, bx - w / 2, by - h, w / 2, h, w, h, 1, 1, deg);
        batch.setColor(1, 1, 1, 1);
    }

    private static final float[] TAIL = new float[2]; // scratch for tailPoint; read immediately

    /** Boost feels like more thrust: the plume doubles back along the exhaust axis. */
    static float stretch(Ship s) {
        return s.boosting ? 2f : 1f;
    }

    private static float mix(float base, float target, float k) {
        return base + (target - base) * k;
    }

    /** 0 on the first tap, then one level per step the longer the key stays down (0.1 / 0.3 /
     *  0.6 s), so a hold climbs through all four pack levels. Boost runs the same ramp —
     *  it changes hue and length, not the level. */
    static int level(Ship s) {
        float t = s.thrustHold;
        return t >= 0.6f ? 3 : t >= 0.3f ? 2 : t >= 0.1f ? 1 : 0;
    }

    /** Placeholder hull: cube + nose wedge + canopy, 24x24 to match the collision box.
     *  Drawn with the same dip offset as the sprite, so the fallback reads identically. */
    public void drawHull(ShapeRenderer shapes, Ship s) {
        if (blinking(s)) return;
        float deg = (float) Math.toDegrees(s.facing);
        float c = (float) Math.cos(s.facing), n = (float) Math.sin(s.facing);
        float px = -n * 11f, py = c * 11f; // perpendicular, one nose-half
        float cy = drawnY(s);

        shapes.setColor(s.def.r, s.def.g, s.def.b, s.dashTime > 0 ? 0.5f : 1f);
        shapes.rect(s.x - HALF, cy - HALF, HALF, HALF, Ship.W, Ship.H, 1, 1, deg);
        shapes.triangle(s.x + c * HALF, cy + n * HALF,
                s.x + c * HALF + px, cy + n * HALF + py,
                s.x + c * HALF - px, cy + n * HALF - py);

        shapes.setColor(1, 1, 1, s.dashTime > 0 ? 0.4f : 0.85f);
        shapes.rect(s.x - 5, cy - 5, 5, 5, 10, 10, 1, 1, deg);
    }

    /** Charge glow — drawn after the hull so it reads as light building inside it. */
    public void drawCharge(ShapeRenderer shapes, Ship s) {
        if (s.weapon.def.chargeTime <= 0 || s.weapon.chargeRatio() <= 0) return;
        if (blinking(s)) return;
        float r = s.weapon.chargeRatio();
        shapes.setColor(1, 1, 1, 0.25f + 0.65f * r);
        shapes.circle(s.x, s.y, 20 + 14 * r);
    }

    private static boolean blinking(Ship s) {
        return s.invuln > 0 && ((int) (s.invuln * 12) % 2 == 0);
    }

    public void dispose() {
        art.dispose();
    }
}
