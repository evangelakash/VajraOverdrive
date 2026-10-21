# The Armoury of Atala (the hangar)

The shop is dressed as the forge Maya built under Bala's rule. Same six sections and the same
keys as before; what changed is the palette, the words, and that every purchase now shows what
it does before it is bought. Screenshots are in `docs/shop-redesign/`.

| Section | What it shows |
|---|---|
| WAR TABLE | the hull, pattern and Astra in hand, what to buy next, and the Vajra's seven bands |
| HULLS | a hull grid, and a **radar** of the selected hull: six axes, the fully upgraded outline, and the hull you are flying |
| REFORGING | the upgrade matrix, and for the highlighted upgrade a before / after radar and a row per axis |
| WEAPONS | a table with a DPS column, and a card of bars, a spread fan and two seconds of fire |
| ASTRA FORGE, SHARDS | unchanged, retinted |

## The numbers (`ShopStats`, headless and tested)

Hull axes are real quantities from `Ship`: **hull** is hit points after armour
(`hull / (1 - damageReduction)`), **shield** is `50 + bonus`, **speed**, **agility**, **dash** is
dashes per second, **guns** is damage-mult / cooldown-mult. A radar ring is the best any hull
reaches fully upgraded, so the outlines always fit.

Weapon figures follow `Weapon`: a pull cycles at its cooldown, but a burst cannot cycle faster than
its own length and a charge shot cannot cycle faster than its charge (and pays double when full).
DPS and "per pull" include the flying hull's upgrades. Bars compare against the pattern in hand
(the lavender tick). `ShopStatsTest` pins all of it.

## Palette

Indigo ground, dark brass hairlines, saffron for selected / actionable, lotus cream text. Peacock
green is a gain and rose a loss, used only on numbers and charts.

## Glyphs

The HUD face (`courbe-sans`) has no `+`, `-`, `%`, middle dot or em dash. Changes are written as
ratios ("x1.35") and words ("hull up 25"); `ShopStatsTest.shopTextOnlyUsesGlyphsTheHudFaceDraws`
keeps new strings honest.

## Confirming a purchase

Every purchase asks first: `B` on a hull or upgrade, `N` or a click on a weapon, a click on a price
cell. A small plate comes up over the page with what it is, what it does, the price, and what is left
afterwards (`docs/shop-redesign/confirm-*.png`).

| Key / click | Does |
|---|---|
| ENTER or Y, or the BUY button | buys it |
| ESC, N or BACKSPACE, the CANCEL button, or a click outside the plate | cancels; the hangar stays open |

While the plate is up it owns every key, so TAB, X (sell) or a second B do nothing behind it. A
purchase that could not go through (too dear, locked, already owned) asks nothing and does nothing,
as before. Owned weapons still fit at once, since nothing is spent. `ShopConfirmTest` covers it.
