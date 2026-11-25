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
- Contrails: two white trails behind every plane that widen, sink and fade (`render.contrails`, `render.contrailSeconds`).
