# CurseForge 发布清单（ModSync）

## 0. 素材

| 项目 | 位置 | 说明 |
|---|---|---|
| 26.3 主文件 | `build/libs/modsync-26.3-0.1.0.jar` | 123,720 字节，SHA-256 `29F6C57C…38D392` |
| 26.2 备用文件 | `build/libs/modsync-26.2-0.1.0.jar` | 123,716 字节，单独上传，勾 26.2 |
| 项目头像 | `docs/curseforge/avatar-400.png` | 必须正好 400×400 |
| 项目描述 | `docs/curseforge/description.md` | 英文在前，可直接粘贴 |
| 截图 | `docs/review.png`、`docs/result.png` | 传到 Gallery |
| 许可证 | MIT | License 下拉选 MIT |
| 源码地址 | 建议先建 GitHub 仓库 | 填在项目侧栏 Source |

## 1. 账号

1. 打开 <https://authors.curseforge.com/>
2. 用 Google / Discord / GitHub / Twitch 登录（Overwolf 账号）
3. 补全作者资料，勾选同意 Mod Author Terms

## 2. 建项目

<https://authors.curseforge.com/#/projects/create/choose-game> → 选 **Minecraft**

| 字段 | 填什么 |
|---|---|
| Name | `ModSync`（不要带 Minecraft / Fabric / 版本号） |
| Summary | Shares the host's mod list during the configuration phase and downloads only the missing jars over the same Minecraft connection. |
| Class | Mods |
| Main category | Server Utility |
| Additional categories | Utility & QoL |
| Avatar | `avatar-400.png`（必须 400×400，不能纯色） |
| Description | 粘贴 `description.md` 全文 |
| License | MIT |
| Third-party downloads | 想让 Prism / 其他启动器也能拉取就允许；只影响收益统计 |

点 **Create**。

## 3. 上传文件

项目 → **Files** → **Add File**

| 字段 | 填什么 |
|---|---|
| File | `modsync-26.3-0.1.0.jar` |
| Display name | `ModSync 0.1.0 (Fabric 26.3)` |
| Release type | Release |
| Release options | 审核通过后自动发布 |
| Environment | Client and Server |
| Minecraft versions | 26.3 |
| Mod loader | Fabric |
| Java version | 25 |
| Changelog | Initial release — configuration-phase manifest, chunked transfer with SHA-256, staged install with backup, `/modsync` commands, English/Chinese UI. |
| Related projects | 把 **Fabric API** 设为 Required Dependency |

提交后状态是 **Under Review**。

## 4. 审核

- 一般几分钟到 3 个工作日。
- 被要求修改就按提示改完重新提交；**不要删项目重传**。
- 26.2 的 jar 等 26.3 通过后再走一遍 Add File，游戏版本勾 26.2。

## 5. 通过之后

- 项目页 Gallery 补两张截图（`review.png`、`result.png`）。
- 侧栏补 Source / Issues 链接。
- 可选：同一份文件再发一份到 Modrinth（审核更宽松，Fabric 用户多）。

## 容易被打回的点

1. 名字里出现游戏名、加载器名、版本号 → `ModSync` 没问题。
2. 描述太短、只有图片、或只有中文 → 用 `description.md`。
3. 头像不是 400×400 或纯色 → 用生成好的 `avatar-400.png`。
4. 文件勾的版本/加载器和实际不符 → 26.3 的 jar 只勾 26.3 + Fabric。
5. 功能涉及"从服务器下载 jar" → 描述里已写明：默认不自动安装、SHA-256 校验、只写 `mods/`、大小上限；审核问起就按这段解释。
6. 没有 Release 类型文件的项目不会同步到 CurseForge App → 第一个文件必须是 Release。

## 可选：以后用 Gradle 自动发布

装 [`mc-publish`](https://github.com/Kir-Antipov/mc-publish) 或 `cursegradle`，在 `build.gradle` 里配 CurseForge 项目 ID + API Token（Authors Console → API Tokens），以后 `./gradlew build publishMods` 就能同时发 CurseForge / Modrinth / GitHub Release。
