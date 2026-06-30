package dev._0w0b.cutebearFurniture;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

import org.geysermc.floodgate.api.FloodgateApi;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.bukkit.Bukkit.getLogger;

public class BedrockCrashFix {


    private static UUID findUUIDInObject(Object obj, int depth, Set<Object> visited) {
        if (obj == null || depth > 4) return null;
        if (!visited.add(obj)) return null;

        if (obj instanceof UUID) {
            return (UUID) obj;
        }

        if (obj.getClass().getName().contains("GameProfile")) {
            try {
                java.lang.reflect.Method getField = obj.getClass().getMethod("getId");
                return (UUID) getField.invoke(obj);
            } catch (Exception ignored) {}
            try {
                java.lang.reflect.Method getField = obj.getClass().getMethod("getUUID");
                return (UUID) getField.invoke(obj);
            } catch (Exception ignored) {}
        }

        if (java.lang.reflect.Proxy.isProxyClass(obj.getClass())) {
            try {
                java.lang.reflect.InvocationHandler handler = java.lang.reflect.Proxy.getInvocationHandler(obj);
                UUID found = findUUIDInObject(handler, depth + 1, visited);
                if (found != null) return found;
            } catch (Exception ignored) {}
        }

        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            String name = c.getName();
            if (name.startsWith("java.") || name.startsWith("sun.")) continue;

            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    UUID found = findUUIDInObject(val, depth + 1, visited);
                    if (found != null) return found;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public static boolean isBedrock(Player player) {
        if (player == null) return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (UnsupportedOperationException e) {
            UUID uuid = findUUIDInObject(player, 0, new HashSet<>());
            if (uuid != null) {
                return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
            }
        }
        return false;
    }
    /**
     * Dual-check for the furniture item_model component.
     * ItemMeta#getItemModel() works for live server-side items.
     * DataComponentTypes.ITEM_MODEL works for ProtocolLib packet-side items where
     * the meta layer may not carry over the model key after NMS deserialization.
     * Both paths are tried so neither edge-case is missed.
     */
    public static boolean isUnsafe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() != Material.POISONOUS_POTATO) return false;

        // Path 1: Bukkit ItemMeta layer (works for server-created items)
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                NamespacedKey model = meta.getItemModel();
                if (model != null && model.getNamespace().equals("cutebear_furniture")) return true;
            }
        } catch (Exception ignored) {}

        // Path 2: Paper data-component layer (works for ProtocolLib packet-reconstructed items)
        try {
            if (item.hasData(DataComponentTypes.ITEM_MODEL)) {
                var model = item.getData(DataComponentTypes.ITEM_MODEL).key();
                if (model != null && model.namespace().equals("cutebear_furniture")) return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    /** Replace a furniture item with a plain stick, keeping the display name. */
    private static ItemStack sanitize(ItemStack item) {
        ItemStack safe = new ItemStack(Material.STICK, item.getAmount());
        ItemMeta oldMeta = item.getItemMeta();
        if (oldMeta != null && oldMeta.hasDisplayName()) {
            ItemMeta newMeta = safe.getItemMeta();
            if (newMeta != null) {
                newMeta.setDisplayName(oldMeta.getDisplayName());
                safe.setItemMeta(newMeta);
            }
        }
        return safe;
    }
    public static void init() {
        getLogger().info("Bedrock crash fix enabled.");

        // ── SET_SLOT: single slot update ─────────────────────────────────────
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(CutebearFurniture.getInstance(), PacketType.Play.Server.SET_SLOT) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.getPlayer() == null) return;
                    if (!isBedrock(event.getPlayer())) return;

                    ItemStack item = event.getPacket().getItemModifier().read(0);
                    if (isUnsafe(item)) {
                        event.getPacket().getItemModifier().write(0, sanitize(item));
                    }
                }
            });

        // ── SET_CURSOR_ITEM: cursor/held item in 1.21+ containers ────────────
        // Replaces the old SET_SLOT windowId=-1 mechanism for cursor updates.
        // Fired when the player picks up an item into their cursor (e.g. stonecutter result).
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(CutebearFurniture.getInstance(), PacketType.Play.Server.SET_CURSOR_ITEM) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.getPlayer() == null) return;
                    if (!isBedrock(event.getPlayer())) return;

                    ItemStack item = event.getPacket().getItemModifier().read(0);
                    if (isUnsafe(item)) {
                        event.getPacket().getItemModifier().write(0, sanitize(item));
                    }
                }
            });

        // ── WINDOW_ITEMS: full inventory snapshot (slots + cursor) ────────────
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(CutebearFurniture.getInstance(), PacketType.Play.Server.WINDOW_ITEMS) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.getPlayer() == null) return;
                    if (!isBedrock(event.getPlayer())) return;

                    PacketContainer packet = event.getPacket();

                    // index 0: all slots
                    List<ItemStack> originalItems = packet.getItemListModifier().read(0);
                    if (originalItems != null) {
                        boolean changed = false;
                        List<ItemStack> safeItems = new ArrayList<>(originalItems);
                        for (int i = 0; i < safeItems.size(); i++) {
                            if (isUnsafe(safeItems.get(i))) {
                                safeItems.set(i, sanitize(safeItems.get(i)));
                                changed = true;
                            }
                        }
                        if (changed) packet.getItemListModifier().write(0, safeItems);
                    }

                    // index 0: cursor/carried item (present in 1.17+)
                    try {
                        ItemStack cursor = packet.getItemModifier().read(0);
                        if (isUnsafe(cursor)) {
                            packet.getItemModifier().write(0, sanitize(cursor));
                        }
                    } catch (Exception ignored) {
                        // field absent on some versions — safe to skip
                    }
                }
            });

        // ── WINDOW_CLICK: client-to-server click validation ──────────────────
        // Intercept client clicks to prevent server-side desync when the client
        // clicks a "fake" STICK but the server expects the real POISONOUS_POTATO.
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(CutebearFurniture.getInstance(), PacketType.Play.Client.WINDOW_CLICK) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    try {
                        if (event.getPlayer() == null) return;
                        if (!isBedrock(event.getPlayer())) return;

                        PacketContainer packet = event.getPacket();
                        ItemStack cursorItem = event.getPlayer().getItemOnCursor();
                        if (cursorItem != null && isUnsafe(cursorItem)) {
                            packet.getItemModifier().write(0, cursorItem.clone());
                        }
                    } catch (Exception ignored) {
                    }
                }
            });

    }
}