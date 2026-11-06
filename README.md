# Cutebear's furniture

Fork of [cutebear/CutebearFurniture](https://codeberg.org/cutebear/CutebearFurniture) that adds wood, wool and concrete variants. Use it with the matching [FurnitureResource fork](https://github.com/lzlrd/FurnitureResource).

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
| Any wool | Arm Chair in that colour |
| Any concrete | Trash Can in that colour |
| Quartz Block | BathTub, Sink, Toilet |

Wood types: oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, pale oak, poplar, crimson and warped. Types missing from the server's Minecraft version are skipped. Check [here](src/main/java/dev/_0w0b/cutebearFurniture/CutebearFurniture.java) if this is out of date.


