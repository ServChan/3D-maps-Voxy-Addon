# VoxyMap LTS

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.3+-DBD0B4)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-E76F00)](https://adoptium.net/)
[![Client](https://img.shields.io/badge/side-client-4C8BF5)](#requirements)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

VoxyMap LTS is a maintained client-side fork of [3D Maps Voxy Addon](https://github.com/raben12paste/3D-maps-Voxy-Addon). It opens a full-screen 3D map backed by Voxy LOD data for Minecraft 26.2.

## Features

- Full-screen Voxy terrain view opened with a configurable key, `M` by default.
- Nether roof strip based on the dimension's logical ceiling.
- Mouse dragging, middle-button rotation, cursor-centered zoom, and smooth keyboard movement.
- Exact player centering plus projected markers for the local player and other loaded players.
- Optional VanillaSquad global player markers with compact skin heads and clustered territory outlines.
- Connectivity-aware room cutaway computed in bounded slices outside the render-state loop.
- Stable near terrain: while the map is open, Voxy's cached LODs fill its normal player-centered hand-off zone instead of using Sodium's angle-dependent real-camera visibility set.
- Optional singleplayer pause and adjustable camera speed.
- Optional handoff to Xaero's World Map with a return button.
- English, Russian, Spanish (Spain and Mexico), and Polish translations.

## Requirements

- Minecraft `26.2`.
- Java `25`.
- Fabric Loader `0.19.3` or newer.
- Fabric API.
- Voxy `0.2.18-beta` exactly. The cutaway shader integration targets this internal shader layout.

Iris and Xaero's World Map are optional integrations. The mod is client-only and does not need to be installed on the server.

## Controls

| Control | Action |
| --- | --- |
| `M` | Open or close the map; the opening binding can be changed in Controls |
| Left mouse drag | Move the map |
| Middle mouse drag | Rotate the camera |
| Mouse wheel / `+` / `-` | Zoom toward the cursor |
| `W/A/S/D` or arrow keys | Move the camera |
| `Shift` | Move faster |
| `C` | Center on the player |
| `Esc` | Close the map |

Settings are stored in `config/voxymap.properties`. Writes use a sibling temporary file and keep `voxymap.properties.bak` for recovery.

## Build

The build targets Minecraft 26.2 and produces the distributable JAR:

```powershell
.\gradlew.bat clean build --warning-mode all
```

The normal non-sources JAR is written to `build/libs/voxy-map-<version>.jar`.

## Known limitations

- Room cutaway only removes a locally verified enclosed ceiling; unloaded or uncertain block data disables the local cut rather than guessing.
- The Nether roof strip uses the dimension's logical ceiling. Only terrain already present in Voxy's cache can be shown.
- VoxyMap only displays terrain already available to Voxy; it does not load chunks or reveal unexplored server data.
- Optional distant VanillaSquad markers come from that server's public web-map endpoints and are disabled by default.
- Build and package checks do not replace an in-game test with the exact Voxy, Iris, Sodium, and Xaero versions in the target profile.

## Русский

VoxyMap LTS — поддерживаемый клиентский форк 3D Maps Voxy Addon для Minecraft 26.2. Он открывает полноэкранную трёхмерную карту на данных LOD из Voxy.

Карта открывается клавишей `M`; назначение можно изменить в стандартных настройках управления. ЛКМ перемещает карту, СКМ поворачивает камеру, колесо меняет масштаб, `W/A/S/D` двигают камеру, `Shift` ускоряет, `C` точно возвращает центр к игроку. На карте отображаются отдельная отметка текущего игрока и именные отметки других загруженных игроков. В ближней зоне карта использует уже кэшированные LOD Voxy, поэтому её геометрия не зависит от направления обычной камеры игрока и не вызывает дополнительную загрузку чанков. В настройках можно включить срез замкнутых помещений, удалённых игроков VanillaSquad и контуры территорий по флагам. Настройки сохраняются в `config/voxymap.properties`, рядом хранится резервная копия.

## Credits and license

Original project and implementation: [raben12paste](https://github.com/raben12paste). LTS maintenance and Minecraft 26.x compatibility: `LTS_Server`.

Licensed under the MIT License. See [LICENSE](LICENSE).
