# Changelog

## 1.4.0

### 新增

- 内容缓存：客户端按 SHA-256 缓存模组文件，命中时直接复用。
- HTTP Range 断点续传：NeoForge / Forge 版支持从 `.partial` 文件继续下载。
- NeoForge 服主面板：
  - `/modsync/panel`
  - `/modsync/api/status`
- 同版本内容校验：版本号相同但 jar 的 SHA-256 不同时，强制替换。

### 修复

- 服务端换了文件名但 mod id/version 未变时，旧 jar 不会被留下，避免重复模组导致加载错误。
- 断点文件损坏导致 HTTP 416 时，自动删除并重新完整下载。
- Forge 1.21.1 面板、缓存和 Range 代码已同步。

### 平台

- Fabric 26.3
- Fabric 1.21.1
- Forge 1.21.1
- NeoForge 1.21.1
- NeoForge 26.3

Forge 26.3 暂未构建：当前 ForgeGradle 工具链无法解析对应 Forge 版本。
