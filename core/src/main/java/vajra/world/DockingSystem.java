package vajra.world;

import vajra.audio.Sfx;

import vajra.ship.Ship;

/** Shop docking: enter the bay, hold, dock, then park until the hull flies out. Extracted from
 *  World, which still owns the shop positions and state arrays for rendering and tests. */
public final class DockingSystem {
    private static final float BAY_X0 = -16, BAY_X1 = 20, BAY_Y0 = -64, BAY_Y1 = -28;
    private static final float EPS = 0.05f; // a hull pressed flush against a wall still counts as in

    private final World world;

    public DockingSystem(World world) {
        this.world = world;
    }

    /** True when the WHOLE hull sits inside the marked bay under shop i. */
    public boolean inDockBox(int i, float x, float y) {
        float sx = world.shops[i][0], sy = world.shops[i][1];
        return x - Ship.W / 2 >= sx + BAY_X0 - EPS && x + Ship.W / 2 <= sx + BAY_X1 + EPS
                && y - Ship.H / 2 >= sy + BAY_Y0 - EPS && y + Ship.H / 2 <= sy + BAY_Y1 + EPS;
    }

    /** Dock state per shop: enter to start, leave mid-way to cancel, hold to finish, close parks
     *  it until the ship flies out. */
    public void update(float dt) {
        for (int i = 0; i < world.shops.length; i++) {
            boolean in = inDockBox(i, world.ship.x, world.ship.y);
            switch (world.shopState[i]) {
                case World.UNDOCKED:
                    if (in) {
                        world.shopState[i] = World.DOCKING;
                        world.shopTimer[i] = 0;
                        world.sound(Sfx.DOCK);
                    }
                    break;
                case World.DOCKING:
                    if (!in) {
                        world.shopState[i] = World.UNDOCKED;
                        break;
                    }
                    world.shopTimer[i] += dt;
                    if (world.shopTimer[i] >= World.DOCK_TIME) {
                        world.shopTimer[i] = World.DOCK_TIME;
                        world.shopState[i] = World.DOCKED;
                    }
                    break;
                case World.SITTING:
                    if (!in) world.shopState[i] = World.UNDOCKED;
                    break;
                default:
                    break; // DOCKED: waits for the screen to open, then closeDocked()
            }
        }
    }

    /** A docking just finished — the screen opens the hangar on this. */
    public boolean shopReady() {
        for (int s : world.shopState) if (s == World.DOCKED) return true;
        return false;
    }

    /** Closing the hangar parks that bay as SITTING, so it cannot reopen until you fly out. */
    public void closeDocked() {
        for (int i = 0; i < world.shopState.length; i++)
            if (world.shopState[i] == World.DOCKED) world.shopState[i] = World.SITTING;
    }
}
