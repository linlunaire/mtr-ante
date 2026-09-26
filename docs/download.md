# 下载

**本模组还在开发测试阶段，存在缺陷在所难免，如有问题请报告。**



**使用前请先阅读 [新增特性使用指引](feature.md) ！**



## ……下载

您可以去往[Github](https://github.com/aphrodite281/mtr-ante/releases) 或 [Modrinth](https://modrinth.com/mod/mtr-ante/) 下载最新版本的模组。

当前移植测试版要求：

- Minecraft 1.21.1
- MTR `1.21.1-3.3.0-beta-1` 或更高版本
- Fabric Loader + Fabric API，或 NeoForge `21.1.248` 或更高版本
- Architectury API 13 或更高版本

本分支仅提供 Fabric 与 NeoForge 的 Minecraft 1.21.1 构建；不要将旧 Forge 版本与 NeoForge 版本混用。



## 连接到服务器

从 0.2.0 版本起，NTE 不再仅需客户端安装。**安装 NTE 后，只可连接到已安装了相同版本 NTE 的服务器**。否则可能出现方块与物品缺失、混乱等情况。

不论如何，在服务器上使用 NTE 自带的或以资源包导入的含 OBJ 模型的列车时，其他没有安装 NTE 和对应资源包的玩家将不能看到这列车。

如果您想在未安装 NTE 的服务器上继续使用立体轨道等功能，您可将 NTE 的 JAR 文件（名称形如 `MTR-NTE-fabric-1.19.2-0.2.0.jar`）重命名，添加 `-client` 使其名称形如 `MTR-NTE-fabric-1.19.2-0.2.0-client.jar`。NTE 将自动检测到您的意图并进行调整。修改完成并重启游戏后，您即可在任意服务器中使用立体轨道、列车隐藏等功能，不需要在服务器一侧额外安装任何组件；但此时您无法使用发车铃和装饰物件功能。**此途径只用于连接到未安装 NTE 的服务器。**如需单人游戏或连接有 NTE 的服务器，请再把它改回去。
