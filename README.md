# ANTE 26.2（Java 版）

本分支保留 ANTE 的 Minecraft 26.2 Java 版本，当前版本为 `1.1.2-26.2`，支持 Fabric 与 NeoForge。它为 MTR 3 提供自定义列车、轨道模型、脚本渲染和装饰物，不能独立运行，也不支持 MTR 4。

Kotlin 版在 [master](https://github.com/linlunaire/mtr-ante/tree/master)，Minecraft 1.21.1 的 YLTE 在 [1.21.1](https://github.com/linlunaire/mtr-ante/tree/1.21.1)。

## 安装

使用 Java 25，搭配 [MTR 的 Java 26.2 分支](https://github.com/linlunaire/Minecraft-Transit-Railway/tree/26.2)。Fabric 需要 Fabric API 和 Architectury API；NeoForge 需要 Architectury API。本分支不需要 Kotlin LunaCore。

脚本运行库和配置库已包含在模组中。使用自定义列车和模型时，还需安装对应的资源包。客户端和服务端应使用同一版本；升级前备份存档、配置和资源包，并先在副本中检查线路与脚本。

## 构建

使用 JDK 25，将 MTR 与本仓库放在同一级目录：

```text
workspace/
├── Minecraft-Transit-Railway-3.x.x/
└── mtr-ante/
```

先构建 MTR，再在本仓库运行：

```powershell
.\gradlew.bat build
```

Linux/macOS 使用 `./gradlew build`。自定义 MTR 路径使用 `-PmtrProjectDir=<路径>`。构建通过后，两个加载器的 JAR 位于 `build/release/`。

依赖版本见 [gradle.properties](gradle.properties)，精确的 MTR 提交见 [构建工作流](.github/workflows/build.yml)。每次分发维护版本时递增 `mod_version` 并重新构建。

## 上游与许可证

基于 Aphrodite281 的 [ANTE](https://github.com/aphrodite281/mtr-ante) 和 Zbx1425 的 [Nemo's Transit Expansion](https://github.com/zbx1425/mtr-nte)。

代码采用 [MIT License](LICENSE)。模型、纹理、声音等内容保留各自许可证和 [原作者署名](docs/feature.md)。
