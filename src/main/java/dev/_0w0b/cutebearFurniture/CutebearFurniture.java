package dev._0w0b.cutebearFurniture;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import static org.bukkit.Bukkit.getLogger;
import static org.bukkit.Bukkit.getServer;

public final class CutebearFurniture extends JavaPlugin {
    private static CutebearFurniture instance;

    @Override
    public void onEnable() {
        CutebearFurniture.instance = this;
        // Plugin startup logic
        Bukkit.getPluginManager().registerEvents(new EventListener(), this);
        registerFurnitureRecipe("chair", "Oak Chair", Material.OAK_LOG);
        registerFurnitureRecipe("dinner_table", "Dinner Table", Material.OAK_LOG);
        registerFurnitureRecipe("coffee_table_basic", "Coffee Table", Material.OAK_LOG);
        registerFurnitureRecipe("classic_desk_all", "Classic Desk", Material.OAK_LOG);
        registerFurnitureRecipe("classic_stool", "Classic Stool", Material.OAK_LOG);
        registerFurnitureRecipe("chair_classic", "Classic Chair", Material.OAK_LOG);
        registerFurnitureRecipe("chair_modern", "Modern Chair", Material.OAK_LOG);
        registerFurnitureRecipe("chair_dinner", "Dinner Chair", Material.OAK_LOG);
        registerFurnitureRecipe("bathtub", "BathTub", Material.QUARTZ_BLOCK);
        registerFurnitureRecipe("basic_sink", "Sink", Material.QUARTZ_BLOCK);
        registerFurnitureRecipe("white_arm_chair", "White Arm Chair", Material.WHITE_WOOL);
        registerFurnitureRecipe("trash_can", "Trash Can", Material.GRAY_CONCRETE);
        registerFurnitureRecipe("toilet", "Toilet", Material.QUARTZ_BLOCK);

        if (getServer().getPluginManager().isPluginEnabled("floodgate")) {
            if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
                BedrockCrashFix.init();
            } else {
                getLogger().severe("ProtocolLib not installed, Bedrock player may be inventory banned!");
            }
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void registerFurnitureRecipe(String modelId, String name, Material input) {
        NamespacedKey key = new NamespacedKey("cutebear_furniture", modelId);

        ItemStack result = new ItemStack(Material.POISONOUS_POTATO);
        result.unsetData(DataComponentTypes.CONSUMABLE);
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {

            meta.setDisplayName(name);
            meta.setItemModel(key);
            result.setItemMeta(meta);
        }

        StonecuttingRecipe recipe = new StonecuttingRecipe(key, result, input);
        Bukkit.addRecipe(recipe);
    }

    public static CutebearFurniture getInstance() {
        return instance;
    }
}
