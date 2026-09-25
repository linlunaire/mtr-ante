# MTR-ANTE

Aphrodite's Nemo's Transit Expansion (MTR-ANTE) 是一个基于 Minecraft Transit Railway（MTR）的实验性功能扩展。

`master` 默认构建 **Minecraft 26.2 / ANTE 1.1.1-beta.5**，同时保留 1.21.1 构建入口。需要配合 [MTR 社区移植版](https://github.com/linlunaire/Minecraft-Transit-Railway) 的同一 Minecraft 版本使用，仅支持 Fabric 与 NeoForge。

独立 1.21.1 基线保存在 [tag `1.1.1-1.21.1-beta.2`](https://github.com/linlunaire/mtr-ante/tree/1.1.1-1.21.1-beta.2)，对应 [MTR tag `1.21.1-3.3.2`](https://github.com/linlunaire/Minecraft-Transit-Railway/tree/1.21.1-3.3.2)。原有 `alpha` 历史保留，不做重写。

> [!WARNING]
> 此版本仍处于移植测试阶段，可能存在功能缺失、兼容性问题或其他未知问题，不建议用于重要存档。

## 构建

26.2 使用 **Java 25 / Gradle 9.5.1**；1.21.1 使用 **Java 21 / Gradle 8.14.5**。使用项目自带的 Gradle Wrapper，配置 `JAVA_HOME` 或传入 `-JavaHome` 选择对应 JDK。

先将 MTR 检出到相邻的 `Minecraft-Transit-Railway-3.x.x` 目录，构建对应版本，再构建 ANTE。26.2 的已验证 MTR 源码基线为 [`fa2a24a39`](https://github.com/linlunaire/Minecraft-Transit-Railway/commit/fa2a24a39b978a1c81c64319c5d16da837ede250)；1.21.1 使用上述 MTR tag。GitHub Actions 固定这两个提交，先构建依赖再构建 ANTE，不自动发布到 Modrinth 或 GitHub Pages。

### Windows

```powershell
.\gradlew.bat build --console=plain                 # 默认 26.2
.\gradlew.bat build '-Version=1.21.1' --console=plain # 指定 1.21.1
```

Linux / macOS 使用 `./gradlew build -Version=26.2` 或 `./gradlew build -Version=1.21.1`。

26.2 产物位于 `build/release/`，1.21.1 产物位于 `build/`。两版目录与 JDK 独立；编译、兼容性检查通过不等于游戏及多人服务器验证通过，部署前请备份存档并测试。
