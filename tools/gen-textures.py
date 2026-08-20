#!/usr/bin/env python3
"""
gen-textures — paints every texture this mod ships.

The dragons ride on VANILLA's EnderDragon model, so their skins have to land on
vanilla's UV layout exactly. That layout is not ours to invent, and neither is the
scale/spine/membrane detail drawn onto it, so the skins are RECOLOURED FROM VANILLA
`entity/enderdragon/dragon.png` rather than drawn from scratch. This is the same
call — and the same technique — as the Menagerie spawn eggs; see ASSETS-ORIGIN.md.

Why a recolour and not a hue rotation: vanilla's dragon.png is **entirely
greyscale** (every opaque pixel has r == g == b, 39 distinct values). Rotating its
hue is a no-op and would ship seven identical black dragons. So each pixel's grey
value is mapped through a per-variant colour ramp anchored on the SOURCE image's own
measured darkest / most-common / brightest values: vanilla's scale detail and shading
survive intact, the colour is entirely ours, and the ramp re-derives itself if the
source art ever changes rather than assuming numbers that happened to hold once.

Everything here is deterministic — rerunning produces byte-identical files.

Usage: python3 tools/gen-textures.py [--vanilla-jar <minecraft-client.jar>]
       python3 tools/gen-textures.py --contact-sheet /tmp/sheet.png
"""

import argparse
import io
import os
import sys
import zipfile
from collections import Counter

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "dragons")

SRC_SKIN = "assets/minecraft/textures/entity/enderdragon/dragon.png"
SRC_EYES = "assets/minecraft/textures/entity/enderdragon/dragon_eyes.png"
SRC_SADDLE = "assets/minecraft/textures/item/saddle.png"
SRC_EGG = "assets/minecraft/textures/item/cow_spawn_egg.png"
SRC_DRAGON_EGG = "assets/minecraft/textures/block/dragon_egg.png"

# ---------------------------------------------------------------------------
# The seven hides. (shadow, base, highlight) — the ramp's three stops.
#   shadow    the deepest crevices between scales
#   base      what the animal reads as from across a valley
#   highlight spines, claws, the leading edge of the wing bones
# Order here is the registry order; DragonVariant.java must list the same names in
# the same order and the asset audit holds it to that.
# ---------------------------------------------------------------------------
VARIANTS = {
    "crimson":  ((0x20, 0x06, 0x06), (0x6E, 0x14, 0x14), (0xE8, 0x50, 0x3C)),
    "emerald":  ((0x06, 0x18, 0x0E), (0x14, 0x56, 0x2A), (0x5B, 0xE0, 0x7C)),
    "sapphire": ((0x06, 0x10, 0x24), (0x16, 0x38, 0x6E), (0x57, 0xA7, 0xF0)),
    "amethyst": ((0x18, 0x08, 0x24), (0x4A, 0x1A, 0x6E), (0xC7, 0x7B, 0xF0)),
    "amber":    ((0x24, 0x14, 0x04), (0x7A, 0x4A, 0x0E), (0xF6, 0xC6, 0x4A)),
    "obsidian": ((0x05, 0x05, 0x08), (0x16, 0x16, 0x1C), (0x8A, 0x8A, 0xA0)),
    "ivory":    ((0x4A, 0x46, 0x3E), (0xB9, 0xB2, 0xA2), (0xFF, 0xFB, 0xF0)),
}

# Eye glow per variant: bright enough to read on the emissive layer at night.
EYES = {
    "crimson": (0xFF, 0x8A, 0x50), "emerald": (0x8C, 0xFF, 0x9E),
    "sapphire": (0x8A, 0xD4, 0xFF), "amethyst": (0xE0, 0x9B, 0xFF),
    "amber": (0xFF, 0xE0, 0x7A), "obsidian": (0xC8, 0xC8, 0xE6),
    "ivory": (0xFF, 0xF4, 0xC8),
}

# ---------------------------------------------------------------------------
# Saddle overlay, in vanilla dragon-UV coordinates.
#
# Derived from ModelPart.Cube's UV layout, not guessed. The body cube is
# addBox(-12, 1, -16, 24, 24, 64) at texOffs (0, 0), so for (u,v,w,h,d) = (0,0,24,24,64):
#
#   DOWN  x[u+d .. u+d+w]     = [64,  88]   y[v .. v+d]     = [0, 64]
#   UP    x[u+d+w .. u+d+2w]  = [88, 112]   y[v .. v+d]     = [0, 64]
#   WEST  x[u .. u+d]         = [0,   64]   y[v+d .. v+d+h] = [64, 88]
#   EAST  x[u+d+w .. u+d+w+d] = [88, 152]   y[v+d .. v+d+h] = [64, 88]
#
# The renderer flips the model with scale(-1,-1,1), so the cube's min-Y face — DOWN —
# is what faces the sky. That is the dragon's BACK, and that is where a rider sits.
#
# Along the DOWN rect, texture y=0 is the cube's maxZ (tail) and y=64 its minZ (neck):
# ModelPart.Cube gives the DOWN polygon vertices {l1,l0,t0,t1} (l = maxZ, t = minZ) the
# UVs (u1,v0),(u0,v0),(u0,v1),(u1,v1). So "distance forward from the shoulders" is
# f = 64 - y, and the same f maps to x = 64 - f on the WEST flank and x = 88 + f on
# the EAST flank. The seat and its girth straps are placed in f and converted below,
# which is why the straps meet the seat instead of landing somewhere near it.
# ---------------------------------------------------------------------------
SEAT_F = (4, 26)          # seat pad, blocks*16 forward of the body cube's rear edge
GIRTH_F = ((8, 12), (18, 22))
LEATHER = (0x6B, 0x44, 0x23, 255)
LEATHER_DARK = (0x3E, 0x27, 0x12, 255)
LEATHER_LIGHT = (0x8B, 0x5A, 0x2B, 255)
BUCKLE = (0xC0, 0xA0, 0x50, 255)


def load(jar, name):
    with zipfile.ZipFile(jar) as z:
        im = Image.open(io.BytesIO(z.read(name))).convert("RGBA")
        im.load()
        return im


def opaque_pixels(im):
    return [p for p in im.get_flattened_data() if p[3] > 0]


def grey(px):
    """The source's own value for a pixel. Vanilla's dragon is greyscale; if that ever
    stops being true, fall back to luminance rather than silently reading only red."""
    if px[0] == px[1] == px[2]:
        return px[0]
    return round(0.2126 * px[0] + 0.7152 * px[1] + 0.0722 * px[2])


def measure(im):
    """Darkest / most-common / brightest opaque value, measured from the real input."""
    values = [grey(p) for p in opaque_pixels(im)]
    if not values:
        sys.exit("source skin has no opaque pixels")
    counts = Counter(values)
    return min(values), counts.most_common(1)[0][0], max(values)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def recolour(im, lo, mid, hi, shadow, base, highlight):
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    src, dst = im.load(), out.load()
    cache = {}
    for y in range(im.height):
        for x in range(im.width):
            px = src[x, y]
            if px[3] == 0:
                continue
            g = grey(px)
            if g not in cache:
                if g <= mid:
                    t = 0.0 if mid == lo else (g - lo) / (mid - lo)
                    cache[g] = lerp(shadow, base, max(0.0, min(1.0, t)))
                else:
                    t = 0.0 if hi == mid else (g - mid) / (hi - mid)
                    cache[g] = lerp(base, highlight, max(0.0, min(1.0, t)))
            # cutout render types derive their layer from sprite alpha, so keep alpha
            # binary: anything between 0 and 255 silently becomes TRANSLUCENT
            dst[x, y] = cache[g] + (255 if px[3] > 127 else 0,)
    return out


def recolour_eyes(im, colour):
    """The eye sprite is a 4-colour purple glow; keep its shading, change its hue."""
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    src, dst = im.load(), out.load()
    peak = max((max(p[:3]) for p in opaque_pixels(im)), default=255)
    for y in range(im.height):
        for x in range(im.width):
            px = src[x, y]
            if px[3] == 0:
                continue
            t = max(px[:3]) / peak
            dst[x, y] = tuple(round(c * t) for c in colour) + (255,)
    return out


def saddle_overlay(size):
    """Straps and a seat pad, painted onto the same UV sheet as the skins.

    Rendered as a second pass over the whole model, so every pixel that is not
    saddle must be fully transparent.
    """
    im = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    # --- seat pad, on the back (DOWN rect x 64..88) ---
    y0, y1 = 64 - SEAT_F[1], 64 - SEAT_F[0]          # f -> texture y
    d.rectangle([64, y0, 87, y1 - 1], fill=LEATHER)
    d.rectangle([64, y0, 87, y0 + 1], fill=LEATHER_DARK)     # cantle (rear lip)
    d.rectangle([64, y1 - 2, 87, y1 - 1], fill=LEATHER_DARK)  # pommel (front lip)
    d.rectangle([68, y0 + 3, 83, y1 - 4], fill=LEATHER_LIGHT)  # worn seat
    d.rectangle([64, y0, 65, y1 - 1], fill=LEATHER_DARK)      # side welts
    d.rectangle([86, y0, 87, y1 - 1], fill=LEATHER_DARK)

    # --- girth straps, down both flanks (WEST x 0..64, EAST x 88..152) ---
    for f0, f1 in GIRTH_F:
        for x0, x1 in ((64 - f1, 64 - f0), (88 + f0, 88 + f1)):
            d.rectangle([x0, 64, x1 - 1, 87], fill=LEATHER)
            d.rectangle([x0, 64, x0, 87], fill=LEATHER_DARK)
            d.rectangle([x1 - 1, 64, x1 - 1, 87], fill=LEATHER_DARK)
            d.rectangle([x0 + 1, 78, x1 - 2, 80], fill=BUCKLE)   # buckle at the flank
        # and the same straps continuing across the back, joining the seat
        for x0, x1 in ((64, 88),):
            d.rectangle([x0, 64 - f1, x1 - 1, 64 - f0 - 1], fill=LEATHER_DARK)
    return im


def recolour_item(im, families, targets):
    """Two-family luminance recolour — the Menagerie spawn-egg technique, reused.

    Every opaque colour is assigned to whichever seed it is nearer, then redrawn in
    that family's target colour at the same luminance ratio it had. Vanilla's
    silhouette and shading ramp survive; only the hue changes.
    """
    def lum(c):
        return max(1.0, 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2])

    def dist(a, b):
        return sum((a[i] - b[i]) ** 2 for i in range(3))

    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    src, dst = im.load(), out.load()
    for y in range(im.height):
        for x in range(im.width):
            px = src[x, y]
            if px[3] == 0:
                continue
            fam = min(range(len(families)), key=lambda i: dist(px[:3], families[i]))
            ratio = lum(px[:3]) / lum(families[fam])
            dst[x, y] = tuple(min(255, round(v * ratio)) for v in targets[fam]) + (255,)
    return out


def two_seeds(im):
    counts = Counter(p[:3] for p in opaque_pixels(im))
    ranked = [c for c, _ in counts.most_common()]
    base = ranked[0]

    def dist(a, b):
        return sum((a[i] - b[i]) ** 2 for i in range(3))

    accent = next((c for c in ranked[1:] if dist(c, base) > 55 ** 2), None)
    if accent is None:
        accent = tuple(min(255, v + 60) for v in base)
    return base, accent


def egg_texture(im, shadow, base, highlight, cracked):
    """Recolour the vanilla dragon egg's 16x16 shell into one variant.

    Vanilla's egg is three colours: two near-black shell greys and one saturated
    purple speckle. Rather than naming those three colours — which would break the
    moment the art changed — pixels are split by SATURATION: flat ones are shell and
    take the variant's shadow..base range by relative luminance, saturated ones are
    speckle and take the highlight. `cracked` adds the late-incubation fissures, drawn
    in the highlight so the egg visibly reads as "about to open".
    """
    def lum(c):
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

    groups = {False: [], True: []}
    for p in opaque_pixels(im):
        groups[max(p[:3]) - min(p[:3]) > 20].append(lum(p[:3]))
    if not groups[False]:
        sys.exit("dragon egg source has no shell pixels")

    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    src, dst = im.load(), out.load()
    for y in range(im.height):
        for x in range(im.width):
            px = src[x, y]
            if px[3] == 0:
                continue
            speckled = max(px[:3]) - min(px[:3]) > 20
            values = groups[speckled]
            lo_v, hi_v = min(values), max(values)
            t = 0.0 if hi_v == lo_v else (lum(px[:3]) - lo_v) / (hi_v - lo_v)
            dst[x, y] = (highlight if speckled else lerp(shadow, base, t)) + (255,)

    if cracked:
        # the fissures glow: the variant highlight pushed most of the way to white, so
        # "nearly hatched" is readable at a glance and from across the camp
        glow = tuple(round(c + (255 - c) * 0.65) for c in highlight) + (255,)
        d = ImageDraw.Draw(out)
        d.line([(5, 0), (5, 3), (7, 5), (7, 9), (5, 12), (5, 15)], fill=glow)
        d.line([(11, 0), (11, 4), (9, 6), (9, 10), (12, 13), (12, 15)], fill=glow)
        d.line([(7, 5), (11, 4)], fill=glow)
        d.line([(0, 8), (3, 8), (5, 7)], fill=glow)
        d.line([(15, 9), (13, 9), (9, 10)], fill=glow)
    return out


def mod_icon(size=128):
    """Overlapping scales in all seven hides — legible as a mod icon at 32px."""
    im = Image.new("RGBA", (size, size), (0x14, 0x14, 0x1A, 255))
    d = ImageDraw.Draw(im)
    names = list(VARIANTS)
    r = size // 6
    row = 0
    y = -r // 2
    while y < size + r:
        offset = 0 if row % 2 == 0 else r
        x = -r + offset
        col = 0
        while x < size + r:
            _, base, highlight = VARIANTS[names[(row * 3 + col) % len(names)]]
            d.pieslice([x, y, x + 2 * r, y + 2 * r], 0, 180, fill=base + (255,))
            d.arc([x, y, x + 2 * r, y + 2 * r], 0, 180, fill=highlight + (255,), width=2)
            x += 2 * r
            col += 1
        y += r
        row += 1
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--vanilla-jar", default=os.path.expanduser(
        "~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar"))
    ap.add_argument("--contact-sheet")
    args = ap.parse_args()

    if not os.path.exists(args.vanilla_jar):
        sys.exit(f"vanilla jar not found: {args.vanilla_jar}")

    skin = load(args.vanilla_jar, SRC_SKIN)
    eyes = load(args.vanilla_jar, SRC_EYES)
    # The source's own shape decides everything downstream. A writer that hardcodes
    # 256x256 would silently truncate a resized sheet to its top-left corner — that
    # exact bug shipped once already (cosmos, 64x64 entity sheets into a 16x16 writer).
    if skin.size != eyes.size:
        sys.exit(f"skin {skin.size} and eyes {eyes.size} disagree; UVs would not line up")
    lo, mid, hi = measure(skin)
    print(f"source {SRC_SKIN} {skin.size[0]}x{skin.size[1]} "
          f"ramp anchors: darkest={lo} commonest={mid} brightest={hi}")
    if lo >= mid or mid >= hi:
        sys.exit(f"degenerate ramp from source ({lo}/{mid}/{hi}) — cannot build a gradient")

    ent = os.path.join(RES, "textures", "entity", "dragon")
    item = os.path.join(RES, "textures", "item")
    block = os.path.join(RES, "textures", "block")
    os.makedirs(ent, exist_ok=True)
    os.makedirs(item, exist_ok=True)
    os.makedirs(block, exist_ok=True)
    dragon_egg = load(args.vanilla_jar, SRC_DRAGON_EGG)

    # The saddle is COMPOSITED INTO each hide rather than shipped as an overlay pass.
    # An overlay would mean submitting vanilla's model a second time with identical
    # geometry, so the two passes share every depth value and z-fight: the saddle would
    # flicker, or lose, depending on the driver. Baking it in costs one 4KB texture per
    # colour and cannot fail that way.
    saddle = saddle_overlay(skin.size)

    sheet = Image.new("RGBA", (skin.width * len(VARIANTS), skin.height), (0, 0, 0, 0))
    for i, (name, (shadow, base, highlight)) in enumerate(VARIANTS.items()):
        hide = recolour(skin, lo, mid, hi, shadow, base, highlight)
        hide.save(os.path.join(ent, f"{name}.png"))
        saddled = hide.copy()
        saddled.alpha_composite(saddle)
        saddled.save(os.path.join(ent, f"{name}_saddled.png"))
        recolour_eyes(eyes, EYES[name]).save(os.path.join(ent, f"{name}_eyes.png"))
        sheet.paste(hide, (i * skin.width, 0))
        egg_texture(dragon_egg, shadow, base, highlight, False).save(
            os.path.join(block, f"{name}_dragon_egg.png"))
        egg_texture(dragon_egg, shadow, base, highlight, True).save(
            os.path.join(block, f"{name}_dragon_egg_cracked.png"))
        print(f"  {name:9s} base #{base[0]:02X}{base[1]:02X}{base[2]:02X}")

    # item icons, recoloured from their vanilla counterparts
    vanilla_saddle = load(args.vanilla_jar, SRC_SADDLE)
    seeds = two_seeds(vanilla_saddle)
    recolour_item(vanilla_saddle, seeds, [(0x5A, 0x38, 0x1C), (0xC0, 0xA0, 0x50)]).save(
        os.path.join(item, "dragon_saddle.png"))

    vanilla_egg = load(args.vanilla_jar, SRC_EGG)
    seeds = two_seeds(vanilla_egg)
    recolour_item(vanilla_egg, seeds, [VARIANTS["obsidian"][1], VARIANTS["amethyst"][2]]).save(
        os.path.join(item, "dragon_spawn_egg.png"))

    mod_icon().save(os.path.join(RES, "icon.png"))

    if args.contact_sheet:
        sheet.save(args.contact_sheet)
        print(f"contact sheet -> {args.contact_sheet}")
    print(f"wrote {len(VARIANTS)} hides (bare + saddled) + eyes + eggs "
          f"(intact & cracked), 2 item icons, icon.png")


if __name__ == "__main__":
    main()
