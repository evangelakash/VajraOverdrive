package vajra.screen;

import com.badlogic.gdx.math.Vector2;
import vajra.ship.ShipDef;
import vajra.world.Popups;
import vajra.world.World;

/** The hangar's purchase logic: what a pressed row actually DOES.
 *
 *  <p>All geometry, hit testing and every label live in {@link ShopUi} — this used to keep its
 *  own copy of the row coordinates, duplicated out of the draw code, and the two drifted. What is
 *  left here is the ledger: a pressed row turns into a call on the hangar, and the rules about
 *  what may be bought and when live in {@link vajra.ship.Hangar} where they can be tested
 *  headless.
 *
 *  <p>Nothing is persisted here any more — a run owns nothing on exit. */
public final class ShopController {
    private final World world;
    private final ShopUi ui;
    private boolean open;

    public ShopController(World world, ShopUi ui) {
        this.world = world;
        this.ui = ui;
    }

    /** Opens on what the run is actually flying. */
    public void open() {
        open = true;
        ui.syncTo(world.hangar);
    }

    public void close() {
        open = false;
        world.closeDocked();
    }

    public boolean isOpen() { return open; }
    public ShopUi ui() { return ui; }

    public boolean keyDown(int keycode) {
        return key(keycode);
    }

    private boolean key(int keycode) {
        int k = keycode;
        if (k == com.badlogic.gdx.Input.Keys.TAB) { ui.setTab(ui.tab() + 1); return true; }
        if (k >= com.badlogic.gdx.Input.Keys.NUM_1 && k <= com.badlogic.gdx.Input.Keys.NUM_7) {
            ui.setShip(k - com.badlogic.gdx.Input.Keys.NUM_1);
            world.selectShip(ui.ship()); // a no-op on a hull this run has not bought
            return true;
        }
        if (k == com.badlogic.gdx.Input.Keys.V) {
            // V is the talk key whenever a session lives: cycling the weapon here used to
            // open the microphone every time the pilot changed pattern.
            if (vajra.net.Net.session != null) return true;
            ui.setWeapon((ui.weapon() + 1) % vajra.weapon.WeaponDef.ALL.length);
            if (world.hangar.weaponOwned[ui.weapon()]) world.selectWeapon(ui.weapon());
            return true;
        }
        if (k == com.badlogic.gdx.Input.Keys.N) return buy(ui.weapon(), ShopUi.WEAPON);
        if (k == com.badlogic.gdx.Input.Keys.W) { ui.setTier(ui.tier() + 1); return true; }
        if (k == com.badlogic.gdx.Input.Keys.S) { ui.setTier(ui.tier() - 1); return true; }
        if (k == com.badlogic.gdx.Input.Keys.B) return buyTier();
        if (k == com.badlogic.gdx.Input.Keys.X) { world.hangar.sellShards(); return true; }
        return false;
    }

    public boolean click(Vector2 m) {
        if (!ui.click(m)) return false;
        switch (ui.hitRegion()) {
            case ShopUi.CLOSE: close(); return true;
            case ShopUi.SELL: world.hangar.sellShards(); return true;
            case ShopUi.HULL: world.selectShip(ui.ship()); return true;
            case ShopUi.TIER: return buyTier();
            case ShopUi.WEAPON: return buy(ui.weapon(), ShopUi.WEAPON);
                default: return true;
        }
    }

    public void hover(Vector2 m) {
        ui.hover(m);
    }

    /** Tier 0 is the hull itself; 1 and 2 are upgrades on it. Both are "B". */
    private boolean buyTier() {
        if (ui.tier() == 0) {
            int spent = world.hangar.buyShip(ui.ship(), world.lokaIndex);
            if (spent > 0) world.selectShip(ui.ship());
            return true;
        }
        world.hangar.buy(ShipDef.ALL[ui.ship()], ui.tier());
        return true;
    }

    /** One buy path for both the key and the click: unlocked patterns get fitted, the rest bought. */
    private boolean buy(int weapon, int region) {
        if (world.hangar.weaponOwned[weapon]) {
            world.selectWeapon(weapon);
            return true;
        }
        if (world.hangar.buyWeapon(weapon)) world.selectWeapon(weapon);
        return true;
    }

}