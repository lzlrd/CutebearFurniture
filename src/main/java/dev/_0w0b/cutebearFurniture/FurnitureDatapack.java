package dev._0w0b.cutebearFurniture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The data pack that adds structure voids to the climbable block tag, so Java players climb bunk ladders. The plugin
 * keeps it up to date in the main world's datapacks folder; the server loads it at startup, so a new or changed copy
 * takes effect at the next restart.
 */
final class FurnitureDatapack {
    private static final String NAME = "cutebear_furniture";
    private static final String[] FILES = {"pack.mcmeta", "data/minecraft/tags/block/climbable.json"};

    private FurnitureDatapack() {
    }

    static void install(JavaPlugin plugin) {
        Path root = Bukkit.getWorldContainer().toPath()
                .resolve(Bukkit.getWorlds().getFirst().getName()).resolve("datapacks").resolve(NAME);
        boolean changed = false;
        for (String file : FILES) {
            try (InputStream stream = plugin.getResource("datapack/" + file)) {
                if (stream == null)
                    throw new IOException("datapack/" + file + " is missing from the plugin");
                byte[] bytes = stream.readAllBytes();
                Path target = root.resolve(file);
                if (Files.exists(target) && Arrays.equals(Files.readAllBytes(target), bytes))
                    continue;
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
                changed = true;
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not install the " + NAME + " data pack in " + root
                        + ", so Java players can't climb bunk ladders: " + exception);
                return;
            }
        }
        if (changed)
            plugin.getLogger().info("Installed the " + NAME + " data pack in " + root);
        if (!Tag.CLIMBABLE.isTagged(Material.STRUCTURE_VOID))
            plugin.getLogger().warning("Java players can climb bunk ladders once the server restarts and loads the "
                    + NAME + " data pack");
    }
}
