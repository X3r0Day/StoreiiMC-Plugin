package net.xyressa.storeiiMC.filesystem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.xyressa.storeiiMC.StoreiiMC;
import net.xyressa.storeiiMC.model.Drive;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MapWriter {

    private final StoreiiMC plugin;
    private final NamespacedKey mapIdKey;
    private final NamespacedKey chunkIndexKey;
    private static final int CHUNK_SIZE = 16384;

    public MapWriter(StoreiiMC plugin) {
        this.plugin = plugin;
        this.mapIdKey = new NamespacedKey(plugin, "map_link_id");
        this.chunkIndexKey = new NamespacedKey(plugin, "chunk_index");
    }

    public void importFromUrl(Drive drive, String urlString, String filename, Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.net.URL url = java.net.URI.create(urlString).toURL();
                java.net.InetAddress address = java.net.InetAddress.getByName(url.getHost());
                if (address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                    player.sendMessage(Component.text("Security Error: Cannot import from local/internal IPs.").color(NamedTextColor.RED));
                    return;
                }

                player.sendMessage(Component.text("[MCFS] Downloading stream...").color(NamedTextColor.YELLOW));
                File tempFile = new File(plugin.getDataFolder(), "temp/dl_" + System.currentTimeMillis() + ".tmp");
                if (!tempFile.getParentFile().exists()) tempFile.getParentFile().mkdirs();

                try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                     java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }

                player.sendMessage(Component.text("[MCFS] Processing...").color(NamedTextColor.YELLOW));
                plugin.getServer().getScheduler().runTask(plugin, () -> processFileStreamed(drive, tempFile, filename, player));
            } catch (Exception e) {
                player.sendMessage(Component.text("Error: " + e.getMessage()).color(NamedTextColor.RED));
            }
        });
    }

    public void importFromWeb(String driveId, File tempFile, String filename) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Drive drive = plugin.getDriveManager().getDrive(driveId);
            if (drive == null) {
                plugin.getLogger().warning("Web upload failed: Drive '" + driveId + "' not found.");
                tempFile.delete();
                return;
            }
            plugin.getLogger().info("Processing Web Upload for Drive " + driveId + ": " + filename);
            processFileStreamed(drive, tempFile, filename, null);
        });
    }

    private void processFileStreamed(Drive drive, File file, String filename, Player player) {
        try (FileInputStream fis = new FileInputStream(file)) {
            List<ItemStack> currentLayerItems = new ArrayList<>();
            List<CompletableFuture<Void>> ioTasks = new ArrayList<>();
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int chunkIndex = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunkData = Arrays.copyOf(buffer, bytesRead);
                createAndRegisterChunk(chunkData, chunkIndex, currentLayerItems, ioTasks);
                chunkIndex++;
            }

            if (currentLayerItems.isEmpty()) {
                createAndRegisterChunk(new byte[0], 0, currentLayerItems, ioTasks);
            }

            CompletableFuture.allOf(ioTasks.toArray(new CompletableFuture[0])).join();
            List<ItemStack> packed = packRecursively(currentLayerItems);

            ItemStack rootItem = packed.get(0);
            ItemMeta meta = rootItem.getItemMeta();
            meta.displayName(Component.text(filename).color(NamedTextColor.AQUA));
            rootItem.setItemMeta(meta);

            addToDrive(drive, rootItem, player);
            file.delete();

        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            if (player != null) player.sendMessage(Component.text(msg).color(NamedTextColor.RED));
            else plugin.getLogger().severe(msg);
            e.printStackTrace();
        }
    }


    private void createAndRegisterChunk(byte[] data, int index, List<ItemStack> items, List<CompletableFuture<Void>> tasks) {
        MapView view = Bukkit.createMap(Bukkit.getWorlds().get(0));
        view.setTrackingPosition(false);
        view.setCenterX(0);
        view.setCenterZ(0);
        view.setLocked(true);
        view.getRenderers().clear();

        plugin.getMapManager().registerMap(view.getId(), data);
        tasks.add(plugin.getMapManager().saveMapDataAsync(view.getId(), data));

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);

        meta.getPersistentDataContainer().set(mapIdKey, PersistentDataType.INTEGER, view.getId());
        meta.getPersistentDataContainer().set(chunkIndexKey, PersistentDataType.INTEGER, index);

        meta.displayName(Component.text("Sector " + index).color(NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);

        items.add(item);

        if (tasks.size() > 100) tasks.removeIf(CompletableFuture::isDone);
    }

    private List<ItemStack> packRecursively(List<ItemStack> items) {
        int layer = 1;
        while (items.size() > 1) {
            List<ItemStack> nextLayer = new ArrayList<>();
            int batchSize = 27;
            for (int i = 0; i < items.size(); i += batchSize) {
                int end = Math.min(items.size(), i + batchSize);
                List<ItemStack> batch = items.subList(i, end);
                ItemStack shulker = new ItemStack(Material.CYAN_SHULKER_BOX);
                BlockStateMeta bsm = (BlockStateMeta) shulker.getItemMeta();
                if (bsm.getBlockState() instanceof ShulkerBox box) {
                    for (int slot = 0; slot < batch.size(); slot++) box.getInventory().setItem(slot, batch.get(slot));
                    bsm.setBlockState(box);
                    bsm.displayName(Component.text("Layer " + layer).color(NamedTextColor.GRAY));
                    shulker.setItemMeta(bsm);
                    nextLayer.add(shulker);
                }
            }
            items = nextLayer;
            layer++;
        }
        return items;
    }

    private void addToDrive(Drive drive, ItemStack item, Player player) {
        boolean success = drive.getCluster().addItem(item);
        if (success) {
            if (player != null) player.sendMessage(Component.text("[MCFS] Saved to drive '" + drive.getId() + "'!").color(NamedTextColor.GREEN));
            else plugin.getLogger().info("File saved to drive " + drive.getId());
        } else {
            if (drive.getRootLocation() != null) {
                drive.getRootLocation().getWorld().dropItem(drive.getRootLocation().add(0, 1, 0), item);
                if (player != null) player.sendMessage(Component.text("Drive Full! Dropped item.").color(NamedTextColor.RED));
            }
        }
    }
}