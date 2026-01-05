package net.xyressa.storeiiMC.filesystem;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.xyressa.storeiiMC.StoreiiMC;
import net.xyressa.storeiiMC.model.Drive;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class MinecraftFS {

    private final StoreiiMC plugin;
    private final NamespacedKey mapIdKey;
    private final NamespacedKey chunkIndexKey;

    private static final String ICON_FOLDER = "<svg style='width:24px;height:24px;vertical-align:middle;fill:#ffcb6b;margin-right:10px;' viewBox='0 0 24 24'><path d='M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'/></svg>";
    private static final String ICON_FILE = "<svg style='width:24px;height:24px;vertical-align:middle;fill:#82aaff;margin-right:10px;' viewBox='0 0 24 24'><path d='M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z'/></svg>";
    private static final String ICON_AUDIO = "<svg style='width:24px;height:24px;vertical-align:middle;fill:#c792ea;margin-right:10px;' viewBox='0 0 24 24'><path d='M12 3v9.28c-.47-.17-.97-.28-1.5-.28C8.01 12 6 14.01 6 16.5S8.01 21 10.5 21c2.31 0 4.2-1.75 4.45-4H15V6h4V3h-7z'/></svg>";

    public MinecraftFS(StoreiiMC plugin) {
        this.plugin = plugin;
        this.mapIdKey = new NamespacedKey(plugin, "map_link_id");
        this.chunkIndexKey = new NamespacedKey(plugin, "chunk_index");
    }

    public static class FilePointer {
        public boolean found = false;
        public boolean isBinary = false;
        public byte[] textData = null;
        public List<Integer> mapIds = new ArrayList<>();
    }

    private static class ChunkInfo {
        int id; int index;
        public ChunkInfo(int id, int index) { this.id = id; this.index = index; }
    }

    public FilePointer scanForFile(Drive drive, String targetFilename) {
        FilePointer result = new FilePointer();

        if (targetFilename.isEmpty()) {
            for (org.bukkit.block.Container chest : drive.getCluster().getContainers()) {
                for (ItemStack item : chest.getInventory().getContents()) {
                    if (item != null && "index.html".equals(getItemName(item))) {
                        result.found = true;
                        if (item.getType().name().contains("BOOK")) {
                            result.isBinary = false;
                            result.textData = readBookContent(item);
                        } else {
                            result.isBinary = true;
                            extractBinaryData(item, result);
                        }
                        return result;
                    }
                }
            }
            result.found = true;
            result.isBinary = false;
            result.textData = generateDirectoryUI(drive, "").getBytes(StandardCharsets.UTF_8);
            return result;
        }

        for (org.bukkit.block.Container chest : drive.getCluster().getContainers()) {
            for (ItemStack item : chest.getInventory().getContents()) {
                if (item == null || item.getType() == Material.AIR) continue;
                String itemName = getItemName(item);
                if (itemName == null) continue;

                if (itemName.equals(targetFilename)) {
                    result.found = true;
                    if (item.getType().name().contains("BOOK")) {
                        result.isBinary = false;
                        result.textData = readBookContent(item);
                    } else {
                        result.isBinary = true;
                        extractBinaryData(item, result);
                    }
                    return result;
                }
            }
        }
        return result;
    }

    private void extractBinaryData(ItemStack item, FilePointer result) {
        List<ChunkInfo> chunks = new ArrayList<>();
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(mapIdKey, PersistentDataType.INTEGER)) {
            int id = item.getItemMeta().getPersistentDataContainer().get(mapIdKey, PersistentDataType.INTEGER);
            int index = item.getItemMeta().getPersistentDataContainer().getOrDefault(chunkIndexKey, PersistentDataType.INTEGER, 0);
            chunks.add(new ChunkInfo(id, index));
        } else if (item.getType().name().contains("SHULKER_BOX")) {
            collectChunksRecursive(item, chunks);
        }
        chunks.sort(Comparator.comparingInt(c -> c.index));
        for (ChunkInfo c : chunks) result.mapIds.add(c.id);
    }

    private void collectChunksRecursive(ItemStack containerItem, List<ChunkInfo> chunks) {
        if (!(containerItem.getItemMeta() instanceof BlockStateMeta bsm)) return;
        if (!(bsm.getBlockState() instanceof ShulkerBox box)) return;
        for (ItemStack item : box.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta.getPersistentDataContainer().has(mapIdKey, PersistentDataType.INTEGER)) {
                int id = meta.getPersistentDataContainer().get(mapIdKey, PersistentDataType.INTEGER);
                int index = meta.getPersistentDataContainer().getOrDefault(chunkIndexKey, PersistentDataType.INTEGER, 0);
                if (!meta.getPersistentDataContainer().has(chunkIndexKey, PersistentDataType.INTEGER)) index = chunks.size();
                chunks.add(new ChunkInfo(id, index));
            } else if (item.getType().name().contains("SHULKER_BOX")) collectChunksRecursive(item, chunks);
        }
    }

    public static String getItemName(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName()) return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        if (meta instanceof BookMeta bookMeta && bookMeta.hasTitle()) return PlainTextComponentSerializer.plainText().serialize(bookMeta.title());
        return null;
    }

    private byte[] readBookContent(ItemStack book) {
        if (!(book.getItemMeta() instanceof BookMeta meta)) return new byte[0];
        StringBuilder content = new StringBuilder();
        for (var page : meta.pages()) content.append(PlainTextComponentSerializer.plainText().serialize(page));
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }


    public String generateDirectoryUI(Drive drive, String currentFolder) {
        if (currentFolder == null) currentFolder = "";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><title>").append(drive.getId()).append("</title>");
        html.append("<style>");
        html.append("body { font-family: 'Segoe UI', sans-serif; background: #121212; color: #e0e0e0; padding: 20px; }");
        html.append(".navbar { display: flex; align-items: center; border-bottom: 1px solid #333; padding-bottom: 10px; margin-bottom: 20px; }");
        html.append(".navbar input { background: #1e1e1e; border: 1px solid #444; padding: 8px; color: white; border-radius: 4px; margin-left: auto; width: 250px; }");
        html.append(".file-list { background: #1e1e1e; border: 1px solid #333; border-radius: 8px; overflow: hidden; margin-bottom: 20px; }");
        html.append(".file-item { padding: 12px 20px; border-bottom: 1px solid #333; display: flex; align-items: center; transition: background 0.2s; position: relative; }");
        html.append(".file-item:hover { background: #252525; }");
        html.append(".file-name { color: #82aaff; text-decoration: none; font-size: 1.05em; flex-grow: 1; margin-left: 10px; cursor:pointer; }");
        html.append(".preview-box { width: 48px; height: 48px; display: flex; justify-content: center; align-items: center; background: #121212; border-radius: 4px; border: 1px solid #333; overflow: hidden; }");
        html.append(".preview-box img { width: 100%; height: 100%; object-fit: cover; }");

        // Buttons
        html.append(".action-bar { display: flex; gap: 10px; margin-top: 20px; }");
        html.append(".btn { padding: 10px 20px; background: #333; color: white; border: none; border-radius: 4px; cursor: pointer; font-weight: bold; text-decoration:none; display:inline-block; font-size:14px; }");
        html.append(".btn:hover { background: #444; }");
        html.append(".btn-primary { background: #007acc; } .btn-primary:hover { background: #0063a5; }");
        html.append(".btn-success { background: #28a745; } .btn-success:hover { background: #218838; }");

        // Upload Zone
        html.append(".upload-zone { border: 2px dashed #444; padding: 30px; text-align: center; color: #888; border-radius: 8px; margin-top: 20px; transition: border-color 0.2s; }");
        html.append(".upload-zone.dragover { border-color: #007acc; color: #fff; background: rgba(0,122,204,0.1); }");

        // Context Menu
        html.append(".context-menu { position: absolute; background: #333; border: 1px solid #555; border-radius: 4px; display: none; z-index: 100; box-shadow: 0 4px 8px rgba(0,0,0,0.5); }");
        html.append(".context-btn { display: block; padding: 8px 15px; color: white; cursor: pointer; border: none; background: none; width: 100%; text-align: left; }");
        html.append(".context-btn:hover { background: #444; }");

        // Main thing
        html.append(".modal { display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.8); z-index:200; justify-content:center; align-items:center; }");
        html.append(".modal-content { background:#1e1e1e; padding:20px; border-radius:8px; width:80%; height:85%; display:flex; flex-direction:column; position:relative; }");
        html.append(".modal-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:10px; }");
        html.append("textarea { flex-grow:1; background:#121212; color:#cfcfcf; border:1px solid #444; font-family:monospace; padding:10px; resize:none; }");
        html.append("</style></head><body>");

        // Header
        html.append("<div class='navbar'><h1>").append(drive.getId().toUpperCase());
        if (!currentFolder.isEmpty()) html.append("/").append(currentFolder);
        html.append("</h1>");
        html.append("<input type='text' id='search' placeholder='Search files...' onkeyup='filterFiles()'></div>");

        // List
        html.append("<div class='file-list' id='list'>");

        if (!currentFolder.isEmpty()) {
            String parent = currentFolder.contains("/") ? currentFolder.substring(0, currentFolder.lastIndexOf('/')) : "";
            html.append("<div class='file-item'><a href='?path=").append(parent).append("' class='file-name' style='color:#ffcb6b'>.. (Parent)</a></div>");
        }

        Set<String> processedFolders = new HashSet<>();

        for (org.bukkit.block.Container chest : drive.getCluster().getContainers()) {
            for (ItemStack item : chest.getInventory().getContents()) {
                if (item == null || item.getType() == Material.AIR) continue;
                String name = getItemName(item);
                if (name == null) continue;

                if (!currentFolder.isEmpty()) {
                    if (!name.startsWith(currentFolder + "/")) continue;
                    name = name.substring(currentFolder.length() + 1);
                }

                if (name.contains("/")) {
                    String folderName = name.substring(0, name.indexOf('/'));
                    if (processedFolders.contains(folderName)) continue;
                    processedFolders.add(folderName);

                    String fullPath = currentFolder.isEmpty() ? folderName : currentFolder + "/" + folderName;
                    html.append("<div class='file-item' data-name='").append(folderName).append("'>");
                    html.append(ICON_FOLDER);
                    html.append("<a href='?path=").append(fullPath).append("' class='file-name' style='color:#ffcb6b'>").append(folderName).append("</a>");
                    html.append("</div>");
                } else {
                    String link = "/" + drive.getId() + "/" + (currentFolder.isEmpty() ? name : currentFolder + "/" + name);
                    String lower = name.toLowerCase();
                    String preview;
                    boolean isEditable = false;

                    if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                        preview = "<div class='preview-box'><img src='" + link + "'></div>";
                    } else if (lower.endsWith(".mp3") || lower.endsWith(".wav")) {
                        preview = ICON_AUDIO + "<audio controls src='" + link + "' style='height:24px; width:150px;'></audio>";
                    } else if (lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".yml") || lower.endsWith(".js") || lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".java") || lower.endsWith(".py")) {
                        preview = ICON_FILE;
                        isEditable = true;
                    } else {
                        preview = ICON_FILE;
                    }

                    String clickAction;
                    if (isEditable) {
                        clickAction = "onclick=\"openEditor('" + link + "'); return false;\"";
                    } else {
                        clickAction = "href='" + link + "'";
                    }

                    html.append("<div class='file-item' data-name='").append(name).append("' oncontextmenu='showContext(event, \"").append(link).append("\", ").append(isEditable).append(")'>");
                    html.append(preview);
                    html.append("<a ").append(clickAction).append(" class='file-name'>").append(name).append("</a>");
                    html.append("</div>");
                }
            }
        }
        html.append("</div>");

        // Action Buttons
        html.append("<div class='action-bar'>");
        html.append("<button class='btn btn-primary' onclick=\"document.getElementById('fileInput').click()\">Upload File</button>");
        html.append("<button class='btn' onclick='createNewNote()'>+ New Note</button>");
        html.append("<input type='file' id='fileInput' style='display:none' onchange='upload(this.files[0])'>");
        html.append("</div>");

        // Drop Zone (For the file upload)
        html.append("<div class='upload-zone' id='dropZone'>");
        html.append("<p id='statusText'>Drag & Drop files here or use the Upload Button</p>");
        html.append("</div>");

        // Context Menu
        html.append("<div id='ctxMenu' class='context-menu'>");
        html.append("<button class='context-btn' onclick='copyLink()'>Copy Link</button>");
        html.append("<button class='context-btn' id='btnEdit' onclick='openEditor()'>Edit File</button>");
        html.append("</div>");

        // Editor
        html.append("<div id='editor' class='modal'><div class='modal-content'>");
        html.append("<div class='modal-header'><h3>Editor</h3>");
        html.append("<a id='dlBtn' href='#' download class='btn btn-success' style='padding:5px 10px; font-size:12px;'>Download File</a>");
        html.append("</div>");
        html.append("<textarea id='editContent'></textarea>");
        html.append("<div style='margin-top:10px; display:flex; gap:10px;'><button class='btn btn-primary' onclick='saveFile()'>Save Changes</button>");
        html.append("<button class='btn' onclick='closeEditor()'>Close</button></div>");
        html.append("</div></div>");

        // Scripts
        html.append("<script>");
        html.append("let currentLink = '';");
        html.append("let currentFolder = '").append(currentFolder).append("';");

        html.append("function filterFiles() {");
        html.append("  let q = document.getElementById('search').value.toLowerCase();");
        html.append("  document.querySelectorAll('.file-item').forEach(el => {");
        html.append("    el.style.display = el.getAttribute('data-name').toLowerCase().includes(q) ? 'flex' : 'none';");
        html.append("  });");
        html.append("}");

        html.append("function showContext(e, link, editable) {");
        html.append("  e.preventDefault(); currentLink = link;");
        html.append("  let menu = document.getElementById('ctxMenu');");
        html.append("  menu.style.top = e.pageY + 'px'; menu.style.left = e.pageX + 'px'; menu.style.display = 'block';");
        html.append("  document.getElementById('btnEdit').style.display = editable ? 'block' : 'none';");
        html.append("}");
        html.append("document.addEventListener('click', () => document.getElementById('ctxMenu').style.display='none');");

        html.append("function copyLink() { navigator.clipboard.writeText(location.origin + currentLink); }");

        // Editor Logic
        html.append("function openEditor(link) {");
        html.append("  if(!link) link = currentLink;");
        html.append("  currentLink = link;");
        html.append("  document.getElementById('editor').style.display='flex';");
        html.append("  document.getElementById('dlBtn').href = link;");
        html.append("  document.getElementById('editContent').value = 'Loading...';");
        html.append("  fetch(link).then(r=>r.text()).then(t => document.getElementById('editContent').value = t);");
        html.append("}");

        html.append("function closeEditor() { document.getElementById('editor').style.display='none'; }");

        html.append("function saveFile() {");
        html.append("  let content = document.getElementById('editContent').value;");
        html.append("  let filename = decodeURIComponent(currentLink.split('/').pop());");
        html.append("  if(currentFolder) filename = currentFolder + '/' + filename;");
        html.append("  fetch('/edit', { method:'POST', body: 'drive=").append(drive.getId()).append("&filename='+filename+'&content='+encodeURIComponent(content) }).then(()=>location.reload());");
        html.append("}");

        html.append("function createNewNote() {");
        html.append("  let name = prompt('Enter filename (e.g. notes.txt):', 'notes.txt');");
        html.append("  if(name) {");
        html.append("    let blob = new Blob([''], {type: 'text/plain'});");
        html.append("    if(currentFolder) name = currentFolder + '/' + name;");
        html.append("    uploadFile(blob, name);");
        html.append("  }");
        html.append("}");

        html.append("function upload(f) {");
        html.append("  let name = f.name;");
        html.append("  if(currentFolder) name = currentFolder + '/' + name;");
        html.append("  uploadFile(f, name);");
        html.append("}");

        html.append("function uploadFile(f, name) {");
        html.append("  const status = document.getElementById('statusText');");
        html.append("  status.innerText = 'Uploading ' + name + '...';");
        html.append("  fetch('/upload',{ method:'POST', headers:{'X-Filename':encodeURIComponent(name), 'X-Drive-ID':'").append(drive.getId()).append("'}, body:f })");
        html.append("  .then(async r => {");
        html.append("     if(r.ok) { location.reload(); }");
        html.append("     else { let txt = await r.text(); alert('Upload Failed: ' + txt); status.innerText = 'Upload Failed'; }");
        html.append("  })");
        html.append("  .catch(e => { alert('Network Connection Error: ' + e); status.innerText = 'Network Error'; console.error(e); });");
        html.append("}");

        html.append("const dz = document.getElementById('dropZone');");
        html.append("dz.addEventListener('dragover', (e) => { e.preventDefault(); dz.classList.add('dragover'); });");
        html.append("dz.addEventListener('dragleave', (e) => { e.preventDefault(); dz.classList.remove('dragover'); });");
        html.append("dz.addEventListener('drop', (e) => { e.preventDefault(); dz.classList.remove('dragover'); upload(e.dataTransfer.files[0]); });");

        html.append("</script></body></html>");
        return html.toString();
    }
}