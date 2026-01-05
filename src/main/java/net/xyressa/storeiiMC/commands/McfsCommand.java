package net.xyressa.storeiiMC.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.xyressa.storeiiMC.StoreiiMC;
import net.xyressa.storeiiMC.filesystem.MapWriter;
import net.xyressa.storeiiMC.model.Drive;
import net.xyressa.storeiiMC.web.WebServer;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class McfsCommand implements CommandExecutor {

    private final StoreiiMC plugin;

    public McfsCommand(StoreiiMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                if (args.length == 2) {
                    String name = args[1];

                    if (!name.matches("[a-zA-Z0-9_-]+")) {
                        player.sendMessage(Component.text("Invalid name! Use only A-Z, 0-9, _ and - (No dots)").color(NamedTextColor.RED));
                        return true;
                    }

                    var target = player.getTargetBlockExact(5);
                    if (target != null && target.getState() instanceof Chest) {

                        if (!player.hasPermission("storeiimc.admin")) {
                            int limit = plugin.getConfig().getInt("limits.max-drives-per-player", 3);
                            int current = plugin.getDriveManager().getPlayerDriveCount(player.getUniqueId());

                            if (current >= limit) {
                                player.sendMessage(Component.text("Limit reached! You can only own " + limit + " drives.").color(NamedTextColor.RED));
                                return true;
                            }
                        }

                        if (plugin.getDriveManager().getDrive(name) != null) {
                            player.sendMessage(Component.text("Drive name already taken.").color(NamedTextColor.RED));
                            return true;
                        }

                        plugin.getDriveManager().createDrive(name, player.getUniqueId(), target.getLocation());
                        player.sendMessage(Component.text("[MCFS] Drive '" + name + "' created successfully!").color(NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Look at a chest to turn it into a drive.").color(NamedTextColor.RED));
                    }
                } else {
                    player.sendMessage(Component.text("Usage: /mcfs create <driveName>").color(NamedTextColor.RED));
                }
                break;

            case "deletedrive":
                if (args.length == 2) {
                    String name = args[1];
                    Drive drive = plugin.getDriveManager().getDrive(name);

                    if (drive == null) {
                        player.sendMessage(Component.text("Drive not found.").color(NamedTextColor.RED));
                        return true;
                    }

                    // Security Check: Owner OR Admin
                    if (drive.getOwner().equals(player.getUniqueId()) || player.hasPermission("storeiimc.admin")) {
                        plugin.getDriveManager().deleteDrive(name);
                        player.sendMessage(Component.text("[MCFS] Drive '" + name + "' deleted.").color(NamedTextColor.RED));
                    } else {
                        player.sendMessage(Component.text("You do not own this drive.").color(NamedTextColor.RED));
                    }
                } else {
                    player.sendMessage(Component.text("Usage: /mcfs deletedrive <driveName>").color(NamedTextColor.RED));
                }
                break;

            case "password":
                if (args.length == 3) {
                    String name = args[1];
                    String pass = args[2];
                    Drive d = plugin.getDriveManager().getDrive(name);
                    if (d != null && d.getOwner().equals(player.getUniqueId())) {
                        plugin.getDriveManager().setPassword(name, pass); // Hashes automatically now
                        player.sendMessage(Component.text("[MCFS] Password set for '" + name + "'.").color(NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Drive not found or you are not the owner.").color(NamedTextColor.RED));
                    }
                } else {
                    player.sendMessage(Component.text("Usage: /mcfs password <driveName> <newPassword>").color(NamedTextColor.RED));
                }
                break;

            case "import":
                if (args.length == 3) {
                    var target = player.getTargetBlockExact(5);
                    if (target != null) {
                        Drive d = plugin.getDriveManager().getDriveByLocation(target.getLocation());
                        if (d != null && d.getOwner().equals(player.getUniqueId())) {
                            new MapWriter(plugin).importFromUrl(d, args[1], args[2], player);
                        } else {
                            player.sendMessage(Component.text("Look at one of your drives.").color(NamedTextColor.RED));
                        }
                    } else player.sendMessage(Component.text("Look at a drive chest.").color(NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("Usage: /mcfs import <url> <filename>").color(NamedTextColor.RED));
                }
                break;

            case "webimport":
                if (args.length == 2) {
                    String filename = args[1];
                    var target = player.getTargetBlockExact(5);
                    if (target != null) {
                        Drive d = plugin.getDriveManager().getDriveByLocation(target.getLocation());
                        if (d != null && d.getOwner().equals(player.getUniqueId())) {

                            String code = UUID.randomUUID().toString().substring(0, 8);
                            WebServer.pendingImports.put(code, new WebServer.PendingImport(player, d, filename));

                            Component msg = Component.text("[MCFS] Click here to paste URL: ")
                                    .color(NamedTextColor.YELLOW)
                                    .append(Component.text("http://localhost:8080/paste/" + code)
                                            .color(NamedTextColor.AQUA)
                                            .clickEvent(ClickEvent.openUrl("http://localhost:8080/paste/" + code)));

                            player.sendMessage(msg);

                        } else {
                            player.sendMessage(Component.text("Look at one of your drives.").color(NamedTextColor.RED));
                        }
                    } else player.sendMessage(Component.text("Look at a drive chest.").color(NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("Usage: /mcfs webimport <filename>").color(NamedTextColor.RED));
                }
                break;

            case "list":
                var target = player.getTargetBlockExact(5);
                if (target != null) {
                    Drive d = plugin.getDriveManager().getDriveByLocation(target.getLocation());
                    if (d != null) {
                        if(d.isPrivate() && !d.getOwner().equals(player.getUniqueId())) {
                            player.sendMessage(Component.text("This drive is private.").color(NamedTextColor.RED));
                        } else {
                            plugin.getFileOps().listFiles(d, player);
                        }
                    } else player.sendMessage(Component.text("Not a drive.").color(NamedTextColor.RED));
                } else player.sendMessage(Component.text("Look at a drive.").color(NamedTextColor.RED));
                break;

            case "delete":
                if (args.length == 2) {
                    plugin.getFileOps().deleteFile(args[1], player);
                } else {
                    player.sendMessage(Component.text("Usage: /mcfs delete <filename>").color(NamedTextColor.RED));
                }
                break;

            case "purge":
                plugin.getFileOps().purgeOrphanedFiles(player);
                break;

            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- MCFS Help ---").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/mcfs create <name>").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/mcfs password <drive> <pass>").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/mcfs deletedrive <name>").color(NamedTextColor.RED));
        player.sendMessage(Component.text("/mcfs webimport <name>").color(NamedTextColor.AQUA));
        player.sendMessage(Component.text("/mcfs list").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/mcfs delete <filename>").color(NamedTextColor.GOLD));
    }
}