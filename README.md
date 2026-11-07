# Cutebear's furniture

Fork of [cutebear/CutebearFurniture](https://codeberg.org/cutebear/CutebearFurniture) that adds wood, wool, concrete and gold variants, 85 more pieces from Paladin's Furniture, and makes placed furniture solid. Use it with the matching [FurnitureResource fork](https://github.com/lzlrd/FurnitureResource).

## A furniture plugin for papermc.

### Resources came from [paladins-furniture](https://github.com/UnlikePaladin/paladins-furniture)

Geyser is not supported due to having no good way of doing item display, bedrock not supported on Linux (yes I know there are third party method but google play does not want to work), bedrock being very hard to develop as there are no debug mode or mods, if you have a geyser server, you should still install [GeyserDisplayEntity](https://github.com/GeyserExtensionists/GeyserDisplayEntity) to make sure bedrock player could interact with stuff even if they do not display properly.

~~Might add Hytale support if someone made a server compability layer and it is good~~


## Install:

1. download the jar from [Releases](https://github.com/lzlrd/CutebearFurniture/releases) or build this plugin

2. download `cutebear_furniture.zip` from [FurnitureResource releases](https://github.com/lzlrd/FurnitureResource/releases) (or build [it](https://github.com/lzlrd/FurnitureResource)) and put it as server side resource pack

3. install the plugin

4. (Geyser only) Install [GeyserDisplayEntity](https://github.com/GeyserExtensionists/GeyserDisplayEntity) in geyser extensions and add [pack](https://github.com/GeyserExtensionists/GeyserDisplayEntity/blob/main/packs/GeyserDisplayEntityPack.mcpack) to geyser's pack, then add `cutebear_furniture.mcpack` to geyser's `packs` and `cutebear_furniture.json` to geyser's `custom_mappings` from the same FurnitureResource release


## Player guide:

Furniture could be made in the stonecutter

| Input | Furniture |
|---|---|
| Any log, wood, stem or hyphae (stripped or not) | Chair, Dinner Table, Coffee Table, Classic Desk, Classic Stool, Classic Chair, Modern Chair, Dinner Chair in that wood |
| Any wool | Arm Chair, Simple Sofa and Classic Oak Chair in that colour |
| Any carpet | Shower Towel in that colour |
| Any concrete | Trash Can in that colour |
| Green, pink, light blue, blue, orange or yellow concrete | Froggy Chair in that colour |
| White or gray concrete | Fridge, Freezer, Stove, Oven Range Hood and Modern Light Pendant in that colour |
| Black Concrete | Xbox Series X Fridge |
| Iron Block | Iron Fridge, Iron Freezer, Iron Stove, Iron Oven Range Hood, Iron Microwave, Iron Toaster, Kitchen Stovetop |
| Leather | Leather Armchair |
| Glowstone | Simple Ceiling Light |
| Glass | Glass Modern Light Pendant |
| Glass Pane | White Mirror, Gray Mirror |
| Lever | Light Switch |
| Iron Bars | Basic Shower Head, Basic Shower Handle, Mesh Trashcan |
| Bowl | Basic Plate |
| Iron Nugget | Basic Cutlery |
| Paper | Toilet Paper |
| Crafting Table | Furniture Workbench |
| Quartz Block | BathTub, Sink, Toilet |
| Gold Block | Gold BathTub, Gold Sink, Gold Toilet |

Wood types: oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, pale oak, poplar, crimson and warped. Types missing from the server's Minecraft version are skipped. Check [here](src/main/java/dev/_0w0b/cutebearFurniture/CutebearFurniture.java) if this is out of date.

Most furniture goes on top of a block and faces you. Shower towels, oven range hoods, mirrors, the light switch, shower head and handle, and toilet paper go on the side of a block, backed against it. The ceiling light and light pendants go under a block and give off light level 15. The pieces from Paladin's Furniture are decorative: fridges, freezers, stoves and the workbench don't open. [furniture.json](src/main/resources/furniture.json), which FurnitureResource's `tools/import_pfm.py` writes, lists each of these pieces with its input and placement.

Placed furniture is solid: barrier blocks fill the space it stands in, two blocks for a bathtub and two high for the Xbox fridge. Wall and ceiling pieces are not solid, nor are the microwave, toaster, stovetop, plate and cutlery. Clicking the barriers works like clicking the furniture: left click breaks it, right click sits on chairs, stools, toilets, sofas and armchairs. Furniture placed with an older version gets its barriers the next time its chunk loads, and a bathtub placed with 1.7, which put its second barrier on the side away from the model, gets that barrier moved. If something removes a piece's entities directly, such as `/kill`, its barriers stay behind; break them in creative.
