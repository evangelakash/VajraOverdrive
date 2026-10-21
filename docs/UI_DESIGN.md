# The pothi and the dial: the menu UI

Every non-game screen (title, settings, controls, profile, pause, lobby, netplay, the shop) is one
design: **a manuscript read through an instrument**. The book is the Bhagavata Purana as it was
copied, on bark and palm leaf. The instrument is modern and a little sci-fi: a ruler scale, reticle
brackets, a dial, a table of figures. Both are set in the colours of soil, never festival colours.
Screenshots: `docs/ui-redesign/`.

## Where it comes from

- **The Bhagavata itself.** Its fifth book describes the seven nether worlds, Atala to Patala. They
  are this game's seven lokas, so the title screen carries a gauge of them (Atala marked OPEN, the
  rest sealed) and the soil along the foot of every screen is seven strata, one per loka. Its
  eighth book tells of Vamana and Trivikrama, who broke the Vajra.
- **The old book.** Palm-leaf and bark manuscripts are long narrow leaves with a line of ink
  incised along the grain and a hole near each end; the leaves are bundled on a cord strung through
  the holes (a *pothi*). So the title menu is seven leaves on one cord, the leaf in hand pale bark
  with the label in soot ink, the rest shaded bark with the label in bone. In the shop the sections
  are leaves on a cord.
- **The instrument.** Modernism and sci-fi supply the thin rules, flat colour and dark space, the
  ruler along the top edge, the reticle brackets on every panel, the twenty-four-spoke dial (the
  spokes of the dharma wheel) behind the page, and numbers set in a squared technical face.

*Provenance.* The Bhagavata structure (the nether worlds in book 5, Vamana and Trivikrama in book
8) and the pothi's make are general knowledge I did not source in this session; the earlier research
pass (markets, gateways, gold-leaf framing) is not the basis of this version. If you want the
Bhagavata mapping checked against the text, that is worth doing before it goes in marketing copy.

## Palette (soil, one cool note)

Black cotton soil (ground), dark umber (lit ground), clay-umber (panels), bark, palm leaf, soot ink,
bone (text), yellow ochre (the chosen), terracotta (a loss), laterite, copper (line-work), bronze
(headings), and verdigris, oxidised copper, for a gain: the one cool accent.

## Type

**Yatra One** for titles (a hand-drawn Indic face) and **Rajdhani SemiBold** for everything the
player reads and every figure (squared and technical, designed with Devanagari). Both are outline
fonts rasterised at 3x and mip-mapped, so they stay crisp at any window size. The title screen also
sets the double danda (the mark that ends a verse) from the font. The HUD keeps its own faces.

## The pieces (`Puranic`)

All vector, drawn into an open shape pass: `backdrop` (soil, bark `grain`, a faint `dial`, the
`strata`), `ruler`, `reticle`, `border` and `plate` (reticle-cornered panels), `leaf` and `cord`
(the pothi), `wheel` and `divider`. `Template`, `Ornament` and `UiMenu` keep their method names, so
every screen that already called them picked the look up together.

## Not pixel art, with two exceptions

Menus use no bitmaps. The ship hulls, weapon icons and in-world pickups are the game's own sprite
art (gameplay content) and stay as they are; the studio splash keeps its logo and exact ink field.

## Rules to keep it smooth

- Menu text is `ui.menu`, `ui.body`, `ui.small`, `ui.head`, `ui.title`. `ui.hud` is the pixel HUD face.
- Coin and gem icons in menus are `Icons.coinUi()` and `Icons.shardUi(i)`.
- Ornament goes in the shape pass, text in the batch pass.

## The in-flight HUD

The HUD is the margin of the pothi: six blocks round the edge of the play, so the middle of the
screen is the fight and nothing else. Screenshots: `docs/ui-redesign/hud-*.png`.

| Block | Where | Shows |
|---|---|---|
| Vitals | top-left | hull, shield, energy and dash as ruler-bar meters with figures; lives as dharma wheels; the hull flown and the purse |
| Score | top-centre | the ten-digit score; while a chain runs the slab grows a row with the chain and a meter that drains with the combo window |
| Location + route | right edge | the loka, region and yojana reading; under it a ruler of the whole climb with the five bands (the current one lit), gates, shops, checkpoints, the ship, and a line for how far you have climbed |
| Radar | bottom-left | the minimap dial, north up, with a bezel scale |
| Armament console | bottom-centre | the fitted weapon (face, name, damage per second) and, beside it, one Astra cell per element this loka pays |
| Crew | under the vitals | co-op pilots, seat colour, ping (hidden solo) |

What changed in structure, not just colour: the depth tape became a route ruler that also shows
what is ahead; the weapon plate and the shard gauge, which were stacked in the bottom-right corner,
share one console; the loka and yojana text moved out of the middle of the right wall into a tag;
lives became wheels. Hull turns amber under half and clay-red under a quarter (and pulses): the
colour is the warning. `HudLayoutTest` asserts no two blocks overlap at their largest and that the
centre stays empty.
