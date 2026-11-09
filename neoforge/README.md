# ModSync for NeoForge（方案 A：进服前同步）

NeoForge 版沿用 Forge 版思路：**先在游戏外读取房主的 HTTP 清单，下载缺失模组，重启后再进服**。  
NeoForge 的登录阶段同样会检查模组列表，所以不要直接点“加入服务器”硬闯。

---

## 版本

| 项目 | 值 |
|---|---|
| Minecraft | **1.21.1 / 1.21.11 / 26.1.2 / 26.2 / 26.3** |
| NeoForge | 21.1.265 / 21.11.48 / 26.1.2.126 / 26.2.0.93 / 26.3.0.101 |
| Java | 21（1.21.x）/ 25（26.x） |
| 产物 | `neoforge*/build/libs/modsync-0.1.0.jar` |

Forge 是另一个独立产物，见 `forge/README.md`。

---

## 安装

1. 房主和客人都安装对应版本的 **NeoForge**；
2. 把 `modsync-0.1.0.jar` 放进实例的 `mods/`；
3. 房主启动服务器/世界；
4. 房主的 ModSync 默认在 **游戏端口+1** 启动 HTTP 服务（25566），失败会尝试 8710；
5. 公网服务器需要放行这个 HTTP 端口；局域网一般不用。

---

## 主菜单语言切换

标题界面右上角除了 `ModSync`，还有一个语言按钮；点击会弹出 16 种语言的列表，选中后切换整个 Minecraft 的语言，ModSync 界面会跟随。

---

## 使用

1. 客户端启动后，在 **标题界面右上角** 或 **多人游戏界面右上角** 点 `ModSync`；
2. 选择服务器；
3. ModSync 读取 `服务器:游戏端口+1` 或 `8710` 的清单；
4. 查看缺失/版本不符；
5. 点“下载并安装”；
6. 完成后完全退出并重启；
7. 正常进服。

> 这是“先同步、再进服”，不是“点加入后自动传”。NeoForge 的登录检查决定了这一点。

---

## 配置

```text
config/modsync-forge.json
```

（为了和 Forge 版保持一致，NeoForge 版也使用这个文件名。）

```json
{
  "server": {
    "enabled": true,
    "syncPort": 0,
    "excludeMods": [],
    "maxFileSizeMb": 200,
    "maxTotalSizeMb": 1024
  },
  "client": {
    "enabled": true,
    "backup": true,
    "maxTotalSizeMb": 1024
  }
}
```

`syncPort=0` 表示游戏端口+1；固定端口可填 8710。

---

## 命令

- `/modsync status`
- `/modsync list`
- `/modsync reload`（需要 OP）

---

## 安全与限制

- 客户端必须手动选择服务器、手动点下载；
- 服务端只共享 `mods/` 下的顶层 jar；
- 下载后 SHA-256 校验，失败不安装；
- 旧 jar 备份到 `mods/.modsync-backup/`；
- HTTP 无认证，公网暴露时请限制来源；
- 需要额外开放 HTTP 端口；
- 不支持自动安装 NeoForge/Java/Minecraft；
- 不会自动删除本地多余模组；
- 需要重启。

**只加入你信任的服务器；模组是程序。**

---

## 构建

```bash
cd neoforge
./gradlew build
```

需要 JDK 21。产物：

```text
neoforge/build/libs/modsync-0.1.0.jar
```

---

## 上架 CurseForge

- Game: Minecraft
- Loader: **NeoForge**
- Game version: **1.21.1**
- Upload `modsync-0.1.0.jar`

---

## 验证状态

- NeoForge 服务端启动通过；
- 清单接口返回真实数据（Cloth Config 1,163,890 字节 + SHA-256）；
- `/modsync/files/...` 返回 200 与完整大小；
- 客户端代码编译通过；
- **完整客户端 GUI 下载流程还需要一次实机回归**。
