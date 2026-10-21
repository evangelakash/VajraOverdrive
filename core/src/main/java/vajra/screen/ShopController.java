package vajra.screen;

import com.badlogic.gdx.math.Vector2;
import vajra.ship.Hangar;
import vajra.ship.ShipDef;
import vajra.weapon.WeaponDef;
import vajra.world.LokaTable;
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
    /** The purchase waiting on a yes. While it is set the hangar answers only to the modal. */
    private ShopUi.Confirm pending;

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
        pending = null;
        ui.setConfirm(null);
        world.closeDocked();
    }

    public boolean isOpen() { return open; }

    /** Is a purchase waiting on a yes? GameScreen asks, so ESC cancels it and does not close the shop. */
    public boolean confirming() { return pending != null; }
    public ShopUi ui() { return ui; }

    public boolean keyDown(int keycode) {
        return key(keycode);
    }

    private boolean key(int keycode) {
        int k = keycode;
        if (pending != null) { // the modal owns every key: a purchase is only ever one yes away
            if (k == com.badlogic.gdx.Input.Keys.ENTER || k == com.badlogic.gdx.Input.Keys.Y) answer(true);
            else if (k == com.badlogic.gdx.Input.Keys.ESCAPE || k == com.badlogic.gdx.Input.Keys.N
                    || k == com.badlogic.gdx.Input.Keys.BACKSPACE) answer(false);
            return true;
        }
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
        if (pending != null) {
            int hit = ui.confirmHit(m);
            if (hit == ShopUi.CONFIRM_BUY) answer(true);
            else if (hit != ShopUi.CONFIRM_INSIDE) answer(false); // CANCEL, or a click outside the card
            return true;
        }
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
        if (pending != null) ui.confirmHover(m);
        else ui.hover(m);
    }

    // ---- buying: every purchase is asked for first -----------------------------------------

    /** Tier 0 is the hull itself; 1 and 2 are upgrades on it. Both are "B". */
    private boolean buyTier() {
        return ask(confirmTier(world.hangar, world.lokaIndex, ui.ship(), ui.tier()));
    }

    /** One buy path for both the key and the click: owned patterns get fitted at once (nothing
     *  is spent), the rest are asked for. */
    private boolean buy(int weapon, int region) {
        if (world.hangar.weaponOwned[weapon]) {
            world.selectWeapon(weapon);
            return true;
        }
        return ask(confirmWeapon(world.hangar, weapon));
    }

    /** Raise the modal for a purchase that would really go through; a purchase that would not
     *  (too dear, locked, already owned) does nothing, exactly as before. */
    private boolean ask(ShopUi.Confirm c) {
        if (c != null) {
            pending = c;
            ui.setConfirm(c);
        }
        return true;
    }

    private void answer(boolean yes) {
        ShopUi.Confirm c = pending;
        pending = null;
        ui.setConfirm(null);
        if (!yes || c == null) return;
        switch (c.kind) {
            case ShopUi.Confirm.HULL:
                if (world.hangar.buyShip(c.ship, world.lokaIndex) > 0) world.selectShip(c.ship);
                break;
            case ShopUi.Confirm.TIER:
                world.hangar.buy(ShipDef.ALL[c.ship], c.tier);
                break;
            default:
                if (world.hangar.buyWeapon(c.weapon)) world.selectWeapon(c.weapon);
        }
    }

    /** The question for a hull or an upgrade, or null when the purchase would not go through.
     *  Pure: it reads the hangar and changes nothing, so the tests can pin it headless. */
    static ShopUi.Confirm confirmTier(Hangar h, int loka, int ship, int tier) {
        if (ship < 0 || ship >= ShipDef.ALL.length) return null;
        ShipDef d = ShipDef.ALL[ship];
        if (tier == 0) {
            if (h.ownsShip(ship) || !LokaTable.shipUnlocked(loka, ship) || h.coins < d.cost) return null;
            return ShopUi.Confirm.hull(d, d.cost, h.coins - d.cost);
        }
        if (tier < 1 || tier > 2 || !h.ownsShip(ship) || h.owned[ship][tier]) return null;
        ShipDef.Upgrade u = d.upgrades[tier];
        int shards = tier == 2 ? Hangar.SHARD_COST : 0;
        if (h.coins < u.cost || h.shardCount() < shards) return null;
        return ShopUi.Confirm.tier(d, tier, u.cost, shards, h.coins - u.cost, h.shardCount() - shards);
    }

    /** The question for a pattern, or null when it is owned or out of reach. */
    static ShopUi.Confirm confirmWeapon(Hangar h, int weapon) {
        if (weapon < 0 || weapon >= WeaponDef.ALL.length || h.weaponOwned[weapon]) return null;
        WeaponDef w = WeaponDef.ALL[weapon];
        if (h.coins < w.cost) return null;
        return ShopUi.Confirm.weapon(h, w, h.coins - w.cost);
    }
}
