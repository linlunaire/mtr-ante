# 原生刷子回归（可选）

这是仅用于测试的独立 NeoForge 项目，不进入 YLTE 发布 JAR，也不操作桌面输入。使用根项目的 Gradle 8.14.5 Wrapper 和 Java 21，需要可用的 OpenGL 设备；不能在无 GPU 的 CI 上冒充渲染验证。

先构建 YanlingMTR 1.0.3，再运行 YLTE 根项目的 `gradlew.bat build`，然后从 YLTE 根项目运行：

```powershell
./gradlew.bat -p tests/brush-runtime runClient --console=plain --no-daemon
```

程序在 `build/diagnostics/brush-runtime/client-candidate` 新建临时测试世界，通过游戏 API 放置和登记双向轨道、持刷瞄准轨道，然后读取实际帧缓冲。断言 L/R 字面朝向和对应区域的明亮中性像素，点击真实 Cloth 纵曲线开关、执行产生崩溃的 tooltip 回调，并检查刷子组件、服务端接收和服务端轨道半径。结束时正常退出，截图位于 `evidence-candidate`；无 PASS 标记、FAIL 标记或服务端任务错误都会令命令失败。

`"-PprobeJar=绝对路径" -PprobeName=old` 可测试旧包：beta.6 应因文字背向摄像机失败。加 `-PprobeMode=brush` 仅跳过文字断言，继续到真实编辑开关，beta.6 应因空物品注册表失败。`"-PmtrTestJar=绝对路径"` 可指定本体包；`"-PprobeNeoForgeVersion=21.1.252"` 可使用崩溃报告对应的加载器。Windows 调用 bat 时将带路径或点号的属性整个加引号。

客户端窗口在测试中隐藏，不替换用户的 mods、配置或存档。测试采用独立简化世界、默认资源和 OpenGL，不代表完整 Rapid 整合包、光影或远端多人服务器验证。构建依赖下载和 NeoForge 启动画面仍可能短暂显示；所有世界、日志和图片都在 build 下，clean 会清除它们。

## CTS 追加包检查（可选）

将修复后的 ZIP 复制到对应 `client-<probeName>/resourcepacks/`，在该独立目录的 `options.txt` 中启用；不要改 Rapid 的目录。`-PprobeCtsPacks=true` 在创建测试世界前检查 57 个已登记的列车（其中 55 个真实脚本渲染器）、35 个非空轨道模型和 17 个已上传的装饰模型，之后继续刷子回归。仅测试轨道包时用 `-PprobeCtsPacks=track`，预期列车数为 0；缺失或空模型、资源重载后追加包被移除都会失败。

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/graalvm-jdk-21.0.7+8.1'
./gradlew.bat -p tests/brush-runtime runClient '-PprobeName=cts-complete' '-PprobeCtsPacks=true' '-PprobeNeoForgeVersion=21.1.252' '-PmtrTestJar=G:/mc/.minecraft/versions/Rapid/mods/YanlingMTR-neoforge-1.21.1-1.0.2.jar' --offline --console=plain --no-daemon
```

路径按本机调整，保持 Java 21。2026-10-04 原广州包的 `dsg0IIs.obj` 大写路径使该检查报 `FileNotFoundException: mtrsteamloco:rails/dsg0iis.obj`；修复只将 OBJ/MTL 文件名规范为小写，载荷字节不变。轨道包另从所提供的广州包补齐短第三轨 OBJ、MTL 和 `hnt.png`，不替换已有纹理、模型或 IDs。

两款 `gzmtr_b8_1421` / `gzmtr_b8_1421_express` 在原包中声明了不存在的 `gzmtr_b8_new` 资源，因此明确作为缺失源文件列出，不计入 57 个有效列车，不移除定义或伪造替代车型。检测到源模型补齐时检查会失败，要求移除该例外并实测它们。此检查不覆盖所有列车运行帧、LCD 显示和缺失音频的恢复。
