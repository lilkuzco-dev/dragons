# Asset origin

Provenance for everything this mod draws. Short version: **every texture here is
recoloured from Mojang's own art**, deliberately, and none of it is original work.
`tools/gen-textures.py` is the only thing that writes into
`src/main/resources/assets/dragons/textures/` — the files are build output, not
hand-authored art, and re-running the script reproduces them byte for byte.

## Why recolour rather than draw

The whole premise of this mod is "the Ender Dragon's model, in seven colours". The model
is vanilla's, used as-is (no copy, no fork), which means the skins have to land on
vanilla's UV layout exactly — every scale, spine and wing-membrane pixel is at a
coordinate the model dictates. Hand-drawing onto someone else's UV layout produces a worse
result than recolouring the art that layout was made for, and it would still be derived
work in every sense that matters.

This is the same call Menagerie made for its spawn eggs, and it is stated plainly here for
the same reason: it is a choice, not an accident.

## The dragon hides — `textures/entity/dragon/*.png`

Source: `assets/minecraft/textures/entity/enderdragon/dragon.png` (256x256).

**Vanilla's dragon is entirely greyscale.** Every opaque pixel has `r == g == b`, across 39
distinct values. This matters: a hue rotation, which is the obvious way to "re-hue" a
texture, is a mathematical no-op on it and would have shipped seven identical black
dragons.

So each pixel's grey value is mapped through a three-stop colour ramp per variant:

| stop | source anchor | becomes |
|---|---|---|
| darkest | the source's own minimum opaque value | the variant's shadow |
| commonest | the source's own modal value | the variant's base |
| brightest | the source's own maximum opaque value | the variant's highlight |

The anchors are **measured from the input every run** rather than hardcoded, so the ramp
re-derives itself if the source art ever changes instead of silently mapping through
numbers that happened to hold once. Vanilla's scale detail and shading survive intact; the
colour is entirely ours.

Alpha is kept binary (0 or 255). Cutout render types in 26.2 derive their chunk layer from
the sprite's own alpha, so any value in between would silently make the hide translucent.

### Saddled hides — `*_saddled.png`

The same hide with a saddle composited in: a leather seat pad on the body's back and girth
straps down both flanks, painted at UV rectangles derived from `ModelPart.Cube`'s layout
for the body cube rather than guessed (the derivation is written out in the generator).

It is **baked into the hide rather than shipped as an overlay pass** because an overlay
would mean submitting vanilla's model a second time at identical geometry: both passes
write the same depth values and z-fight, so the saddle would flicker or lose depending on
the driver. A texture swap cannot fail that way. The cost is one extra 4KB sheet per colour.

### Eyes — `*_eyes.png`

Source: `assets/minecraft/textures/entity/enderdragon/dragon_eyes.png` — 86 opaque pixels
in three shades of purple. Kept at their original relative brightness, retinted per variant.
Rendered on the emissive layer, as on the boss.

## The eggs — `textures/block/*_dragon_egg.png`

Source: `assets/minecraft/textures/block/dragon_egg.png` (16x16, three colours).

Pixels are split by **saturation** rather than by naming the three source colours: flat
ones are shell and take the variant's shadow-to-base range by relative luminance, saturated
ones are speckle and take the highlight. Splitting on a measured property rather than on
literal colour values means the script still does the right thing if the source art changes.

`*_cracked.png` adds glowing fissures for the late incubation stages, drawn in the
variant's highlight pushed most of the way to white.

The block **model** is vanilla's: `assets/dragons/models/block/*.json` sets
`"parent": "minecraft:block/dragon_egg"` and overrides its `all` texture. The geometry is
Mojang's, referenced rather than copied.

## Item icons

| file | source | treatment |
|---|---|---|
| `textures/item/dragon_saddle.png` | `minecraft:textures/item/saddle.png` | two-family luminance recolour to darker leather + brass |
| `textures/item/dragon_spawn_egg.png` | `minecraft:textures/item/cow_spawn_egg.png` | same, into the obsidian/amethyst pair |

Both use the Menagerie spawn-egg technique: cluster the template's opaque colours into two
families, measure each pixel's luminance relative to its family seed, re-apply that ratio in
the target colour. Vanilla's silhouette and shading ramp survive; only the hue changes.
26.2 removed the tintable `template_spawn_egg`, so a mod cannot supply two colours and let
the game draw the icon — every egg is its own flat texture now.

## `icon.png`

The only file here that is not derived from anything: overlapping scale arcs in all seven
variant colours, drawn from scratch by the generator.

## Sounds

None shipped. The dragons reuse vanilla's `entity.ender_dragon.*` events. That is partly
taste and partly the machine: this box has no ffmpeg, no oggenc and no Homebrew, and
`afconvert` cannot write Vorbis, so any bespoke sound would have to be stereo — and
Minecraft plays stereo `.ogg` with no position and no attenuation while logging nothing
about it.
