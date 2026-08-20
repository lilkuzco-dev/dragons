# Dragons verification log

## v0.1.1 — post-ship verification (2026-08-20)

0.1.1 shipped at 13:38Z on 2026-08-20. This entry is written afterwards, during a
folder-wide audit that found the repo had no verification log at all.

### The battery on disk was stale

Frames in `build/run-gametest/screenshots/` were timestamped **09:35**; the 0.1.1 commit
is **09:38:19**, and it was not a small one — `DragonsLoot.java` +129 lines, plus a new
render-test assertion in the same commit. So the frames were of the code as it stood
*before* the release, and the loot assertion added by that commit had never run.

Re-ran the battery on the shipped code. **BUILD SUCCESSFUL in 2m 12s, 16 frames**,
2026-08-20. ✅

### Measured, not asserted by hand

| What | Result |
|---|---|
| Ridden flight speed | `24.0 blocks/s over 40 ticks` — the target is ~24, a shade over an elytra glide ✅ |
| Taming rate | `counts [21, 7, 27, 34, 13] mean 20.4 chicken per dragon` — the 5% roll predicts ~20; a certain roll would be exactly 1.0, so the roll is real and the spread is the spread of a 5% chance ✅ |
| Warfront loot binding | With warfront absent the mod says so — *"warfront NOT installed — dragon eggs have no loot source at all; dragons are creative-only in this world"* — and the battery confirms none of the named castle tables was modified anyway. Both halves of the assertion added in the 0.1.1 commit now actually run. ✅ |
| Asset audit | `79 own paths, all present; 3 vanilla paths, all still present; 3 vanilla recipe ingredients, all real items` ✅ |

### Frames read (rule 9)

Sixteen, covering the acquisition and riding loop end to end: `dragon_lineup`,
`grounded`, `baby_beside_adult`, `saddle_bare`/`worn`/`above`, `eggs`, `egg_stages`,
`hatched`, `leashed`, `taming_attempt`, `tamed`, `tamed_saddled`, `ridden`,
`ridden_flight`, `bond_expired`.

- **`dragon_lineup`** — all seven variants render, each in its own hue (white, black,
  orange, purple, blue, green, red), models and textures intact. ✅
- **`dragon_ridden_flight`** — a ridden dragon in first person: wings and tail frame the
  camera, the *Best Friends Forever* advancement fires, and the taming round reports in
  chat. Riding is drawn, not merely simulated. ✅

### What is actually deployed

- The jar on the server hashes to `d74be01642fc24a9e5317fe8…`, exactly what `mods.json`
  declares — verified by pulling the file off the server and hashing it. ✅
- `dragons 0.1.1` initialised in the 13:43 boot, 104 mods, zero mixin failures, zero
  errors. ✅
- Rebuilt from committed source at `v0.1.1`: `tools/jar-compare.js` reports **IDENTICAL**
  across all 124 entries. The shipped artifact is the committed source, byte for byte. ✅

### Soft dependency on Warfront, deliberately

`fabric.mod.json` declares `warfront` under `recommends`, not `depends`, and every loot
hook is guarded by `DragonsLoot.warfrontPresent()`. Without Warfront the mod loads and
says out loud that eggs have no source. That is the correct shape for a soft integration
and the battery asserts both branches of it.

### Not covered by this entry

- No dragon has been hatched or ridden on the live server. Eggs come from Warfront castle
  loot, and castles only generate in chunks created after 0.4.7 landed today — the world's
  chunks predate that.
- The taming rate is measured over five dragons. It is consistent with a 5% roll; it is not
  a tight statistical bound.
