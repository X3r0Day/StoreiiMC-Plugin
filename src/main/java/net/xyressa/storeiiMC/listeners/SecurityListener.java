package net.xyressa.storeiiMC.listeners;

import net.xyressa.storeiiMC.StoreiiMC;
import net.xyressa.storeiiMC.model.Drive;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

public class SecurityListener implements Listener {

    private final StoreiiMC plugin;

    public SecurityListener(StoreiiMC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Container)) return;
        Container chest = (Container) event.getInventory().getHolder();

        Drive drive = plugin.getDriveManager().getDriveByLocation(chest.getLocation());
        if (drive == null) return;

        if (!event.getPlayer().hasPermission("storeiimc.admin")) {
            if (!drive.getOwner().equals(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c[MCFS] Access Denied: This drive belongs to another player.");
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Container)) return;

        Drive drive = plugin.getDriveManager().getDriveByLocation(event.getBlock().getLocation());
        if (drive == null) return;

        if (!event.getPlayer().hasPermission("storeiimc.admin")) {
            if (!drive.getOwner().equals(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c[MCFS] You cannot break another player's drive.");
            }
        }
    }
}