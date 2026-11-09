package dev._0w0b.cutebearFurniture;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Barrier blocks that make placed furniture solid, light blocks that make lamps shine and structure voids that make
 * ladders climbable.
 *
 * Each piece's interaction entity stores the blocks it placed as offsets from its own block,
 * so removing the piece clears exactly those blocks and nothing a player built there.
 */
final class FurnitureBarriers {
    // Pieces placed or upgraded by this version sit their interaction on the floor of their block, sized to nothing
    // when a barrier fills that block, and give lamps a barrier with the light above.
    static final int VERSION = 3;

    private FurnitureBarriers() {
    }

    static NamespacedKey barriersKey() {
        return new NamespacedKey(CutebearFurniture.getInstance(), "barriers");
    }

    static NamespacedKey furnitureKey() {
        return new NamespacedKey(CutebearFurniture.getInstance(), "furniture");
    }

    static NamespacedKey lightsKey() {
        return new NamespacedKey(CutebearFurniture.getInstance(), "lights");
    }

    // Set by 1.8 and 1.9 on pieces whose barriers follow the corrected facing below. Version 3 replaces it.
    static NamespacedKey footprintKey() {
        return new NamespacedKey(CutebearFurniture.getInstance(), "footprint_v2");
    }

    static NamespacedKey versionKey() {
        return new NamespacedKey(CutebearFurniture.getInstance(), "version");
    }

    static NamespacedKey climbKey() {
        return new NamespacedKey(CutebearFurniture.getInstance(), "climb");
    }

    // The display of a piece, or null if the interaction isn't a piece's or its display isn't loaded.
    static ItemDisplay display(Interaction interaction) {
        String uuid = interaction.getPersistentDataContainer().get(furnitureKey(), PersistentDataType.STRING);
        if (uuid == null)
            return null;
        return Bukkit.getEntity(UUID.fromString(uuid)) instanceof ItemDisplay display ? display : null;
    }

    // The direction a piece's model x axis points in, from the yaw its display got at placement. Item displays turn
    // their model half a turn, so at yaw 0 the model's x axis points west.
    static BlockFace facing(float yaw) {
        return switch (Math.floorMod(Math.round(yaw / 90f), 4)) {
            case 1 -> BlockFace.NORTH;
            case 2 -> BlockFace.EAST;
            case 3 -> BlockFace.SOUTH;
            default -> BlockFace.WEST;
        };
    }

    // The direction a wall piece faces, away from the wall it hangs on, from its display's yaw: the inverse of the
    // yaw placement gives it.
    static BlockFace wallFace(ItemDisplay display, FurnitureTypes.Type type) {
        int base = Math.floorMod(Math.round(display.getYaw()) - type.yawOffset(), 360);
        BlockFace from = switch (Math.floorMod(Math.round(base / 90f), 4)) {
            case 1 -> BlockFace.SOUTH;
            case 2 -> BlockFace.WEST;
            case 3 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
        return from.getOppositeFace();
    }

    // The direction a piece's model z axis points in: a quarter turn clockwise from its x axis, seen from above.
    private static BlockFace zFacing(float yaw) {
        return switch (facing(yaw)) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    // The blocks a piece covers: its own, plus the second block of a bathtub, which is two blocks long. Imported
    // pieces list theirs in furniture.json, and wall and ceiling pieces have none.
    static List<Block> footprint(Block origin, ItemDisplay display) {
        List<Block> blocks = new ArrayList<>();
        FurnitureTypes.Type type = FurnitureTypes.of(display);
        if (type != null) {
            BlockFace x = facing(display.getYaw());
            BlockFace z = zFacing(display.getYaw());
            for (int[] offset : type.footprint())
                blocks.add(origin.getRelative(
                        x.getModX() * offset[0] + z.getModX() * offset[2],
                        offset[1],
                        x.getModZ() * offset[0] + z.getModZ() * offset[2]));
            return blocks;
        }
        blocks.add(origin);
        ItemStack item = display.getItemStack();
        if (item != null && item.hasData(DataComponentTypes.ITEM_MODEL)
                && String.valueOf(item.getData(DataComponentTypes.ITEM_MODEL).key()).endsWith("bathtub"))
            blocks.add(origin.getRelative(facing(display.getYaw())));
        return blocks;
    }

    // Fills the empty blocks a piece covers with barriers, records them on its interaction and fits the interaction
    // to them. A lamp is solid with its light in the block above; with that block taken it shines from its own
    // block instead and stays see-through. A ladder gets a structure void in its own block, which the plugin's data
    // pack makes climbable.
    static void place(Interaction interaction, ItemDisplay display, Predicate<Block> allowed) {
        Block origin = interaction.getLocation().getBlock();
        List<Block> blocks = footprint(origin, display);
        FurnitureTypes.Type type = FurnitureTypes.of(display);
        Block light = null;
        if (type != null && type.light() > 0) {
            Block above = origin.getRelative(BlockFace.UP);
            boolean solid = blocks.contains(origin) && origin.isEmpty() && allowed.test(origin);
            if (solid && above.isEmpty() && allowed.test(above)) {
                light = above;
            } else {
                blocks.remove(origin);
                light = origin;
            }
        }
        List<Integer> offsets = new ArrayList<>();
        boolean solidOrigin = false;
        for (Block block : blocks) {
            if (!block.isEmpty() || !allowed.test(block))
                continue;
            block.setType(Material.BARRIER, false);
            offsets.add(block.getX() - origin.getX());
            offsets.add(block.getY() - origin.getY());
            offsets.add(block.getZ() - origin.getZ());
            solidOrigin |= block.equals(origin);
        }
        PersistentDataContainer data = interaction.getPersistentDataContainer();
        data.set(barriersKey(), PersistentDataType.INTEGER_ARRAY, offsets.stream().mapToInt(Integer::intValue).toArray());
        data.remove(footprintKey());
        data.remove(lightsKey());
        if (light != null && light.isEmpty()) {
            light.setType(Material.LIGHT, false);
            if (light.getBlockData() instanceof Light level) {
                level.setLevel(Math.min(type.light(), level.getMaximumLevel()));
                light.setBlockData(level, false);
            }
            data.set(lightsKey(), PersistentDataType.INTEGER_ARRAY, new int[]{
                    light.getX() - origin.getX(), light.getY() - origin.getY(), light.getZ() - origin.getZ()});
        }
        data.remove(climbKey());
        if (type != null && type.climb() && origin.isEmpty() && allowed.test(origin)) {
            origin.setType(Material.STRUCTURE_VOID, false);
            data.set(climbKey(), PersistentDataType.INTEGER_ARRAY, new int[]{0, 0, 0});
        }
        data.set(versionKey(), PersistentDataType.INTEGER, VERSION);
        fit(interaction, origin, solidOrigin);
    }

    // Puts the interaction on the floor of the piece's block. Over a barrier it has no size, so clicks reach the
    // barrier, which takes a block placed against its top like any other block, on Java and through Geyser alike. A
    // piece without one keeps a one-block interaction to be clicked through.
    private static void fit(Interaction interaction, Block origin, boolean solid) {
        Location floor = origin.getLocation().add(0.5, 0, 0.5);
        floor.setYaw(interaction.getLocation().getYaw());
        if (!interaction.getLocation().equals(floor))
            interaction.teleport(floor);
        interaction.setInteractionWidth(solid ? 0f : 1f);
        interaction.setInteractionHeight(solid ? 0f : 1f);
    }

    // Removes the barriers, light and climbing block a piece placed, leaving any other block that is there now.
    static void remove(Interaction interaction) {
        Block origin = interaction.getLocation().getBlock();
        PersistentDataContainer data = interaction.getPersistentDataContainer();
        clear(origin, data.get(barriersKey(), PersistentDataType.INTEGER_ARRAY), Material.BARRIER);
        clear(origin, data.get(lightsKey(), PersistentDataType.INTEGER_ARRAY), Material.LIGHT);
        clear(origin, data.get(climbKey(), PersistentDataType.INTEGER_ARRAY), Material.STRUCTURE_VOID);
    }

    private static void clear(Block origin, int[] offsets, Material material) {
        for (Block block : blocks(origin, offsets)) {
            if (block.getType() == material)
                block.setType(Material.AIR, false);
        }
    }

    // The blocks at a list of offsets from a piece's block.
    static List<Block> blocks(Block origin, int[] offsets) {
        List<Block> blocks = new ArrayList<>();
        if (offsets == null)
            return blocks;
        for (int i = 0; i + 2 < offsets.length; i += 3)
            blocks.add(origin.getRelative(offsets[i], offsets[i + 1], offsets[i + 2]));
        return blocks;
    }

    // The barriers a piece placed.
    static List<Block> barriers(Interaction interaction) {
        return blocks(interaction.getLocation().getBlock(),
                interaction.getPersistentDataContainer().get(barriersKey(), PersistentDataType.INTEGER_ARRAY));
    }

    // The climbing block a ladder placed, or null for any other piece.
    static Block climb(Interaction interaction) {
        List<Block> blocks = blocks(interaction.getLocation().getBlock(),
                interaction.getPersistentDataContainer().get(climbKey(), PersistentDataType.INTEGER_ARRAY));
        return blocks.isEmpty() ? null : blocks.getFirst();
    }

    // The interaction of the piece a barrier or ladder's climbing block belongs to, or null for any other block.
    static Interaction owner(Block block) {
        return owner(block, block.getType());
    }

    // The same for a block that was, or is about to be, the given material, such as one being built over.
    static Interaction owner(Block block, Material material) {
        if (material != Material.BARRIER && material != Material.STRUCTURE_VOID)
            return null;
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(centre, 2, 2, 2, e -> e instanceof Interaction)) {
            Interaction interaction = (Interaction) entity;
            if (material == Material.BARRIER ? barriers(interaction).contains(block) : block.equals(climb(interaction)))
                return interaction;
        }
        return null;
    }

    // Brings a piece placed by an older version up to this one, once: barriers for pieces from before they existed,
    // the barrier 1.7 put on the wrong side of a bathtub moved, footprints, lamps and ladders as furniture.json now
    // has them, and the interaction moved down out of the way of the block above.
    static void upgrade(Interaction interaction) {
        PersistentDataContainer data = interaction.getPersistentDataContainer();
        if (data.getOrDefault(versionKey(), PersistentDataType.INTEGER, 0) >= VERSION)
            return;
        ItemDisplay display = display(interaction);
        if (display == null)
            return;
        Block origin = interaction.getLocation().getBlock();
        remove(interaction);
        place(interaction, display, block -> true);
    }
}
