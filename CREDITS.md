# Credits

## Mojang Studios

Everything this mod draws is derived from Minecraft's own art, and the model it draws on
is Minecraft's own model, used at runtime rather than copied:

- **`EnderDragonModel`** — the dragon mesh and all of its neck/tail/wing animation. This
  mod bakes vanilla's `ModelLayers.ENDER_DRAGON` and submits it; no geometry is duplicated.
- **`block/dragon_egg`** — the egg's block model, referenced as a `parent` with only its
  texture overridden.
- **`entity/enderdragon/dragon.png`, `dragon_eyes.png`, `block/dragon_egg.png`,
  `item/saddle.png`, `item/cow_spawn_egg.png`** — recoloured into every texture this mod
  ships. See `ASSETS-ORIGIN.md` for exactly what is done to each and why.
- **Sounds** — the `entity.ender_dragon.*` events, used as-is.

## Design lineage

- The **hatch-from-an-item** loop and its "put it next to the one thing it needs and come
  back later" shape are modelled on vanilla's **dried ghast**, with the clock reworked to
  survive an unloaded chunk.
- The **flying-mount controls** and the **harness-shaped saddle recipe** follow the **happy
  ghast**, so a player who has ridden one already knows how to ride this.
- The **taming feedback** — hearts on success, smoke on failure, one roll per item — is the
  **wolf**'s, reused deliberately so it reads as familiar rather than as something new to
  learn.

## Code

Written for this repo. `tools/gen-textures.py`'s two-family luminance recolour is ported
from Menagerie's `tools/gen-spawn-eggs.py` (same author, same repo family).
