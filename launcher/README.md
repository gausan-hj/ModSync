# ModSync Launcher（原型）

本地启动器/模组管理器，用来验证：

- 实例管理；
- Modrinth 模组搜索；
- 自动安装前置依赖；
- 启用/禁用/删除模组；
- 从 ModSync 服务器同步模组清单。

## 启动

```powershell
cd launcher
node server.mjs
```

打开：

```text
http://127.0.0.1:17321
```

## 已实现

- 创建 Fabric / NeoForge / Forge 实例；
- 按实例的加载器和 Minecraft 版本搜索 Modrinth；
- 下载模组并递归安装 required 前置；
- SHA-512 校验 Modrinth 文件；
- 启用/禁用 `.jar` 与 `.jar.disabled`；
- 从 `http://房主:同步端口` 读取 ModSync manifest；
- 下载服务器文件并校验 SHA-256；
- 打开实例目录；
- 配置自定义启动命令。

## 当前限制

这是启动器原型，不是完整 Minecraft 启动器：

- 还没有微软登录；
- 还没有 Minecraft 版本 JSON/assets/libraries 下载；
- “启动游戏”只执行实例设置里的自定义命令；
- 还不能直接调用 Modrinth App / Prism 启动实例。

完整启动需要补：

```text
Microsoft OAuth
版本清单
assets / libraries / natives
Java 选择
JVM 参数
游戏进程管理
```

## 下一步

优先把 Modrinth App 或 Prism 的实例作为“外部启动器”，本工具只负责模组和同步；  
之后再决定是否自己实现完整启动流程。
