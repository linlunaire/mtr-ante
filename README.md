# YLTE（Yanling Transit Expansion）

YLTE 是 ANTE 的 Minecraft 1.21.1 维护版，为 YanlingMTR / MTR 提供自定义模型、脚本渲染、装饰物和轨道工具。当前版本为 `1.1.2`，支持 Fabric 与 NeoForge。

本分支使用 Java。旧 Java 26.2 版在 [26.2](https://github.com/linlunaire/mtr-ante/tree/26.2)，独立 Kotlin 版在 [master](https://github.com/linlunaire/mtr-ante/tree/master)；后续 26.2 开发已迁入 [YanlingMTR](https://github.com/linlunaire/YanlingMTR/tree/master/ante)。

## 安装

使用 Java 21，推荐搭配 [YanlingMTR 1.0.3](https://github.com/linlunaire/YanlingMTR/tree/1.21.1)。加载器接受 MTR `1.21.1-3.3.4` 及以上兼容版本。Fabric 需要 Fabric API 和 Architectury API；NeoForge 需要 Architectury API。

用 YLTE JAR 替换原 ANTE，不要同时安装。模组保留 `mtrsteamloco` 标识、资源命名空间、Java／脚本接口、`ANTE_FLAG`、`ANTE-Data` 存档键及网络协议，既有资源包组名和动态资源包 ID 不变。

客户端与服务端应一起更新。升级前备份存档、配置和资源包，并先在副本中检查列车与脚本。1.21.1 和 26.2 的模组文件不能混用。

## 1.1.2 更新

限制轨道异步构建和待上传积压，补全原生缓冲区异常清理、区块卸载取消及 VAO 释放。原有资源包、脚本接口和协议 11 保持不变。崩溃证据、诊断参数和同世界对照方法见 [轨道内存修复](docs/rail-memory-2026-10-14.md)；自动检查不替代连续传送与实际显卡渲染验收。

### 1.1.1

修复中文搜索缺少拼音库的问题，保留全拼和首字母搜索；修复字符串配置编码、直接节点编辑、列车配置 ID 顺序、路线工具和手持装饰物的修改保存，并补充服务端编辑权限与目标检查。

新写入的字符串配置可能使用 `YLTE-SM1:` 编码，旧版无法完整读取；此前已损坏或丢失的数据仍需从备份恢复。旧版本修复和定向测试命令见 [维护记录](docs/maintenance-history.md)，原生客户端检查见 [1.21.1 维护检查](https://github.com/linlunaire/YanlingMTR/blob/1.21.1/docs/health-review-2026-10-11.md)。

## 构建

使用 JDK 21，先构建相邻的 `Minecraft-Transit-Railway-1.21.1`，再在本仓库运行：

```powershell
.\gradlew.bat build --console=plain
```

自定义 MTR 路径使用 `-PmtrProjectDir=<路径>`。构建会检查 Minecraft 版本和开发 JAR，并在兼容性检查通过后导出：

- `build/release/YLTE-fabric-1.1.2-1.21.1.jar`
- `build/release/YLTE-neoforge-1.1.2-1.21.1.jar`

CI 固定依赖提交，不自动发布。每次分发维护更新时递增 `gradle.properties` 的 `mod_version` 并重新构建。历史产物位于 `build/release/archive/`，该目录会被 `clean` 清除，不是永久备份。Modrinth 手动上传资料保存在 `modrinth/`，YLTE 使用独立项目。

## 上游与许可证

基于 Aphrodite281 的 [ANTE](https://github.com/aphrodite281/mtr-ante) 和 Zbx1425 的 [Nemo's Transit Expansion](https://github.com/zbx1425/mtr-nte)，保留 [MIT License](LICENSE) 与原作者署名。

拼音库及词典见 [Pinyin-notices.txt](common/src/main/resources/licenses/Pinyin-notices.txt)，脚本运行库见 [Graal-runtime-notices.txt](common/src/main/resources/licenses/Graal-runtime-notices.txt)。
