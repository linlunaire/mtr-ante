# MTR-ANTE

Aphrodite's Nemo's Transit Expansion (MTR-ANTE) 是一个基于 Minecraft Transit Railway（MTR）的实验性功能扩展。

本目录继续维护 **Minecraft 1.21.1 / ANTE 1.1.1-beta.3**，需要配合 [MTR 1.21.1](https://github.com/linlunaire/Minecraft-Transit-Railway/tree/1.21.1) **3.3.3 或更高版本**使用，仅支持 Fabric 与 NeoForge，与 26.2、Kotlin 重构目录分开开发。

> [!WARNING]
> 此版本仍处于移植测试阶段，可能存在功能缺失、兼容性问题或其他未知问题，不建议用于重要存档。

## 构建

需要 **Java 21**，并建议使用项目自带的 Gradle Wrapper 进行构建。

### Windows

```powershell
.\gradlew.bat build --console=plain
```

先构建相邻的 `Minecraft-Transit-Railway-1.21.1`，再构建本项目。自定义路径使用 `-PmtrProjectDir=路径`；构建会检查 Minecraft 版本和三个开发 JAR，避免误用 26.2 / Kotlin 项目。

两个加载器的产物统一位于 `build/release/`。实际 Sponge Mixin 轨道构造与序列化回归通过后才导出产物，构建失败不会再通过 finalizer 复制旧 JAR。CI 仍固定历史 MTR tag 作为基础兼容性检查，不自动发布。

每次分发修复或优化版本时，递增 `gradle.properties` 中的 `mod_version`，重新构建以同步文件名、模组元数据和启动日志版本；MTR 的最低版本由同文件中的 `mtr_min_version` 指定。

定向验证：`gradlew :fabric:checkRailCompatibility :neoforge:checkRailCompatibility`。此检查覆盖新建轨道、MessagePack、旧 NBT 和网络往返，不替代多人联机和渲染实测。
