# VoxyMap runtime regression checklist

Run this checklist against the exact JAR intended for release in the Everything Voxy Minecraft 26.2
profile. A successful Gradle build is required first, but does not replace these checks.

## Startup and compatibility

- Confirm exactly one `voxy-map-*.jar` is active and its SHA-256 matches `build/libs`.
- Start with Voxy 0.2.18-beta, Iris and the normal shader pack; inspect `latest.log` for VoxyMap,
  mixin, shader and OpenGL errors.
- Open Settings and confirm Diagnostics reports that all integrations are ready.
- Toggle the shader pack off and on, reopen the map and verify terrain and ore masking recover.

## Full-screen map

- Open and close the map repeatedly; pan, rotate, zoom at the cursor and center on the player.
- Test a small window or large GUI scale: scroll the settings panel and confirm rows neither draw nor
  receive clicks outside the panel.
- Place a waypoint on terrain, a cave surface and a multi-level build. Rename, delete and undo it.
- Search and operate every object type in the Objects panel; test player follow and manual cancellation.

## Rendering risk cases

- Nether: verify whole-quad roof removal, the close-range inspection aperture and room cutaway around
  the player without exposing unloaded/unknown columns.
- Solas rain: verify distant terrain remains visible and `latest.log` has no program-texture errors.
- Dense player group: pan and zoom through the group; labels must remain stable, collapse to `name +N`
  and expand on hover without flicker.
- HappyGhastScanner: display a long, wide spiral and confirm no line streaks or severe frame-time drop.
- VanillaSquad: test hidden local player resolution plus Build, Farm and Art manual world selections.

## Minimap and lifecycle

- Cross chunk and dimension boundaries; verify texture, mobs, waypoints, rotation, zoom and chunk grid.
- Die twice and verify only the newest death waypoint remains without a visible tick hitch.
- Disconnect while sharing or receiving a map and verify the transfer stops immediately.

## Two-client sync and map sharing

- Test read-only and read/write waypoint sync, wrong password, timeout and recovery.
- Share a bounded region between independently explored copies of the same world. Confirm the size
  prompt appears before transfer and existing receiver sections are not overwritten.
- Send malformed/unsupported protocol data in an isolated test environment and confirm the receiver
  reports an error without excessive allocation or partial uncontrolled import.

Record the Minecraft log, screenshots for the rendering cases and the artifact SHA-256 with the release.
