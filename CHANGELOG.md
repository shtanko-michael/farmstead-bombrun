# Changelog

All notable changes to Bomb Run. Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

- Design plan ([PLAN.md](PLAN.md)) and repository setup.
- First raid on NeoForge 1.21.1: Strike Remote with a target ring, a squadron flying a deterministic plan, vanilla TNT
  that explodes on impact, `/bombrun strike | cancel | undo | snapshots | raids | stats | reload`.
- Strike power on the remote (Ctrl + wheel): five levels from a light strike (1 plane) to the apocalypse (12 planes,
  bombs every 1.6 blocks); the server sends its levels and limits to the remote. Up to 5000 bombs per raid.
- Explosion budget that adapts to the server's tick time; falling bombs ignore other blasts; broken blocks do not drop
  items (both configurable).
- Blasts seen and heard from up to 512 blocks: glowing fireball, flames, embers, smoke columns, a boom delayed by
  distance and camera shake. Own engine, whistle, siren and remote sounds.
- Undo puts back sand over caves and water exactly as it was.
- Textured fire: each blast is a cluster of animated flame puffs (white-hot core → orange flame → embers → smoke).
- A raid ends only when its bombs have gone off (at most a minute after the planes left; leftovers are removed),
  and a bomb from a finished raid never comes back with its chunk — before, bombs lying in unloaded chunks went off
  during `/bombrun undo` and wrecked the restored area.
- Planes appear and leave `strike.approachDistance` (520) blocks from the target, beyond their render distance:
  they fly in from the horizon instead of popping up in the sky.
- Contrails: two white trails behind every plane that widen, sink and fade (`render.contrails`, `render.contrailSeconds`).
