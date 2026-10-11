# ANTE（Kotlin 版）

ANTE 为 MTR 提供自定义列车和轨道模型、JavaScript 渲染、装饰物与轨道编辑工具。本分支保留 Minecraft 26.2 的独立 Kotlin 版本，支持 Fabric 与 NeoForge。

| 分支 | 版本 |
| --- | --- |
| [master](https://github.com/linlunaire/mtr-ante/tree/master) | Minecraft 26.2，独立 Kotlin 版 |
| [26.2](https://github.com/linlunaire/mtr-ante/tree/26.2) | Minecraft 26.2，旧 Java 版 |
| [1.21.1](https://github.com/linlunaire/mtr-ante/tree/1.21.1) | Minecraft 1.21.1，YLTE Java 维护版 |

后续的 26.2 开发已迁入 [YanlingMTR 的 ante 目录](https://github.com/linlunaire/Minecraft-Transit-Railway/tree/master/ante)，随本体统一构建。本仓库的 `master` 保留合并前的独立版本 `1.2.0-26.2-kotlin.4`，便于重建与兼容性核对。

## 安装

使用 Java 25，安装相同加载器的 ANTE、MTR 26.2 Kotlin 版和 [Kotlin LunaCore 0.2.1](https://github.com/linlunaire/Kotlin-LunaCore)。MTR 最低兼容版本为 `26.2-3.4.0-kotlin.4`，不能使用 Java 维护版或 MTR 4。

Fabric 需要 Fabric API 和 Architectury API；NeoForge 需要 Architectury API。脚本运行库和配置库已包含在模组中，Kotlin 运行库由 LunaCore 提供。使用自定义列车和模型时，还需安装对应的资源包。

升级时替换原 ANTE JAR，不要同时安装两份。模组保留 `mtrsteamloco` 标识、资源命名空间、脚本接口和存档键。客户端和服务端应使用同一版本；升级前备份，并在存档副本中测试线路、列车和脚本。

## 构建

使用 JDK 25，将 Kotlin LunaCore、MTR 和本仓库放在同一级目录。先构建 LunaCore，再构建 MTR，最后在本仓库运行：

```powershell
.\gradlew.bat build
```

Linux/macOS 使用 `./gradlew build`。精确的依赖提交见 [构建工作流](.github/workflows/build.yml)；MTR 使用 [独立附属兼容快照](https://github.com/linlunaire/Minecraft-Transit-Railway/tree/compat/jcm-26.2-kotlin.4)。自定义目录使用 `-PmtrProjectDir=<路径>` 和 `-PtransitCoreProjectDir=<路径>`。

两个加载器的 JAR 位于 `build/release/`。当前分支仍是预览版，构建检查不代表已有存档、多人联机或全部脚本渲染已经验证。具体检查见 [迁移记录](docs/kotlin-migration.md)。

## 上游与许可证

基于 Aphrodite281 的 [Aphrodite's Nemo's Transit Expansion](https://github.com/aphrodite281/mtr-ante) 和 Zbx1425 的 [Nemo's Transit Expansion](https://github.com/zbx1425/mtr-nte)。

代码采用 [MIT License](LICENSE)。模型、纹理、声音等内容保留各自许可证和 [原作者署名](docs/feature.md)。
