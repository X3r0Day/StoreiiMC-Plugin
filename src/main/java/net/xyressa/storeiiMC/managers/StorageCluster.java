package net.xyressa.storeiiMC.managers;

import net.xyressa.storeiiMC.StoreiiMC;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class StorageCluster {

    private final StoreiiMC plugin;
    private final Location rootLocation;
    private List<Container> cachedContainers = null;

    public StorageCluster(StoreiiMC plugin, Location rootLocation) {
        this.plugin = plugin;
        this.rootLocation = rootLocation;
    }

    public void invalidateCache() {
        cachedContainers = null;
    }

    public List<Container> getContainers() {
        if (cachedContainers != null) return cachedContainers;
        List<Container> containers = new ArrayList<>();

        if (rootLocation == null || !(rootLocation.getBlock().getState() instanceof Container)) {
            return containers;
        }

        int limit = plugin.getConfig().getInt("limits.max-nodes-per-drive", 1);

        if (limit <= 1) {
            containers.add((Container) rootLocation.getBlock().getState());
            this.cachedContainers = containers;
            return containers;
        }

        // That's the BFS Logic well it only runs when you change the drive in config
        Queue<Block> queue = new LinkedList<>();
        Set<Location> visited = new HashSet<>();
        queue.add(rootLocation.getBlock());
        visited.add(rootLocation);

        while (!queue.isEmpty() && containers.size() < limit) {
            Block current = queue.poll();
            if (current.getState() instanceof Container container) {
                containers.add(container);
                int[][] directions = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
                for (int[] dir : directions) {
                    Block neighbor = current.getRelative(dir[0], dir[1], dir[2]);
                    if (!visited.contains(neighbor.getLocation()) && neighbor.getState() instanceof Container) {
                        visited.add(neighbor.getLocation());
                        queue.add(neighbor);
                    }
                }
            }
        }

        this.cachedContainers = containers;
        return containers;
    }

    public boolean addItem(ItemStack item) {
        invalidateCache();
        for (Container container : getContainers()) {
            if (container.getInventory().firstEmpty() != -1) {
                container.getInventory().addItem(item);
                return true;
            }
        }
        return false;
    }
}