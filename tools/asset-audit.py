#!/usr/bin/env python3
"""
asset-audit — fail the build on any asset path this mod can request but does not ship.

A missing texture in Minecraft is a checkerboard, not an exception: nothing is logged,
nothing crashes, and the first anyone hears of it is a screenshot. This audit runs against
the REMAPPED jar — the artifact that actually ships, not the source tree — and asserts its
own anchors, so a refactor that stops it finding anything fails the build instead of
quietly passing on an empty list.

Four classes of path are checked:

  1. Our own. Every `dragons:` texture the Java or the JSON names must be in the jar.
  2. Vanilla's. This mod is built on vanilla's Ender Dragon model and vanilla block model,
     so it hardcodes `minecraft:` paths. Those get RENAMED between versions — 26.2 already
     did it to `entity/pig/pig_temperate.png` — and a stale one is a silent checkerboard.
     They are held against the real client jar.
  3. Recipe ingredients. A recipe naming an item that does not exist does not crash and
     does not warn in play — the data pack drops it during load and the result is simply
     uncraftable forever. 26.2 renamed `minecraft:chain` to `minecraft:iron_chain` and
     that is exactly how it presented. Every `minecraft:` id in our recipes is held
     against the client jar's per-item model files, which are one per registered item.
  4. The roster. DragonVariant.java and gen-textures.py each carry a list of the seven
     colours. If they ever disagree, one of them is painting or requesting a colour the
     other has never heard of.

Usage: python3 tools/asset-audit.py --jar <built.jar> [--vanilla-jar <client.jar>]
"""

import argparse
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "dragons"

# Vanilla paths this mod names directly. Each one is a silent checkerboard if it moves.
VANILLA_REQUIRED = [
    "assets/minecraft/models/block/dragon_egg.json",
    "assets/minecraft/textures/entity/enderdragon/dragon.png",
    "assets/minecraft/textures/block/dragon_egg.png",
]


def variants_from_java():
    path = os.path.join(ROOT, "src/main/java/dev/lilkuzco/dragons/entity/DragonVariant.java")
    with open(path) as fh:
        body = fh.read()
    enum = body.split("public enum DragonVariant {", 1)[1].split(";", 1)[0]
    found = re.findall(r'^\s*[A-Z_]+\("([a-z_]+)"\)', enum, re.M)
    if not found:
        sys.exit(f"asset-audit: anchor lost — no variants parsed out of {path}")
    return found


def variants_from_generator():
    path = os.path.join(ROOT, "tools/gen-textures.py")
    with open(path) as fh:
        body = fh.read()
    block = body.split("VARIANTS = {", 1)[1].split("}\n", 1)[0]
    found = re.findall(r'^\s*"([a-z_]+)":', block, re.M)
    if not found:
        sys.exit(f"asset-audit: anchor lost — no variants parsed out of {path}")
    return found


def recipe_ingredients():
    """Every `minecraft:` item id our recipe JSON names, with the file it came from."""
    found = []
    recipes = os.path.join(ROOT, "src/main/resources/data", NS, "recipe")
    if not os.path.isdir(recipes):
        sys.exit(f"asset-audit: anchor lost — no recipe directory at {recipes}")
    for entry in sorted(os.listdir(recipes)):
        if not entry.endswith(".json"):
            continue
        with open(os.path.join(recipes, entry)) as fh:
            body = json.load(fh)
        for value in list(body.get("key", {}).values()) + [body.get("ingredient")]:
            for item in (value if isinstance(value, list) else [value]):
                if isinstance(item, str) and item.startswith("minecraft:"):
                    found.append((entry, item))
    if not found:
        sys.exit(f"asset-audit: anchor lost — no vanilla ingredients parsed out of {recipes}")
    return found


def referenced_paths(names):
    """Every asset path the mod can ask for, derived the same way the code derives it."""
    wanted = {
        f"assets/{NS}/icon.png",
        f"assets/{NS}/textures/item/dragon_saddle.png",
        f"assets/{NS}/textures/item/dragon_spawn_egg.png",
        f"assets/{NS}/models/item/dragon_saddle.json",
        f"assets/{NS}/models/item/dragon_spawn_egg.json",
        f"assets/{NS}/items/dragon_saddle.json",
        f"assets/{NS}/items/dragon_spawn_egg.json",
        f"assets/{NS}/lang/en_us.json",
        f"data/{NS}/recipe/dragon_saddle.json",
    }
    for name in names:
        # DragonVariant builds these two from its own id
        wanted.add(f"assets/{NS}/textures/entity/dragon/{name}.png")
        wanted.add(f"assets/{NS}/textures/entity/dragon/{name}_saddled.png")
        wanted.add(f"assets/{NS}/textures/entity/dragon/{name}_eyes.png")
        # DragonsBlocks builds the egg block from the same id
        egg = f"{name}_dragon_egg"
        wanted.add(f"assets/{NS}/textures/block/{egg}.png")
        wanted.add(f"assets/{NS}/textures/block/{egg}_cracked.png")
        wanted.add(f"assets/{NS}/blockstates/{egg}.json")
        wanted.add(f"assets/{NS}/models/block/{egg}.json")
        wanted.add(f"assets/{NS}/models/block/{egg}_cracked.json")
        wanted.add(f"assets/{NS}/items/{egg}.json")
        wanted.add(f"data/{NS}/loot_table/blocks/{egg}.json")
    return wanted


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", required=True)
    ap.add_argument("--vanilla-jar", default=os.path.expanduser(
        "~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar"))
    args = ap.parse_args()

    java_names = variants_from_java()
    gen_names = variants_from_generator()
    if java_names != gen_names:
        sys.exit("asset-audit: DragonVariant.java and gen-textures.py disagree on the roster\n"
                 f"  java:      {java_names}\n  generator: {gen_names}")
    print(f"asset-audit: roster agreed by both sides ({len(java_names)}): {', '.join(java_names)}")

    with zipfile.ZipFile(args.jar) as z:
        shipped = set(z.namelist())
    missing = sorted(p for p in referenced_paths(java_names) if p not in shipped)
    if missing:
        sys.exit("asset-audit: the mod can request these and the jar does not have them:\n  "
                 + "\n  ".join(missing))
    print(f"asset-audit: {len(referenced_paths(java_names))} own paths, all present")

    if os.path.exists(args.vanilla_jar):
        with zipfile.ZipFile(args.vanilla_jar) as z:
            vanilla = set(z.namelist())
        gone = [p for p in VANILLA_REQUIRED if p not in vanilla]
        if gone:
            sys.exit("asset-audit: hardcoded vanilla paths that no longer exist in the client "
                     "jar (each one is a silent checkerboard):\n  " + "\n  ".join(gone))
        print(f"asset-audit: {len(VANILLA_REQUIRED)} vanilla paths, all still present")

        ingredients = recipe_ingredients()
        unknown = [f"{where}: {item}" for where, item in ingredients
                   if f"assets/minecraft/items/{item.removeprefix('minecraft:')}.json" not in vanilla]
        if unknown:
            sys.exit("asset-audit: recipe ingredients that are not items in this version "
                     "(the recipe silently fails to load and the result is uncraftable):\n  "
                     + "\n  ".join(unknown))
        print(f"asset-audit: {len(ingredients)} vanilla recipe ingredients, all real items")
    else:
        sys.exit(f"asset-audit: vanilla jar not found at {args.vanilla_jar}; refusing to pass "
                 "without checking the paths this mod hardcodes")


if __name__ == "__main__":
    main()
