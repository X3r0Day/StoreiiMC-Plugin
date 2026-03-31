***

# StoreiiMC

**Cloud Storage... inside a Minecraft Chest. Yeah, really.**

### StoreiiMC is a Plugin for PaperMC :3

StoreiiMC turns specific chests in your world into secure, web-accessible "Drives." You can drag-and-drop files from your browser into a chest, edit text files live, and stream music, all stored entirely within Minecraft's NBT data (Maps inside Shulkers).

Or you can also import your files directly via URLs.

It’s basically Google Drive, but the backend is a Shulker Box.


## What is this?

Built because curiosity wouldn’t let me stop.

**The Logic:**
1.  Files are chopped into 16KB binary chunks.
2.  Chunks are written to the NBT data of Filled Maps.
3.  Maps are packed into Shulker Boxes.
4.  Shulker Boxes are stored in your physical chest.
5.  A built-in Jetty Web Server (port 8080) serves a full UI to manage it all.


## Features

*   **Web Dashboard:** A full dark-mode UI with Drag & Drop uploads.
*   **Live Text Editor:** Click any `.txt`, `.json`, or `.yml` file in the browser to edit and save it instantly. Great for keeping notes.
*   **Media Preview:** View images and play `.mp3` files directly from the dashboard.
*   **Multi-User System:** Players can create their own private Drives.
*   **Security:** Well there could be many security vulnerablities, as a security researcher I basically sanitized and have checks on almost every entry points! which includes but not limit to:
    *   Drives can be password-protected (hashed with PBKDF2). (You can create public drives too without a password)
    *   Protection against XSS (Cross-Site Scripting) and SSRF.
    *   DoS protection (configurable per-file size limits).

*   **Import from URL:** Use /mcfs import <URL> <filename.extension> to directly import if link is short!
*   **Magic Links:** Use `/mcfs webimport <filename.extension>` to generate a one-time link for uploading from long URLs (cuz minecraft chat have 256 char limit).

***

## Quick Start

1.  **Setup:** Download plugin from here [storeiimc-plugin](https://modrinth.com/plugin/stoeriimc-plugin) into your `/plugins` folder.
2.  **Run:** Start the server. The web server starts on port `8080` by default.
3.  **Create a Drive:**
    *   Place a Chest.
    *   Look at it.
    *   Run `/mcfs create MyDrive`.
4.  **Access:** Open `http://localhost:8080` in your browser.
5.  **Config:** They are inside plugins/StoreiiMC as `config.yml` & `drive.yml`


***

## Commands Cheat Sheet

| Command | Description |
| :--- | :--- |
| `/mcfs create <name>` | Turns the target chest into a Drive. |
| `/mcfs password <name> <pass>` | Sets a password for the drive (req. for web login). |
| `/mcfs webimport <filename>` | Generates a "Magic Link" to upload files via URL. |
| `/mcfs list` | Lists files in the drive you are looking at. |
| `/mcfs delete <filename>` | Deletes a file (frees up disk space & chest slots). |
| `/mcfs deletedrive <name>` | Nukes the drive configuration. |
| `/mcfs purge` | **Admin:** Scans disk for orphaned data chunks and deletes them. |


## Configuration (`config.yml`)

We enforce limits to stop players from crashing your disk space.

```yaml
limits:
  # Maximum file size allowed for upload (prevents DoS)
  max-file-size-mb: 1024 # 1024 MB Per-File
  
  # How many drives a non-admin player can own
  max-drives-per-player: 1
  
  # Keep this at 1 unless you want to melt your TPS.
  # 1 Node = 1 Chest = ~8GB of storage potential via nested shulkers (depth 3).
  max-nodes-per-drive: 1
```

***

## Security & Architecture

This isn't just a toy! I tried to make it actually secure for public servers.

*   **Authentication:** The web interface uses a session cookie system. If a drive has a password, you *must* log in.
*   **Isolation:** Players cannot break or open other player's Drive chests.
*   **Sanitization:** Filenames are sanitized to prevent Path Traversal attacks (you can't upload to `../../server.properties`).
*   **DoS:** The web server checks `Content-Length` headers and monitors the input stream. If someone tries to upload a 10GB bomb, the connection is cut immediately.

## Known Limitations

1.  **Don't Break the Chest:** If you use WorldEdit or Creative Mode to break a Drive chest without using `/mcfs deletedrive`, the plugin loses track of the items. You'll need to run `/mcfs purge` to clean up the "ghost" map files from the disk.
2.  **One Chest Rule:** Currently, a Drive is limited to a single chest block. Since we use recursive Shulker packing, you technically have gigabytes of space in that one block, so expanding to double-chests isn't really necessary for normal players.

***


## License & Attribution


This project is protected under the  [![License](https://img.shields.io/badge/License-StoreiiMC-blue.svg)](https://github.com/X3r0Day/StoreiiMC-Plugin/blob/main/LICENSE)

You are free to use and modify this code, provided you give clear credit to **X3r0Day** and link back to this repository.

You are free to use, modify, and distribute this software, but **you must provide appropriate credit** to project *"X3r0Day"* and provide a link to this Repository!

If you modify this code or integrate it into your own project, you **must**:
1.  Keep the original copyright notice in the source files.
2.  Include a link back to this repository in your documentation or README.

"THIS CODE IS VERY READABLE"
"THIS USER HAS CONTRIBUTED TO: https://github.com/torvalds/linux"
"THIS USER HAS CONTRIBUTED TO: https://github.com/danielmiessler/seclists"

**Recommended Attribution Format:**
> *"Based on StoreiiMC-Plugin by X3r0Day."*

---
