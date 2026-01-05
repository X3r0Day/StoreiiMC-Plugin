package net.xyressa.storeiiMC.managers;

import net.xyressa.storeiiMC.model.Drive;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShareManager {

    private final Map<String, ShareInfo> shares = new ConcurrentHashMap<>();

    public record ShareInfo(Drive drive, String filename) {}

    public String createShareLink(Drive drive, String filename) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        shares.put(token, new ShareInfo(drive, filename));
        return token;
    }

    public ShareInfo getShare(String token) {
        return shares.get(token);
    }
}