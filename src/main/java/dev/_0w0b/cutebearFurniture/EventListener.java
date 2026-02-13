package dev._0w0b.cutebearFurniture;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.UUID;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class EventListener implements Listener {
  final int scale = 1;

  @EventHandler
  public void onPlayerInteraction(PlayerInteractEvent event) {
    if (event.getPlayer().getGameMode() == GameMode.ADVENTURE) return;
    if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.hasItem()) {

      if (event.getClickedBlock().getType().isInteractable() && !event.getPlayer().isSneaking())
        return;
      if (event.getBlockFace() != BlockFace.UP) return;
      if (event.hasItem()) {
        ItemStack item = event.getItem();
        if (item.getType() == Material.POISONOUS_POTATO
            && item.hasData(DataComponentTypes.ITEM_MODEL)) {

          String id = String.valueOf(item.getData(DataComponentTypes.ITEM_MODEL).key());
          if (!id.startsWith("cutebear_furniture:")) return;
          if (event.getClickedBlock().getRelative(BlockFace.UP).getType() != Material.AIR) return;
          Location spawnLoc = event.getClickedBlock().getRelative(BlockFace.UP).getLocation();
          spawnLoc.add(0.5, 0.5, 0.5);

          float snappedYaw =
              switch (event.getPlayer().getFacing()) {
                case NORTH -> 270f; // North (180) + 90 = 270 (East)
                case SOUTH -> 90f; // South (0) + 90 = 90 (West)
                case WEST -> 180f; // West (90) + 90 = 180 (North)
                case EAST -> 0f; // East (270) + 90 = 360/0 (South)
                default -> 0f;
              };

          spawnLoc.setYaw(snappedYaw);
          ItemDisplay display =
              event
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
          spawnLoc
              .getWorld()
              .spawn(
                  spawnLoc,
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
                    if (id.contains("chair") || id.contains("stool")) {
                      NamespacedKey SITTABLE_KEY =
                          new NamespacedKey(
                              CutebearFurniture.getInstance(),
                              "is_sittable");
                      entity
                          .getPersistentDataContainer()
                          .set(SITTABLE_KEY, PersistentDataType.BYTE, (byte) 1);
                    }
                  });
          if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
            item.setAmount(item.getAmount() - 1);
        }
      }
    }
  }

  @EventHandler
  public void onRightClick(PlayerInteractEntityEvent event) {
    if (!(event.getRightClicked() instanceof Interaction interaction)) return;

    NamespacedKey SITTABLE_KEY =
        new NamespacedKey(
            CutebearFurniture.getInstance(),
            "is_sittable");
    if (!interaction.getPersistentDataContainer().has(SITTABLE_KEY, PersistentDataType.BYTE))
      return;

    Player player = event.getPlayer();

    NamespacedKey key =
        new NamespacedKey(
            CutebearFurniture.getInstance(), "furniture");
    String uuidStr = interaction.getPersistentDataContainer().get(key, PersistentDataType.STRING);

    if (uuidStr == null || player.isSneaking()) return;
    boolean alreadyOccupied =
        interaction.getNearbyEntities(0.1, 0.1, 0.1).stream()
            .anyMatch(e -> e instanceof ArmorStand && !e.getPassengers().isEmpty());
    if (alreadyOccupied) return;

    UUID displayUUID = UUID.fromString(uuidStr);
    Entity target = Bukkit.getEntity(displayUUID);
    Location seatLoc = interaction.getLocation().clone().add(0, 0.3, 0);
    float yaw = (target.getYaw() + 90 + 360) % 360;
    ArmorStand seat =
        seatLoc
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
                  NamespacedKey SIT_ARMORSTAND_KEY =
                      new NamespacedKey(
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
    if (!(event.getEntity() instanceof Interaction interaction)) return;
    if (event.getDamager() instanceof Player player) {
      if (player.getGameMode() == GameMode.ADVENTURE) {
        event.setCancelled(true);
        return;
      }
    }
    NamespacedKey key =
        new NamespacedKey(
           CutebearFurniture.getInstance(),
            "furniture");
    String uuidStr = interaction.getPersistentDataContainer().get(key, PersistentDataType.STRING);

    if (uuidStr != null) {
      UUID displayUUID = UUID.fromString(uuidStr);
      Entity target = Bukkit.getEntity(displayUUID);
      if (target instanceof ItemDisplay display) {
        ItemStack item = display.getItemStack();
        if (item != null && !item.getType().isAir()) {
          if (event.getDamager() instanceof Player player) {
            if (player.getGameMode() == GameMode.SURVIVAL) {
              item.setAmount(1);
              display.getWorld().dropItemNaturally(display.getLocation(), item);
            }
          }
        }
        display.remove();
        interaction.getNearbyEntities(0.5, 0.5, 0.5).stream()
            .filter(e -> e instanceof ArmorStand)
            .filter(
                e ->
                    e.getPersistentDataContainer()
                        .has(
                            new NamespacedKey(
                                CutebearFurniture.getInstance(),
                                "sit_armorstand"),
                            PersistentDataType.BYTE))
            .forEach(Entity::remove);
        interaction.remove();
        event.setCancelled(true);
      }
    }
  }

  @EventHandler
  public void onDismount(EntityDismountEvent event) {
    if (event.getDismounted() instanceof ArmorStand seat) {
      NamespacedKey SIT_ARMORSTAND_KEY =
          new NamespacedKey(
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
