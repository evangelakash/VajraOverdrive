package vajra.weapon;

/** Firing-pattern data. Every weapon is a row here — patterns are data, not classes.
 *  "Reload" is the cooldown stat; magazine + reload is a later mechanic if wanted. */
public class WeaponDef {
    public String name;
    public int count = 1;        // projectiles per shot
    public int burst = 1;        // shots per trigger pull
    public float spreadDeg;      // total spread across count
    public float burstGap;       // s between burst shots
    public float cooldown = 0.3f;
    public float speed = 700;
    public float damage = 10;
    public float bulletSize = 5;
    public float life = 2.5f;
    public float chargeTime;     // 0 = instant fire
    public float range;          // beam range
    public float aoeRadius;
    public float homingTurn;     // deg/s
    public float aimAssistDeg;
    public boolean beam;
    public boolean aimAssist;
    public boolean pierceEnemies;
    public boolean pierceWalls;
    public float r = 1f, g = 0.9f, b = 0.3f;
    public int cost; // coins to unlock in the hangar; 0 = comes with the hull
    public int idx;  // its row in ALL, stamped below: the hangar labels rows by index

    private WeaponDef() {}

    public static WeaponDef mk(String name) {
        WeaponDef d = new WeaponDef();
        d.name = name;
        return d;
    }

    public WeaponDef count(int v) { count = v; return this; }
    public WeaponDef burst(int v) { burst = v; return this; }
    public WeaponDef spreadDeg(float v) { spreadDeg = v; return this; }
    public WeaponDef burstGap(float v) { burstGap = v; return this; }
    public WeaponDef cooldown(float v) { cooldown = v; return this; }
    public WeaponDef speed(float v) { speed = v; return this; }
    public WeaponDef damage(float v) { damage = v; return this; }
    public WeaponDef bulletSize(float v) { bulletSize = v; return this; }
    public WeaponDef life(float v) { life = v; return this; }
    public WeaponDef chargeTime(float v) { chargeTime = v; return this; }
    public WeaponDef range(float v) { range = v; return this; }
    public WeaponDef aoeRadius(float v) { aoeRadius = v; return this; }
    public WeaponDef homingTurn(float v) { homingTurn = v; return this; }
    public WeaponDef aimAssistDeg(float v) { aimAssistDeg = v; return this; }
    public WeaponDef beam() { beam = true; return this; }
    public WeaponDef aimAssist() { aimAssist = true; return this; }
    public WeaponDef pierceEnemies() { pierceEnemies = true; return this; }
    public WeaponDef pierceWalls() { pierceWalls = true; return this; }
    public WeaponDef cost(int v) { cost = v; return this; }
    public WeaponDef colour(float r, float g, float b) { this.r = r; this.g = g; this.b = b; return this; }

    // Price ladder: DPS rises with cost. Every weapon above 200c must beat the
    // cheaper options in raw output — no weapon is a raw downgrade. Atala is the
    // easy loka; these numbers are the baseline that LokaTable.dmgScale grades
    // up for deeper lokas.
    public static final WeaponDef SINGLE = mk("Single Bolt"); // free — the starter
    public static final WeaponDef DOUBLE = mk("Double Bolt").count(2).spreadDeg(10f).cooldown(0.3f).damage(10).cost(90);
    public static final WeaponDef BURST1 = mk("Burst").burst(3).burstGap(0.09f).cooldown(0.5f).speed(760).damage(10).bulletSize(4).cost(120);
    public static final WeaponDef BURST2 = mk("Twin Burst").count(2).spreadDeg(8f).burst(2).burstGap(0.1f).cooldown(0.55f).speed(740).damage(10).bulletSize(4).cost(170);
    public static final WeaponDef SPREAD = mk("Buckshot").count(5).spreadDeg(45f).cooldown(0.5f).speed(660).damage(8).bulletSize(4).cost(110).colour(1f, 0.85f, 0.4f);
    public static final WeaponDef HEAVY = mk("Heavy Shell").cooldown(0.5f).speed(520).damage(36).bulletSize(10).life(3f).cost(180).colour(1f, 0.45f, 0.15f);
    public static final WeaponDef LIGHT = mk("Light MG").cooldown(0.07f).speed(820).damage(4).bulletSize(3).cost(80).colour(0.4f, 0.9f, 1f);
    public static final WeaponDef CHARGE = mk("Charge Shot").cooldown(0.18f).speed(900).damage(16).bulletSize(7).chargeTime(0.8f).cost(220).colour(0.9f, 0.7f, 1f);
    public static final WeaponDef LASER = mk("Laser").beam().cooldown(0.6f).damage(42).range(900f).cost(320).colour(0.6f, 1f, 0.8f);
    public static final WeaponDef BOMB = mk("Bomb").cooldown(0.7f).speed(330).damage(34).bulletSize(12).aoeRadius(90f).life(3f).cost(260).colour(1f, 0.5f, 0.2f);
    public static final WeaponDef MISSILE = mk("Missile").cooldown(0.45f).speed(420).damage(24).bulletSize(6).homingTurn(160f).life(4f).cost(240).colour(1f, 0.3f, 0.3f);
    public static final WeaponDef TRACKING = mk("Tracking Missiles").count(2).spreadDeg(14f).cooldown(0.6f).speed(440).damage(18).bulletSize(6).homingTurn(380f).life(4f).cost(380).colour(0.8f, 0.4f, 1f);
    public static final WeaponDef AIM = mk("Aim Shot").cooldown(0.18f).speed(840).damage(14).aimAssist().aimAssistDeg(20f).cost(150).colour(1f, 1f, 1f);
    public static final WeaponDef PIERCE = mk("Piercer").cooldown(0.28f).speed(780).damage(18).pierceEnemies().cost(140).colour(0.5f, 1f, 0.4f);
    public static final WeaponDef THROUGH = mk("Wallbore").cooldown(0.35f).speed(720).damage(14).pierceWalls().cost(200).colour(0.5f, 0.7f, 1f);

    public static final WeaponDef[] ALL = {
            SINGLE, DOUBLE, BURST1, BURST2, SPREAD, HEAVY, LIGHT, CHARGE,
            LASER, BOMB, MISSILE, TRACKING, AIM, PIERCE, THROUGH
    };

    static {
        for (int i = 0; i < ALL.length; i++) ALL[i].idx = i;
    }
}
