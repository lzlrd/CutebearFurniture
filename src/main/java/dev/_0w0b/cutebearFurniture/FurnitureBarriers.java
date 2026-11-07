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
 * Barrier blocks that make placed furniture solid.
 *
 * Each piece's interaction entity stores the barriers it placed as offsets from its own block,
 * so removing the piece clears exactly those blocks and nothing a player built there.
 */
final class FurnitureBarriers {
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

    // Set on pieces whose barriers follow the corrected facing below.
    static NamespacedKey footprintKey() {
        return new NamespacedKey(CutebearFurniture.getInstance(), "footprint_v2");
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

    // The direction a piece's model z axis points in: a quarter turn clockwise from its x axis, seen from above.
    private static BlockFace zFacing(float yaw) {
        return switch (facing(yaw)) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private static FurnitureTypes.Type type(ItemDisplay display) {
        ItemStack item = display.getItemStack();
        if (item == null || !item.hasData(DataComponentTypes.ITEM_MODEL))
            return null;
        return FurnitureTypes.get(String.valueOf(item.getData(DataComponentTypes.ITEM_MODEL).key()));
    }

    // The blocks a piece covers: its own, plus the second block of a bathtub, which is two blocks long. Imported
    // pieces list theirs in furniture.json, and wall and ceiling pieces have none.
    static List<Block> footprint(Block origin, ItemDisplay display) {
        List<Block> blocks = new ArrayList<>();
        FurnitureTypes.Type type = type(display);
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

    // Fills the empty blocks a piece covers with barriers and records them on its interaction. Lamps get a light
    // block in their own block instead.
    static void place(Interaction interaction, ItemDisplay display, Predicate<Block> allowed) {
        Block origin = interaction.getLocation().getBlock();
        List<Integer> offsets = new ArrayList<>();
        for (Block block : footprint(origin, display)) {
            if (!block.isEmpty() || !allowed.test(block))
                continue;
            block.setType(Material.BARRIER, false);
            offsets.add(block.getX() - origin.getX());
            offsets.add(block.getY() - origin.getY());
            offsets.add(block.getZ() - origin.getZ());
        }
        PersistentDataContainer data = interaction.getPersistentDataContainer();
        data.set(barriersKey(), PersistentDataType.INTEGER_ARRAY, offsets.stream().mapToInt(Integer::intValue).toArray());
        data.set(footprintKey(), PersistentDataType.BYTE, (byte) 1);
        FurnitureTypes.Type type = type(display);
        if (type != null && type.light() > 0 && origin.isEmpty()) {
            origin.setType(Material.LIGHT, false);
            if (origin.getBlockData() instanceof Light light) {
                light.setLevel(Math.min(type.light(), light.getMaximumLevel()));
                origin.setBlockData(light, false);
            }
            data.set(lightsKey(), PersistentDataType.INTEGER_ARRAY, new int[]{0, 0, 0});
        }
    }

    // Removes the barriers and light a piece placed, leaving any other block that is there now.
    static void remove(Interaction interaction) {
        Block origin = interaction.getLocation().getBlock();
        clear(origin, interaction.getPersistentDataContainer().get(barriersKey(), PersistentDataType.INTEGER_ARRAY), Material.BARRIER);
        clear(origin, interaction.getPersistentDataContainer().get(lightsKey(), PersistentDataType.INTEGER_ARRAY), Material.LIGHT);
    }

    private static void clear(Block origin, int[] offsets, Material material) {
        if (offsets == null)
            return;
        for (int i = 0; i + 2 < offsets.length; i += 3) {
            Block block = origin.getRelative(offsets[i], offsets[i + 1], offsets[i + 2]);
            if (block.getType() == material)
                block.setType(Material.AIR, false);
        }
    }

    // The interaction of the piece a barrier belongs to, or null for any other block.
    static Interaction owner(Block block) {
        if (block.getType() != Material.BARRIER)
            return null;
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(centre, 2, 2, 2, e -> e instanceof Interaction)) {
            Interaction interaction = (Interaction) entity;
            int[] offsets = interaction.getPersistentDataContainer().get(barriersKey(), PersistentDataType.INTEGER_ARRAY);
            if (offsets == null)
                continue;
            Block origin = interaction.getLocation().getBlock();
            for (int i = 0; i + 2 < offsets.length; i += 3) {
                if (origin.getRelative(offsets[i], offsets[i + 1], offsets[i + 2]).equals(block))
                    return interaction;
            }
        }
        return null;
    }

    // Gives a piece placed before barriers existed its barriers, once, and moves the barriers that version 1.7 put on
    // the wrong side of a bathtub.
    static void upgrade(Interaction interaction) {
        PersistentDataContainer data = interaction.getPersistentDataContainer();
        if (data.has(footprintKey(), PersistentDataType.BYTE))
            return;
        String uuid = data.get(furnitureKey(), PersistentDataType.STRING);
        if (uuid == null || !(Bukkit.getEntity(UUID.fromString(uuid)) instanceof ItemDisplay display))
            return;
        clear(interaction.getLocation().getBlock(), data.get(barriersKey(), PersistentDataType.INTEGER_ARRAY), Material.BARRIER);
        place(interaction, display, block -> true);
    }
}
