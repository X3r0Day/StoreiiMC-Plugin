package net.xyressa.storeiiMC.listeners;

import net.kyori.adventure.text.Component;
import net.xyressa.storeiiMC.StoreiiMC;
import org.bukkit.Bukkit;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.HashMap;
import java.util.UUID;

public class ShulkerNavigator implements Listener {

    private final StoreiiMC plugin;
    private final HashMap<UUID, ItemStack> openShulkers = new HashMap<>();

    public ShulkerNavigator(StoreiiMC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClickItem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (item == null) return;

        if (item.getType().name().contains("SHULKER_BOX")) {
            event.setCancelled(true);
            openVirtualShulker(event.getPlayer(), item);
        }
    }

    private void openVirtualShulker(Player player, ItemStack shulkerItem) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta bsm)) return;
        if (!(bsm.getBlockState() instanceof ShulkerBox box)) return;

        Component title = bsm.displayName();
        if (title == null) title = Component.text("File Container");

        Inventory vInv = Bukkit.createInventory(null, 27, title);
        vInv.setContents(box.getInventory().getContents());
        openShulkers.put(player.getUniqueId(), shulkerItem);
        player.openInventory(vInv);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (openShulkers.containsKey(player.getUniqueId())) {
            ItemStack originalShulker = openShulkers.get(player.getUniqueId());
            BlockStateMeta bsm = (BlockStateMeta) originalShulker.getItemMeta();
            if (bsm.getBlockState() instanceof ShulkerBox box) {
                box.getInventory().setContents(event.getInventory().getContents());
                bsm.setBlockState(box);
                originalShulker.setItemMeta(bsm);
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (mainHand.getType() == originalShulker.getType()) {
                    player.getInventory().setItemInMainHand(originalShulker);
                }
            }
            openShulkers.remove(player.getUniqueId());
        }
    }
}