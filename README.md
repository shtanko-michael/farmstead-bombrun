# Bomb Run

**A squadron of planes on call.** Aim the remote at the ground: a red target ring lights up on the terrain and rooftops. Confirm, the siren wails, a flight of planes comes in from the horizon, opens its bomb bays and lays TNT as a carpet, a circle or a grid. `/bombrun undo` puts back everything the raid destroyed.

![Status: in development](https://img.shields.io/badge/status-in%20development-orange)
[![License: MIT](https://img.shields.io/badge/code-MIT-blue)](LICENSE)

By [Farmstead](https://farmsteadminecraft.online). Русская версия — [README.ru.md](README.ru.md).

> **Work in progress.** Nothing is released yet. Bomb Run is a working title. This page describes what the mod is being built to do; the full design is in [PLAN.md](PLAN.md) (in Russian).

## See it

GIFs and a gallery come with the first release.

## Features (planned)

- **A visible squadron.** Cartoon planes of our own, with spinning propellers, contrails, opening bomb bays, a soft shadow on the ground and engine sound with a Doppler shift. No real-world army markings.
- **Aim with a ring.** The target ring follows the terrain and rooftops. Mouse wheel sets the radius, Alt+wheel the approach heading, left click confirms.
- **The remote console.** Right click opens a briefcase-style console: a top-down map, presets, and a red button under a flip-up cover.
- **Drop patterns:** carpet, circle, random within the radius; later ring, grid, line and spiral.
- **Vanilla TNT.** Bombs are ordinary TNT tagged by the mod, so explosion mods, optimisers and land claims keep working.
- **Undo.** Before a raid the target area is saved; `/bombrun undo` restores it in batches.
- **Server friendly.** Caps on live bombs and explosions per tick, LuckPerms permissions with per-player limits. In server mode players without the mod still see the TNT fall.
- **Cameras for filming:** behind the bomb, from the cockpit, satellite view.

For 1.0: three aircraft (a light attack plane, a heavy bomber and an airship), payloads from cluster and incendiary bombs to anvils, parachuting creepers and fireworks, and a survival mode with a craftable remote and cooldowns.

## Downloads and versions

Not released yet. Planned targets:

| Milestone | Minecraft | Loader |
| --- | --- | --- |
| M1 | 1.21.1 | NeoForge |
| M3 | 1.21.1 | + Fabric |
| M5 | 1.20.1, 1.21.x, 26.x | Fabric, NeoForge, Forge (1.20.1) |

Releases will be published on Modrinth, CurseForge and GitHub Releases.

**Requirements: none planned.** No GeckoLib or other libraries.

**Server or client?** Both. A client with the mod on a server without it can use the remote in singleplayer only.

## Commands (planned)

| Command | What it does |
| --- | --- |
| `/bombrun strike <pos> [radius] [preset\|json]` | A raid without the remote (scenes, command blocks, RCON) |
| `/bombrun cancel [raidId\|all]` | Planes turn back, bombs not yet dropped are cancelled |
| `/bombrun undo [raidId]` / `/bombrun snapshots` | Undo a raid / list saved snapshots |
| `/bombrun preset save\|load\|list\|delete <name>` | Presets on the server |
| `/bombrun stats` / `/bombrun reload` | Statistics / reload config and data |

Settings, permissions and limits are listed in [PLAN.md](PLAN.md).

## Roadmap

| Milestone | What |
| --- | --- |
| M0 | Project skeleton, branding, update check, config |
| M1 | Flight plan, one plane, vanilla TNT, target ring, remote, sound, `strike` / `undo` |
| M2 | Console with map, presets, three planes and an airship, all payloads and patterns, cameras, explosion budget |
| M3 | Fabric, permissions, survival mode, claims, server mode for vanilla clients, languages |
| M4 | Ports to every version, test matrix, compatibility |
| M5 | Final assets, media, Modrinth and CurseForge listings, 1.0 |

## License

Code: [MIT](LICENSE). Assets (textures, images, video): [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/).

Made by [Farmstead](https://farmsteadminecraft.online). Bugs and ideas: [GitHub Issues](https://github.com/shtanko-michael/farmstead-bombrun/issues).
