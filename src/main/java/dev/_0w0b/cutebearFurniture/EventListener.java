package dev._0w0b.cutebearFurniture;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.List;
import java.util.UUID;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class EventListener implements Listener {
    final int scale = 1;
    private final FurnitureStorage storage;
    private final FurnitureBeds beds;
    private final FurnitureLadders ladders;

    EventListener(FurnitureStorage storage, FurnitureBeds beds, FurnitureLadders ladders) {
        this.storage = storage;
        this.beds = beds;
        this.ladders = ladders;
    }

    @EventHandler
    public void onPlayerInteraction(PlayerInteractEvent event) {
        if (onFurnitureBlockClick(event))
            return;
        if (event.getPlayer().getGameMode() == GameMode.ADVENTURE)
            return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.hasItem()) {

            if (beds.isTemporary(event.getClickedBlock()))
                return;
            if (event.getClickedBlock().getType().isInteractable() && !event.getPlayer().isSneaking())
                return;
            if (event.hasItem()) {
                ItemStack item = event.getItem();
                if (item.getType() == Material.POISONOUS_POTATO
                        && item.hasData(DataComponentTypes.ITEM_MODEL)) {

                    String id = String.valueOf(item.getData(DataComponentTypes.ITEM_MODEL).key());
                    if (!id.startsWith("cutebear_furniture:"))
                        return;
                    // Floor pieces go on top of the clicked block, wall pieces in front of it and ceiling pieces under it.
                    FurnitureTypes.Type type = FurnitureTypes.get(id);
                    FurnitureTypes.Placement placement = type == null ? FurnitureTypes.Placement.FLOOR : type.placement();
                    BlockFace face = event.getBlockFace();
                    BlockFace from = event.getPlayer().getFacing();
                    switch (placement) {
                        case FLOOR -> {
                            if (face != BlockFace.UP)
                                return;
                        }
                        case CEILING -> {
                            if (face != BlockFace.DOWN)
                                return;
                        }
                        case WALL -> {
                            if (face == BlockFace.UP || face == BlockFace.DOWN)
                                return;
                            from = face.getOppositeFace();
                        }
                    }
                    Block placedBlock = event.getClickedBlock().getRelative(face);
                    if (!placedBlock.isEmpty())
                        return;
                    EquipmentSlot hand = event.getHand();
                    if (hand == null) hand = EquipmentSlot.HAND;
                    BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                            placedBlock,
                            placedBlock.getState(),
                            event.getClickedBlock(),
                            item,
                            event.getPlayer(),
                            true,
                            hand
                    );
                    Bukkit.getPluginManager().callEvent(placeEvent);
                    if (placeEvent.isCancelled() || !placeEvent.canBuild())
                        return;

                    Location spawnLoc = placedBlock.getLocation();
                    spawnLoc.add(0.5, 0.5, 0.5);

                    float snappedYaw = switch (from) {
                        case NORTH -> 270f; // North (180) + 90 = 270 (East)
                        case SOUTH -> 90f; // South (0) + 90 = 90 (West)
                        case WEST -> 180f; // West (90) + 90 = 180 (North)
                        case EAST -> 0f; // East (270) + 90 = 360/0 (South)
                        default -> 0f;
                    };
                    if (type != null)
                        snappedYaw = (snappedYaw + type.yawOffset()) % 360f;

                    spawnLoc.setYaw(snappedYaw);
                    ItemDisplay display = event
                            .getPlayer()
                            .getWorld()
                            .spawn(
                                    spawnLoc,
                                    ItemDisplay.class,
                                    entity -> {
                                        entity.setItemStack(item);
                                        entity.setTransformation(
                                                new Transformation(
                                                        new Vector3f(),
                                                        new AxisAngle4f(),
                                                        new Vector3f(scale, scale, scale),
                                                        new AxisAngle4f()));
                                    });
                    // The interaction sits on the floor of the piece's block. Placing the barriers below fits it to
                    // them.
                    Interaction interaction = spawnLoc
                            .getWorld()
                            .spawn(
                                    placedBlock.getLocation().add(0.5, 0, 0.5),
                                    Interaction.class,
                                    entity -> {
                                        entity.setInteractionWidth(1.0f);
                                        entity.setInteractionHeight(1.0f);
                                        entity
                                                .getPersistentDataContainer()
                                                .set(
                                                        new NamespacedKey(
                                                                CutebearFurniture.getInstance(),
                                                                "furniture"),
                                                        PersistentDataType.STRING,
                                                        display.getUniqueId().toString());
                                        boolean seat = type != null ? type.seat()
                                                : id.contains("chair") || id.contains("stool") || id.contains("toilet");
                                        if (seat) {
                                            NamespacedKey SITTABLE_KEY = new NamespacedKey(
                                                    CutebearFurniture.getInstance(),
                                                    "is_sittable");
                                            entity
                                                    .getPersistentDataContainer()
                                                    .set(SITTABLE_KEY, PersistentDataType.BYTE, (byte) 1);
                                        }
                                    });
                    // Make the piece solid. Blocks beyond the clicked spot (a bathtub's second half) need build rights there too.
                    Player player = event.getPlayer();
                    EquipmentSlot placeHand = hand;
                    FurnitureBarriers.place(interaction, display,
                            block -> block.equals(placedBlock) || canBuild(block, placedBlock, item, player, placeHand));
                    ladders.track(interaction);
                    if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
                        item.setAmount(item.getAmount() - 1);
                }
            }
        }
    }

    // A click on a piece's barrier, or on the climbing block in a ladder, acts on the piece: left click breaks it and
    // right click uses it. Sneaking right clicks place blocks and furniture against it instead, as on a chest.
    private boolean onFurnitureBlockClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null)
            return false;
        Interaction interaction = FurnitureBarriers.owner(block);
        if (interaction == null)
            return false;
        Player player = event.getPlayer();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (canBreak(interaction, player))
                removeFurniture(interaction, player);
            return true;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() != Event.Result.DENY) {
            Runnable use = use(interaction, player);
            if (use == null)
                return false;
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND)
                use.run();
            return true;
        }
        return false;
    }

    // What right clicking a piece does, or null for nothing: sleep in a bed, open a piece that stores things, open the
    // furniture workbench's stonecutter, or sit down.
    private Runnable use(Interaction interaction, Player player) {
        if (player.isSneaking())
            return null;
        ItemDisplay display = FurnitureBarriers.display(interaction);
        if (display == null)
            return null;
        String model = FurnitureTypes.model(display);
        FurnitureTypes.Type type = model == null ? null : FurnitureTypes.get(model);
        if (type != null && type.bed() != null)
            return () -> beds.use(interaction, display, player, type.bed());
        int slots = model == null ? 0 : FurnitureTypes.storage(model);
        if (slots > 0)
            return () -> storage.open(interaction, display, player, slots);
        if (type != null && "stonecutter".equals(type.use()))
            return () -> MenuType.STONECUTTER.builder()
                    .location(display.getLocation())
                    .checkReachable(false)
                    .title(FurnitureStorage.title(display))
                    .build(player)
                    .open();
        if (isSittable(interaction))
            return () -> sit(interaction, player);
        return null;
    }

    private boolean canBuild(Block block, Block against, ItemStack item, Player player, EquipmentSlot hand) {
        BlockPlaceEvent check = new BlockPlaceEvent(block, block.getState(), against, item, player, true, hand);
        Bukkit.getPluginManager().callEvent(check);
        return !check.isCancelled() && check.canBuild();
    }

    private boolean canBreak(Interaction interaction, Player player) {
        if (player.getGameMode() == GameMode.ADVENTURE)
            return false;
        BlockBreakEvent breakEvent = new BlockBreakEvent(interaction.getLocation().getBlock(), player);
        Bukkit.getPluginManager().callEvent(breakEvent);
        return !breakEvent.isCancelled();
    }

    private static boolean isSittable(Interaction interaction) {
        NamespacedKey SITTABLE_KEY = new NamespacedKey(
                CutebearFurniture.getInstance(),
                "is_sittable");
        return interaction.getPersistentDataContainer().has(SITTABLE_KEY, PersistentDataType.BYTE);
    }

    // Pieces without a barrier in their own block are clicked through their interaction.
    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction))
            return;
        Runnable use = use(interaction, event.getPlayer());
        if (use == null)
            return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND)
            use.run();
    }

    // Where a piece's seat goes: just under the top of its block.
    private static Location seat(Interaction interaction) {
        return interaction.getLocation().getBlock().getLocation().add(0.5, 0.8, 0.5);
    }

    private static boolean isSeat(Entity entity) {
        return entity instanceof ArmorStand && entity.getPersistentDataContainer().has(
                new NamespacedKey(CutebearFurniture.getInstance(), "sit_armorstand"), PersistentDataType.BYTE);
    }

    private void sit(Interaction interaction, Player player) {
        NamespacedKey key = new NamespacedKey(
                CutebearFurniture.getInstance(), "furniture");
        String uuidStr = interaction.getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (uuidStr == null || player.isSneaking())
            return;
        Location seatLoc = seat(interaction);
        boolean alreadyOccupied = seatLoc.getWorld().getNearbyEntities(seatLoc, 0.2, 0.2, 0.2).stream()
                .anyMatch(e -> e instanceof ArmorStand && !e.getPassengers().isEmpty());
        if (alreadyOccupied)
            return;

        UUID displayUUID = UUID.fromString(uuidStr);
        Entity target = Bukkit.getEntity(displayUUID);
        if (target == null)
            return;
        float yaw = (target.getYaw() + 90 + 360) % 360;
        ArmorStand seat = seatLoc
                .getWorld()
                .spawn(
                        seatLoc,
                        ArmorStand.class,
                        entity -> {
                            entity.setInvisible(true);
                            entity.setRotation(yaw, 0);
                            entity.setMarker(true);
                            entity.setGravity(false);
                            entity.setPersistent(false);
                            NamespacedKey SIT_ARMORSTAND_KEY = new NamespacedKey(
                                    CutebearFurniture.getInstance(),
                                    "sit_armorstand");
                            entity
                                    .getPersistentDataContainer()
                                    .set(SIT_ARMORSTAND_KEY, PersistentDataType.BYTE, (byte) 1);
                        });
        seat.addPassenger(player);
    }

    @EventHandler
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction interaction))
            return;
        Player player = event.getDamager() instanceof Player damager ? damager : null;
        if (player != null && !canBreak(interaction, player)) {
            event.setCancelled(true);
            return;
        }
        if (removeFurniture(interaction, player))
            event.setCancelled(true);
    }

    // Drops the piece for survival players and what it holds for anyone, then removes its display, seat, blocks and
    // interaction. A bed someone sleeps in wakes them first.
    private boolean removeFurniture(Interaction interaction, Player player) {
        NamespacedKey key = new NamespacedKey(
                CutebearFurniture.getInstance(),
                "furniture");
        String uuidStr = interaction.getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (uuidStr == null)
            return false;
        UUID displayUUID = UUID.fromString(uuidStr);
        Entity target = Bukkit.getEntity(displayUUID);
        if (target != null && !(target instanceof ItemDisplay))
            return false;
        beds.release(interaction);
        if (target instanceof ItemDisplay display) {
            storage.drop(interaction, display.getLocation());
            ItemStack item = display.getItemStack();
            if (item != null && !item.getType().isAir()) {
                if (player != null && player.getGameMode() == GameMode.SURVIVAL) {
                    item.setAmount(1);
                    display.getWorld().dropItemNaturally(display.getLocation(), item);
                }
            }
            ladders.removed(display);
            display.remove();
        } else {
            storage.drop(interaction, interaction.getLocation());
        }
        // A piece whose display is gone still loses its barriers, so it can't leave an invisible wall.
        Location seatLoc = seat(interaction);
        seatLoc.getWorld().getNearbyEntities(seatLoc, 0.5, 0.5, 0.5).stream()
                .filter(EventListener::isSeat)
                .forEach(Entity::remove);
        FurnitureBarriers.remove(interaction);
        interaction.remove();
        return true;
    }

    // Furniture placed by an older version is brought up to date when its chunk loads, and a bed left as a vanilla
    // bed by a stopped server is put back.
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Interaction> pieces = event.getEntities().stream()
                .filter(entity -> entity instanceof Interaction)
                .map(entity -> (Interaction) entity)
                .toList();
        if (pieces.isEmpty())
            return;
        // Next tick, so a bathtub reaching into an unloaded chunk doesn't load it mid entity load.
        Bukkit.getScheduler().runTask(CutebearFurniture.getInstance(), () -> pieces.forEach(piece -> {
            if (piece.isValid())
                load(piece);
        }));
    }

    void load(Interaction piece) {
        FurnitureBarriers.upgrade(piece);
        beds.recover(piece);
        ladders.track(piece);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof ArmorStand seat) {
            NamespacedKey SIT_ARMORSTAND_KEY = new NamespacedKey(
                    CutebearFurniture.getInstance(),
                    "sit_armorstand");
            if (seat.getPersistentDataContainer().has(SIT_ARMORSTAND_KEY, PersistentDataType.BYTE)) {
                seat.remove();
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Entity vehicle = event.getPlayer().getVehicle();

        if (vehicle != null
                && vehicle
                        .getPersistentDataContainer()
                        .has(
                                new NamespacedKey(
                                        CutebearFurniture.getInstance(),
                                        "sit_armorstand"),
                                PersistentDataType.BYTE)) {
            vehicle.remove();
        }
    }
}
