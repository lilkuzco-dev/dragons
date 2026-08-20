# Dragons

Seven colours of dragon for Minecraft 26.2 (Fabric).
You take a castle for an egg, hatch it beside a campfire, and win the hatchling over with
raw chicken before it decides you were not worth waiting for. A grown, tamed dragon takes a
crafted saddle and flies.

The mob wears vanilla's Ender Dragon **model** and nothing else of the boss. In particular
it does not carve holes in the world — see [Not a wrecking ball](#not-a-wrecking-ball).

---

## Getting a dragon

### 1. Take a castle

Dragon eggs come from **Warfront castle chests and nowhere else**. There is no other source:
you get a dragon by taking a castle.

| loot table | chests per castle | chance per chest | eggs per castle |
|---|---|---|---|
| `warfront:castle/aegis` | 17 | 5% | ~0.85 |
| `warfront:castle/dracula` | 16 | 5% | ~0.80 |
| `warfront:castle/sarab` | 24 | 5% | ~1.2 |
| `warfront:castle/vostok` | 24 | 5% | ~1.2 |
| `warfront:castle/hidden_vault` | — | 50% | (no structure uses this table yet) |

The chance per chest looks tiny because a castle is one structure holding sixteen to
twenty-four chests, all rolling the same table. The number that matters is chests × chance,
which comes out at roughly **one egg per castle taken** — usually one, sometimes two,
sometimes none. Each egg that does roll picks one of the seven colours uniformly, and the
colour of the egg is the colour of the dragon inside it.

> **Without Warfront installed, dragons cannot be obtained in survival at all.** That is a
> deliberate coupling, but a silent one would be miserable to diagnose, so the mod warns on
> startup if Warfront is missing, warns again if a castle table it expects never loads
> (Warfront renaming one looks identical to Warfront being absent), and `/dragons loot`
> prints the current wiring on demand.

### 2. Hatch it

Set the egg down with a **lit campfire within 5 blocks** and leave it alone for
**10–25 minutes**. The egg cracks visibly as it gets close, and gives off portal particles
throughout.

You do not have to stand there. The egg records the *time it will hatch* rather than
counting down, so it is exactly as ready when you come back as it would have been if you
had watched — including across a server restart or a chunk nobody loaded for a week. The
campfire does have to still be lit when the moment arrives; if it has gone out, the egg
waits for a new one rather than losing its progress.

### 3. Tame it

The egg opens into a **hatchling at quarter size**, bonded to whoever put the egg down. It
follows that player around for **one hour** of world time.

Feed it **raw chicken**. Each one is a **5% chance** of taming, with the same hearts-or-smoke
burst you get feeding bones to a wolf. It has to be **on the ground** to take food — a
dragon in the air will not be fed, hatchling or otherwise.

If the hour runs out and nobody has tamed it, it leaves and grows up wild. It is still
tameable after that, at the same 5% per chicken, but now you have to find it and catch it
perched.

### 4. Ride it

Craft a **dragon saddle** and right-click a grown, tamed dragon with it:

```
L L L      L = leather
C S C      C = iron chain
           S = saddle
```

Then right-click to mount. Controls are the happy ghast's: look where you want to go and
hold forward, jump to climb, nose down and forward to descend and land. Shears take the
saddle back off; sneak-right-click toggles sit/stay.

Cruising speed is **~24 blocks/s** — a shade over an unboosted elytra glide. That number is
measured by the render battery on every run rather than read off the `FLYING_SPEED`
attribute, because the attribute does not mean what it looks like: it enters the speed
calculation once or twice depending on whether the input clears `getInputVector`'s length
cap, so the same value can produce 9 blocks/s or 220.

### 5. Park it

A **lead** works on a dragon. A leashed dragon will not start a flight, so tying one to a
fence leaves it exactly where you tied it — the same "parked" behaviour a happy ghast has.

---

## Not a wrecking ball

The Ender Dragon flies with `noPhysics` set and calls `checkWalls`, which deletes every
block its hitbox touches. That is why letting one loose in the Overworld carves a trench
through whatever it crosses, and it is the single most important thing this mod does *not*
inherit.

A dragon here is an ordinary tameable animal that:

- **collides with the world** like any other mob (`noPhysics` is never set, and there is no
  block-breaking code anywhere in the mob);
- **paths around obstacles** — it steers with a `FlyingPathNavigation`, whose node evaluator
  treats solid blocks as impassable, so a village or a cliff in the way is something it
  routes around rather than through;
- **cruises above the terrain** — each flight picks a destination 8–20 blocks above the
  world-surface heightmap at the target column, so the ordinary case is flying *over* the
  forest rather than negotiating it.

It is also not hostile. It has no targeting goal that starts a fight; it fights back when
hit, and defends its owner.

---

## Sizes

| | render scale | hitbox |
|---|---|---|
| Ender Dragon (vanilla, for reference) | 1.0 | 16 x 8 |
| grown dragon | 0.5 | 2 x 2 |
| hatchling | 0.125 | 0.5 x 0.5 |

The hitbox is small on purpose. `FlyNodeEvaluator` needs an air corridor as wide as the mob,
and nothing 16 blocks wide fits between two trees — a boss-sized hitbox would fail every
path and then drift into the scenery it was supposed to avoid.

---

## Commands

Read-only, for checking state that is invisible in a screenshot:

- `/dragons census` — every dragon within 48 blocks: age, colour, tame/saddled/flying/
  resting/leashed, and how much of the hatchling hour is left
- `/dragons eggs` — every egg nearby: whether it is warm, whether it is incubating, its
  visible stage and how long until it opens
- `/dragons loot` — whether each castle loot table actually carries dragon eggs. This is the
  one part of the mod the dev environment cannot test, because it keys off tables Warfront
  owns and Warfront is not on the dev classpath

---

## Building

```sh
export JAVA_HOME=/path/to/jdk-25
./gradlew build          # runs the asset audit as a gate
./gradlew runGametest    # the render battery — READ the screenshots
```

`./gradlew build` will not produce a jar without passing `tools/asset-audit.py`, which
checks four things:

1. every `dragons:` asset path the code can construct is actually in the jar;
2. every `minecraft:` path the mod hardcodes still exists in the client jar (these get
   renamed between versions, and a stale one is a silent checkerboard);
3. every vanilla recipe ingredient is a real item in this version — 26.2 renamed
   `minecraft:chain` to `minecraft:iron_chain`, and a recipe naming a missing item does not
   warn, it just never loads and the result is uncraftable forever;
4. `DragonVariant.java` and `tools/gen-textures.py` agree on the roster of seven colours.

`./gradlew runGametest` writes frames to `build/run-gametest/screenshots/`. They are the
only evidence that catches a wrong render scale, a saddle painted onto the wrong cube face,
or a hue rotation that produced seven identical black dragons. **Look at them.**

Textures are regenerated with `python3 tools/gen-textures.py` — see `ASSETS-ORIGIN.md` for
where the art comes from and why.
