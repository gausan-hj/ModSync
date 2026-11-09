# ModSync 架构说明

## 1. 目标与非目标

### 目标

- 让已经在服务器里的客户端，在**还没有进入世界、也还没有被注册表不匹配踢掉**之前，拿到房主的模组清单。
- 在客户端明确同意后，通过**同一条 Minecraft 连接**传输缺失的 jar，不要求额外端口、HTTP 服务、网盘或整合包下载链接。
- 对下载内容做完整性校验，限制文件大小和路径，安装前备份。
- 安装完成后要求重启，避免在运行中的 JVM 里动态加载不受控代码。

### 非目标

- 不解决“客户端完全没有 ModSync”的引导问题。双方必须先手动装 ModSync。
- 不支持 Forge / NeoForge。Forge 在 login 阶段就做 mod list 拒绝，配置阶段的自定义任务来不及执行。
- 不替代启动器/整合包管理器。它只同步 `mods/` 下的顶层 jar，不管理配置、资源包、存档、Java 版本。
- 不自动删除多余模组，不自动重启游戏。

---

## 2. 为什么是 configuration phase

Minecraft 1.20.5+ 的连接流程：

```
login -> configuration -> play
```

- 内容模组（方块、物品、维度）的注册表数据是在 configuration 阶段发送的。
- 如果客户端缺少对应 mod，注册表不匹配通常会在 configuration 阶段或进入 play 时出问题。
- Fabric 的 `ServerConfigurationConnectionEvents.CONFIGURE` 允许在 vanilla 配置**开始前**插入一个 `ConfigurationTask`。任务不完成，vanilla 配置不会继续。

ModSync 的任务流程：

```
服务端 CONFIGURE
  -> addTask(SyncTask)
  -> SyncTask.start() 发送 manifest
  -> 客户端对比
       ├─ 无差异: 发送 READY -> 服务端 completeTask -> vanilla 配置继续 -> 进服
       └─ 有差异: 客户端显示审核界面（连接保持）
                    ├─ 取消: CANCEL + 断开
                    └─ 下载: REQUEST -> 服务端分块发送 -> COMPLETE -> 断开
```

这样客户端不会先看到/应用错误的注册表数据，也不会先被踢。

### 为什么不用 HTTP

- 局域网/内网穿透/联机模组通常只暴露 Minecraft 的 TCP 端口；再开 8710 之类的 HTTP 端口需要额外映射，朋友往往不会弄。
- 走游戏连接可以复用现有 NAT 穿透、隧道、白名单、在线模式认证。
- 代价是协议必须自己分块、限流、校验；实现量比 HTTP 大，但部署体验更好。

### 为什么不做 server list ping 预检

- 服务器列表 ping 的 JSON 可以塞自定义字段，但容量有限，且需要客户端在“加入前”主动解析。
- 文件传输仍然需要第二条通道；configuration 阶段已经能拿到一条已认证的连接，直接复用更简单。

---

## 3. 报文

### 3.1 传输层

- 一个自定义 payload：`modsync:sync`
- 注册方向：
  - `PayloadTypeRegistry.clientboundConfiguration()`
  - `PayloadTypeRegistry.serverboundConfiguration()`
- 内容是一个不透明的 `byte[]`，由 `SyncPayload` 编解码。
- 单帧上限 1,000,000 字节；文件块 32 KB。

### 3.2 帧格式

所有整数大端，字符串使用 `DataOutputStream.writeUTF`。

```
int    MAGIC = 0x4D53594E ("MSYN")
short  VERSION = 1
byte   TYPE
...    type-specific body
```

| TYPE | 方向 | 字段 | 作用 |
|---|---|---|---|
| 1 MANIFEST | S→C | nonce, json | 模组清单 |
| 2 READY | C→S | nonce | 本地已一致，放行 |
| 3 NEEDED | C→S | nonce, files[] | 客户端需要这些文件，正在显示审核界面 |
| 4 REQUEST | C→S | nonce, files[] | 用户已同意，开始传输 |
| 5 PING | C→S | nonce | 审核界面停留期间保活 |
| 6 CANCEL | 双向 | reason | 取消 |
| 10 FILE_START | S→C | index, total, file, size | 开始一个文件 |
| 11 FILE_CHUNK | S→C | index, offset, data | 32 KB 数据块 |
| 12 FILE_END | S→C | index, sha256, ok, error | 文件结束 + 哈希 |
| 13 COMPLETE | S→C | files, bytes | 全部完成 |
| 14 ERROR | S→C | code, message | 错误 |

协议层完全不依赖 Minecraft 类，位于 `dev.modsync.protocol`，有单元测试。

### 3.3 清单 JSON

```json
{
  "protocol": 1,
  "serverName": "ModSync Test",
  "nonce": "uuid",
  "generatedAt": 1760000000000,
  "files": [
    {
      "file": "cloth-config-fabric-26.3.159.jar",
      "size": 1135009,
      "required": true,
      "mods": [
        {
          "id": "cloth-config",
          "version": "26.3.159",
          "name": "Cloth Config v26.3",
          "env": "UNIVERSAL"
        }
      ]
    }
  ]
}
```

- `file` 只是文件名，不是路径。
- 一个 jar 可以包含多个 mod（多 mod jar / 父 jar），客户端会检查其中每个 id/version。
- `required=false` 目前只用于可选的 client-only 模组；默认配置不会共享它们。

---

## 4. 服务端实现

### 4.1 清单扫描

`SyncServer.refreshManifest()`：

1. 遍历 `FabricLoader.getAllMods()`。
2. 跳过平台/自身：`minecraft`、`java`、`fabricloader`、`fabric-api`、`modsync`。
3. 跳过 `environment: server`；默认跳过 `environment: client`。
4. 只处理 `ModOrigin.Kind.PATH`。
5. 遍历 `mod.getOrigin().getPaths()`，只接受：
   - 普通文件；
   - 以 `.jar` 结尾；
   - `toRealPath()` 位于 `gameDir/mods` 之下（防止符号链接/路径逃逸）。
6. 按真实 jar 路径分组；同一 jar 里的多个 mod 合并成一个 `ManifestFile`。
7. 文件大小必须 > 0 且不超过 `maxFileSizeMb`。
8. 生成随机 `nonce`，缓存 `fileMap`（文件名 → 真实路径）。

> 这里用 `getOrigin().getPaths()` 而不是 `getRootPaths()`。后者在部分 Fabric Loader/MC 版本里返回的是 mod 根资源路径（例如 `/`），不是 jar 路径。

### 4.2 配置任务

`SyncConfigurationTask implements ConfigurationTask`：

- `type()`：`modsync:sync`
- `start(sender)`：
  - 把任务放入 `TASKS` map；
  - 发送 `MANIFEST`；
  - 安排空闲超时（默认 900 秒，收到任何客户端消息都会重置）。
- 全局 receiver 按 `packetListener` 找到任务：
  - `READY`：校验 nonce，`completeTask()`，让 vanilla 配置继续。
  - `NEEDED`：只刷新活动时间；客户端正在看审核界面。
  - `PING`：刷新活动时间。
  - `REQUEST`：校验 nonce、文件列表、大小上限，启动 `TransferWorker`。
  - `CANCEL`：`completeTask()`。
- 超时或断开：关闭任务、停止 worker、清理 map。

任务没有实现 `tick()`；生命周期由客户端消息和服务端定时器驱动。

### 4.3 文件传输

`TransferWorker` 在独立 daemon 线程运行：

1. 逐个文件发送 `FILE_START`。
2. 从磁盘读 32 KB，边读边更新 `MessageDigest`，发送 `FILE_CHUNK`。
3. 每块 `Thread.sleep(1)` 做粗略限速（约 32 MB/s 上限），避免 Netty 出站缓冲无限增长。
4. 发送 `FILE_END(index, sha256, true, "")`。
5. 全部完成后发送 `COMPLETE`，然后断开客户端。
6. 任何异常：发送 `ERROR`，断开，记录日志。

服务端不把整个文件读进内存；同一时刻只有一个 32 KB 块。发送线程通过 `listener.send()` 写包，Netty 的写操作是线程安全的。

> `COMPLETE` 先于断开包发出；TCP 顺序保证客户端先处理 `COMPLETE`，再看到断开。客户端在 `COMPLETE` 后自己也会主动关闭连接。

### 4.4 大小限制

- 单文件：`server.maxFileSizeMb`（默认 200 MB）。
- 总大小：`server.maxTotalSizeMb`（默认 1024 MB）。
- 请求的文件名必须精确出现在该连接捕获的清单快照里；不能请求任意路径。
- `/modsync reload` 后新连接使用新快照，旧任务仍用旧快照，避免传输中途文件列表变化。

---

## 5. 客户端实现

### 5.1 状态机

`ClientSession.Phase`：

```
REVIEW -> DOWNLOADING -> VERIFYING -> INSTALLING -> DONE
   |           |              |
   +-----------+--------------+--> FAILED
   +------------------------------> CANCELLED
```

- `REVIEW`：连接保持，审核界面；每 10 秒发 `PING`。
- `DOWNLOADING`：收到 `FILE_START` 后创建 `mods/.modsync-tmp/<uuid>/<index>-<name>`，用 `FileChannel` 按 offset 写。
- `VERIFYING`：`COMPLETE` 后关闭所有通道，在 `ModSync-io` 单线程池里逐个计算 SHA-256。
- `INSTALLING`：全部哈希匹配后，`ModInstaller` 备份 + 移动。
- `DONE`：断开，显示完成界面。
- `FAILED`：清理临时目录，断开，显示失败界面。
- `CANCELLED`：发送 `CANCEL`，清理，断开。

### 5.2 差异计算

`SyncPlanner.plan(manifest, localMods)`：

- 本地 mod 以 id 建 map；重复 id 保留第一个。
- 对清单里的每个 jar：
  - 所有 mod 都在且版本字符串完全相等 → `OK`；
  - 没有 `required` → `OPTIONAL_MISSING`；
  - 只有版本不符 → `VERSION_MISMATCH`；
  - 只有缺失 → `MISSING`；
  - 既有缺失又有版本不符 → `MIXED`。
- 本地有、清单没有的 universal 模组 → `extraLocalMods` 警告。
- 平台 mod（`minecraft`、`java`、`fabricloader`、`fabric-api`、`modsync`）不参与 extras。

版本比较是**字符串精确比较**，不做 semver 兼容判断；这样最安全，避免“看起来兼容”的假阳性。

### 5.3 安装

`ModInstaller.install()`：

1. 规范化 `mods/`，建立本地 id → jar 路径 map。
2. 对每个已校验文件：
   - 清洗文件名，处理重名（`-2`、`-3`…）；
   - 目标必须在 `mods/` 下；
   - 目标已存在 → 移动到备份目录；
   - 清单里同一 mod id 的本地旧 jar（不同文件名）→ 也移动到备份目录，避免重复 id；
   - 移动临时文件到目标。
3. 返回安装列表和备份目录。

文件名清洗（`SafeNames`）：

- 去掉目录部分；
- 只保留 `[A-Za-z0-9._+-]`，其余替换为 `_`；
- 去掉开头的点；
- 强制 `.jar` 结尾；
- 处理 Windows 保留名（`CON`、`PRN`、`COM1`…）；
- 限制 180 字符。

### 5.4 连接断开

- `ClientConfigurationConnectionEvents.DISCONNECT`：
  - `REVIEW` / `DOWNLOADING` → 视为失败，显示失败界面；
  - `VERIFYING` / `INSTALLING` / `DONE` → 服务端在 `COMPLETE` 后主动断开是预期行为，不覆盖结果。
- `ClientSession.fail()` 用 `client.execute()` 切回渲染线程，再设置界面/断开，避免在 Netty 线程直接操作 UI。

---

## 6. 线程模型

| 线程 | 做什么 |
|---|---|
| Netty event loop | 收 `CustomPayload`、解析帧、写文件块、发 READY/REQUEST/PING |
| Server thread | 配置任务 `start`、`completeTask`、命令 |
| `ModSync-transfer` daemon | 服务端读文件并发送块 |
| `ModSync-io` daemon | 客户端 SHA-256 校验、安装 |
| Render thread | 渲染 Screen、处理按钮点击、`setScreenAndShow` |

跨线程状态大多用 `volatile` + `synchronized` 的会话方法保护；UI 更新统一 `client.execute()`。

---

## 7. 失败与恢复

| 情况 | 行为 |
|---|---|
| 服务端清单为空 | 不插入任务，正常进服。 |
| 客户端无 ModSync | 服务端 `canSend` 为 false，不插入任务；客户端按原版流程走（可能因模组不匹配失败）。 |
| 清单 JSON 损坏/协议版本不符 | 客户端断开，显示协议错误。 |
| 文件被服务器改动（大小变化） | worker 检测到 `file grew/changed while sending`，发 ERROR 并断开。 |
| 客户端收到超出声明大小的数据 | 立即失败，删除临时文件。 |
| SHA-256 不匹配 | 不安装，失败界面；已下载的临时文件清理。 |
| 安装中途 IO 失败 | 已移动的文件保留，旧文件在备份目录；显示错误。 |
| 审核界面超时 | 服务端断开；客户端显示失败。 |
| 下载中断 | 临时目录清理；可重新加入从头开始（不做断点续传）。 |

---

## 8. 兼容性与移植

- 目标：Fabric Loader 0.20.x、Minecraft 26.3（主）/26.2（编译验证）、Java 25。
- 使用官方 Mojang mappings（Loom 默认），没有 Yarn 依赖。
- GUI 使用 26.x 的新 `GuiGraphicsExtractor` / `Screen.extractRenderState` 体系；移植到 1.21.x 需要改渲染层。
- 网络层使用 Fabric 的 configuration networking API；如果未来 Fabric 移除 `ConfigurationTask` 或改变 payload 注册方式，需要同步调整。
- 若要支持 Forge/NeoForge：需要在 login 阶段之前做带外清单（HTTP/状态 ping/独立通道），或者利用 NeoForge 的 configuration 自定义 payload；不能直接复用本实现的 Fabric 任务。

---

## 9. 可以继续做的方向

- 断点续传（按文件/块 offset 记录）。
- 清单中带每个文件的 SHA-256，支持“同版本不同 jar”检测。
- 可选的自动重启（需要可靠地重建启动命令，跨启动器较难）。
- 服务器端“必须同步否则拒绝进入”模式（目前客户端可以取消后继续尝试加入）。
- 更细的客户端模组选择（复选框、只同步指定文件）。
- 独立 relay/代码签名机制，用于不信任服务器的场景。
