# ModSync

**模组同步 · Install one mod. Let it fetch the rest.**

ModSync is a Fabric mod for Minecraft 26.x that fixes the oldest problem in modded multiplayer: the host has the mods, the guest does not, and the guest gets kicked before they can even see why.

Instead of sending a modpack link, ModSync sends the host's mod list during Minecraft's **configuration phase** — before registry data is transmitted — and lets the player download only the jars they are missing, over the same Minecraft connection they are already using.

No extra HTTP port. No cloud drive. No re-downloading a 400 MB pack for one changed library.

---

## The problem

Joining a modded world usually goes like this:

1. The host sends a list of 30 mods, or a modpack file.
2. The guest installs them by hand, guesses the right versions, and misses one.
3. Minecraft disconnects during configuration with a registry mismatch — a message that tells the player nothing useful.

ModSync replaces steps 1–3 with a review screen that shows exactly what is missing, what has the wrong version, and how large the download will be.

---

## How it works

1. When the server starts, ModSync scans the top-level `.jar` files in `mods/` and builds a manifest of mod ids, versions, file names and sizes.
2. On join, a configuration task runs **before** vanilla configuration begins. It sends the manifest to the client.
3. The client compares the manifest against its own `mods/` folder by **mod id + version**.
   - Everything matches: the client answers `READY` and the normal join continues.
   - Something is missing or mismatched: the connection is held and a review screen opens.
4. If the player clicks **Download and install**, the client requests the files on the same connection. The server streams them in 32 KB chunks.
5. When all files have arrived, the client verifies each **SHA-256**, stages them in `mods/.modsync-tmp/`, backs up replaced jars to `mods/.modsync-backup/<timestamp>/`, and only then moves the new files into `mods/`.
6. The client asks the player to fully restart Minecraft. After the restart the mods load, the manifest matches, and the next join is instant.

Because all of this happens before registry data is sent, a missing content mod does not get the player kicked first.

---

## Features

- **Manifest exchange in the configuration phase** — works even when the client is missing blocks, items or dimensions.
- **No extra port or service** — reuses the existing Minecraft TCP connection, so LAN worlds, port-forwarded servers and tunneled setups all work.
- **Per-mod comparison** — distinguishes *missing*, *version mismatch*, *partially missing* and *optional*.
- **Chunked transfer with limits** — 32 KB chunks, per-file and total size caps on both sides.
- **SHA-256 verification** — files that do not match are never installed.
- **Staged install with backup** — everything is verified before anything is moved; old jars are backed up.
- **Explicit consent** — nothing is downloaded until the player presses the button.
- **Server-side allowlist by construction** — only top-level `.jar` files inside the server's own `mods/` folder can be shared; configs, worlds and account data are never touched.
- **Chinese and English UI**, following the game language.
- **Admin commands** — `/modsync status`, `/modsync list`, `/modsync reload`.

---

## Requirements

| | |
|---|---|
| Minecraft | 26.3 (a 26.2 build is also provided) |
| Mod loader | Fabric Loader 0.20.1+ |
| Dependency | Fabric API |
| Java | 25 |
| Side | Client **and** server — both need ModSync |

> ModSync is **Fabric only**. Forge and NeoForge reject mismatched mod lists during login, before a configuration task can run, so this approach does not port directly.

---

## Installation

### Server

1. Install Fabric Loader 0.20.1 and Fabric API.
2. Put `modsync-26.3-0.1.0.jar` into the server's `mods/` folder.
3. Restart the server.
4. Run `/modsync status` — it should report how many jars are being shared.

### Client

1. Install Fabric Loader 0.20.1 and Fabric API.
2. Put the **same** `modsync-26.3-0.1.0.jar` into the instance's `mods/` folder.
3. Join the server. If anything is missing, the review screen appears.

The first install is manual for everyone — ModSync cannot download itself before it is loaded. After that, it handles the other mods.

---

## Configuration

The first launch creates `config/modsync.json`:

```json
{
  "server": {
    "enabled": true,
    "shareClientOnlyMods": false,
    "excludeMods": [],
    "maxFileSizeMb": 200,
    "maxTotalSizeMb": 1024,
    "idleTimeoutSeconds": 900
  },
  "client": {
    "enabled": true,
    "backup": true,
    "maxTotalSizeMb": 1024
  }
}
```

| Field | Meaning |
|---|---|
| `server.enabled` | Share the host's mod list at all. |
| `server.shareClientOnlyMods` | Also share `environment: client` mods. Off by default. |
| `server.excludeMods` | Mod ids that must never be shared, e.g. `["sodium", "iris"]`. |
| `server.maxFileSizeMb` | Per-file cap. |
| `server.maxTotalSizeMb` | Per-session total cap. |
| `server.idleTimeoutSeconds` | How long the review screen may sit idle before the server drops the connection. |
| `client.enabled` | Accept manifests; when off, the client always answers `READY`. |
| `client.backup` | Back up replaced jars before installing. |
| `client.maxTotalSizeMb` | Client-side cap on what it will accept. |

Changes take effect after a restart; on a server, `/modsync reload` rebuilds the manifest without restarting.

---

## Commands

| Command | Permission | Effect |
|---|---|---|
| `/modsync status` | everyone | Whether sharing is on, how many jars, total size. |
| `/modsync list` | everyone | List up to 20 shared file names. |
| `/modsync reload` | gamemaster | Rescan `mods/` and rebuild the manifest. |

---

## Security model

Syncing mods means downloading and later executing code, so ModSync is deliberately conservative:

- **Explicit consent only** — there is no auto-install switch.
- **SHA-256 on every file** — a mismatch aborts the install.
- **Write scope is `mods/` only** — file names are sanitized to a single `.jar` name, and the server only serves files whose real path is inside its own `mods/` directory (no path traversal, no symlink escape).
- **Verify, then install** — all files are staged first; a failed transfer leaves no half-installed state.
- **Backups** — replaced jars go to `mods/.modsync-backup/<timestamp>/`.
- **No private data** — the manifest contains mod metadata and file sizes only. Configs, worlds, logs and tokens are never read.
- **Caps on both sides** — a malicious server cannot fill the client's disk.

That said: **only join servers you trust.** A server can offer you any jar, and a jar is a program. ModSync verifies integrity, not intent. It also does not bypass mod licenses — if a mod does not allow redistribution, add it to `server.excludeMods`.

---

## Known limitations

1. **Bootstrap** — both sides must install ModSync manually once.
2. **Fabric only** — no Forge or NeoForge build.
3. **Fabric API is not synced** — install it before joining.
4. **Restart required** — jars cannot be loaded safely into a running JVM.
5. **Top-level jars only** — mods embedded inside another jar travel with their parent; mods outside `mods/` are not shared.
6. **Extra local mods are reported, not removed.**
7. **Same id + same version but different contents** is not detected at manifest time; SHA-256 only runs during an actual transfer.
8. **No resume** — an interrupted download starts over.
9. Only tested on Windows; macOS/Linux should work but are unverified.

---

## License

MIT. Source code and issue tracker are linked in the project sidebar.
