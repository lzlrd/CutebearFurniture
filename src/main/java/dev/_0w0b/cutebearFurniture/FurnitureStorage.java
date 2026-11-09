package dev._0w0b.cutebearFurniture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Furniture that stores items like a chest: 9 slots for small cabinets and nightstands, 27 for counters, drawers,
 * cabinets and trash cans, 54 for fridges and freezers.
 *
 * The items live on the piece's interaction entity and drop when the piece breaks. Players looking into the same
 * piece share one inventory, which is written back after every change.
 */
final class FurnitureStorage implements Listener {
    // A chest stops working for a player further than this from it, and so does a piece.
    private static final double REACH = 8;

    private static final class Holder implements InventoryHolder {
        final UUID piece;
        final Location location;
        Inventory inventory;

        Holder(UUID piece, Location location) {
            this.piece = piece;
            this.location = location;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // Inventories in use or still to be written, by the interaction of their piece.
    private final Map<UUID, Holder> holders = new HashMap<>();
    private final Set<Holder> dirty = new LinkedHashSet<>();

    static NamespacedKey contentsKey() {
        return new NamespacedKey(CutebearFurniture.getInstance(), "storage");
    }

    // Writes changed inventories every tick and closes pieces players have walked away from twice a second.
    void start(CutebearFurniture plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 1, 1);
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkReach, 10, 10);
    }

    // Opens a piece's storage for a player, with the piece's name as its title.
    void open(Interaction interaction, ItemDisplay display, Player player, int size) {
        Holder holder = holders.get(interaction.getUniqueId());
        if (holder == null) {
            holder = new Holder(interaction.getUniqueId(), display.getLocation());
            holder.inventory = Bukkit.createInventory(holder, size, title(display));
            byte[] bytes = interaction.getPersistentDataContainer().get(contentsKey(), PersistentDataType.BYTE_ARRAY);
            if (bytes != null) {
                ItemStack[] stored = ItemStack.deserializeItemsFromBytes(bytes);
                for (int slot = 0; slot < Math.min(size, stored.length); slot++)
                    holder.inventory.setItem(slot, stored[slot]);
            }
            holders.put(holder.piece, holder);
        }
        boolean first = holder.inventory.getViewers().isEmpty();
        if (player.openInventory(holder.inventory) == null) {
            // Another plugin stopped it opening.
            if (holder.inventory.getViewers().isEmpty() && !dirty.contains(holder))
                holders.remove(holder.piece);
            return;
        }
        if (first)
            holder.location.getWorld().playSound(holder.location, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5f, 1f);
    }

    // The piece's name: the name its item was made with, else its name in furniture.json, else one made from its ID.
    static Component title(ItemDisplay display) {
        ItemStack item = display.getItemStack();
        Component name = item != null && item.hasItemMeta() ? item.getItemMeta().displayName() : null;
        if (name != null)
            return name;
        FurnitureTypes.Type type = FurnitureTypes.of(display);
        if (type != null)
            return Component.text(type.name());
        String model = FurnitureTypes.model(display);
        if (model == null)
            return Component.text("Storage");
        StringBuilder words = new StringBuilder();
        for (String word : model.substring(model.indexOf(':') + 1).split("_")) {
            if (word.isEmpty())
                continue;
            if (!words.isEmpty())
                words.append(' ');
            words.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.text(words.toString());
    }

    // Drops what a piece holds where it stood and forgets it, closing it for anyone looking in first.
    void drop(Interaction interaction, Location where) {
        Holder holder = holders.get(interaction.getUniqueId());
        ItemStack[] contents;
        if (holder != null) {
            for (HumanEntity viewer : new ArrayList<>(holder.inventory.getViewers()))
                viewer.closeInventory();
            holders.remove(holder.piece);
            dirty.remove(holder);
            contents = holder.inventory.getContents();
            holder.inventory.clear();
        } else {
            byte[] bytes = interaction.getPersistentDataContainer().get(contentsKey(), PersistentDataType.BYTE_ARRAY);
            contents = bytes == null ? new ItemStack[0] : ItemStack.deserializeItemsFromBytes(bytes);
        }
        interaction.getPersistentDataContainer().remove(contentsKey());
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir())
                where.getWorld().dropItemNaturally(where, item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder holder)
            dirty.add(holder);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder holder)
            dirty.add(holder);
    }

    // The next tick's write saves the piece and, with nobody else looking in, forgets it.
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Holder holder))
            return;
        dirty.add(holder);
        // The closing player still counts as a viewer.
        if (holder.inventory.getViewers().size() <= 1)
            holder.location.getWorld().playSound(holder.location, Sound.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5f, 1f);
    }

    // A piece is closed before its player leaves for somewhere it might unload behind them.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof Holder)
            event.getPlayer().closeInventory();
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof Holder)
            event.getPlayer().closeInventory();
    }

    // Writes changed inventories to their pieces and forgets those nobody has open. One whose piece isn't loaded
    // stays in memory, and is written when it is, rather than being lost or dropped twice.
    private void flush() {
        for (Iterator<Holder> iterator = dirty.iterator(); iterator.hasNext(); ) {
            Holder holder = iterator.next();
            if (!(Bukkit.getEntity(holder.piece) instanceof Interaction interaction))
                continue;
            interaction.getPersistentDataContainer().set(contentsKey(), PersistentDataType.BYTE_ARRAY,
                    ItemStack.serializeItemsAsBytes(holder.inventory.getContents()));
            iterator.remove();
            if (holder.inventory.getViewers().isEmpty())
                holders.remove(holder.piece);
        }
    }

    // Closes a piece for players who are too far from it or whose piece is gone, as a chest would.
    private void checkReach() {
        for (Holder holder : new ArrayList<>(holders.values())) {
            Entity piece = Bukkit.getEntity(holder.piece);
            for (HumanEntity viewer : new ArrayList<>(holder.inventory.getViewers())) {
                Location at = viewer.getLocation();
                if (piece == null || !piece.isValid() || at.getWorld() != holder.location.getWorld()
                        || at.distanceSquared(holder.location) > REACH * REACH)
                    viewer.closeInventory();
            }
        }
    }

    // Closes every piece and writes it back, for the plugin shutting down.
    void closeAll() {
        for (Holder holder : new ArrayList<>(holders.values())) {
            for (HumanEntity viewer : new ArrayList<>(holder.inventory.getViewers()))
                viewer.closeInventory();
            dirty.add(holder);
        }
        flush();
        for (Holder holder : dirty) {
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack item : holder.inventory.getContents())
                if (item != null && !item.getType().isAir())
                    items.add(item);
            CutebearFurniture.getInstance().getLogger().severe("Could not save the " + items.size()
                    + " stacks in the piece at " + holder.location + " because it isn't loaded: " + items);
        }
    }
}
