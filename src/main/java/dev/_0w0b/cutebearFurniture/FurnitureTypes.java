package dev._0w0b.cutebearFurniture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The pieces imported from Paladin's Furniture, read from furniture.json, which FurnitureResource's
 * tools/import_pfm.py writes. The first pieces keep the rules in the rest of the plugin.
 */
final class FurnitureTypes {
    enum Placement { FLOOR, WALL, CEILING }

    /**
     * One piece. yawOffset is added to the yaw of the player's facing (floor and ceiling pieces) or of the wall
     * (wall pieces); footprint lists the barrier blocks as offsets along the model's x, y and z axes. storage is the
     * number of slots the piece holds (0 for none), bed the colour of the vanilla bed it sleeps as (null for none), use
     * the menu it opens (null for none) and climb whether players climb it like a ladder.
     */
    record Type(String id, String name, List<String> inputs, Placement placement, int yawOffset, boolean seat,
                int light, List<int[]> footprint, int storage, String bed, String use, boolean climb) {
    }

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();

    private FurnitureTypes() {
    }

    static void load(JavaPlugin plugin) {
        TYPES.clear();
        try (InputStream stream = plugin.getResource("furniture.json")) {
            if (stream == null) {
                plugin.getLogger().warning("furniture.json is missing, so only the first pieces are available");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("items")) {
                JsonObject item = element.getAsJsonObject();
                List<String> inputs = new ArrayList<>();
                for (JsonElement input : item.getAsJsonArray("inputs"))
                    inputs.add(input.getAsString());
                List<int[]> footprint = new ArrayList<>();
                for (JsonElement offset : item.getAsJsonArray("footprint")) {
                    JsonArray xyz = offset.getAsJsonArray();
                    footprint.add(new int[]{xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()});
                }
                Type type = new Type(
                        item.get("id").getAsString(),
                        item.get("name").getAsString(),
                        inputs,
                        Placement.valueOf(item.get("placement").getAsString().toUpperCase(Locale.ROOT)),
                        item.get("yaw_offset").getAsInt(),
                        item.get("seat").getAsBoolean(),
                        item.get("light").getAsInt(),
                        footprint,
                        item.has("storage") ? item.get("storage").getAsInt() : 0,
                        item.has("bed") ? item.get("bed").getAsString() : null,
                        item.has("use") ? item.get("use").getAsString() : null,
                        item.has("climb") && item.get("climb").getAsBoolean());
                TYPES.put(type.id(), type);
            }
        } catch (Exception exception) {
            plugin.getLogger().severe("Could not read furniture.json: " + exception);
        }
    }

    // The type of a model ID such as cutebear_furniture:white_simple_sofa, or null for the first pieces.
    static Type get(String model) {
        return TYPES.get(model.substring(model.indexOf(':') + 1));
    }

    static Collection<Type> all() {
        return TYPES.values();
    }

    // The type of a placed piece, from its display's item model, or null for the first pieces and other displays.
    static Type of(ItemDisplay display) {
        String model = model(display);
        return model == null ? null : get(model);
    }

    // The item model ID of a placed piece, such as cutebear_furniture:trash_can, or null if its display has none.
    static String model(ItemDisplay display) {
        ItemStack item = display.getItemStack();
        if (item == null || !item.hasData(DataComponentTypes.ITEM_MODEL))
            return null;
        return String.valueOf(item.getData(DataComponentTypes.ITEM_MODEL).key());
    }

    // The slots a piece holds. The first pieces' trash cans hold a chest's worth, like the imported mesh trash can.
    static int storage(String model) {
        Type type = get(model);
        if (type != null)
            return type.storage();
        String id = model.substring(model.indexOf(':') + 1);
        return id.equals("trash_can") || id.endsWith("_trash_can") ? 27 : 0;
    }
}
