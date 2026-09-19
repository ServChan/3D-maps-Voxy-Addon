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
  receive clicks outside the panel. Confirm each setting sits under its group header and that clicking a
  row changes that row's setting (rows are hit-tested by catalog entry, not by position).
- Open the Mod Menu screen from the title screen and from a world: it must scroll (wheel, PageUp/PageDown,
  Tab to a control below the fold), keep the Done button reachable at a small window / large GUI scale,
  and show the same groups as the map panel minus the per-world rows. The speed slider must change the
  in-map camera speed.
- Place a waypoint on terrain, a cave surface and a multi-level build. Rename, delete and undo it.
- Search and operate every object type in the Objects panel; test player follow and manual cancellation.

## Rendering risk cases

- Nether: verify whole-quad roof removal and the close-range inspection aperture, with the map showing
  terrain instead of a flat fog colour (Complementary: Fog suppression on).
- Fog suppression, once per shader pack (Complementary, Rethinking Voxels, Solas, BSL, Mellow, Photon):
  distant terrain stays clear in the Overworld and Nether, `latest.log` has no shader compile or
  program-texture errors, and the mode "Nether only" leaves the Overworld fog alone.
- View direction: the wedge and arrow follow the player's yaw, match the game's real horizontal FOV, and
  stay inside the screen when the player is at the edge of the view or far off screen.
- F2 on the map in both screenshot modes: the saved file has no panels/HUD/chat, keeps the markers only
  in "With markers", and the interface comes back on the next frame.
- VanillaSquad: the VanillaSquad settings and markers appear only while connected to VanillaSquad
  (not on other servers or in singleplayer), including in the Mod Menu screen.
- Dense player group: pan and zoom through the group; labels must remain stable, collapse to `name +N`
  and expand on hover without flicker.
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
