# dist · 成品目录

> 最新发布：**v1.3.0** — <https://github.com/gausan-hj/ModSync/releases/tag/v1.3.0>
> 本目录同时保留历史 0.1.0 构建，方便对照与回退。

按加载器和 Minecraft 版本分开放，jar 不要跨加载器混装。

```text
dist/
├─ fabric/
│  ├─ 26.3/modsync-26.3-1.3.0.jar
│  ├─ 26.2/modsync-26.2-0.1.0.jar
│  ├─ 26.1.2/modsync-26.1.2-0.1.0.jar
│  ├─ 26.1.1/modsync-26.1.1-0.1.0.jar
│  └─ 26.1/modsync-26.1-0.1.0.jar
├─ forge/
│  ├─ 1.21.1/modsync-forge-1.21.1-0.1.0.jar
│  └─ 1.20.1/modsync-forge-1.20.1-0.1.0.jar   （legacy，低于 1.21.0）
└─ neoforge/
   ├─ 26.3/modsync-neoforge-26.3-0.1.0.jar
   ├─ 26.2/modsync-neoforge-26.2-0.1.0.jar
   ├─ 26.1.2/modsync-neoforge-26.1.2-0.1.0.jar
   ├─ 1.21.11/modsync-neoforge-1.21.11-0.1.0.jar
   └─ 1.21.1/modsync-neoforge-1.21.1-0.1.0.jar
```

目标矩阵是每个加载器：

- 最低：Minecraft **1.21.0**
- 最新：Minecraft **26.3**

1.21.0 和 26.3 的 API 差异很大，必须分别构建，不能一个 jar 通吃。当前 Fabric 26.1–26.3、Forge 1.21.1、NeoForge 1.21.1/1.21.11/26.1.2/26.2/26.3 已构建；Fabric 1.21.x、Forge 1.21.11/26.x 仍在移植中。

各加载器说明：

- [Fabric](../fabric/README.md)
- [Forge](../forge/README.md)
- [NeoForge](../neoforge/README.md)
