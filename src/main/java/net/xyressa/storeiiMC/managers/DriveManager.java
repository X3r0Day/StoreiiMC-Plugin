package net.xyressa.storeiiMC.managers;

import net.xyressa.storeiiMC.StoreiiMC;
import net.xyressa.storeiiMC.model.Drive;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DriveManager {

    private final StoreiiMC plugin;
    private final Map<String, Drive> drives = new HashMap<>();
    private final File file;
    private FileConfiguration config;

    public DriveManager(StoreiiMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "drives.yml");
        loadDrives();
    }

    public void createDrive(String id, UUID owner, Location root) {
        Drive drive = new Drive(plugin, id, owner, root);
        drives.put(id.toLowerCase(), drive);
        saveDrive(drive);
    }

    public void deleteDrive(String id) {
        if (!drives.containsKey(id.toLowerCase())) return;

        drives.remove(id.toLowerCase());

        // Remove from config file completely
        config = YamlConfiguration.loadConfiguration(file);
        config.set("drives." + id.toLowerCase(), null);
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public Drive getDrive(String id) {
        if (id == null) return null;
        return drives.get(id.toLowerCase());
    }

    public Map<String, Drive> getAllDrives() {
        return drives;
    }

    public Drive getDriveByLocation(Location loc) {
        for (Drive drive : drives.values()) {
            for (org.bukkit.block.Container c : drive.getCluster().getContainers()) {
                if (c.getLocation().equals(loc)) return drive;
            }
        }
        return null;
    }


    public int getPlayerDriveCount(UUID owner) {
        int count = 0;
        for (Drive d : drives.values()) {
            if (d.getOwner().equals(owner)) count++;
        }
        return count;
    }

    public void setPassword(String id, String rawPassword) {
        Drive d = getDrive(id);
        if (d != null) {
            String hashedPassword = hashPassword(rawPassword);
            d.setPassword(hashedPassword);
            savePasswordToConfig(id, hashedPassword);
        }
    }

    public void savePasswordToConfig(String id, String hashedPassword) {
        config = YamlConfiguration.loadConfiguration(file);
        config.set("drives." + id.toLowerCase() + ".password", hashedPassword);
        try { config.save(file); } catch (IOException e) {}
    }

    private void saveDrive(Drive drive) {
        config = YamlConfiguration.loadConfiguration(file);
        String path = "drives." + drive.getId().toLowerCase();
        config.set(path + ".owner", drive.getOwner().toString());
        config.set(path + ".world", drive.getRootLocation().getWorld().getName());
        config.set(path + ".x", drive.getRootLocation().getBlockX());
        config.set(path + ".y", drive.getRootLocation().getBlockY());
        config.set(path + ".z", drive.getRootLocation().getBlockZ());
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadDrives() {
        if (!file.exists()) return;
        config = YamlConfiguration.loadConfiguration(file);
        if (config.getConfigurationSection("drives") == null) return;

        for (String id : config.getConfigurationSection("drives").getKeys(false)) {
            String path = "drives." + id;
            try {
                UUID owner = UUID.fromString(config.getString(path + ".owner"));
                String w = config.getString(path + ".world");
                int x = config.getInt(path + ".x");
                int y = config.getInt(path + ".y");
                int z = config.getInt(path + ".z");
                String pass = config.getString(path + ".password", null);

                if (Bukkit.getWorld(w) != null) {
                    Location loc = new Location(Bukkit.getWorld(w), x, y, z);
                    Drive drive = new Drive(plugin, id, owner, loc);
                    if (pass != null) drive.setPassword(pass);
                    drives.put(id.toLowerCase(), drive);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load drive: " + id);
            }
        }
    }


    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final String SALT_DIVIDER = ":";

    public static String hashPassword(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();

            return Base64.getEncoder().encodeToString(salt) + SALT_DIVIDER + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null) return false;

        if (!storedHash.contains(SALT_DIVIDER)) {
            return storedHash.equals(rawPassword);
        }

        try {
            String[] parts = storedHash.split(SALT_DIVIDER);
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] hash = Base64.getDecoder().decode(parts[1]);

            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] testHash = skf.generateSecret(spec).getEncoded();

            int diff = hash.length ^ testHash.length;
            for (int i = 0; i < hash.length && i < testHash.length; i++) {
                diff |= hash[i] ^ testHash[i];
            }
            return diff == 0;
        } catch (Exception e) {
            return false;
        }
    }
}