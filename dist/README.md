# dist · 成品目录

> 最新发布：**v1.4.0** — <https://github.com/gausan-hj/ModSync/releases/tag/v1.4.0>

按加载器和 Minecraft 版本分开放置，jar 不要跨加载器混装。

```text
dist/
├─ fabric/
│  ├─ 26.3/modsync-26.3-1.4.0.jar
│  └─ 1.21.1/modsync-1.21.1-1.4.0.jar
├─ forge/
│  └─ 1.21.1/modsync-forge-1.21.1-1.4.0.jar
└─ neoforge/
   ├─ 1.21.1/modsync-neoforge-1.21.1-1.4.0.jar
   └─ 26.3/modsync-neoforge-26.3-1.4.0.jar
```

## 1.4.0 内容

- 按 SHA-256 的内容缓存：`.modsync-cache/<sha256>.jar`；
- HTTP Range 断点续传：未完成的文件保存在 `.modsync-cache/.partial/`；
- 版本号相同但内容不同也会强制替换；
- 替换时清理同 mod id 的旧 jar，避免重复模组；
- NeoForge 服主面板：
  - `/modsync/panel`
  - `/modsync/api/status`

## 下载

<https://github.com/gausan-hj/ModSync/releases/latest>

| 加载器 | Minecraft | 版本 | 同步方式 | 文件 |
|---|---|---|---|---|
| Fabric | 26.3 | 1.4.0 | 配置阶段·同连接 | `modsync-26.3-1.4.0.jar` |
| Fabric | 1.21.1 | 1.4.0 | 配置阶段·同连接 | `modsync-1.21.1-1.4.0.jar` |
| Forge | 1.21.1 | 1.4.0 | 进服前·HTTP | `modsync-forge-1.21.1-1.4.0.jar` |
| NeoForge | 1.21.1 | 1.4.0 | 进服前·HTTP | `modsync-neoforge-1.21.1-1.4.0.jar` |
| NeoForge | 26.3 | 1.4.0 | 进服前·HTTP | `modsync-neoforge-26.3-1.4.0.jar` |

旧版本和历史构建仍保留在对应子目录，方便回退与对照。

## 说明

- Fabric 版没有独立 HTTP 端口，因此没有网页面板；
- Forge 26.3 受 ForgeGradle 工具链限制，暂未构建；
- 服主面板默认跟随同步端口，公网暴露时请注意访问控制。
