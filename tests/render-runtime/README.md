# 1.21.1 实际渲染回归

这个独立 NeoForge 客户端加载指定的 MTR/YLTE JAR，在 `build/diagnostics/render-runtime` 下创建临时平坦世界，不访问日常游戏实例或存档。运行时需要 Java 21、可用 OpenGL 显卡和 Gradle 8.14.x。

从此目录运行 `gradle runClient`，传入本机文件的绝对路径：

```text
-PmtrTestJar=<MTR NeoForge JAR>
-PylteTestJar=<YLTE NeoForge JAR>
-PprobeName=<独立测试名称>
-PprobeMode=shader
-PirisTestJar=<Iris NeoForge JAR>
-PsodiumTestJar=<对应 Sodium NeoForge JAR>
-PclothConfigTestJar=<Cloth Config NeoForge JAR>
-PshaderPack=<启用阴影的光影 ZIP>
```

- `shader`：从相反的两个相机方向，统计真实轨道绘制的 `GL_SAMPLES_PASSED`，要求主画面和阴影均产生像素；不注入矩阵修正。
- `plain`：关闭光影，检查同样的两个方向，保护原有自定义渲染路径；可以不提供 Iris/Sodium。
- `node`：关闭光影，提供 Sodium/Cloth Config，可加 `-PmoreCullingTestJar=<More Culling JAR>`。对全部 16 种节点状态检查原版和 Sodium 的实际邻面剔除结果，并检查选择框、空碰撞、空遮挡和流水不替换节点。安装 More Culling 时依次开启、关闭、再开启方块面剔除，要求下方石头顶面一直保留。

更换可选 mod 组合时使用新的 `probeName`，防止上一次客户端目录中的 mod 污染结果。各模式的日志位于 `client-<probeName>/logs/latest.log`，截图位于 `evidence-<probeName>`。Gradle 只有在日志出现 `RENDER_RUNTIME_PASS` 且没有失败标记时才成功。光影包和第三方 mod 不随测试分发；需要由调用者提供彼此兼容的版本。
