package dev._0w0b.cutebearFurniture;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import static org.bukkit.Bukkit.getLogger;
import static org.bukkit.Bukkit.getServer;

public final class CutebearFurniture extends JavaPlugin {
    private static CutebearFurniture instance;
    private int recipeCount = 0;

    // Model ID and name of each wooden piece. Keep in sync with variants.py in FurnitureResource.
    private static final String[][] WOODEN_FURNITURE = {
            {"chair", "Chair"},
            {"dinner_table", "Dinner Table"},
            {"coffee_table_basic", "Coffee Table"},
            {"classic_desk_all", "Classic Desk"},
            {"classic_stool", "Classic Stool"},
            {"chair_classic", "Classic Chair"},
            {"chair_modern", "Modern Chair"},
            {"chair_dinner", "Dinner Chair"},
    };

    // Wood type and the item tag holding its logs, wood and stripped variants.
    // Types missing from the running Minecraft version are skipped.
    private static final String[][] WOODS = {
            {"spruce", "spruce_logs"},
            {"birch", "birch_logs"},
            {"jungle", "jungle_logs"},
            {"acacia", "acacia_logs"},
            {"dark_oak", "dark_oak_logs"},
            {"mangrove", "mangrove_logs"},
            {"cherry", "cherry_logs"},
            {"pale_oak", "pale_oak_logs"},
            {"poplar", "poplar_logs"},
            {"crimson", "crimson_stems"},
            {"warped", "warped_stems"},
    };

    // Model ID and name of each quartz piece, which also comes in gold. Keep in sync with variants.py in FurnitureResource.
    private static final String[][] QUARTZ_FURNITURE = {
            {"bathtub", "BathTub"},
            {"basic_sink", "Sink"},
            {"toilet", "Toilet"},
    };

    private static final String[] COLOURS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    };

    @Override
    public void onEnable() {
        CutebearFurniture.instance = this;
        // Plugin startup logic
        Bukkit.getPluginManager().registerEvents(new EventListener(), this);
        // The original furniture keeps its IDs and names so placed furniture still works.
        RecipeChoice oak = tagChoice("oak_logs");
        registerFurnitureRecipe("chair", "Oak Chair", oak);
        registerFurnitureRecipe("dinner_table", "Dinner Table", oak);
        registerFurnitureRecipe("coffee_table_basic", "Coffee Table", oak);
        registerFurnitureRecipe("classic_desk_all", "Classic Desk", oak);
        registerFurnitureRecipe("classic_stool", "Classic Stool", oak);
        registerFurnitureRecipe("chair_classic", "Classic Chair", oak);
        registerFurnitureRecipe("chair_modern", "Modern Chair", oak);
        registerFurnitureRecipe("chair_dinner", "Dinner Chair", oak);
        registerFurnitureRecipe("bathtub", "BathTub", materialChoice("quartz_block"));
        registerFurnitureRecipe("basic_sink", "Sink", materialChoice("quartz_block"));
        registerFurnitureRecipe("white_arm_chair", "White Arm Chair", materialChoice("white_wool"));
        registerFurnitureRecipe("trash_can", "Trash Can", materialChoice("gray_concrete"));
        registerFurnitureRecipe("toilet", "Toilet", materialChoice("quartz_block"));

        for (String[] wood : WOODS) {
            RecipeChoice logs = tagChoice(wood[1]);
            for (String[] furniture : WOODEN_FURNITURE) {
                registerFurnitureRecipe(wood[0] + "_" + furniture[0], displayName(wood[0]) + " " + furniture[1], logs);
            }
        }
        for (String colour : COLOURS) {
            if (!colour.equals("white")) {
                registerFurnitureRecipe(colour + "_arm_chair", displayName(colour) + " Arm Chair", materialChoice(colour + "_wool"));
            }
            if (!colour.equals("gray")) {
                registerFurnitureRecipe(colour + "_trash_can", displayName(colour) + " Trash Can", materialChoice(colour + "_concrete"));
            }
        }
        for (String[] furniture : QUARTZ_FURNITURE) {
            registerFurnitureRecipe("gold_" + furniture[0], "Gold " + furniture[1], materialChoice("gold_block"));
        }
        getLogger().info("Registered " + recipeCount + " furniture recipes");

        // Furniture placed before barriers existed becomes solid. Chunks that load later are handled as they load.
        for (World world : Bukkit.getWorlds()) {
            for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) {
                FurnitureBarriers.upgrade(interaction);
            }
        }

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

    private static RecipeChoice tagChoice(String tagName) {
        Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(tagName), Material.class);
        if (tag == null || tag.getValues().isEmpty())
            return null;
        return new RecipeChoice.MaterialChoice(tag);
    }

    private static RecipeChoice materialChoice(String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null)
            return null;
        return new RecipeChoice.MaterialChoice(material);
    }

    private static String displayName(String id) {
        StringBuilder name = new StringBuilder();
        for (String word : id.split("_")) {
            if (!name.isEmpty())
                name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    public void registerFurnitureRecipe(String modelId, String name, RecipeChoice input) {
        // Null when the input block does not exist in this Minecraft version.
        if (input == null)
            return;
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
        recipeCount++;
    }

    public static CutebearFurniture getInstance() {
        return instance;
    }
}
