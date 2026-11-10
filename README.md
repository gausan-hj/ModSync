# ModSync

> 联机模组同步：装一个 ModSync，剩下的模组让它自己下。

ModSync 解决联机时最常见的尴尬：**房主装了模组，朋友没装，或者版本对不上，进服就报错、被踢、崩溃。**

- **Fabric 版**在配置阶段（configuration phase）接管：服务器把模组清单发给客户端，客户端确认差异后，通过**同一条 Minecraft 连接**下载缺失的 jar，校验 SHA-256、备份旧文件、安装，然后提示重启。
- **Forge / NeoForge 版**在登录前就会检查模组列表，所以采用“先同步、再进服”：客户端在标题界面读取房主的 HTTP 清单，下载完成后重启再进服。
- **1.4.0** 增加按 SHA-256 的内容缓存、HTTP Range 断点续传、服主面板；即使模组作者没改版本号，只要 jar 内容不同也会强制替换，并清理同 ID 的旧 jar。

不需要网盘，也不需要把整合包整个发给朋友。

```text
Fabric  : 加入服务器 -> 配置阶段审核 -> 同连接传输 -> 校验 -> 重启 -> 进服
Forge/NeoForge : 标题界面点 ModSync -> HTTP 清单 -> 下载 -> 重启 -> 进服
```

---

## 启动器

仓库包含一个 WPF 启动器，位于 [`launcher-wpf/`](launcher-wpf/)：

- 微软账号登录 / 离线账号；
- 多实例，同一 Minecraft 版本可以有多套模组；
- 实例库、新建/删除实例；
- Modrinth 模组搜索和安装；
- 模组图标、禁用/启用、删除、A→Z、检查更新；
- 内存和 Java 可调；
- 下载 Minecraft 版本；
- NeoForge / Fabric / Forge 选择；
- 启动时不弹出 CMD 窗口；
- ModSync 作为必装模组自动内置。

构建：

```bash
dotnet build launcher-wpf/ModSyncLauncher.csproj -c Release
```

生成：

```text
launcher-wpf/bin/Release/net10.0-windows/ModSyncLauncher.exe
```

启动器界面预览：

![启动器](launcher-wpf/modern-preview.png)

---

## 下载

**最新发布：<https://github.com/gausan-hj/ModSync/releases/latest>**

| 加载器 | Minecraft | Mod 版本 | 同步方式 | 文件 |
|---|---|---|---|---|
| Fabric | 26.3 | 1.4.0 | 配置阶段·同连接 | `modsync-26.3-1.4.0.jar` |
| Fabric | 1.21.1 | 1.4.0 | 配置阶段·同连接 | `modsync-1.21.1-1.4.0.jar` |
| Forge | 1.21.1 | 1.4.0 | 进服前·HTTP | `modsync-forge-1.21.1-1.4.0.jar` |
| NeoForge | 1.21.1 | 1.4.0 | 进服前·HTTP | `modsync-neoforge-1.21.1-1.4.0.jar` |
| NeoForge | 26.3 | 1.4.0 | 进服前·HTTP | `modsync-neoforge-26.3-1.4.0.jar` |

> 其余历史构建（Fabric 26.1 / 26.1.1 / 26.1.2 / 26.2、Forge 1.20.1、NeoForge 1.21.11 / 26.1.2 / 26.2）以 0.1.0 放在仓库的 [`dist/`](dist/) 里，未随 1.4.0 一起发布。

---

## 支持的版本

### 发布版（1.4.0）

| 加载器 | Minecraft | 依赖 | JDK | 状态 |
|---|---|---|---|---|
| Fabric | 26.3 | Fabric Loader 0.20.1、Fabric API 0.165.0+26.3 | 25 | 实机联机验证 |
| Fabric | 1.21.1 | Fabric Loader 0.20.1、Fabric API（1.21.1） | 21 | 同源代码构建 |
| Forge | 1.21.1 | Forge 52.1.0 | 21 | 已构建 |
| NeoForge | 1.21.1 | NeoForge 21.1.265 | 21 | 已构建 |
| NeoForge | 26.3 | NeoForge 26.3.0.101 | 25 | 服务端启动验证 |

### 仓库中的其他构建（0.1.0）

| 目录 | 加载器 | Minecraft | JDK |
|---|---|---|---|
| `fabric/` | Fabric | 26.3 | 25 |
| `fabric-1.21.1/` | Fabric | 1.21.1 | 21 |
| `forge/` | Forge | 1.20.1（legacy） | 17 |
| `forge-1.21.1/` | Forge | 1.21.1 | 21 |
| `forge-1.21.11/` | Forge | 1.21.11 | 21 |
| `neoforge/` | NeoForge | 1.21.1 | 21 |
| `neoforge-1.21.1/` | NeoForge | 1.21.1 | 21 |
| `neoforge-1.21.11/` | NeoForge | 1.21.11 | 21 |
| `neoforge-26.1.2/` | NeoForge | 26.1.2 | 25 |
| `neoforge-26.2/` | NeoForge | 26.2 | 25 |
| `neoforge-26.3/` | NeoForge | 26.3 | 25 |

> `neoforge/` 是 1.21.1 的早期构建（0.1.0）；同版本推荐使用 `neoforge-1.21.1/`（1.3.0）。

---

## 安装

### Fabric

1. 安装对应版本的 **Fabric Loader** 和 **Fabric API**。
2. 把 `modsync-<mc版本>-<mod版本>.jar` 放进 `mods/`。
3. **房主和客人都要放。** ModSync 不能在自己还没加载时把自己下载下来，所以第一次必须手动装这一个 jar。
4. 房主开服/开房间，客人正常加入；模组不一致时会出现审核界面，点“下载并安装”。
5. 完成后**完全退出并重启** Minecraft，再进服。

### Forge / NeoForge

1. 安装对应版本的 Forge / NeoForge。
2. 把 `modsync-forge-...jar` 或 `modsync-neoforge-...jar` 放进 `mods/`（房主和客人都要）。
3. 房主启动服务器；ModSync 会在**游戏端口 + 1**（默认 25566）启动 HTTP 清单服务，被占用时尝试 8710。
4. 公网服务器需要在防火墙/路由器放行该端口；局域网一般不用。
5. 客户端在标题界面或多人游戏界面点 **ModSync**，选择服务器，下载、重启，再进服。

### 配置

- Fabric：`config/modsync.json`
- Forge / NeoForge：`config/modsync-forge.json`

首次启动自动生成。常用字段：

| 字段 | 说明 |
|---|---|
| `server.enabled` | 是否共享模组清单 |
| `server.excludeMods` | 永不共享的模组 id，如 `["sodium", "iris"]` |
| `server.maxFileSizeMb` | 单文件大小上限 |
| `server.maxTotalSizeMb` | 单次同步总大小上限 |
| `server.syncPort` | Forge/NeoForge 的 HTTP 端口；0 = 游戏端口 + 1 |
| `client.enabled` | 客户端是否接受同步 |
| `client.backup` | 替换前是否备份到 `mods/.modsync-backup/` |

### 命令

| 命令 | 权限 | 作用 |
|---|---|---|
| `/modsync status` | 所有人 | 查看共享数量、端口 |
| `/modsync list` | 所有人 | 列出共享 jar |
| `/modsync reload` | OP | 重新扫描 `mods/` |

---

## 从源码构建

每个目录都是独立的 Gradle 项目，互不混装。

| 目录 | 加载器 | MC | JDK | 构建 |
|---|---|---|---|---|
| `fabric/` | Fabric | 26.3 | 25 | `cd fabric && ./gradlew build` |
| `fabric-1.21.1/` | Fabric | 1.21.1 | 21 | `cd fabric-1.21.1 && ./gradlew build` |
| `forge/` | Forge | 1.20.1 | 17 | `cd forge && ./gradlew build` |
| `forge-1.21.1/` | Forge | 1.21.1 | 21 | `cd forge-1.21.1 && ./gradlew build` |
| `neoforge-26.3/` | NeoForge | 26.3 | 25 | `cd neoforge-26.3 && ./gradlew build` |

产物在各自的 `build/libs/`。首次构建需要联网下载 Minecraft、加载器和映射。

---

## 仓库结构

```text
ModSync/
├─ fabric/            Fabric 26.3 主项目
├─ fabric-1.21.1/     Fabric 1.21.1
├─ forge/             Forge 1.20.1（legacy）
├─ forge-1.21.1/      Forge 1.21.1
├─ forge-1.21.11/     Forge 1.21.11
├─ neoforge/          NeoForge 1.21.1（0.1.0 早期构建）
├─ neoforge-1.21.1/   NeoForge 1.21.1
├─ neoforge-1.21.11/  NeoForge 1.21.11
├─ neoforge-26.1.2/   NeoForge 26.1.2
├─ neoforge-26.2/     NeoForge 26.2
├─ neoforge-26.3/     NeoForge 26.3
├─ dist/              按加载器/版本整理好的成品 jar
├─ wiki/              离线 Wiki（HTML，可本地打开）
├─ scripts/           语言文件生成脚本
└─ README.md
```

---

## 工作原理

**Fabric（配置阶段）**

1. 服务端扫描 `mods/` 顶层 jar，生成清单（mod id、版本、文件名、大小）。
2. 在 vanilla 配置流程开始前插入配置任务，把清单发给客户端。
3. 客户端按 `mod id + version` 对比；缺失或版本不符时暂停配置，显示审核界面。
4. 用户确认后，服务端按 32 KB 分块发送；客户端边收边写临时文件。
5. 全部收完后校验 SHA-256，通过后备份旧 jar 并安装。
6. 断开同步连接，提示重启；重启后清单一致，正常进服。

**Forge / NeoForge（进服前）**

1. 服务端在 `游戏端口 + 1`（或 8710）提供 HTTP 清单。
2. 客户端在标题界面主动请求清单，对比本地 `mods/`。
3. 用户确认后下载、校验、备份、安装。
4. 重启后进服。

---

## 安全边界

- **必须手动确认**：没有自动下载开关。
- **SHA-256 校验**：不一致的文件不会安装。
- **只写 `mods/`**：文件名会被清洗，服务端也只读取 `mods/` 真实路径下的文件。
- **先 staging 再安装**：全部校验通过后才移动；失败不会留下半安装状态。
- **备份**：替换的旧 jar 移到 `mods/.modsync-backup/<时间>/`。
- **大小限制**：单文件和总量在客户端、服务端两侧都有上限。

仍然必须强调：**只加入你信任的服务器。** 同步模组本质上是从服务器下载并执行代码；ModSync 校验完整性，但不判断模组是否安全。

---

## 已知限制

1. **双方都要先手动装一次 ModSync**（引导问题）。
2. **Fabric API 不会被同步**，需要客户端预先安装。
3. **下载后必须重启**，不能在运行中的 JVM 里热加载。
4. **只共享 `mods/` 下的顶层 jar**，jar-in-jar 的模组随父 jar 一起走。
5. **不会自动删除客户端多出来的模组**，只提示。
6. Fabric 26.2 及更早版本只做了编译验证；Forge 1.20.1 低于最低支持版本，保留为 legacy。

---

## 链接

- 发布与下载：<https://github.com/gausan-hj/ModSync/releases>
- 问题反馈：<https://github.com/gausan-hj/ModSync/issues>
- Wiki：<https://modsync-beta.vercel.app/ModSync-Wiki.html>

## License

MIT，见 [LICENSE](LICENSE)。
