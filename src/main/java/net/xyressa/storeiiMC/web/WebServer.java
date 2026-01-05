package net.xyressa.storeiiMC.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.xyressa.storeiiMC.StoreiiMC;
import net.xyressa.storeiiMC.filesystem.MapWriter;
import net.xyressa.storeiiMC.filesystem.MinecraftFS;
import net.xyressa.storeiiMC.managers.ShareManager;
import net.xyressa.storeiiMC.model.Drive;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class WebServer {

    private final StoreiiMC plugin;
    private HttpServer server;
    private final File tempFolder;

    private final Map<String, String> authenticatedSessions = new HashMap<>();
    public static final Map<String, PendingImport> pendingImports = new ConcurrentHashMap<>();

    public record PendingImport(Player player, Drive drive, String filename) {}

    private record WebResponse(byte[] data, String mimeType, int status) {}

    public WebServer(StoreiiMC plugin) {
        this.plugin = plugin;
        this.tempFolder = new File(plugin.getDataFolder(), "temp");
        if (!tempFolder.exists()) tempFolder.mkdirs();
    }

    public void start() {
        new Thread(() -> {
            try {
                server = HttpServer.create(new InetSocketAddress(8080), 0);
                server.createContext("/", new MainHandler());
                server.createContext("/upload", new UploadHandler());
                server.createContext("/login", new LoginHandler());
                server.createContext("/paste", new PasteHandler());
                server.createContext("/share", new ShareHandler());
                server.createContext("/edit", new EditHandler());
                server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
                server.start();
            } catch (IOException e) {
                plugin.getLogger().severe("Web Start Error: " + e.getMessage());
            }
        }).start();
    }

    public void stop() { if (server != null) server.stop(0); }


    class MainHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(t.getRequestMethod())) { send405(t); return; }

                String path = t.getRequestURI().getPath();
                if (path.equals("/") || path.equals("/index.html")) {
                    serveDriveList(t);
                    return;
                }

                String[] parts = path.split("/", 3);
                if (parts.length < 2) { send404(t); return; }

                String driveId = parts[1];

                String rawName = (parts.length > 2) ? parts[2] : "";
                String decodedName = "";
                try { decodedName = URLDecoder.decode(rawName, StandardCharsets.UTF_8); }
                catch (Exception e) { decodedName = rawName; }

                final String filename = decodedName;

                if (filename.isEmpty() && !path.endsWith("/")) {
                    t.getResponseHeaders().add("Location", path + "/");
                    t.sendResponseHeaders(302, -1);
                    return;
                }

                String tempFolder = "";
                if (t.getRequestURI().getQuery() != null) {
                    Map<String, String> query = parseQuery(t.getRequestURI().getQuery());
                    if (query.containsKey("path")) tempFolder = query.get("path");
                }
                final String folderPath = tempFolder;

                Drive drive = plugin.getDriveManager().getDrive(driveId);
                if (drive == null) { send404(t); return; }

                if (drive.isPrivate() && !checkAuth(t, driveId)) {
                    serveLoginPage(t, driveId);
                    return;
                }

                CompletableFuture<WebResponse> future = new CompletableFuture<>();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        MinecraftFS fs = new MinecraftFS(plugin);
                        MinecraftFS.FilePointer ptr;

                        if (filename.isEmpty() && folderPath.isEmpty()) {
                            ptr = fs.scanForFile(drive, "index.html");
                            if (!ptr.found) {
                                // No index.html -> Show Dashboard
                                String html = fs.generateDirectoryUI(drive, "");
                                future.complete(new WebResponse(html.getBytes(StandardCharsets.UTF_8), "text/html; charset=UTF-8", 200));
                                return;
                            }
                        }
                        else {
                            if (filename.isEmpty()) {
                                // Subfolder dashboard
                                String html = fs.generateDirectoryUI(drive, folderPath);
                                future.complete(new WebResponse(html.getBytes(StandardCharsets.UTF_8), "text/html; charset=UTF-8", 200));
                                return;
                            }
                            ptr = fs.scanForFile(drive, filename);
                        }


                        if (!ptr.found) {
                            future.complete(new WebResponse("404 Not Found".getBytes(), "text/plain", 404));
                        } else {
                            if (ptr.isBinary) {
                                CompletableFuture.runAsync(() -> {
                                    byte[] data = plugin.getMapManager().readAndStitch(ptr.mapIds);
                                    String mime = determineMime(filename.isEmpty() ? "index.html" : filename);
                                    future.complete(new WebResponse(data, mime, 200));
                                });
                            } else {
                                String mime = determineMime(filename.isEmpty() ? "index.html" : filename);
                                future.complete(new WebResponse(ptr.textData, mime, 200));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        future.complete(new WebResponse(("Server Error: " + e.getMessage()).getBytes(), "text/plain", 500));
                    }
                });

                WebResponse resp = future.get();
                if (resp.status == 200) sendData(t, resp.data, resp.mimeType);
                else sendResponse(t, resp.status, new String(resp.data), resp.mimeType);

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(t, 500, "Internal Error", "text/plain");
            }
        }
    }



    class ShareHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                String path = t.getRequestURI().getPath();
                String token = path.substring(path.lastIndexOf('/') + 1);
                ShareManager.ShareInfo info = plugin.getShareManager().getShare(token);
                if (info == null) { send404(t); return; }

                CompletableFuture<WebResponse> future = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MinecraftFS.FilePointer ptr = new MinecraftFS(plugin).scanForFile(info.drive(), info.filename());
                    if(!ptr.found) future.complete(new WebResponse("Gone".getBytes(), "text/plain", 404));
                    else if(ptr.isBinary) {
                        CompletableFuture.runAsync(() -> {
                            byte[] data = plugin.getMapManager().readAndStitch(ptr.mapIds);
                            future.complete(new WebResponse(data, determineMime(info.filename()), 200));
                        });
                    } else future.complete(new WebResponse(ptr.textData, determineMime(info.filename()), 200));
                });

                WebResponse resp = future.get();
                if(resp.status == 200) sendData(t, resp.data, resp.mimeType);
                else send404(t);

            } catch (Exception e) { sendResponse(t, 500, "Error", "text/plain"); }
        }
    }

    class EditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(t.getRequestMethod())) { send405(t); return; }
                InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String body = br.readLine();
                Map<String, String> params = parseQuery(body);

                String driveId = params.get("drive");
                String filename = params.get("filename");
                String content = params.get("content");

                if (driveId == null || filename == null || content == null) {
                    sendResponse(t, 400, "Missing parameters", "text/plain"); return;
                }
                if (!checkAuth(t, driveId)) { sendResponse(t, 401, "Unauthorized", "text/plain"); return; }

                Drive drive = plugin.getDriveManager().getDrive(driveId);
                if (drive == null) { send404(t); return; }

                plugin.getFileOps().deleteFileSilently(drive, filename);

                CompletableFuture.runAsync(() -> {
                    try {
                        File temp = new File(tempFolder, "edit_" + System.currentTimeMillis() + ".txt");
                        try (FileOutputStream fos = new FileOutputStream(temp)) {
                            fos.write(content.getBytes(StandardCharsets.UTF_8));
                        }
                        new MapWriter(plugin).importFromWeb(driveId, temp, filename);
                    } catch (Exception e) { e.printStackTrace(); }
                });
                sendResponse(t, 200, "Saved", "text/plain");
            } catch (Exception e) { sendResponse(t, 500, "Error", "text/plain"); }
        }
    }

    class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(t.getRequestMethod())) { send405(t); return; }

                String rawName = t.getRequestHeaders().getFirst("X-Filename");
                String driveId = t.getRequestHeaders().getFirst("X-Drive-ID");

                String decoded = "uploaded.bin";
                if(rawName != null) {
                    try { decoded = URLDecoder.decode(rawName, StandardCharsets.UTF_8); }
                    catch(Exception e) { decoded = rawName; }
                }

                decoded = decoded.replaceAll("\\.\\.", "");
                decoded = decoded.replaceAll("[^a-zA-Z0-9._\\-/ ]", "_");
                final String filename = decoded; // Effectively Final

                if (driveId == null) { sendResponse(t, 400, "Missing Drive ID", "text/plain"); return; }

                long limitBytes = plugin.getConfig().getLong("limits.max-file-size-mb", 50) * 1024 * 1024;
                String lenStr = t.getRequestHeaders().getFirst("Content-Length");
                if (lenStr != null) {
                    try { if (Long.parseLong(lenStr) > limitBytes) { sendResponse(t, 413, "Too Large", "text/plain"); return; } } catch(Exception e){}
                }

                File tempFile = new File(tempFolder, "up_" + System.currentTimeMillis() + ".tmp");
                try (InputStream is = t.getRequestBody(); FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int n; long total = 0;
                    while ((n = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, n);
                        total += n;
                        if (total > limitBytes) {
                            fos.close(); if(tempFile.exists()) tempFile.delete();
                            sendResponse(t, 413, "Too Large", "text/plain"); return;
                        }
                    }
                }
                new MapWriter(plugin).importFromWeb(driveId, tempFile, filename);
                sendResponse(t, 200, "Upload received!", "text/plain");
            } catch (Exception e) { sendResponse(t, 500, "Error", "text/plain"); }
        }
    }

    class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                    InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    String query = br.readLine();
                    Map<String, String> params = parseQuery(query);
                    String driveId = params.get("drive");
                    String password = params.get("password");
                    Drive drive = plugin.getDriveManager().getDrive(driveId);
                    if (drive != null && drive.checkPassword(password)) {
                        String token = UUID.randomUUID().toString();
                        authenticatedSessions.put(token, driveId);
                        t.getResponseHeaders().add("Set-Cookie", "mcfs_auth=" + token + "; Path=/");
                        t.getResponseHeaders().add("Location", "/" + driveId + "/");
                        t.sendResponseHeaders(302, -1);
                    } else { sendResponse(t, 401, "Invalid Password", "text/plain"); }
                }
            } catch (Exception e) { sendResponse(t, 500, "Error", "text/plain"); }
        }
    }

    class PasteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                String path = t.getRequestURI().getPath();
                String code = path.substring(path.lastIndexOf('/') + 1);
                if (!pendingImports.containsKey(code)) { sendResponse(t, 404, "Invalid Code", "text/plain"); return; }
                if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                    PendingImport info = pendingImports.get(code);
                    String html = "<html><body><h2>Import " + info.filename() + "</h2><form method='POST'><input name='url' placeholder='URL'><button>Go</button></form></body></html>";
                    sendResponse(t, 200, html, "text/html");
                } else if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                    InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    String body = br.readLine();
                    Map<String, String> params = parseQuery(body);
                    if (params.get("url") != null) {
                        PendingImport info = pendingImports.remove(code);
                        new MapWriter(plugin).importFromUrl(info.drive(), params.get("url"), info.filename(), info.player());
                        sendResponse(t, 200, "Started", "text/plain");
                    }
                }
            } catch (Exception e) { sendResponse(t, 500, "Error", "text/plain"); }
        }
    }


    private void serveDriveList(HttpExchange t) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><title>MCFS Drives</title>");
        html.append("<style>body{font-family:sans-serif;background:#121212;color:#eee;padding:40px}a{color:#4af;text-decoration:none}div{margin:10px;padding:10px;background:#333;border-radius:5px}</style></head><body>");
        html.append("<h1>MCFS Storage</h1>");
        for (Drive d : plugin.getDriveManager().getAllDrives().values()) {
            String lock = d.isPrivate() ? "" : "";
            html.append("<div>").append(lock).append(" <a href='/").append(d.getId()).append("/'>").append(d.getId().toUpperCase()).append("</a></div>");
        }
        html.append("</body></html>");
        sendResponse(t, 200, html.toString(), "text/html; charset=UTF-8");
    }

    private void serveLoginPage(HttpExchange t, String driveId) throws IOException {
        String html = "<html><head><meta charset='UTF-8'></head><body style='background:#222;color:#fff;text-align:center;margin-top:50px'>" +
                "<h2>Locked Drive: " + driveId + "</h2>" +
                "<form action='/login' method='POST'>" +
                "<input type='hidden' name='drive' value='" + driveId + "'>" +
                "<input type='password' name='password' placeholder='Password'>" +
                "<button type='submit'>Access</button>" +
                "</form></body></html>";
        sendResponse(t, 200, html, "text/html; charset=UTF-8");
    }

    private boolean checkAuth(HttpExchange t, String driveId) {
        if (t.getRequestHeaders().containsKey("Cookie")) {
            String cookieLine = t.getRequestHeaders().getFirst("Cookie");
            for (String c : cookieLine.split(";")) {
                String[] parts = c.trim().split("=");
                if (parts.length == 2 && parts[0].trim().equals("mcfs_auth")) {
                    String token = parts[1];
                    return driveId.equals(authenticatedSessions.get(token));
                }
            }
        }
        return false;
    }

    private void send404(HttpExchange t) throws IOException { sendResponse(t, 404, "404 Not Found", "text/plain"); }
    private void send405(HttpExchange t) throws IOException { t.sendResponseHeaders(405, -1); }

    private void sendResponse(HttpExchange t, int code, String msg, String mime) throws IOException {
        t.getResponseHeaders().set("Content-Type", mime);
        t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        t.sendResponseHeaders(code, data.length);
        try (OutputStream os = t.getResponseBody()) { os.write(data); }
    }

    private void sendData(HttpExchange t, byte[] data, String mime) throws IOException {
        t.getResponseHeaders().set("Content-Type", mime);
        t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        t.getResponseHeaders().set("Content-Security-Policy", "default-src 'self' 'unsafe-inline' 'unsafe-eval'; img-src 'self' data:; media-src 'self';");
        t.sendResponseHeaders(200, data.length);
        try (OutputStream os = t.getResponseBody()) { os.write(data); }
        catch (IOException ignored) {}
    }

    private String determineMime(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".xml")) return "text/xml";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "text/plain; charset=UTF-8";
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> res = new HashMap<>();
        if (query == null) return res;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                String value = entry[1];
                if(entry.length > 2) value = param.substring(param.indexOf('=') + 1);
                try { res.put(entry[0], URLDecoder.decode(value, StandardCharsets.UTF_8)); } catch(Exception e){}
            }
        }
        return res;
    }
}