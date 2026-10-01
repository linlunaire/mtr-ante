# YLTE — Yanling Transit Expansion

YLTE（Yanling Transit Expansion）是原 ANTE 的 **Minecraft 1.21.1 独立维护分支**，为 YanlingMTR / MTR 提供自定义模型、脚本渲染、装饰物及轨道工具。基于 Aphrodite's Nemo's Transit Expansion 与 Nemo's Transit Expansion，保留原作者 Zbx1425、Aphrodite281 的署名和 MIT 许可证。

本次更名发布为 **1.1.1-1.21.1-beta.6**，延续原 ANTE 版本序列。使用 Java 21，支持 Fabric 与 NeoForge，推荐配合 **YanlingMTR 1.21.1 / 1.0.0**；加载器仍接受 MTR `1.21.1-3.3.4` 及以上兼容版本。安装对应加载器的 Architectury API，Fabric 还需要 Fabric API。

**26.2 的新版 ANTE 已合并到 YanlingMTR 的源码仓库和统一构建中**，仍按本体和可选 ANTE 模块输出不同 JAR；这里的 YLTE 是旧版独立分支，不用于 26.2。仓库 URL 和历史标签不变。

更名不改变 `mtrsteamloco` Mod ID、资源命名空间、Java/脚本接口、`ANTE_FLAG`、`ANTE-Data` 存档键或网络协议。升级时用 YLTE 替换原 ANTE JAR，不能同时安装。既有资源包组名和动态资源包 ID 保持不变。

> [!WARNING]
> 此版本仍处于移植测试阶段，可能存在功能缺失、兼容性问题或其他未知问题，不建议用于重要存档。

## 构建

需要 **Java 21**，并建议使用项目自带的 Gradle Wrapper 进行构建。

### Windows

```powershell
.\gradlew.bat build --console=plain
```

先构建相邻的 `Minecraft-Transit-Railway-1.21.1`，再构建本项目。自定义路径使用 `-PmtrProjectDir=路径`；构建会检查 Minecraft 版本和三个开发 JAR，避免误用 26.2 / Kotlin 项目。

两个加载器的产物统一位于 `build/release/`。实际 Sponge Mixin 轨道构造、序列化及网络注册回归通过后才导出产物，构建失败不会再通过 finalizer 复制旧 JAR。CI 固定包含专服网络修复的 MTR 提交，不自动发布。

产物为 `YLTE-fabric-1.1.1-1.21.1-beta.6.jar` 和 `YLTE-neoforge-1.1.1-1.21.1-beta.6.jar`。发布前校验实际元数据、界面名称和许可；旧的同加载器 1.21.1 ANTE/YLTE 包可恢复地保存在 `build/release/archive/`，该目录会被 `clean` 清除，不是永久备份。Modrinth 手动上传字段和介绍保存在 `modrinth/`，YLTE 使用独立项目。

每次分发修复或优化版本时，递增 `gradle.properties` 中的 `mod_version`，重新构建以同步文件名、模组元数据和启动日志版本；MTR 的最低版本由同文件中的 `mtr_min_version` 指定。

定向验证：`gradlew :fabric:checkRailCompatibility :neoforge:checkRailCompatibility`。此检查覆盖新建轨道、MessagePack、旧 NBT 和网络往返，不替代多人联机和渲染实测。

beta.4 补齐专用服务器的版本检查、编辑界面和路径创建三个 S2C 通道注册，防止 Architectury 在发送数据时因缺少编码器崩溃；不改变包 ID 和数据格式。`gradlew :common:checkNetworkCompatibility` 覆盖专服/客户端注册、真实编码器字节往返和缺少编码器的失败对照，不启动实际联机服务器。

beta.5 修复 NeoForge 发布包遗漏 access widener 到 access transformer 的转换，导致 `BlazeRenderType` 读取私有 `RenderType.CompositeRenderType.state` 时发生 `IllegalAccessError`。打包时将共有权限规则转换为 `META-INF/accesstransformer.cfg`，保持原有三角形模式和原版渲染状态。`gradlew :neoforge:checkRenderTypeAccess` 使用最终 JAR 中的真实工厂与 NeoForge AT 引擎，先还原开发环境被拓宽的私有字段，再验证不透明、半透明及两种信标光束路径；该检查已接入 `build`，可用 `-PrenderTestJar=旧包路径` 验证失败对照，仍需在游戏中验证光影和列车渲染。
