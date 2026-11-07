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

    // The direction a piece's model x axis points in, from the yaw its display got at placement.
    static BlockFace facing(float yaw) {
        return switch (Math.floorMod(Math.round(yaw / 90f), 4)) {
            case 1 -> BlockFace.SOUTH;
            case 2 -> BlockFace.WEST;
            case 3 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    // The blocks a piece covers: its own, plus the second block of a bathtub, which is two blocks long.
    static List<Block> footprint(Block origin, ItemDisplay display) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(origin);
        ItemStack item = display.getItemStack();
        if (item != null && item.hasData(DataComponentTypes.ITEM_MODEL)
                && String.valueOf(item.getData(DataComponentTypes.ITEM_MODEL).key()).endsWith("bathtub"))
            blocks.add(origin.getRelative(facing(display.getYaw())));
        return blocks;
    }

    // Fills the empty blocks a piece covers with barriers and records them on its interaction.
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
        interaction.getPersistentDataContainer().set(barriersKey(), PersistentDataType.INTEGER_ARRAY,
                offsets.stream().mapToInt(Integer::intValue).toArray());
    }

    // Removes the barriers a piece placed, leaving any other block that is there now.
    static void remove(Interaction interaction) {
        int[] offsets = interaction.getPersistentDataContainer().get(barriersKey(), PersistentDataType.INTEGER_ARRAY);
        if (offsets == null)
            return;
        Block origin = interaction.getLocation().getBlock();
        for (int i = 0; i + 2 < offsets.length; i += 3) {
            Block block = origin.getRelative(offsets[i], offsets[i + 1], offsets[i + 2]);
            if (block.getType() == Material.BARRIER)
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

    // Gives a piece placed before barriers existed its barriers, once.
    static void upgrade(Interaction interaction) {
        PersistentDataContainer data = interaction.getPersistentDataContainer();
        if (data.has(barriersKey(), PersistentDataType.INTEGER_ARRAY))
            return;
        String uuid = data.get(furnitureKey(), PersistentDataType.STRING);
        if (uuid != null && Bukkit.getEntity(UUID.fromString(uuid)) instanceof ItemDisplay display)
            place(interaction, display, block -> true);
    }
}
