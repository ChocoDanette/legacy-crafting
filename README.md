# Legacy Crafting

In-world crafting for Minecraft 1.21.1 (NeoForge 21.1), made for the TerraFirmaCraft Legacy modpack. It works with or
without TerraFirmaCraft 4.2.

## How it works

1. Place items on the ground with the place key (V by default): a block holds four items, as a 2x2 grid. With
   TerraFirmaCraft, this is TFC's own key and placed items; without it, the mod places the items itself, and a right click
   on an item picks it up.
2. Look at the items: the result shows under the crosshair. When several recipes match, the mouse wheel chooses one.
   Press the craft key (R by default, "Craft" in the controls). The grid is read as seen from
   where you stand: the two quarters farther from you are the top row.
3. Stay within 3 blocks while the bar under the crosshair fills (1 to 5 seconds, depending on the recipe). Walking away
   or changing the items stops the craft.
4. Hold the craft key to craft again and again: the emptied quarters are refilled from your inventory with the same
   items, until one runs out. The result drops on the block. Ingredients are used up; remainders (a damaged tool, an empty bucket) stay in place.

Workbenches (the vanilla crafting table and TFC's workbenches) keep their items. Lay a 3x3 recipe, then press "Craft"
(or the craft key): the arrow fills over the same time, the window must stay open, and the result lands in the result
slot. The result shows above the arrow before crafting; when several recipes match, the arrows beside it (or the
mouse wheel) choose which one to craft. "-", "+", "--" and "++" set how many times to craft it in a row. The items of the grid show on
top of the workbench in the world.

Any 2x2 crafting recipe works, TFC's included. The inventory's own 2x2 crafting grid is removed: crafting happens in
the world.

## For pack makers

- Server config `legacycrafting-server.toml`: `groundPlacing` (true: without TFC, the place key puts items on the
  ground), base time, time per ingredient, minimum and maximum time, fixed times
  for some recipes (`recipeSeconds`), the distance at which the craft stops, and `disableInventoryCrafting` (true: the
  inventory has no 2x2 grid).
- Attribute `legacycrafting:crafting_time` (default 1) multiplies every craft time of a player. A "lazy" trait can add
  a modifier, for example:
  `/attribute @s legacycrafting:crafting_time modifier add legacy:lazy 0.5 add_multiplied_base` (1.5 times longer).
- Block tag `legacycrafting:crafting_surfaces`: blocks read as a 2x2 grid. It holds `tfc:placed_item`.
- Block tag `legacycrafting:workbenches`: blocks opened as workbenches that keep their items (`minecraft:crafting_table`
  and `#tfc:workbenches`). Server config `workbenchCrafting` turns this off.
- Crafts fire NeoForge's item crafted event, so KubeJS `ItemEvents.crafted` sees them.

## Build

Released versions are on the Releases page of this repository, each with its jar and changelog (see `CHANGELOG.md`).
GitHub Actions also builds a test jar on every push: open the latest run in the Actions tab and download
`legacycrafting-jar`. Local build: `gradle build` (Gradle 8.10+, Java 21). The jar is written to `build/libs/`.

## License

MIT, see `LICENSE`.
