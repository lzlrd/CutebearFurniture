package dev._0w0b.cutebearFurniture;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.bukkit.Bukkit.getLogger;
import static org.bukkit.Bukkit.getServer;

public class BedrockCrashFix {
    public static boolean isBedrock(UUID uuid){
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }
    public static boolean isUnsafe(ItemStack item){

        if (item.getType() == Material.POISONOUS_POTATO
                && item.hasData(DataComponentTypes.ITEM_MODEL)){
            String id = String.valueOf(item.getData(DataComponentTypes.ITEM_MODEL).key());
            if (id.startsWith("cutebear_furniture:")) return true;
        }
        return false;

    }

    public static void init(){

        getLogger().info("Floodgate not found! Bedrock workaround will be enabled.");
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(CutebearFurniture.getInstance(), PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isBedrock(event.getPlayer().getUniqueId()))return;

                ItemStack item = event.getPacket().getItemModifier().read(0);

                    if (isUnsafe(item)) {
                        ItemStack new_item=new ItemStack(Material.STICK,item.getAmount());
                        ItemMeta meta = new_item.getItemMeta();
                        meta.setDisplayName(item.getItemMeta().getDisplayName());
                        new_item.setItemMeta(meta);
                        event.getPacket().getItemModifier().write(0, new_item);
                }
            }
        });
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(CutebearFurniture.getInstance(), PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isBedrock(event.getPlayer().getUniqueId())) return;

                PacketContainer packet = event.getPacket();
                List<ItemStack> originalItems = packet.getItemListModifier().read(0);
                boolean needsUpdate = false;

                List<ItemStack> safeItems = new ArrayList<>(originalItems);

                for (int i = 0; i < safeItems.size(); i++) {
                    ItemStack item = safeItems.get(i);
                    if (isUnsafe(item)) {
                        ItemStack new_item=new ItemStack(Material.STICK,item.getAmount());
                        ItemMeta meta = new_item.getItemMeta();
                        meta.setDisplayName(item.getItemMeta().getDisplayName());
                        new_item.setItemMeta(meta);
                        safeItems.set(i, new_item);

                        needsUpdate = true;
                    }
                }

                if (needsUpdate) {
                    packet.getItemListModifier().write(0, safeItems);
                }
            }

    });

    }

}