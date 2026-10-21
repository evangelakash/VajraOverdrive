# The Puranic bazaar: the menu UI

Every non-game screen (title, settings, controls, profile, pause, lobby, netplay, the shop) is
drawn in one visual language, taken from how the old Indian market looked and was run. None of it
is pixel art: type is smooth outline fonts, ornament is vector shapes, and the window is
multisampled. Screenshots: `docs/ui-redesign/`.

## What the research said, and what we drew from it

| Finding | Source | In the UI |
|---|---|---|
| A market had a central bazaar of shops and stalls, with guilds (*shrenis*) running their own bazaars per craft, and state officers (*Panyadhyaksha*) regulating quality and fair prices | "Market / Trade Centers in Ancient India" (CESS); Arthashastra summaries | The shop is split by craft into sections (hulls, reforging, weapons, astra forge, shards); a price is posted plainly, with the change an upgrade makes shown beside it |
| The *torana* is a ceremonial arched gateway, with garlands of mango leaf hung on it for auspicious occasions | Wikipedia, "Torana (architecture)" | The title menu stands in a pointed, three-ruled torana; marigold and mango-leaf garland across the doorway |
| Ayodhya's streets are hung with banners, pennants and garlands; the city is described as sprinkled with fragrant water and thronged | Valmiki Ramayana, Ayodhya Kanda, sarga 7 | A scalloped cloth valance over every screen, with bells and lamps that sway on the title |
| Paintings and manuscript leaves are framed in gold leaf | Brown University Library, "The Production of Miniature Painting" | Cards and panels take a double-ruled gold-leaf border with a lotus in each corner |

**On "hammock".** The brief said everything should have a hammock on it. I read that as the hanging
cloth of the bazaar: the valance, hanging tabs, tags and lamps. If you meant something else (a
literal hammock, a swinging sign), say so and it is a small change.

The research is from secondary sources; the market-structure source mixes periods (some of its
examples are Mughal or later). The design leans on the images that recur across sources (cloth,
gateways, garlands, lamps, gold leaf), not on any single claim.

## The pieces (`Puranic`)

All vector, drawn into an open shape pass: `canopy` (valance, scallops, bells, lamps), `garland`,
`lotus`, `torana`, `temple` / `stupa` (the skyline), `border` and `plate` (gold-leaf panels),
`tag` and `pill` (palm-leaf boards), `vajra`, `divider`, and `backdrop` (the night sky, stars and
a faint wheel). Palette: vermilion, saffron, marigold, gold leaf, brass, ivory, indigo night.

Type (`Ui`): **Rozha One** for titles and headings, **Yatra One** for menus and text, drawn from
outline fonts rasterised at 3x and mip-mapped, so they stay crisp at any window size. The HUD keeps
its own faces.

## Shared layer

`Template`, `Ornament` and `UiMenu` keep their method names, so every screen that already called
them picks the new look up together. The title screen (`HomeScreen`) is a full recomposition; the
shop is restyled in the same language (hanging section boards on a cord, gold-leaf cards, smooth
coin and gem icons).

## Not pixel art, with two exceptions

The ship hulls, the weapon icons and the in-world pickups are the game's own sprite art. They are
gameplay content, not UI chrome, and are left as they are. The studio splash card keeps its logo
image and exact ink field (its test pins them).

## Rules to keep it smooth

- Menu text uses `ui.menu`, `ui.body`, `ui.small`, `ui.head`, `ui.title`; `ui.hud` is the pixel HUD face and
  is not for menus.
- Coin and gem icons in menus are `Icons.coinUi()` and `Icons.shardUi(i)`.
- Draw ornament in the shape pass, text in the batch pass; ornament that hangs over text (bells,
  lamps) is only on the title screen.
