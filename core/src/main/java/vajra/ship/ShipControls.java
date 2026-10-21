package vajra.ship;

/** Ship input, sampled once per frame. Logic-side view of keyboard + mouse; GameScreen implements it.
 *  Movement is screen-relative and reported in screen axes: up-positive Y, which is also the
 *  world's up (the world is y-up), so Ship applies it directly. */
public interface ShipControls {
    float thrustY();    // +1 up (W / UP), -1 down (S / DOWN)
    float thrustX();    // +1 right (D / RIGHT), -1 left (A / LEFT)
    float rotate();     // +1 counter-clockwise (Q), -1 clockwise (E); thrust never turns the ship
    boolean fire();     // SPACE or mouse LMB held
    boolean boost();    // SHIFT held
    boolean dash();     // C held (edge-detected in Ship)
    boolean shield();   // F or mouse RMB held
    boolean mouseAim(); // LMB held: the nose tracks the cursor instead of travel
    float aimX();       // cursor in world coords
    float aimY();

    /** The hands-off pilot: nothing pressed, nothing asked. Used by the headless gates that
     *  only need a hull to exist and a world to turn. */
    ShipControls NONE = new ShipControls() {
        public float thrustY() { return 0; }
        public float thrustX() { return 0; }
        public float rotate() { return 0; }
        public boolean fire() { return false; }
        public boolean boost() { return false; }
        public boolean dash() { return false; }
        public boolean shield() { return false; }
        public boolean mouseAim() { return false; }
        public float aimX() { return 0; }
        public float aimY() { return 0; }
    };
}
