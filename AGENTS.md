# VoxyMap LTS development notes

The workspace root `AGENTS.md` (`E:/Coding/Minecraft/AGENTS.md`) also applies. This file only lists
where this project deliberately deviates from that default, plus patterns specific to it.

## Deviations from the workspace default

- **Minecraft `26.2` only, single target. This is the one exception to the workspace's 26.3 rule** (user
  decision 2026-09-28): Voxy has no 26.3 build yet, so VoxyMap stays on 26.2 until Voxy ships one. Then port it
  to 26.3 like the rest of the workspace (SDL3 input, `EntityRenderer`/`LevelRenderer`/`ClientClockManager`
  changes; see the root `AGENTS.md` rule 0). Do not add a second compile target.
- **Package is `com.voxymap.client`**, not `org.lts.voxymap`. Keep using the existing package; do not
  "correct" it to match the workspace default.
- Mod ID `voxymap`, archives base name `voxy-map` (so the built jar is `voxy-map-<version>.jar`, not
  `voxymap-<version>.jar` - check `build/libs/` by that name).
- **Two mod jars per build.** The main `voxy-map-<version>.jar` is the release build and has **no**
  VanillaSquad web-map player markers: `com/voxymap/client/playermarkers/**`, the separate `src/playerMarkers`
  source set that fetches player positions from the VanillaSquad web map, is not in it.
  `voxy-map-<version>-player-markers.jar` (task `playerMarkersJar`) is the same jar plus that source set. Client code reaches
  that module only through `integration/RemotePlayerMarkers`, which loads it by class name and falls back to
  "no markers" when it is absent. Never import `com.voxymap.client.playermarkers` from `src/client`;
  `ProjectContractTest` pins this; the `jar` task fails if a player-marker class leaks into the main jar, and
  `playerMarkersJar` fails if they are missing from the variant.

## Voxy shader coupling (fragile - read before touching `VoxyShaderPatcher`)

`VoxyShaderPatcher` string-patches Voxy's internal `quads3.vert`/`quads.frag` source (marker strings,
uniform locations `30` and `33`-`38`). The mod declares `voxy >= 0.2.18-beta` in `fabric.mod.json` and is
compiled against `voxy_version` in `gradle.properties` (`0.2.18-beta`). `0.2.19-beta` was checked by diffing:
the four shader files are byte-identical and every Voxy class and member VoxyMap uses is unchanged, except
that `VoxyConfig.useEnvironmentalFog` (boolean) became `fogMode` (String), which `VoxyBridge` already handles.
A newer Voxy can still change that layout, so re-check it (see below) before widening the claim.

- The patcher fails loudly (`patch()` throws `IllegalStateException`) by design - `VoxyShaderPatcherTest`
  pins this. Do not make `patch()` itself swallow the mismatch.
- The mixin call site uses `patchSafely()` instead, which catches that exception, disables the
  Nether roof removal and ore mask (`VoxyShaderPatcher.isSupported()` flips to `false`, checked by
  `MDICSectionRendererMixin` before touching their uniforms), and logs a warning - so an
  incompatible Voxy update degrades gracefully instead of crashing shader compilation for the whole
  client. Keep both halves in sync if you touch either.
- Every `@Inject` into a Voxy class carries `require = 0`: the dependency range is open-ended (`>=0.2.18-beta`), and a
  renamed Voxy method must switch the feature off, not stop the game from starting.
- After bumping `voxy_version`, re-verify the marker strings in `VoxyShaderPatcher` against the
  new Voxy jar's actual shader source before assuming the patch still applies.

## Anti-xray invariant (Nether roof removal)

The roof band is removed by discarding whole quads (`voxymapQuadMinY >= voxymapRoofCutY` in
`VoxyShaderPatcher`), never by clipping fragments at a plane: a plane slices straight through the
server's anti-xray decoy LODs and shows them as a tiled cross-section that looks like real terrain.
`ProjectContractTest` pins this. Do not add a per-fragment height cut, and do not add any feature that
treats client data the server may have withheld (unloaded or obfuscated chunks) as verified terrain or
open air - the removed room cutaway was exactly that kind of guess.

The ore mask follows the same rule from the other side: `OreMaskController` marks every Voxy model whose
block is a vanilla `minecraft:*_ore` or `minecraft:ancient_debris`, and the patched fragment shader paints
those models grey everywhere Voxy draws them, not only on the map. It has no setting; it switches off only
together with the Voxy shader patch (`VoxyShaderPatcher.isSupported()`) or when Voxy's `modelService` field
cannot be read.

## Mixin gating

`VoxyMapMixinPlugin` applies a mixin whose class name starts with `Iris` only when Iris is loaded, so Iris
classes are never touched without Iris. A new mixin that targets Iris must use that prefix; any other
optional target (Xaero) uses `@Pseudo` plus `require = 0`, as `XaeroGuiMapMixin` does.

## Minimap heads

Holding the player-list key swaps minimap dots for heads (`MinimapHeads`). Mob faces are not hardcoded:
while the minimap and its mobs are on, `LivingEntityRendererMixin` hands each rendered mob to
`MobHeadIcons`, which stores the `head` part's face rectangle per texture (at most 4096, then the cache
resets). Renderers with a single model get their face without being seen first; for the rest, a texture
variant never rendered this session keeps its dot. All of it catches `RuntimeException | LinkageError`
and falls back to the dot.

## Shader-pack fog patches (`ShaderPackFogPatcher`)

Fog suppression works by rewriting each supported pack's small fog include as Iris loads it
(`IrisIncludeProcessorMixin`), gated by the `voxymapMapView` uniform. Each pack is a `Flavor` of anchors
keyed by include path; a flavour applies only if every anchor is present. Two edit kinds: `Replace`
(scale a fog amount in front of where it is blended) and `EntryReturn` (return "no fog" as the first
statement of a fog function). When a pack updates, re-check its anchors against the new source; the
unit tests use excerpts, so they cannot notice a real layout change. A new pack means a new flavour,
its include path in the map, and a test excerpt.

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
