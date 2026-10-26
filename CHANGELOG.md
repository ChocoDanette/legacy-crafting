# Changelog

Each version is published as a GitHub release with its jar. To release, set `mod_version` in
`gradle.properties`, add its section below, merge to `main`, then run the Release workflow
from the Actions tab (it creates the tag `v<mod_version>`), or push that tag yourself.

## 0.2.0

- The 2x2 crafting grid of the player's inventory is gone: its slots are locked, and the inventory screen no longer
  shows the grid, its "Crafting" title or the recipe book button. Items that the recipe book or another mod moves
  into the grid go back to the player. Server config `disableInventoryCrafting` (true by default) turns this off.

## 0.1.0

- Craft in the world: items placed on the ground with TFC's V key form a 2x2 grid. The craft key (R) starts the
  matching crafting recipe; the player stays within 3 blocks for 1 to 5 seconds (1 s + 0.5 s per ingredient, clamped),
  with a progress bar under the crosshair. The result drops on the block; remainders stay in place.
- Attribute `legacycrafting:crafting_time` multiplies a player's craft times, for traits such as "lazy".
- Server config for the times, fixed times per recipe, and the distance.
