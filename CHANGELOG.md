# Changelog

Each version is published as a GitHub release with its jar. To release, set `mod_version` in
`gradle.properties`, add its section below, merge to `main`, then run the Release workflow
from the Actions tab (it creates the tag `v<mod_version>`), or push that tag yourself.

## 0.5.0

- The mod works without TerraFirmaCraft. Without TFC, it places items on the ground itself: the place key (V by
  default, "Place item on the ground" in the controls) puts one item of the main hand in the quarter of the block the
  player looks at, four items per block (`legacycrafting:placed_items`). A right click on a quarter picks its item
  up; the block drops its items when broken and goes away with its last item. With TFC, TFC's placed items are used as
  before and the key is not registered, so the two never compete for V. Server config `groundPlacing` (true by default)
  turns it off.
- Holding the craft key down repeats the craft: when a craft on the ground ends, each quarter it emptied gets the same
  item again from the player's inventory, and the same recipe starts again. It stops when the key is released or an
  item runs out.
- Crafting sounds and visuals: the sound depends on the ingredients (wood: an axe stripping a log; blocks: their hit
  sound; other items: rustling leather), the player's arm swings, and bits of the ingredients fly off the ground.
- No more messages on screen: a craft that cannot start or stops just ends quietly.
- Workbench window: the progress fills the arrow like a furnace's. A quantity, set with "-" and "+" (one less, one
  more), "--" (1) and "++" (as many as the grid allows, tools aside, 64 at most): the Craft button, now above the
  result, crafts the recipe that many times in a row, and the number counts down while it works.
- The items laid in a workbench's grid show on top of the block, for every player who sees it.
- Released under the MIT license (`LICENSE`).
- The mod list shows a logo and links to the source and the issue tracker.

## 0.4.0

- Workbench preview: the result of the grid shows above the arrow before crafting, with its tooltip. When the grid
  matches several recipes, the arrows beside it (or the mouse wheel over it) choose which one the Craft button makes.
- Ground preview: looking at items placed on the ground shows the result under the crosshair. When several recipes
  match, "< 1/3 >" shows beside it and the mouse wheel chooses which one the craft key makes (the wheel then no
  longer changes the hotbar slot).

## 0.3.0

- Workbenches (vanilla crafting table and TFC workbenches, tag `legacycrafting:workbenches`) keep their items: what is
  left in the 3x3 grid or the result slot stays there, saved with the chunk, and drops when the workbench is broken
  or blown up. The "Craft" button (or the craft key) starts the recipe, which takes the same time as on the ground;
  the progress fills the arrow, and closing the window or changing the grid stops it. The result goes to the result
  slot, or to the player when the slot holds something else. Server config `workbenchCrafting` (true by default).
- Clicking where the inventory's crafting arrow was no longer opens JEI's recipes.

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
