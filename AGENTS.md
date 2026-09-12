# VoxyMap LTS development notes

The workspace root `AGENTS.md` (`E:/Coding/Minecraft/AGENTS.md`) also applies. This file only lists
where this project deliberately deviates from that default, plus patterns specific to it.

## Deviations from the workspace default

- **Minecraft `26.2` only, single target** - unlike most workspace mods, this is a user-requested
  boundary, not an oversight. Do not add a `26.1.2` compile target or a dual-build verification step
  unless the user asks for it.
- **Package is `com.voxymap.client`**, not `org.lts.voxymap`. Keep using the existing package; do not
  "correct" it to match the workspace default.
- Mod ID `voxymap`, archives base name `voxy-map` (so the built jar is `voxy-map-<version>.jar`, not
  `voxymap-<version>.jar` - check `build/libs/` by that name).

## Voxy shader coupling (fragile - read before touching `RoomCutawayShaderPatcher`)

`RoomCutawayShaderPatcher` string-patches Voxy `0.2.18-beta`'s exact internal `quads3.vert`/
`quads.frag` source (marker strings, uniform locations `30`-`33`). Bumping the `voxy_version` in
`gradle.properties` can silently change that layout.

- The patcher fails loudly (`patch()` throws `IllegalStateException`) by design - `RoomCutawayShaderPatcherTest`
  pins this. Do not make `patch()` itself swallow the mismatch.
- The mixin call site uses `patchSafely()` instead, which catches that exception, disables the
  cutaway feature (`RoomCutawayShaderPatcher.isSupported()` flips to `false`, checked by
  `MDICSectionRendererMixin` before touching the cutaway uniforms), and logs a warning - so an
  incompatible Voxy update degrades gracefully instead of crashing shader compilation for the whole
  client. Keep both halves in sync if you touch either.
- After bumping `voxy_version`, re-verify the marker strings in `RoomCutawayShaderPatcher` against the
  new Voxy jar's actual shader source before assuming the patch still applies.

## Anti-xray invariant (`RoomCutawayComputer`)

`UNKNOWN` (an unloaded/withheld chunk) must never be treated as passable air anywhere in the flood
fill - including the two seed cells (the player's own feet/head position). Seeding used to skip the
classifier for those two cells; it now calls it exactly like every other cell
(`Search#seedFeet`). If you touch the seeding path again, keep it going through the classifier - a
bypass here is exactly the kind of gap that could reveal server-side anti-xray decoy geometry as if it
were verified real terrain. `RoomCutawayComputerTest` has regression cases for this.

## Optional-mod integration pattern

Xaero's World Map and HappyGhastScanner are not compile-time dependencies (neither is published
anywhere this project's Gradle can resolve) and not required to be installed. Both integrations
(`XaeroWorldMapBridge`, `HappyGhastScannerBridge`) follow the same shape - copy it for the next one:

1. `FabricLoader.getInstance().isModLoaded("<mod_id>")` gates everything.
2. Resolve `Class.forName(...)` + `Method` handles **once**, in a static field initializer, not per
   call - these run every render frame while the map is open.
3. Wrap every reflective call site in `catch (ReflectiveOperationException | LinkageError |
   RuntimeException)` (not narrower) and fail to `null`/no-op - an incompatible or absent mod must
   never throw out of a render or mixin call site. `ClassCastException` is a `RuntimeException`;
   listing both in one multi-catch is a compile error.
4. `MapScreen`'s settings panel makes an optional feature's row appear only when the bridge reports
   available: set the row's `*RowY` field to `-1` when hidden (already excluded from hit-testing by
   `isInsideSettingsRow`'s `rowY >= 0` guard) and grow/shrink `settingsPanelH` to match.

## Screen projection precision

A world point near edge-on to the camera (small but valid `depth`, just past the near-plane cutoff)
projects to a technically finite but wildly wrong screen coordinate - projection is only meaningful
for points in front of the camera, and nothing downstream can recover a bad coordinate once produced.
This bit twice and needs both fixes kept:

1. `MapScreen#projectToScreen` clamps `screenX`/`screenY` to `±1.0E6` before rounding to `int`.
   `Math.round(double)` returns a `long`; narrowing an unclamped astronomical value to `int` silently
   wraps around instead of clamping, producing a coordinate that can still pass the line clipper's
   checks and drive a per-pixel fill loop into hundreds of millions of iterations - a full render-
   thread freeze, not a crash, hard to spot from a stack trace.
2. `MapScreen#clipLineToViewport` rejects (`return null`) any clipped segment longer than roughly two
   screen diagonals. Clamping alone is not enough: a bad-but-clamped coordinate is still a *valid*,
   finite point that can legitimately fall inside the padded viewport rectangle, so the clipper accepts
   it and returns a segment that streaks across (and past) the whole screen. One such line is a fill
   loop of a few thousand iterations, not hundreds of millions - not a freeze, but with enough of them
   in one frame (the HappyGhastScanner route can draw dozens across a wide spiral) it is a severe,
   silent frame-time hit that reads as "FPS drops to 1 whenever the spiral is on screen."

Any new screen-space overlay must reuse `projectToScreen`/`clipLineToViewport`, not reimplement
projection or clipping math, and must not remove either guard. The HappyGhastScanner route overlay is
what first surfaced both, because it draws points thousands of blocks from the player, spread across a
wide spiral - territory outlines and player markers stay much closer together and rarely hit the edge
case.

## Deploy target

Same as the workspace default: `D:/Modrinth App/profiles/Everything Voxy/mods/`. That profile also
has `happyghastscanner-1.0.0.jar` installed, so the route overlay is testable in-game without
installing anything extra.
