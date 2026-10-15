# YLTE 1.21.1 维护记录

1.1.2 修复轨道异步构建/上传积压、原生缓冲区异常清理、已卸载区块的旧任务及 VAO 所有权。新增故障注入和取消/预算检查接入 `:common:check`，协议及存档格式不变。证据与实际游戏验收边界见 [轨道内存修复](rail-memory-2026-10-14.md)。

定向验证：`gradlew :fabric:checkRailCompatibility :neoforge:checkRailCompatibility`。此检查覆盖新建轨道、MessagePack、旧 NBT 和网络往返，不替代多人联机和渲染实测。

beta.4 补齐专用服务器的版本检查、编辑界面和路径创建三个 S2C 通道注册，防止 Architectury 在发送数据时因缺少编码器崩溃；不改变包 ID 和数据格式。`gradlew :common:checkNetworkCompatibility` 覆盖专服/客户端注册、真实编码器字节往返和缺少编码器的失败对照，不启动实际联机服务器。

beta.5 修复 NeoForge 发布包遗漏 access widener 到 access transformer 的转换，导致 `BlazeRenderType` 读取私有 `RenderType.CompositeRenderType.state` 时发生 `IllegalAccessError`。打包时将共有权限规则转换为 `META-INF/accesstransformer.cfg`，保持原有三角形模式和原版渲染状态。`gradlew :neoforge:checkRenderTypeAccess` 使用最终 JAR 中的真实工厂与 NeoForge AT 引擎，先还原开发环境被拓宽的私有字段，再验证不透明、半透明及两种信标光束路径；该检查已接入 `build`，可用 `-PrenderTestJar=旧包路径` 验证失败对照，仍需在游戏中验证光影和列车渲染。

beta.7 修复刷子轨道长度/半径文字的 X 轴镜像，使字面朝向摄像机，不再被背面剔除；修复纵曲线编辑时物品编码使用空注册表导致的崩溃，并将编辑后的 CustomData 写回刷子后再同步。包 ID、数据格式和协议 11 保持不变。客户端与服务端都应更新 YLTE；只更新客户端不能修复旧服务端的空注册表解码。YanlingMTR 1.0.1 本体无需替换。

`gradlew :neoforge:checkBrushPacketCompatibility` 使用发布 JAR 的真实刷子更新与收发方法、原版物品组件编码器，覆盖主手/副手、启用/禁用、其他组件保留和缓冲区释放；`-PbrushTestJar=旧包路径 -PbrushTestMode=packet` 或 `brush` 可验证失败对照。这个无界面检查不替代实机渲染；独立原生客户端回归见 `tests/brush-runtime/README.md`。

beta.8 补齐发布包遗漏的 GraalJS、Polyglot、Truffle 运行库，修复脚本追加包重载时 `NoClassDefFoundError: org/graalvm/polyglot/io/FileSystem` 导致资源包被移除的问题。合并服务注册、保留多版本 JAR 标记，并隔离 GraalVM 系统模块已有的 SDK 包；脚本接口和协议不变，不需要额外安装 GraalJS JAR。

`gradlew :neoforge:checkScriptLoaderStartup :fabric:checkScriptRuntimeArtifact :neoforge:checkScriptRuntimeArtifact` 使用最终发布 JAR 验证真实脚本加载入口、旧式 Java 导入、服务注册和模块边界，不借用 Gradle 的 Graal 依赖。`-PscriptTestJar=旧包路径` 可复现旧包缺类；`"-PscriptPacks=包1.zip;包2.zip"` 额外解析追加包脚本，仅验证语法，不代表完整游戏渲染或音效实测。
