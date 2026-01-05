package net.xyressa.storeiiMC.filesystem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.xyressa.storeiiMC.StoreiiMC;
import net.xyressa.storeiiMC.model.Drive;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class FileOperations {

    private final StoreiiMC plugin;
    private final NamespacedKey mapIdKey;

    public FileOperations(StoreiiMC plugin) {
        this.plugin = plugin;
        this.mapIdKey = new NamespacedKey(plugin, "map_link_id");
    }

    public void listFiles(Drive drive, Player player) {
        List<org.bukkit.block.Container> containers = drive.getCluster().getContainers();
        player.sendMessage(Component.text("=== Drive: " + drive.getId() + " (" + containers.size() + " Nodes) ===").color(NamedTextColor.YELLOW));
        boolean found = false;

        for (org.bukkit.block.Container chest : containers) {
            for (ItemStack item : chest.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    String name = MinecraftFS.getItemName(item);
                    if (name != null) {
                        found = true;
                        boolean isDoc = item.getType().name().contains("BOOK");
                        String type = isDoc ? "[DOC] " : "[BIN] ";
                        NamedTextColor color = isDoc ? NamedTextColor.GREEN : NamedTextColor.AQUA;

                        Component message = Component.text(type + name).color(color)
                                .append(Component.text(" [DELETE]")
                                        .color(NamedTextColor.RED)
                                        .clickEvent(ClickEvent.suggestCommand("/mcfs delete " + name)));

                        player.sendMessage(message);
                    }
                }
            }
        }
        if (!found) player.sendMessage(Component.text("Empty Drive.").color(NamedTextColor.GRAY));
    }


    public void deleteFileSilently(Drive drive, String filename) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (org.bukkit.block.Container chest : drive.getCluster().getContainers()) {
                for (ItemStack item : chest.getInventory().getContents()) {
                    if (item != null && filename.equals(MinecraftFS.getItemName(item))) {

                        List<Integer> idsToDelete = new ArrayList<>();
                        collectMapIds(item, idsToDelete);

                        // Remove from chest (Sync)
                        chest.getInventory().remove(item);

                        // Clean up disk (Async)
                        CompletableFuture.runAsync(() -> {
                            for (int id : idsToDelete) plugin.getMapManager().deleteMapData(id);
                        });
                        return; // Found and deleted
                    }
                }
            }
        });
    }


    public void deleteFile(String filename, Player player) {
        org.bukkit.block.Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(Component.text("Look at a drive chest to delete files.").color(NamedTextColor.RED));
            return;
        }

        Drive drive = plugin.getDriveManager().getDriveByLocation(target.getLocation());
        if (drive == null) {
            player.sendMessage(Component.text("Not a drive.").color(NamedTextColor.RED));
            return;
        }

        if (!drive.getOwner().equals(player.getUniqueId()) && !player.hasPermission("storeiimc.admin")) {
            player.sendMessage(Component.text("Access Denied.").color(NamedTextColor.RED));
            return;
        }

        for (org.bukkit.block.Container chest : drive.getCluster().getContainers()) {
            for (ItemStack item : chest.getInventory().getContents()) {
                if (item != null && filename.equals(MinecraftFS.getItemName(item))) {
                    List<Integer> idsToDelete = new ArrayList<>();
                    collectMapIds(item, idsToDelete);
                    chest.getInventory().remove(item);
                    player.sendMessage(Component.text("[MCFS] Deleting " + filename + "...").color(NamedTextColor.YELLOW));

                    CompletableFuture.runAsync(() -> {
                        for (int id : idsToDelete) plugin.getMapManager().deleteMapData(id);
                        player.sendMessage(Component.text("[MCFS] Deleted.").color(NamedTextColor.GREEN));
                    });
                    return;
                }
            }
        }
        player.sendMessage(Component.text("File not found in this drive.").color(NamedTextColor.RED));
    }

    public void purgeOrphanedFiles(Player player) {
        player.sendMessage(Component.text("[MCFS] Starting Global Purge...").color(NamedTextColor.YELLOW));
        CompletableFuture.runAsync(() -> {
            Set<Integer> activeIds = new HashSet<>();
            for (Drive drive : plugin.getDriveManager().getAllDrives().values()) {
                for (org.bukkit.block.Container chest : drive.getCluster().getContainers()) {
                    for (ItemStack item : chest.getInventory().getContents()) {
                        if (item != null && item.getType() != Material.AIR) collectMapIds(item, activeIds);
                    }
                }
            }
            player.sendMessage(Component.text("[MCFS] Active chunks: " + activeIds.size()).color(NamedTextColor.YELLOW));

            File mapsFolder = new File(plugin.getDataFolder(), "maps");
            File[] files = mapsFolder.listFiles((dir, name) -> name.endsWith(".bin"));
            if (files == null) return;

            int deleted = 0;
            for (File file : files) {
                try {
                    int id = Integer.parseInt(file.getName().replace("map_", "").replace(".bin", ""));
                    if (!activeIds.contains(id)) {
                        if (file.delete()) deleted++;
                    }
                } catch (Exception e) {}
            }
            player.sendMessage(Component.text("[MCFS] Purged " + deleted + " files.").color(NamedTextColor.GREEN));
        });
    }

    private void collectMapIds(ItemStack item, java.util.Collection<Integer> ids) {
        if (item == null || !item.hasItemMeta()) return;
        if (item.getItemMeta().getPersistentDataContainer().has(mapIdKey, PersistentDataType.INTEGER)) {
            Integer id = item.getItemMeta().getPersistentDataContainer().get(mapIdKey, PersistentDataType.INTEGER);
            if (id != null) ids.add(id);
        }
        if (item.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack sub : box.getInventory().getContents()) collectMapIds(sub, ids);
        }
    }
}