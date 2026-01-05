package net.xyressa.storeiiMC.model;

import net.xyressa.storeiiMC.StoreiiMC;
import net.xyressa.storeiiMC.managers.DriveManager;
import net.xyressa.storeiiMC.managers.StorageCluster;
import org.bukkit.Location;

import java.util.UUID;

public class Drive {
    private final String id;
    private final UUID owner;
    private final Location rootLocation;
    private String password; // This now stores the HASH
    private final StorageCluster cluster;

    public Drive(StoreiiMC plugin, String id, UUID owner, Location rootLocation) {
        this.id = id;
        this.owner = owner;
        this.rootLocation = rootLocation;
        this.cluster = new StorageCluster(plugin, rootLocation);
    }

    public String getId() { return id; }
    public UUID getOwner() { return owner; }
    public Location getRootLocation() { return rootLocation; }
    public StorageCluster getCluster() { return cluster; }

    public boolean isPrivate() {
        return password != null && !password.isEmpty();
    }


    public boolean checkPassword(String input) {
        if (password == null) return true;
        return DriveManager.verifyPassword(input, password);
    }

    public void setPassword(String passwordHash) {
        this.password = passwordHash;
    }
}