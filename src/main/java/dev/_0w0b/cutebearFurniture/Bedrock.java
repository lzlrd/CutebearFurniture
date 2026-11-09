package dev._0w0b.cutebearFurniture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Tells Bedrock players, who join through Geyser and Floodgate, from Java players. Without Floodgate every player
 * counts as Java.
 */
final class Bedrock {
    private static Boolean floodgate;

    private Bedrock() {
    }

    static boolean is(Player player) {
        if (floodgate == null)
            floodgate = Bukkit.getPluginManager().isPluginEnabled("floodgate");
        if (!floodgate)
            return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (UnsupportedOperationException exception) {
            // The crash fix knows how to find the player's ID when this happens. It needs ProtocolLib.
            return Bukkit.getPluginManager().isPluginEnabled("ProtocolLib") && BedrockCrashFix.isBedrock(player);
        }
    }
}
