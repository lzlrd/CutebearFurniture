package dev._0w0b.cutebearFurniture;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Beds you sleep in. While a player sleeps, the bed's barriers become a vanilla bed of its colour and its model is
 * hidden, so the server, Java and Bedrock players all treat it as a real bed: night skipping, phantoms, the sleep
 * messages and setting the player's spawn all work as vanilla. The barriers and model come back when they get up.
 *
 * The player's spawn is set beside the bed, as a forced spawn, because the bed is barriers again by the time they
 * respawn. Respawning checks the bed is still there, as vanilla does.
 */
final class FurnitureBeds implements Listener {
    private record Active(UUID piece, UUID display, UUID sleeper, Block foot, Block head, BlockFace facing,
                          Material bed, ItemStack item) {
    }

    // Where a player's furniture bed spawn points: the bed, and the spot beside it the spawn is set to.
    private record Spawn(World world, Block foot, BlockFace facing, Block spot) {
        Block head() {
            return foot.getRelative(facing);
        }
    }

    private final Map<Block, Active> byBlock = new HashMap<>();
    private final Map<UUID, Active> byPiece = new HashMap<>();
    // The player being put to bed, and what the bed enter event decided for them.
    private Player entering;
    private PlayerBedEnterEvent.BedEnterResult entered;
    private boolean disabling;

    // The model a sleeping player's bed hides, kept on its interaction so a crash can't lose it.
    static NamespacedKey hiddenItemKey() {
        return new NamespacedKey(CutebearFurniture.getInstance(), "bed_item");
    }

    private static NamespacedKey spawnKey() {
        return new NamespacedKey(CutebearFurniture.getInstance(), "bed_spawn");
    }

    // Puts beds back once a second for players who stopped sleeping in them some way the leave event misses.
    void start(CutebearFurniture plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20, 20);
    }

    boolean isTemporary(Block block) {
        return block != null && byBlock.containsKey(block);
    }

    // Puts a player to bed, or tells them why not, as a vanilla bed would.
    void use(Interaction interaction, ItemDisplay display, Player player, String colour) {
        if (byPiece.containsKey(interaction.getUniqueId())) {
            player.sendActionBar(Component.translatable("block.minecraft.bed.occupied"));
            return;
        }
        Material bed = Material.matchMaterial(colour + "_bed");
        if (bed == null)
            return;
        // Paladin's beds have their head one block along the model's x axis, which is where a vanilla bed faces.
        Block foot = interaction.getLocation().getBlock();
        BlockFace facing = FurnitureBarriers.facing(display.getYaw());
        Block head = foot.getRelative(facing);
        List<Block> barriers = FurnitureBarriers.barriers(interaction);
        if (!barriers.contains(foot) || !barriers.contains(head)
                || foot.getType() != Material.BARRIER || head.getType() != Material.BARRIER) {
            player.sendActionBar(Component.translatable("block.minecraft.bed.obstructed"));
            return;
        }
        Active active = new Active(interaction.getUniqueId(), display.getUniqueId(), player.getUniqueId(), foot, head,
                facing, bed, display.getItemStack());
        swapIn(active, interaction, display, player);
        entering = player;
        entered = null;
        boolean asleep = false;
        try {
            asleep = player.sleep(head.getLocation(), false);
        } catch (RuntimeException exception) {
            CutebearFurniture.getInstance().getLogger().warning("Could not put " + player.getName() + " to bed: " + exception);
        } finally {
            entering = null;
        }
        if (!asleep) {
            restore(active);
            if (entered != null)
                tellWhy(player, entered);
        }
    }

    // Swaps the barriers for a vanilla bed and hides the model, keeping the model on the interaction until it's back.
    private void swapIn(Active active, Interaction interaction, ItemDisplay display, Player player) {
        interaction.getPersistentDataContainer().set(hiddenItemKey(), PersistentDataType.BYTE_ARRAY,
                active.item().serializeAsBytes());
        Bed foot = (Bed) active.bed().createBlockData();
        foot.setPart(Bed.Part.FOOT);
        foot.setFacing(active.facing());
        Bed head = (Bed) active.bed().createBlockData();
        head.setPart(Bed.Part.HEAD);
        head.setFacing(active.facing());
        active.foot().setBlockData(foot, false);
        active.head().setBlockData(head, false);
        // The sleeper's game gets the bed before it hears they're asleep, which Bedrock needs to show it.
        player.sendBlockChange(active.foot().getLocation(), foot);
        player.sendBlockChange(active.head().getLocation(), head);
        display.setItemStack(null);
        byBlock.put(active.foot(), active);
        byBlock.put(active.head(), active);
        byPiece.put(active.piece(), active);
    }

    // Puts the barriers and model back. A model whose display isn't loaded comes back when it loads.
    private void restore(Active active) {
        if (byPiece.get(active.piece()) != active)
            return;
        byPiece.remove(active.piece());
        byBlock.remove(active.foot());
        byBlock.remove(active.head());
        for (Block block : List.of(active.foot(), active.head())) {
            if (block.getType() == active.bed())
                block.setType(Material.BARRIER, false);
        }
        if (Bukkit.getEntity(active.display()) instanceof ItemDisplay display) {
            display.setItemStack(active.item());
            if (Bukkit.getEntity(active.piece()) instanceof Interaction interaction)
                interaction.getPersistentDataContainer().remove(hiddenItemKey());
        }
    }

    // Wakes anyone sleeping in a piece and puts it back, before the piece is broken.
    void release(Interaction interaction) {
        Active active = byPiece.get(interaction.getUniqueId());
        if (active == null)
            return;
        Player player = Bukkit.getPlayer(active.sleeper());
        if (player != null && player.isSleeping())
            player.wakeup(false);
        restore(active);
    }

    // Wakes everyone and puts every bed back, for the plugin shutting down.
    void releaseAll() {
        disabling = true;
        for (Active active : new ArrayList<>(byPiece.values())) {
            Player player = Bukkit.getPlayer(active.sleeper());
            if (player != null && player.isSleeping())
                player.wakeup(false);
            restore(active);
        }
    }

    // Puts back a bed left as a vanilla bed by a server that stopped while someone slept in it.
    void recover(Interaction interaction) {
        PersistentDataContainer data = interaction.getPersistentDataContainer();
        byte[] bytes = data.get(hiddenItemKey(), PersistentDataType.BYTE_ARRAY);
        if (bytes == null || byPiece.containsKey(interaction.getUniqueId()))
            return;
        ItemDisplay display = FurnitureBarriers.display(interaction);
        if (display == null)
            return;
        for (Block block : FurnitureBarriers.barriers(interaction)) {
            if (Tag.BEDS.isTagged(block.getType()))
                block.setType(Material.BARRIER, false);
        }
        ItemStack shown = display.getItemStack();
        if (shown == null || shown.getType().isAir())
            display.setItemStack(ItemStack.deserializeBytes(bytes));
        data.remove(hiddenItemKey());
    }

    // A sleeper lies in the bed's head block. getBedLocation can't tell: some servers give the respawn point.
    private void check() {
        for (Active active : new ArrayList<>(byPiece.values())) {
            Player player = Bukkit.getPlayer(active.sleeper());
            if (player == null || !player.isSleeping() || !inBed(player, active.head()))
                restore(active);
        }
    }

    private static boolean inBed(Player player, Block head) {
        Location at = player.getLocation();
        return at.getWorld() == head.getWorld() && at.getBlockX() == head.getX() && at.getBlockZ() == head.getZ()
                && Math.abs(at.getY() - head.getY()) < 1.5;
    }

    private static void tellWhy(Player player, PlayerBedEnterEvent.BedEnterResult result) {
        Component message = switch (result) {
            case NOT_POSSIBLE_NOW -> Component.translatable("block.minecraft.bed.no_sleep");
            case NOT_POSSIBLE_HERE -> Component.text("You can't sleep in this dimension");
            case TOO_FAR_AWAY -> Component.translatable("block.minecraft.bed.too_far_away");
            case OBSTRUCTED -> Component.translatable("block.minecraft.bed.obstructed");
            case NOT_SAFE -> Component.translatable("block.minecraft.bed.not_safe");
            // Where a vanilla bed explodes, as in the Nether and the End. Newer servers than the API built against
            // name it EXPLOSION.
            default -> result.name().equals("EXPLOSION") ? Component.text("You can't sleep in this dimension") : null;
        };
        if (message != null)
            player.sendActionBar(message);
    }

    // Records why a player couldn't sleep. A plugin that denied it has said so itself, and one that allowed it put
    // them to bed.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getPlayer() != entering)
            return;
        entered = event.useBed() == Event.Result.DEFAULT ? event.getBedEnterResult() : null;
    }

    // The bed goes back once the player has stood up, which needs it to still be a bed.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        Active active = byBlock.get(event.getBed());
        if (active == null || disabling)
            return;
        Bukkit.getScheduler().runTask(CutebearFurniture.getInstance(), () -> {
            Player player = Bukkit.getPlayer(active.sleeper());
            if (player == null || !player.isSleeping())
                restore(active);
        });
    }

    // Nobody else can use, break or move a bed someone is sleeping in.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!isTemporary(event.getClickedBlock()))
            return;
        event.setCancelled(true);
        if (event.getAction().isRightClick() && event.getHand() == EquipmentSlot.HAND)
            event.getPlayer().sendActionBar(Component.translatable("block.minecraft.bed.occupied"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isTemporary(event.getBlock()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(byBlock::containsKey);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(byBlock::containsKey);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(byBlock::containsKey))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(byBlock::containsKey))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isTemporary(event.getBlock()))
            event.setCancelled(true);
    }

    // Sleeping sets the spawn at the bed. It's moved to a spot beside it, forced so the barriers don't spoil it.
    // Copies of that spawn the server makes, such as on respawning, keep the record; any other spawn replaces it.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSetSpawn(PlayerSetSpawnEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        if (event.getCause() == PlayerSetSpawnEvent.Cause.BED && location != null) {
            Active active = byBlock.get(location.getBlock());
            if (active != null) {
                Block spot = standUpSpot(active.foot(), active.head(), active.facing());
                if (spot == null) {
                    // Nowhere to stand: the old spawn stays, as it would for an obstructed vanilla bed.
                    event.setCancelled(true);
                    return;
                }
                Location target = spawnLocation(spot, active.head());
                if (sameBlock(player.getPotentialRespawnLocation(), target))
                    event.setNotifyPlayer(false);
                event.setLocation(target);
                event.setForced(true);
                write(player, new Spawn(spot.getWorld(), active.foot(), active.facing(), spot));
                return;
            }
        }
        Spawn spawn = read(player);
        if (spawn == null)
            return;
        if (location == null && event.getCause() == PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN && exists(spawn)) {
            // The spot was blocked, so the server is clearing the spawn. The bed is still there: use another spot.
            Block spot = standUpSpot(spawn.foot(), spawn.head(), spawn.facing());
            if (spot != null) {
                event.setLocation(spawnLocation(spot, spawn.head()));
                event.setForced(true);
                event.setNotifyPlayer(false);
                write(player, new Spawn(spawn.world(), spawn.foot(), spawn.facing(), spot));
                return;
            }
        }
        if (location == null || !location.getBlock().equals(spawn.spot()))
            player.getPersistentDataContainer().remove(spawnKey());
    }

    // A player respawning at a furniture bed that has been broken goes to the world spawn, as vanilla sends them
    // from a broken bed. One whose spot beside the bed was blocked gets another.
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Spawn spawn = read(player);
        if (spawn == null || event.isBedSpawn() || event.isAnchorSpawn())
            return;
        if (exists(spawn)) {
            if (event.isMissingRespawnBlock()) {
                Block spot = standUpSpot(spawn.foot(), spawn.head(), spawn.facing());
                if (spot != null)
                    event.setRespawnLocation(spawnLocation(spot, spawn.head()));
            }
            return;
        }
        player.getPersistentDataContainer().remove(spawnKey());
        if (event.isMissingRespawnBlock())
            return;
        event.setRespawnLocation(Bukkit.getWorlds().getFirst().getSpawnLocation());
        Bukkit.getScheduler().runTask(CutebearFurniture.getInstance(), () -> {
            player.setRespawnLocation(null);
            player.sendMessage(Component.translatable("block.minecraft.spawn.not_valid"));
        });
    }

    // A bed's barriers, or the vanilla bed someone sleeps in, still stand at a spawn's bed.
    private boolean exists(Spawn spawn) {
        for (Block block : List.of(spawn.foot(), spawn.head())) {
            if (block.getType() != Material.BARRIER && !isTemporary(block))
                return false;
        }
        return true;
    }

    // A spot beside the bed to stand in, looked for in vanilla's order: around the bed from beside its head, then
    // on top of it.
    private static Block standUpSpot(Block foot, Block head, BlockFace facing) {
        BlockFace side = clockwise(facing);
        int fx = facing.getModX(), fz = facing.getModZ(), sx = side.getModX(), sz = side.getModZ();
        int[][] around = {
                {sx, sz}, {sx - fx, sz - fz}, {sx - 2 * fx, sz - 2 * fz}, {-2 * fx, -2 * fz},
                {-sx - 2 * fx, -sz - 2 * fz}, {-sx - fx, -sz - fz}, {-sx, -sz}, {-sx + fx, -sz + fz},
                {fx, fz}, {sx + fx, sz + fz},
        };
        for (int[] offset : around) {
            Block spot = head.getRelative(offset[0], 0, offset[1]);
            if (free(spot) && free(spot.getRelative(BlockFace.UP)) && !spot.getRelative(BlockFace.DOWN).isPassable())
                return spot;
        }
        for (Block top : List.of(head.getRelative(BlockFace.UP), foot.getRelative(BlockFace.UP))) {
            if (free(top) && free(top.getRelative(BlockFace.UP)))
                return top;
        }
        return null;
    }

    private static BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    // A block a player can respawn in: nothing to collide with and no liquid, which is what a forced spawn needs.
    private static boolean free(Block block) {
        return block.isPassable() && !block.isLiquid();
    }

    // A spot as a spawn, facing the bed as vanilla turns a player who respawns at one.
    private static Location spawnLocation(Block spot, Block head) {
        Location location = spot.getLocation();
        Vector towards = head.getLocation().add(0.5, 0, 0.5).toVector().subtract(location.clone().add(0.5, 0, 0.5).toVector());
        if (towards.lengthSquared() > 0)
            location.setDirection(towards);
        location.setPitch(0);
        return location;
    }

    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() == b.getWorld() && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private static void write(Player player, Spawn spawn) {
        player.getPersistentDataContainer().set(spawnKey(), PersistentDataType.STRING, String.join(";",
                spawn.world().getUID().toString(),
                String.valueOf(spawn.foot().getX()), String.valueOf(spawn.foot().getY()), String.valueOf(spawn.foot().getZ()),
                spawn.facing().name(),
                String.valueOf(spawn.spot().getX()), String.valueOf(spawn.spot().getY()), String.valueOf(spawn.spot().getZ())));
    }

    private static Spawn read(Player player) {
        String value = player.getPersistentDataContainer().get(spawnKey(), PersistentDataType.STRING);
        if (value == null)
            return null;
        try {
            String[] parts = value.split(";");
            World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (world == null)
                return null;
            Block foot = world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            Block spot = world.getBlockAt(Integer.parseInt(parts[5]), Integer.parseInt(parts[6]), Integer.parseInt(parts[7]));
            return new Spawn(world, foot, BlockFace.valueOf(parts[4]), spot);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
