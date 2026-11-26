# MapMaker (Fabric 1.21.11, client-side)

Turn any image into map art and save it as a `.nbt` schematic for Litematica.

## Build (no local setup)
1. Push this folder to a GitHub repo.
2. Actions tab -> "build" -> download the `mapmaker-jar` artifact.
3. Put the jar in `mods/` (needs Fabric API).

If the build complains about a version, copy `minecraft_version`, `yarn_mappings`,
`loader_version` and `fabric_version` from your other mod's `gradle.properties`.

## Use
- Open with `/mapmaker`, or bind a key inside the screen (Key button).
- Paste (Ctrl+V), Upload..., or drag an image onto the window.
- Width/Height are in maps (128 blocks each).
- Block types (can be mixed): Full Blocks, Carpet, Candles, Resin, Leaves (leaf litter).
  Where colours overlap, the special type wins and full blocks fill the gaps.
  Blocks... lets you swap the block used for each map colour or switch a colour off.
- Mode: Flat (1 shade per colour) or Staircase (3 shades, varying heights).
- Base: the block placed under carpet/candles/resin/leaves (they need something to sit on).
- Save .nbt writes to `<game dir>/schematics/`; load it in Litematica.

## Placing it
The schematic has one extra row at the north edge. That is the reference row the map
shades against, so place the schematic with that row one block north of the map's top edge.
