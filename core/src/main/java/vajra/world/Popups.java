package vajra.world;

/** FLOATING TEXT — score payments, combo tiers, notices. The old project's universal popup
 *  system (src_reboot v0.9.5), ported with rendering split off to WorldRenderer per the
 *  logic-never-imports-rendering rule: push(Spec) into a fixed ring pool (oldest overwritten,
 *  zero allocation in play) -> update() rises it and ages it out -> renderer draws each live
 *  slot with punch-in scale, an outline shadow and an end-of-life fade. */
public final class Popups {
    public static final int MAX = 64;
    public static final float LIFE = 1.3f;   // seconds on screen
    public static final float RISE = 35f;    // px/s upward drift
    public static final float INTRO = 0.12f; // punch-in window (0 = none)

    /** Fully-described popup; chainable for readability at the push sites. Fields are public:
     *  the renderer reads them straight off the pooled slots. `img` names a pre-rendered
     *  Devanagari phrase (PopupArt); text is the English line under it (or the whole popup
     *  when there is no image). Slots live as long as text != null. */
    public static final class Spec {
        public String text = "";
        public String img; // PopupArt key, or null for text-only
        public float x, y;
        public float r = 1, g = 1, b = 1;
        public float scale = 1;
        public float life = LIFE, lifeMax = LIFE;
        public float rise = RISE, intro = INTRO;

        public Spec text(String v) { text = v; return this; }
        public Spec img(String v) { img = v; return this; }
        public Spec at(float px, float py) { x = px; y = py; return this; }
        public Spec color(float cr, float cg, float cb) { r = cr; g = cg; b = cb; return this; }
        public Spec scale(float v) { scale = v; return this; }
        public Spec life(float v) { life = v; lifeMax = v; return this; }
        public Spec rise(float v) { rise = v; return this; }
        public Spec intro(float v) { intro = v; return this; }
    }

    private final Spec[] pool = new Spec[MAX];
    private int cursor;

    public Popups() {
        for (int i = 0; i < MAX; i++) {
            pool[i] = new Spec();
            pool[i].text = null; // empty slot = no popup
        }
    }

    /** Copies the spec into the next pooled slot (oldest overwritten when full). */
    public void push(Spec spec) {
        Spec slot = pool[cursor];
        cursor = (cursor + 1) % MAX;
        slot.text = spec.text;
        slot.img = spec.img;
        slot.x = spec.x;
        slot.y = spec.y;
        slot.r = spec.r;
        slot.g = spec.g;
        slot.b = spec.b;
        slot.scale = spec.scale;
        slot.life = spec.life;
        slot.lifeMax = spec.lifeMax;
        slot.rise = spec.rise;
        slot.intro = spec.intro;
    }

    /** Advances every live popup: rise + decay; dead ones free their slot. */
    public void update(float dt) {
        for (Spec s : pool) {
            if (s.text == null) continue;
            s.life -= dt;
            s.y += s.rise * dt;
            if (s.life <= 0f) s.text = null;
        }
    }

    /** Fade over the last third of life; punch-in eases 0.5x -> full over the intro window
     *  (easeOutCubic). Public: WorldRenderer paints, this stays logic. */
    public float alphaOf(Spec s) {
        return Math.min(1f, s.life / (s.lifeMax * 0.35f));
    }

    public float punchOf(Spec s) {
        float t = s.intro > 0f ? Math.min(1f, (s.lifeMax - s.life) / s.intro) : 1f;
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        return s.scale * (0.5f + 0.5f * ease);
    }

    /** Live slots for the renderer's iteration. */
    public Spec[] slots() {
        return pool;
    }

    public int activeCount() {
        int n = 0;
        for (Spec s : pool) if (s.text != null) n++;
        return n;
    }

    /** True while any popup is alive (test/inspection helper). */
    public boolean anyActive() {
        return activeCount() > 0;
    }
}
