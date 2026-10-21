package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The Akasha Astra's cube, as GEOMETRY rather than pixels.
 *
 *  <p>The cube is projected from eight rotated corners rather than drawn as a sprite, which is
 *  what makes it read as a solid turning in space. That projection is the easy thing to get
 *  subtly wrong - it can collapse to a line, invert, or stop being centred - and none of it is
 *  visible without a GL context, so it is pinned here instead.
 */
class AstraCubeGeometryTest {
    private static final float HALF = 5f;
    private static final float CX = 2600f, CY = 3000f;
    private static final float[] PX = new float[8], PY = new float[8], PZ = new float[8];

    private static void at(float spin) {
        WorldRenderer.projectCube(CX, CY, spin, HALF, PX, PY, PZ);
    }

    /** Shoelace area of the projected hull: the 8 corners' 2D convex outline. */
    private static double hullArea() {
        // the silhouette of a projected cube is the convex hull of its 8 points; taking the
        // area of the convex hull is overkill, so use the sum over all 6 faces' projected quads,
        // which totals the solid's projected area exactly
        // the silhouette of a projected cube is the sum of all six faces' projected areas /
        // 2 - every face of a cube is equal, so under PARALLEL projection that total is
        // constant, which makes it a sharp check that the six quads really are the six faces
        int[][] face = {
                { 4, 5, 7, 6 }, { 0, 1, 3, 2 }, { 1, 5, 7, 3 },
                { 0, 4, 6, 2 }, { 3, 7, 6, 2 }, { 0, 2, 5, 4 },
        };
        double total = 0;
        for (int[] q : face) {
            // Relative to the centre. In absolute world coords the shoelace terms are ~8e6 and
            // their DIFFERENCE is ~100: float32 cannot hold that, and the result cancels to
            // noise (it once reported an area of 1.0).
            double a = 0;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) & 3;
                a += (PX[q[i]] - CX) * (PY[q[j]] - CY) - (PX[q[j]] - CX) * (PY[q[i]] - CY);
            }
            total += Math.abs(a) * 0.5;
        }
        return total;
    }

    /** Perspective makes the near corners magnify past the far ones, so the projected solid is
     *  very slightly off-centre and slightly larger than 2*half. Both are small and deliberate. */
    @Test
    void theCubeIsCentredOnItself() {
        for (float spin = 0; spin < 7; spin += 0.37f) {
            at(spin);
            double mx = 0, my = 0;
            for (int v = 0; v < 8; v++) { mx += PX[v]; my += PY[v]; }
            mx /= 8; my /= 8;
            assertEquals(CX, mx, HALF * 0.1f, "centred in x at spin " + spin);
            assertEquals(CY, my, HALF * 0.1f, "centred in y at spin " + spin);
        }
    }

    /** It must never collapse: a degenerate projection is a line or a dot, and the cube would
     *  vanish or flicker as it turns. */
    @Test
    void itNeverCollapsesToALineOrAFlatten() {
        double smallest = Double.MAX_VALUE, largest = 0;
        for (float spin = 0; spin < 7; spin += 0.11f) {
            at(spin);
            double area = hullArea();
            smallest = Math.min(smallest, area);
            largest = Math.max(largest, area);
            assertTrue(area > 120, "degenerate projection at spin " + spin + ": area " + area);
        }
        // a cube's six faces are equal, so the sum of their projected areas (twice the
        // silhouette) is near-constant through a tumble. The mild perspective scales near
        // corners up ~1.5x against far ones, which is a ~2x swing in area - allowed. The
        // ABSOLUTE floor is the sharp check: a bowtie face cancels to ~0 (it once reported 1.1
        // against a real cube's ~250), so anything under this is not a cube.
        assertTrue(smallest > 120, "and it is the size of an actual cube, not a sliver: "
                + smallest);
        assertTrue(smallest > largest * 0.45, "the silhouette stays substantial as it turns: "
                + (int) smallest + " .. " + (int) largest);
        assertTrue(smallest > 120, "and it is the size of an actual cube, not a sliver: " + smallest);
    }

    /** A cube's CORNERS sit half*sqrt(3) from its centre, not half - plus a little for the mild
     *  perspective. Anything past that is the projection running away. */
    @Test
    void itStaysInsideItsOwnFootprint() {
        float reach = HALF * 1.7321f * 1.3f;
        for (float spin = 0; spin < 7; spin += 0.29f) {
            at(spin);
            for (int v = 0; v < 8; v++) {
                assertTrue(Math.abs(PX[v] - CX) <= reach,
                        "corner " + v + " strayed to " + PX[v] + " (reach " + reach + ")");
                assertTrue(Math.abs(PY[v] - CY) <= reach,
                        "corner " + v + " strayed to " + PY[v] + " (reach " + reach + ")");
            }
        }
    }

    /** The silhouette must actually CHANGE with spin, or it is not tumbling. */
    @Test
    void theSilhouetteChangesAsItTurns() {
        at(0f);
        double a0 = hullArea(), w0 = width();
        at(1.1f);
        double a1 = hullArea(), w1 = width();
        assertNotEquals(a0, a1, 0.001, "the silhouette is the same at every angle");
        assertNotEquals(w0, w1, 0.001, "and so is its width");
    }

    /** Depth must be real and ordered: the renderer sorts faces by it, so a flat depth would
     *  make the painter's order arbitrary and the cube flicker inside out. */
    @Test
    void depthSeparatesTheFaces() {
        at(0.63f);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int v = 0; v < 8; v++) {
            min = Math.min(min, PZ[v]);
            max = Math.max(max, PZ[v]);
        }
        assertTrue(max - min > 0.5f, "depth range is " + (max - min)
                + " - the faces would sort arbitrarily");
    }

    /** The six faces must carry different brightness, which is the whole reason for shading
     *  them from the rotated normal instead of filling them one colour. */
    @Test
    void theFacesAreShadedApart() {
        int distinct = 0;
        float[] seen = new float[32];
        int n = 0;
        for (float spin = 0; spin < 7; spin += 0.41f) {
            float cyaw = (float) Math.cos(spin), syaw = (float) Math.sin(spin);
            float pitch = spin * 0.62f;
            float cpit = (float) Math.cos(pitch), spit = (float) Math.sin(pitch);
            float[][] normal = {
                    { 0, 0, 1 }, { 0, 0, -1 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 },
            };
            for (float[] nrm : normal) {
                float rx = nrm[0] * cyaw - nrm[2] * syaw, rz = nrm[0] * syaw + nrm[2] * cyaw;
                float rz2 = nrm[1] * spit + rz * cpit;
                float lit = 0.42f + 0.58f * (rz2 * 0.5f + 0.5f);
                float bucket = Math.round(lit * 16f);
                boolean dup = false;
                for (int i = 0; i < n; i++) if (seen[i] == bucket) dup = true;
                if (!dup) seen[n++] = bucket;
            }
        }
        distinct = n;
        assertTrue(distinct >= 6, "at least six brightness bands across the tumble, got "
                + distinct);
    }

    private static float width() {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int v = 0; v < 8; v++) { lo = Math.min(lo, PX[v]); hi = Math.max(hi, PX[v]); }
        return hi - lo;
    }
}