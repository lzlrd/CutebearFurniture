package dev._0w0b.cutebearFurniture;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Ladders players climb. Each bunk ladder holds a structure void, which the plugin's data pack adds to the climbable
 * block tag, so Java players climb the model they see. Bedrock decides what it can climb by itself and can't climb an
 * invisible block, so Bedrock players are shown a vanilla ladder in its place, without the model, and climb that.
 *
 * Bedrock players get the ladder two ticks after anything that sends them the block: the server sends a block it
 * just changed at the end of the next tick, which would replace the ladder. They get every ladder near them again
 * once their game has loaded the world, as one sent while it loads is lost.
 */
final class FurnitureLadders implements Listener {
    private static final int SETTLE = 2;

    // Ladder displays loaded on the server, hidden from Bedrock players.
    private final Set<UUID> displays = new HashSet<>();

    private static boolean isLadder(ItemDisplay display) {
        FurnitureTypes.Type type = FurnitureTypes.of(display);
        return type != null && type.climb();
    }

    // A ladder's climbing block as Bedrock players are shown it: a vanilla ladder on the same wall.
    private static BlockData ladder(ItemDisplay display) {
        Ladder ladder = (Ladder) Material.LADDER.createBlockData();
        ladder.setFacing(FurnitureBarriers.wallFace(display, FurnitureTypes.of(display)));
        return ladder;
    }

    // Shows a placed or loaded ladder to the Bedrock players in its world as a vanilla ladder.
    void track(Interaction interaction) {
        ItemDisplay display = FurnitureBarriers.display(interaction);
        if (display == null || !isLadder(display))
            return;
        displays.add(display.getUniqueId());
        List<Player> bedrock = display.getWorld().getPlayers().stream().filter(Bedrock::is).toList();
        for (Player player : bedrock) {
            player.hideEntity(CutebearFurniture.getInstance(), display);
            show(player, List.of(display));
        }
    }

    // Sends a Bedrock player ladders as vanilla ladders once things settle, those still standing.
    private static void show(Player player, Collection<ItemDisplay> ladders) {
        Bukkit.getScheduler().runTaskLater(CutebearFurniture.getInstance(), () -> {
            if (!player.isOnline())
                return;
            for (ItemDisplay display : ladders) {
                Block climb = display.getLocation().getBlock();
                if (display.isValid() && display.getWorld() == player.getWorld() && climb.getType() == Material.STRUCTURE_VOID)
                    player.sendBlockChange(climb.getLocation(), ladder(display));
            }
        }, SETTLE);
    }

    void removed(ItemDisplay display) {
        displays.remove(display.getUniqueId());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities())
            displays.remove(entity.getUniqueId());
    }

    // Hidden entities are per session, so a Bedrock player who joins has the loaded ladders hidden again. Their
    // ladders arrive with their chunks.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!Bedrock.is(player))
            return;
        for (UUID uuid : displays) {
            if (Bukkit.getEntity(uuid) instanceof ItemDisplay display)
                player.hideEntity(CutebearFurniture.getInstance(), display);
        }
    }

    // A chunk sent to a Bedrock player shows its ladders' structure voids as they really are, so they're sent again
    // as ladders. A chunk whose entities load later gets them from track.
    @EventHandler
    public void onChunkLoad(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        if (!event.getChunk().isEntitiesLoaded() || !Bedrock.is(player))
            return;
        List<ItemDisplay> ladders = new ArrayList<>();
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof ItemDisplay display && isLadder(display))
                ladders.add(display);
        }
        if (!ladders.isEmpty())
            show(player, ladders);
    }

    // A Bedrock player whose game has loaded the world, on joining or respawning, gets the ladders around them again.
    @EventHandler
    public void onLoaded(PlayerClientLoadedWorldEvent event) {
        Player player = event.getPlayer();
        if (!Bedrock.is(player))
            return;
        int reach = (Bukkit.getViewDistance() + 1) * 16;
        List<ItemDisplay> ladders = new ArrayList<>();
        for (UUID uuid : displays) {
            if (Bukkit.getEntity(uuid) instanceof ItemDisplay display && display.getWorld() == player.getWorld()
                    && Math.abs(display.getX() - player.getX()) <= reach && Math.abs(display.getZ() - player.getZ()) <= reach)
                ladders.add(display);
        }
        if (!ladders.isEmpty())
            show(player, ladders);
    }

    // A ladder's structure void is only ever its own: water doesn't wash it away, pistons and explosions leave it and
    // blocks aren't placed into it. Nothing else in a world uses structure voids.
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (event.getToBlock().getType() == Material.STRUCTURE_VOID)
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockReplacedState().getType() == Material.STRUCTURE_VOID
                && FurnitureBarriers.owner(event.getBlock(), Material.STRUCTURE_VOID) != null)
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> block.getType() == Material.STRUCTURE_VOID))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> block.getType() == Material.STRUCTURE_VOID))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.STRUCTURE_VOID);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.STRUCTURE_VOID);
    }
}
