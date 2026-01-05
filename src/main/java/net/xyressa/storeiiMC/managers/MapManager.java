package net.xyressa.storeiiMC.managers;

import net.xyressa.storeiiMC.StoreiiMC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MapManager {

    private final StoreiiMC plugin;
    private final File mapsFolder;

    public MapManager(StoreiiMC plugin) {
        this.plugin = plugin;
        this.mapsFolder = new File(plugin.getDataFolder(), "maps");
        if (!mapsFolder.exists()) mapsFolder.mkdirs();
    }

    public CompletableFuture<Void> saveMapDataAsync(int mapId, byte[] data) {
        return CompletableFuture.runAsync(() -> {
            try (FileOutputStream fos = new FileOutputStream(new File(mapsFolder, "map_" + mapId + ".bin"))) {
                fos.write(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void registerMap(int mapId, byte[] data) {
        applyRenderer(mapId, data);
    }

    public byte[] getMapData(int mapId) {
        File file = new File(mapsFolder, "map_" + mapId + ".bin");
        if (!file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        } catch (Exception e) { return null; }
    }

    public byte[] readAndStitch(List<Integer> mapIds) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            for (int id : mapIds) {
                byte[] chunk = getMapData(id);
                if (chunk != null) buffer.write(chunk);
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void deleteMapData(int mapId) {
        CompletableFuture.runAsync(() -> {
            File file = new File(mapsFolder, "map_" + mapId + ".bin");
            if (file.exists()) file.delete();
        });
    }

    public void loadAllMaps() {
        CompletableFuture.runAsync(() -> {
            File[] files = mapsFolder.listFiles((dir, name) -> name.endsWith(".bin"));
            if (files == null) return;
            for (File file : files) {
                try {
                    int id = Integer.parseInt(file.getName().replace("map_", "").replace(".bin", ""));
                    byte[] data = getMapData(id);
                    Bukkit.getScheduler().runTask(plugin, () -> applyRenderer(id, data));
                } catch (Exception e) {}
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void applyRenderer(int mapId, byte[] data) {
        MapView view = Bukkit.getMap(mapId);
        if (view == null) return;
        for (MapRenderer r : view.getRenderers()) view.removeRenderer(r);
        view.addRenderer(new BinaryMapRenderer(data));
        view.setLocked(true);
    }

    private static class BinaryMapRenderer extends MapRenderer {
        private final byte[] data;
        public BinaryMapRenderer(byte[] data) { this.data = data; }
        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            for (int i = 0; i < 128 * 128; i++) {
                if (i >= data.length) break;
                canvas.setPixel(i % 128, i / 128, data[i]);
            }
        }
    }
}