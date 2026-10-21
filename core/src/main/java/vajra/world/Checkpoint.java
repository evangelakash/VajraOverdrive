package vajra.world;

/** One per region: a layered marker — pink collector at the back, gold globe over it, gold frame
 *  on top, all three concentric. Touching it (through the stack) spends it: what remains dims to
 *  30%, and the spot becomes the respawn anchor until a later checkpoint is taken. */
public class Checkpoint {
    public static final float TOUCH_R = 48; // ship centre to stack centre; scales with the 2× stack
    public static final float DIM = 0.3f;

    public final float x, y;
    public boolean collected;

    public Checkpoint(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public boolean touches(float px, float py) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy <= TOUCH_R * TOUCH_R;
    }

    public float alpha() {
        return collected ? DIM : 1f;
    }
}
