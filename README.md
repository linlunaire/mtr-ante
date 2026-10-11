# MTR-ANTE

Aphrodite's Nemo's Transit Expansion (MTR-ANTE) 是一个基于 Minecraft Transit Railway（MTR）的实验性功能扩展。

此 tag 为 **Minecraft 1.21.1 / ANTE 1.1.1-beta.2 基线**，需要配合 [MTR 1.21.1-3.3.2](https://github.com/linlunaire/Minecraft-Transit-Railway/tree/1.21.1-3.3.2) 使用，仅支持 Fabric 与 NeoForge。后续 26.2 工作在 master 延续。

> [!WARNING]
> 此版本仍处于移植测试阶段，可能存在功能缺失、兼容性问题或其他未知问题，不建议用于重要存档。

## 构建

需要 **Java 21**，并建议使用项目自带的 Gradle Wrapper 进行构建。

### Windows

```powershell
.\gradlew.bat build --console=plain
```

先将上述 MTR tag 检出到相邻的 `Minecraft-Transit-Railway-3.x.x` 目录并构建，再构建本项目。GitHub Actions 已固定该 MTR tag，自动按此顺序生成两个加载器产物；不会自动发布到 Modrinth 或 GitHub Pages。
