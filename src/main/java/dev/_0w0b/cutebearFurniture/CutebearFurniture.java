package dev._0w0b.cutebearFurniture;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import net.kyori.adventure.text.event.DataComponentValue;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CutebearFurniture extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        Bukkit.getPluginManager().registerEvents(new EventListener(),this);
        registerFurnitureRecipe("chair","Oak Chair",Material.OAK_LOG);
        registerFurnitureRecipe("dinner_table","Dinner Table",Material.OAK_LOG);
        registerFurnitureRecipe("coffee_table_basic","Coffee Table",Material.OAK_LOG);
        registerFurnitureRecipe("classic_desk_all","Classic Desk",Material.OAK_LOG);
        registerFurnitureRecipe("classic_stool","Classic Stool",Material.OAK_LOG);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
    public void registerFurnitureRecipe(String modelId, String name, Material input) {
        NamespacedKey key = new NamespacedKey("cutebear_furniture", modelId);

        ItemStack result = new ItemStack(Material.POISONOUS_POTATO);
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.setFood(null);
            meta.setDisplayName(name);
            meta.setItemModel(key);
            result.setItemMeta(meta);
        }

        StonecuttingRecipe recipe = new StonecuttingRecipe(key, result, input);
        Bukkit.addRecipe(recipe);
    }
}
