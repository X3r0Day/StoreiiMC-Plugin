package net.xyressa.storeiiMC;

import net.xyressa.storeiiMC.commands.McfsCommand;
import net.xyressa.storeiiMC.filesystem.FileOperations;
import net.xyressa.storeiiMC.listeners.SecurityListener;
import net.xyressa.storeiiMC.listeners.ShulkerNavigator;
import net.xyressa.storeiiMC.managers.DriveManager;
import net.xyressa.storeiiMC.managers.MapManager;
import net.xyressa.storeiiMC.managers.ShareManager; // Import this
import net.xyressa.storeiiMC.web.WebServer;
import org.bukkit.plugin.java.JavaPlugin;

public final class StoreiiMC extends JavaPlugin {

    private WebServer webServer;
    private MapManager mapManager;
    private DriveManager driveManager;
    private FileOperations fileOps;
    private ShareManager shareManager; // 1. Add Field

    @Override
    public void onEnable() {
        // Setup Config
        getConfig().addDefault("limits.max-drives-per-player", 1); // Drive per player
        getConfig().addDefault("limits.max-nodes-per-drive", 1); // Node per drive
        getConfig().addDefault("limits.max-file-size-mb", 1024); // Per-File max size
        getConfig().options().copyDefaults(true);
        saveConfig();


        this.mapManager = new MapManager(this);
        this.driveManager = new DriveManager(this);
        this.fileOps = new FileOperations(this);
        this.shareManager = new ShareManager();

        getServer().getScheduler().runTaskLater(this, () -> mapManager.loadAllMaps(), 20L);

        // Register Listeners
        getServer().getPluginManager().registerEvents(new ShulkerNavigator(this), this);
        getServer().getPluginManager().registerEvents(new SecurityListener(this), this);

        // Register Command
        if (getCommand("mcfs") != null) {
            getCommand("mcfs").setExecutor(new McfsCommand(this));
        }


        webServer = new WebServer(this);
        webServer.start();

        getLogger().info("StoreiiMC started. Cloud Storage Active.");
    }

    @Override
    public void onDisable() {
        if (webServer != null) webServer.stop();
    }

    public MapManager getMapManager() { return mapManager; }
    public DriveManager getDriveManager() { return driveManager; }
    public FileOperations getFileOps() { return fileOps; }

    // 3. Add this Getter Method
    public ShareManager getShareManager() { return shareManager; }
}