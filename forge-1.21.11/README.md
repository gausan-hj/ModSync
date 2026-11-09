# ModSync for Forge 1.20.1（方案 A：进服前同步）

这是 Forge 版，和 Fabric 版的体验**不一样**，因为 Forge 在 login 阶段就会拒绝模组列表不一致的客户端。

Forge 版不尝试在连接里自动传文件，而是：

```text
先在多人游戏/标题界面点 ModSync
  -> 读取房主的 HTTP 清单
  -> 对比本地 mods
  -> 下载缺失/版本不符的 jar
  -> 装好、重启
  -> 再正常点“加入服务器”
```

所以它是“**先同步，再进服**”，第一次不会再让你硬闯 Forge 的 mod 检查。

---

## 版本

| 项目 | 值 |
|---|---|
| Minecraft | **1.20.1** |
| Forge | **47.4.28**（1.20.1 recommended） |
| Java | **17** |
| 产物 | `forge/build/libs/modsync-0.1.0.jar` |

> 这是 Forge 1.20.1，不是 NeoForge。NeoForge 1.21.1 需要单独移植。

---

## 安装

1. 房主和客人都装 **Forge 1.20.1**。
2. 把 `modsync-0.1.0.jar` 放进对应实例的 `mods/`。
3. 房主启动服务器/世界。
4. 房主的 ModSync 会在 **游戏端口 + 1** 上启动 HTTP 同步服务（默认 25566）；如果被占用，会尝试 8710。
5. 公网服务器需要在防火墙/路由器放行这个 HTTP 端口；局域网一般不用。

> 客户端不需要 Fabric API，也不需要 NeoForge。

---

## 主菜单语言切换

标题界面右上角除了 `ModSync`，还有一个语言按钮；点击会弹出 16 种语言的列表，选中后切换整个 Minecraft 的语言，ModSync 界面会跟随。

---

## 使用

### 房主

- 正常启动 Forge 服务器即可；
- 日志会显示：

```text
ModSync sharing 1 jar(s), 1181413 bytes
ModSync sync server listening on 0.0.0.0:25566
```

- 命令：
  - `/modsync status`：查看共享数量、HTTP 端口；
  - `/modsync list`：列出共享 jar；
  - `/modsync reload`：重新扫描 `mods/`（需要 OP 权限）。

### 客人

1. 启动 Forge 1.20.1 客户端；
2. 在 **标题界面右上角** 或 **多人游戏界面右上角** 点 `ModSync`；
3. 选择要同步的服务器；
4. ModSync 会连接 `服务器地址:游戏端口+1`（失败再试 8710），读取清单；
5. 看到缺失/版本不符的模组后，点“下载并安装”；
6. 完成后 **完全退出游戏并重启**；
7. 再正常进服。

> 如果房主把 HTTP 服务放在 8710，客户端也会自动尝试。  
> 如果房主用了其他端口，需要把 `server.syncPort` 改成 8710，或者手动放行/映射。

---

## 配置

路径：

```text
config/modsync-forge.json
```

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

| 字段 | 说明 |
|---|---|
| `server.syncPort` | `0` = 游戏端口+1；也可以固定 8710。 |
| `server.excludeMods` | 不共享的 mod id。 |
| `server.maxFileSizeMb` | 单个 jar 上限。 |
| `server.maxTotalSizeMb` | 总大小上限。 |
| `client.backup` | 替换旧 jar 前备份到 `mods/.modsync-backup/`。 |

---

## HTTP 接口

- `GET /modsync/manifest.json`
- `GET /modsync/files/<文件名>`
- `GET /modsync/health`

清单包含每个 jar 的 `sha256`；客户端下载后会校验，不一致不安装。

---

## 安全边界

- 客户端必须手动选择服务器、手动点下载；
- 只从房主的 `mods/` 读取顶层 jar，不读取配置/存档；
- 文件路径固定，只按清单文件名提供，不允许路径穿越；
- 客户端先写临时目录，SHA-256 校验通过后才安装；
- 旧文件备份到 `mods/.modsync-backup/`；
- HTTP 服务默认无密码，局域网内可见；公网暴露时请只分享给信任的人，或通过防火墙限制来源。

**只加入你信任的服务器。模组是程序，会在你的电脑上运行代码。**

---

## 已知限制

1. **不是“点加入就自动传”**。Forge 的 login 检查决定了必须先同步、再进服。
2. **需要额外开放 HTTP 端口**。默认游戏端口+1；如果服务器在 NAT/面板服后面，可能无法开放，需改用端口映射或整合包方案。
3. **只支持 Forge 1.20.1**；NeoForge 1.21.1 是另一个移植。
4. **不能自动安装 Forge 本身、Java、Minecraft**。
5. **不会自动删除本地多出来的模组**，只提示。
6. **需要重启**；不支持运行时热加载。
7. **服务端 HTTP 无认证**；只适合信任的联机环境。
8. 如果房主使用 CurseForge 整合包，最稳的方式仍然是发整合包，而不是依赖 HTTP 同步。

---

## 构建

需要 JDK 17 和 ForgeGradle（MDK 已带 Gradle 8.8）：

```bash
cd forge
./gradlew build
```

Windows：

```powershell
cd forge
.\gradlew.bat build
```

产物：

```text
forge/build/libs/modsync-0.1.0.jar
```

---

## 上架 CurseForge

创建 CurseForge 项目时选：

- Game: Minecraft
- Category: Mods
- Loader: **Forge**
- Game version: **1.20.1**
- Upload `modsync-0.1.0.jar`

项目页建议写清楚：需要在房主和客户端都安装；房主需开放 HTTP 端口；这是“先同步再进服”的方案。

---

## 验证状态

- Forge 服务端启动通过；
- 清单接口返回真实数据（1 个 Cloth Config jar，1,181,413 字节，SHA-256 已包含）；
- `/modsync/files/...` 返回 200 与完整大小；
- **正式打包 jar** 也在独立安装的 Forge 1.20.1 服务端上加载成功，并启动 25568 端口的 HTTP 服务；
- 客户端代码编译通过；
- **完整客户端 GUI 下载流程还需要一次实机回归**（当前测试环境的桌面被用户占用）。
