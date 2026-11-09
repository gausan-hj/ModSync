from pathlib import Path
import json, re, time, requests

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {
    "ja_jp": "ja", "ko_kr": "ko", "de_de": "de", "fr_fr": "fr", "es_es": "es",
    "pt_br": "pt", "it_it": "it", "ru_ru": "ru", "tr_tr": "tr", "pl_pl": "pl",
    "nl_nl": "nl", "th_th": "th", "id_id": "id", "vi_vn": "vi",
}
EN = {
    "modsync.name": "ModSync",
    "modsync.subtitle": "Sync the host's mods before you join",
    "modsync.review.title": "Sync mods",
    "modsync.review.summary": "%s file(s) to download · %s",
    "modsync.review.extras": "%s local mod(s) are not on this server and may cause incompatibilities",
    "modsync.consent": "The server will send jar files. Mods are programs and can run code on your computer. Only continue if you trust this server and the people running it.",
    "modsync.download": "Download & install",
    "modsync.cancel": "Cancel",
    "modsync.cancelled": "Mod sync cancelled.",
    "modsync.quit": "Quit game",
    "modsync.failed": "Mod sync failed.",
    "modsync.status.ok": "OK",
    "modsync.status.missing": "Missing",
    "modsync.status.mismatch": "Wrong version",
    "modsync.status.mixed": "Partly missing",
    "modsync.status.optional": "Optional",
    "modsync.progress.title": "Downloading mods",
    "modsync.progress.waiting": "Waiting for the server manifest...",
    "modsync.progress.connecting": "Opening the sync connection...",
    "modsync.progress.hint": "Keep this window open. Minecraft must be restarted after the download.",
    "modsync.progress.files": "Files %s/%s · %s",
    "modsync.result.success": "Mod sync complete",
    "modsync.result.failed": "Mod sync failed",
    "modsync.result.installed_short": "Installed %s mod file(s)",
    "modsync.result.backup": "Previous jars were backed up to: %s",
    "modsync.result.restart": "Fully quit and restart Minecraft to load the new mods.",
    "modsync.result.failed_hint": "Nothing that failed verification was installed. You can open the mods folder to inspect leftovers.",
    "modsync.result.open_folder": "Open mods folder",
    "modsync.server.done": "ModSync finished. Restart Minecraft to load the new mods.",
    "modsync.error.protocol": "Incompatible ModSync protocol.",
    "modsync.error.manifest": "The server sent an invalid ModSync manifest.",
    "modsync.error.server": "The server could not send the ModSync manifest.",
    "modsync.error.request": "The server rejected the ModSync file request.",
    "modsync.error.file_too_large": "A mod file is larger than the server's limit.",
    "modsync.error.total_too_large": "The sync is larger than the server's limit.",
    "modsync.error.read": "The server could not read a mod file.",
    "modsync.error.transfer": "The mod transfer failed.",
    "modsync.error.timeout": "ModSync timed out. Please join again.",
    "modsync.error.server_stopping": "The server is stopping.",
    "modsync.forge.select_title": "ModSync · Select server",
    "modsync.forge.select_hint": "Sync first, then join from Multiplayer",
    "modsync.forge.no_servers": "No saved servers yet. Add one in Multiplayer first.",
    "modsync.forge.selected": "Selected: %s  %s",
    "modsync.forge.sync_selected": "Sync selected server",
    "modsync.forge.close": "Close",
    "modsync.forge.loading": "Reading the host's mod manifest...",
    "modsync.forge.port_hint": "Connects to the sync service on game port +1 by default",
    "modsync.forge.host_hint": "The host also needs the Forge build of ModSync and must allow the extra HTTP port (game port +1, fallback 8710).",
    "modsync.forge.review_title": "ModSync · Pre-join sync",
    "modsync.forge.need_download": "%s file(s) to download · %s",
    "modsync.forge.retry": "Retry",
    "modsync.forge.error_size": "This sync is larger than the client's configured limit.",
    "modsync.forge.extras": "%s local mod(s) are not on the server and may be incompatible",
    "modsync.forge.downloading": "ModSync · Downloading",
    "modsync.forge.preparing": "Preparing...",
    "modsync.forge.files": "Files %s/%s · %s",
    "modsync.forge.keep_open": "Keep this window open; fully restart the game when done.",
    "modsync.forge.installed": "Installed %s mod file(s)",
    "modsync.forge.backup": "Previous jars backed up to: %s",
    "modsync.forge.failed_hint": "Files that failed verification were not installed.",
    "modsync.forge.back": "Back",
}
ZH = {
    "modsync.name": "模组同步",
    "modsync.subtitle": "进服前补齐房主的模组",
    "modsync.review.title": "模组同步",
    "modsync.review.summary": "需要下载 %s 个文件 · %s",
    "modsync.review.extras": "服务器没有这 %s 个本地模组，可能造成不兼容",
    "modsync.consent": "服务器会发送 jar 文件。模组是程序，会在你的电脑上运行代码；只在你信任该服务器和服主时继续",
    "modsync.download": "下载并安装",
    "modsync.cancel": "取消",
    "modsync.cancelled": "已取消模组同步。",
    "modsync.quit": "退出游戏",
    "modsync.failed": "模组同步失败。",
    "modsync.status.ok": "已同步",
    "modsync.status.missing": "缺失",
    "modsync.status.mismatch": "版本不符",
    "modsync.status.mixed": "部分缺失",
    "modsync.status.optional": "可选",
    "modsync.progress.title": "正在下载模组",
    "modsync.progress.waiting": "等待服务器发送清单...",
    "modsync.progress.connecting": "正在建立同步连接...",
    "modsync.progress.hint": "请保持窗口打开。下载完成后需要完全重启 Minecraft。",
    "modsync.progress.files": "文件 %s/%s · %s",
    "modsync.result.success": "模组同步完成",
    "modsync.result.failed": "模组同步失败",
    "modsync.result.installed_short": "已安装 %s 个模组文件",
    "modsync.result.backup": "旧文件已备份到：%s",
    "modsync.result.restart": "请完全退出并重新启动 Minecraft，新模组才会加载。",
    "modsync.result.failed_hint": "没有安装任何未通过校验的文件。你可以打开 mods 文件夹检查残留文件。",
    "modsync.result.open_folder": "打开模组文件夹",
    "modsync.server.done": "ModSync 同步完成，请重启游戏加载新模组。",
    "modsync.error.protocol": "ModSync 协议不兼容。",
    "modsync.error.manifest": "服务器发送了无效的 ModSync 清单。",
    "modsync.error.server": "服务器无法发送 ModSync 清单。",
    "modsync.error.request": "服务器拒绝了 ModSync 文件请求。",
    "modsync.error.file_too_large": "某个模组文件超过了服务器的限制。",
    "modsync.error.total_too_large": "同步总大小超过了服务器的限制。",
    "modsync.error.read": "服务器无法读取某个模组文件。",
    "modsync.error.transfer": "模组传输失败。",
    "modsync.error.timeout": "ModSync 等待超时，请重新加入。",
    "modsync.error.server_stopping": "服务器正在关闭。",
    "modsync.forge.select_title": "ModSync · 选择服务器",
    "modsync.forge.select_hint": "先同步，再回多人游戏点加入",
    "modsync.forge.no_servers": "还没有保存的服务器。请先在多人游戏里添加服务器。",
    "modsync.forge.selected": "选中：%s  %s",
    "modsync.forge.sync_selected": "同步选中服务器",
    "modsync.forge.close": "关闭",
    "modsync.forge.loading": "正在读取房主的模组清单...",
    "modsync.forge.port_hint": "默认连接游戏端口 +1 的同步服务",
    "modsync.forge.host_hint": "需要房主也安装 Forge 版 ModSync，并允许访问额外的 HTTP 端口（默认游戏端口+1，失败时尝试 8710）。",
    "modsync.forge.review_title": "ModSync · 进服前同步",
    "modsync.forge.need_download": "需要下载 %s 个文件 · %s",
    "modsync.forge.retry": "重试",
    "modsync.forge.error_size": "同步大小超过客户端配置上限。",
    "modsync.forge.extras": "本地还有 %s 个服务器没有的模组，可能造成不兼容",
    "modsync.forge.downloading": "ModSync · 正在下载",
    "modsync.forge.preparing": "准备中...",
    "modsync.forge.files": "文件 %s/%s · %s",
    "modsync.forge.keep_open": "保持窗口打开；完成后需要完全重启游戏。",
    "modsync.forge.installed": "已安装 %s 个模组文件",
    "modsync.forge.backup": "旧文件备份：%s",
    "modsync.forge.failed_hint": "没有通过校验的文件不会被安装。",
    "modsync.forge.back": "返回",
}
PLACEHOLDER = "\uE001"


def protect(value):
    return value.replace("%s", PLACEHOLDER)


def restore(value):
    return value.replace(PLACEHOLDER, "%s")


def translate(values, code):
    marker = "\n\uE000\n"
    q = marker.join(protect(v) for v in values)
    target = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=" + code + "&dt=t&q=" + requests.utils.quote(q, safe="")
    r = requests.post("https://r.jina.ai/", json={"url": target}, timeout=90)
    r.raise_for_status()
    text = r.text.split("Markdown Content:", 1)[-1].strip()
    data = json.loads(text)
    joined = "".join(part[0] for part in data[0] if part and part[0])
    parts = joined.split(marker)
    if len(parts) != len(values):
        print("segment mismatch", code, len(parts), len(values))
        return None
    return [restore(p.strip()) for p in parts]


def write(locale, mapping):
    data = json.dumps(mapping, ensure_ascii=False, indent="\t") + "\n"
    for loader in ("fabric", "forge", "neoforge"):
        folder = ROOT / loader / "src" / "main" / "resources" / "assets" / "modsync" / "lang"
        folder.mkdir(parents=True, exist_ok=True)
        (folder / f"{locale}.json").write_text(data, encoding="utf-8")


write("en_us", EN)
write("zh_cn", ZH)
keys = list(EN)
values = [EN[k] for k in keys]
for locale, code in TARGETS.items():
    result = None
    for attempt in range(3):
        try:
            result = translate(values, code)
            if result:
                break
        except Exception as error:
            print(locale, "retry", attempt + 1, error)
            time.sleep(1.5)
    if not result:
        print("FAILED", locale)
        continue
    write(locale, dict(zip(keys, result)))
    print("wrote", locale, len(result))
