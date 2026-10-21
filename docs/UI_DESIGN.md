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
