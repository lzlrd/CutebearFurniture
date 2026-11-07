package dev._0w0b.cutebearFurniture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The pieces imported from Paladin's Furniture, read from furniture.json, which FurnitureResource's
 * tools/import_pfm.py writes. The first pieces keep the rules in the rest of the plugin.
 */
final class FurnitureTypes {
    enum Placement { FLOOR, WALL, CEILING }

    /**
     * One piece. yawOffset is added to the yaw of the player's facing (floor and ceiling pieces) or of the wall
     * (wall pieces); footprint lists the barrier blocks as offsets along the model's x, y and z axes.
     */
    record Type(String id, String name, List<String> inputs, Placement placement, int yawOffset, boolean seat,
                int light, List<int[]> footprint) {
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
                        footprint);
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
}
