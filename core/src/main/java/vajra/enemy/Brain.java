package vajra.enemy;

import vajra.world.Effect;
import vajra.world.TileMap;
import vajra.world.World;

/** Enemy brain. One step: pick a desired velocity by role, then bend it around rock,
 *  mates and the leash. Writes e.vx/e.vy only — Enemy integrates and collides.
 *  Predefined rules for everyone, improvisation by kind. */
// ponytail: slot + separation scans are O(n) per unit, call-to-arms O(n) per hit — fine at the
// 165-unit roster (~50k/frame); go index loops / a spatial grid if the roster passes ~300.
public class Brain {
    private static final float WHISKER = 46;
    private static final float MATE = 46;
    private static final float LEASH = 300;   // FORMATIONS: no further than 300px of the slot
    private static final float RANK_BOOST = 1.15f; // rank-1 leaders and elites run 15% hotter
    private static final float GAIN = 2.2f;   // closing gain on the orbit point
    private static final float TURN = 6f;     // velocity steering rate
    private static final float ROUT_NEUTRAL = 2.5f; // FORMATIONS: flee 2.5s, then regroup
    private static final float ROUT_SPEED = 180;    // FORMATIONS: running has to read as running
    private static final float FIND = 700;    // regroup: senior search radius, px
    private static final float SLOT_MIN = 70; // formation ring never crowds tighter than this
    private static final float SPIN = 0.4f;   // formation ring rotation, rad/s
    private static final float WOUND = 0.3f;  // wound response kicks in below this hp fraction
    private static final float[][] PASSAGES = { // arena mouths: a stood-down unit walks out one
            { 2600, 20450 }, { 2600, 17700 }, { 5050, 19100 }, { 150, 19100 } };

    public static void think(Enemy e, World w, float dt, float t) {
        vajra.ship.Ship hull = w.nearestHull(e.x, e.y);
        float dx = hull.x - e.x, dy = hull.y - e.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        e.facing = (float) Math.atan2(dy, dx);
        if (e.commander != null && e.commander.dead && !e.routed) {
            e.routed = true;
            e.routAge = 0; // a stale age from an earlier rout would skip the flee entirely
        }
        boolean wounded = e.hp < e.maxHp * WOUND; // WOUND RESPONSE: beaten units change tactics
        boolean berserk = wounded && (e.kind == Enemy.Kind.VAKRA || e.kind == Enemy.Kind.BHAVI);
        // tier cruise: every kind has its own speed, scaled by rank and by the wounded berserk
        float cruise = e.speed * (e.rank > 0 ? RANK_BOOST : 1f) * (berserk ? 1.5f : 1f);

        float gx = 0, gy = 0; // desired velocity, px/s
        if (e.dismissed) {
            // STAND-DOWN: the commander waved us off — walk out the nearest arena passage,
            // then retire with a gold ring so the roster does not keep ghosts
            e.dismissAge += dt;
            float px = 2600, py = 17700; // south mouth (closest to the pit on average)
            for (float[] p : PASSAGES) {
                float qx = p[0] - e.x, qy = p[1] - e.y, rx = px - e.x, ry = py - e.y;
                if (qx * qx + qy * qy < rx * rx + ry * ry) {
                    px = p[0];
                    py = p[1];
                }
            }
            float qx = px - e.x, qy = py - e.y;
            float qd = (float) Math.sqrt(qx * qx + qy * qy);
            if (qd > 1) {
                gx = qx / qd * ROUT_SPEED;
                gy = qy / qd * ROUT_SPEED;
            }
            if (e.dismissAge > 6) {
                e.dead = true;
                w.addEffect(Effect.ring(e.x, e.y, 70, 1f, 0.84f, 0.35f));
            }
        } else if (e.hunt) {
            // CACHE AMBUSH: converge straight on the ship — no ring, no leash, no orbit
            if (dist > 1) {
                gx = dx / dist * cruise;
                gy = dy / dist * cruise;
            }
        } else if (e.routed) {
            // rout: run from the dead commander's post for a beat, then regroup
            e.routAge += dt;
            if (e.routAge > ROUT_NEUTRAL) {
                e.commander = findSenior(e, w);
                if (e.commander == null) { // no senior in sight: take its own spawn post back
                    e.homeX = e.spawnX;
                    e.homeY = e.spawnY;
                }
                e.routed = false;
            } else {
                // run from the dead commander's post — normalized, so running always reads as
                // running; a unit caught dead on the post breaks away from the player instead
                float rx = e.x - e.commander.homeX, ry = e.y - e.commander.homeY;
                float rd = (float) Math.sqrt(rx * rx + ry * ry);
                if (rd < 1) {
                    rx = -dx;
                    ry = -dy;
                    rd = dist;
                }
                if (rd > 1) {
                    gx = rx / rd * ROUT_SPEED;
                    gy = ry / rd * ROUT_SPEED;
                }
            }
        }
        if (!e.routed && !e.dismissed && !e.hunt) {
            // where we belong: sentry post, a formation ring slot, or the patrol point
            float tx, ty;
            if (e.sentry && !(wounded && e.kind == Enemy.Kind.LANCE)) {
                tx = e.homeX;
                ty = e.homeY;
            } else if (e.commander != null && !e.commander.dead) {
                e.homeX = e.commander.homeX; // the post of a formation unit IS its leader's post:
                e.homeY = e.commander.homeY; // leash and ring then never pull against each other
                int n = 0, slot = 0; // FORMATIONS: slots spread by the living member count
                for (Enemy o : w.enemies) {
                    if (o.dead || o.commander != e.commander) continue;
                    if (o == e) slot = n;
                    n++;
                }
                float a = t * SPIN + (float) (slot * 2 * Math.PI / Math.max(1, n));
                // the boss's ring stands well clear of his 96 px hull
                float r = e.commander.boss ? 300
                        : Math.max(SLOT_MIN, n * MATE / (float) (2 * Math.PI));
                tx = e.commander.x + (float) Math.cos(a) * r;
                ty = e.commander.y + (float) Math.sin(a) * r;
            } else {
                float a = t * e.orbitRate + e.phase;
                tx = e.homeX + (float) Math.cos(a) * e.patrolAmp;
                ty = e.homeY + (float) Math.sin(a) * e.patrolAmp;
            }
            gx = (tx - e.x) * GAIN;
            gy = (ty - e.y) * GAIN;
            // flair off while recovering from a knock; wounded sentries are allowed to break cover
            if (e.recover <= 0 && (!e.sentry || wounded)) {
                switch (e.kind) {
                    case AURIS: // kite: back off when crowded, never close (wounded from much further)
                        if (dist < (wounded ? 520 : 240) && dist > 1) {
                            gx -= dx / dist * 300;
                            gy -= dy / dist * 300;
                        }
                        break;
                    case LANCE: // wounded shooters break rank and back off the player
                    case HATAKA:
                    case MAYAVI:
                        if (wounded && dist < 500 && dist > 1) {
                            gx -= dx / dist * 300;
                            gy -= dy / dist * 300;
                        }
                        break;
                    case VAKRA: // weaves harder the closer you are (wounded weaves wider)
                        if (dist < (wounded ? 560 : 420)) {
                            gx += (float) Math.sin(t * 2.6f + e.phase) * 220;
                            gy += (float) Math.cos(t * 2.1f + e.phase) * 220;
                        }
                        break;
                    case BHAVI: // edges into frontal range (wounded berserks in from further)
                        if (dist < (wounded ? 700 : 520) && dist > 1) {
                            gx += dx / dist * 340;
                            gy += dy / dist * 340;
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        // where we actually intend to go — the whiskers look that way, not at the player
        float gs = (float) Math.sqrt(gx * gx + gy * gy);
        float fx, fy;
        if (gs > 1) {
            fx = gx / gs;
            fy = gy / gs;
        } else if (dist > 1) {
            fx = dx / dist;
            fy = dy / dist;
        } else {
            fx = (float) Math.cos(e.facing);
            fy = (float) Math.sin(e.facing);
        }
        // wall whiskers: probe where we are going, shy away from rock
        boolean ahead = w.blockedPx(e.x + fx * WHISKER, e.y + fy * WHISKER);
        boolean left = w.blockedPx(e.x + (fx * 0.87f - fy * 0.5f) * WHISKER, e.y + (fy * 0.87f + fx * 0.5f) * WHISKER);
        boolean right = w.blockedPx(e.x + (fx * 0.87f + fy * 0.5f) * WHISKER, e.y + (fy * 0.87f - fx * 0.5f) * WHISKER);
        if (ahead) {
            gx -= fx * 170;
            gy -= fy * 170;
            if (!left) {
                gx += -fy * 140;
                gy += fx * 140;
            } else if (!right) {
                gx += fy * 140;
                gy += -fx * 140;
            }
        } else {
            if (left) {
                gx += fy * 70;
                gy += -fx * 70;
            }
            if (right) {
                gx += -fy * 70;
                gy += fx * 70;
            }
        }

        // separation: mates keep MATE px apart — more from a 96 px hull
        for (Enemy o : w.enemies) {
            if (o == e || o.dead) continue;
            float need = (o.boss || e.boss) ? 170 : MATE;
            float mx = e.x - o.x, my = e.y - o.y;
            float md2 = mx * mx + my * my;
            if (md2 < need * need && md2 > 0.01f) {
                float md = (float) Math.sqrt(md2);
                gx += mx / md * (need - md) * 4f;
                gy += my / md * (need - md) * 4f;
            }
        }

        // leash: never stray past LEASH px of home. Routed units have left their post for good
        // and hunters are on a job — neither may be dragged back by the post.
        float hx = e.x - e.homeX, hy = e.y - e.homeY;
        float hd = (float) Math.sqrt(hx * hx + hy * hy);
        if (!e.routed && !e.dismissed && !e.hunt && hd > LEASH) {
            gx -= hx / hd * (hd - LEASH) * 4f;
            gy -= hy / hd * (hd - LEASH) * 4f;
        }

        gs = (float) Math.sqrt(gx * gx + gy * gy);
        float max = e.dismissed ? ROUT_SPEED
                : e.routed && e.routAge <= ROUT_NEUTRAL ? ROUT_SPEED
                : cruise; // tier cruise for everyone else, hunt included
        if (gs > max) {
            gx = gx / gs * max;
            gy = gy / gs * max;
        }
        float k = 1 - (float) Math.exp(-TURN * dt);
        e.vx += (gx - e.vx) * k;
        e.vy += (gy - e.vy) * k;
    }

    /** Nearest living outranking unit inside the finding radius — where a broken court re-forms.
     *  Rock-blind candidates are skipped: a ring behind a ridge would fight the whiskers forever. */
    private static Enemy findSenior(Enemy e, World w) {
        Enemy best = null;
        float bestD = FIND * FIND;
        for (Enemy o : w.enemies) {
            if (o == e || o.dead || o.rank <= e.rank) continue;
            float ox = o.x - e.x, oy = o.y - e.y, od = ox * ox + oy * oy;
            if (od < bestD && w.clearLine(e.x, e.y, o.x, o.y)) {
                bestD = od;
                best = o;
            }
        }
        return best;
    }
}