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

## Voxy shader coupling (fragile - read before touching `VoxyShaderPatcher`)

`VoxyShaderPatcher` string-patches Voxy `0.2.18-beta`'s exact internal `quads3.vert`/
`quads.frag` source (marker strings, uniform locations `30` and `33`-`38`). Bumping the `voxy_version` in
`gradle.properties` can silently change that layout.

- The patcher fails loudly (`patch()` throws `IllegalStateException`) by design - `VoxyShaderPatcherTest`
  pins this. Do not make `patch()` itself swallow the mismatch.
- The mixin call site uses `patchSafely()` instead, which catches that exception, disables the
  Nether roof removal and ore mask (`VoxyShaderPatcher.isSupported()` flips to `false`, checked by
  `MDICSectionRendererMixin` before touching their uniforms), and logs a warning - so an
  incompatible Voxy update degrades gracefully instead of crashing shader compilation for the whole
  client. Keep both halves in sync if you touch either.
- After bumping `voxy_version`, re-verify the marker strings in `VoxyShaderPatcher` against the
  new Voxy jar's actual shader source before assuming the patch still applies.

## Anti-xray invariant (Nether roof removal)

The roof band is removed by discarding whole quads (`voxymapQuadMinY >= voxymapRoofCutY` in
`VoxyShaderPatcher`), never by clipping fragments at a plane: a plane slices straight through the
server's anti-xray decoy LODs and shows them as a tiled cross-section that looks like real terrain.
`ProjectContractTest` pins this. Do not add a per-fragment height cut, and do not add any feature that
treats client data the server may have withheld (unloaded or obfuscated chunks) as verified terrain or
open air - the removed room cutaway was exactly that kind of guess.

## Optional-mod integration pattern

Xaero's World Map is not a compile-time dependency (it is not published anywhere this project's
Gradle can resolve) and is not required to be installed. Its integration (`XaeroWorldMapBridge`)
is the shape to copy for the next optional mod:

1. `FabricLoader.getInstance().isModLoaded("<mod_id>")` gates everything.
2. Resolve `Class.forName(...)` + `Method` handles **once**, in a static field initializer, not per
   call - these run every render frame while the map is open.
3. Wrap every reflective call site in `catch (ReflectiveOperationException | LinkageError |
   RuntimeException)` (not narrower) and fail to `null`/no-op - an incompatible or absent mod must
   never throw out of a render or mixin call site. `ClassCastException` is a `RuntimeException`;
   listing both in one multi-catch is a compile error.
4. Settings live in `SettingsCatalog`, which both the map panel and the Mod Menu screen render.
   To make a row depend on an optional mod, add a condition to `SettingsCatalog.sections(...)` (with a
   test) rather than hiding the row in one screen only.

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
   in one frame (a long route or outline drawn across a wide area) it is a severe, silent
   frame-time hit that reads as "FPS drops to 1 whenever that overlay is on screen."

Any new screen-space overlay must reuse `projectToScreen`/`clipLineToViewport`, not reimplement
projection or clipping math, and must not remove either guard. A route overlay (since removed) is
what first surfaced both, because it drew points thousands of blocks from the player - territory
outlines and player markers stay much closer together and rarely hit the edge case.

## Deploy target

Same as the workspace default: `D:/Modrinth App/profiles/Everything Voxy/mods/`.
