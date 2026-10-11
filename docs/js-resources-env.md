# JavaScript 资源与环境

(A)NTE 使用 Rhino 作为 JavaScript 引擎，它是一个基于 Java 的 JavaScript 解释器，您可以使用 Rhnio 的特性。同时 (A)NTE 也为环境提供了一些独有的类或方法。

这里是一些方法，用于在 JavaScript 脚本中随意控制载入或者获取资源包内的资源。

写在函数以外的顶层空间内的代码会在资源包加载时运行，可用于加载模型、贴图等资源。推荐将不需要每列车都不同的资源（如模型等）存储在全局变量，避免相同内容每列车都加载一份带来过多内存占用。


## 环境

### 变量
(A)NTE 为 JavaScript 环境提供了以下环境变量：

#### CONFIG_INFO
- `CONFIG_INFO` : 使用此脚本的Json块的配置信息
例如：
您在mtrsteamloco:eyecandies/script.json中定义了如下配置：
```json
{
    "qssnn": {
        "name": "obj.ante.script",
        "scriptFile": "mtrsteamloco:eyecandies/ante/script.js",
        "qssnn": true,
        "configs": ["123", 345, true, 33],
        "objects": {
            "obj1": {
                "name": "obj1",
                "type": "model",
                "path": "mtrsteamloco:eyecandies/obj1.obj"
            }
            "obj2": {
                "name": "obj2",
                "type": "texture",
                "path": "mtrsteamloco:eyecandies/obj2.png"
            }
        }
    }
}
```

那么在脚本中，您可以通过 `CONFIG_INFO` 访问到配置信息，例如：

```javascript
print(CONFIG_INFO.name); // "obj.ante.script"
print(CONFIG_INFO.configs[0]); // "123"
print(CONFIG_INFO.objects.obj1.path); // "mtrsteamloco:eyecandies/obj1.obj"
print(CONFIG_INFO.key); // qssnn
```

您可以参考 [RailwayAesthetics-Future](https://github.com/aphrodite281/RailwayAesthetics-Future) 中 [JavaScript脚本](https://github.com/aphrodite281/RailwayAesthetics-Future/blob/main/assets/mtrsteamloco/eyecandies/signal_lighta/common.js#4) 和 [Json配置](https://github.com/aphrodite281/RailwayAesthetics-Future/blob/main/assets/mtrsteamloco/eyecandies/signal_lighta.json) 的搭配。

### 方法
(A)NTE 为 JavaScript 环境额外提供了以下方法：

| 方法名 | 说明 |
| --- | --- |
| [`include`](#加载代码与资源) | 载入并运行位置相对于这个 JS 文件的另一个 JS 文件。 |
| [`print`](js-util.md#输出测试信息) | 输出测试信息到控制台。 |
| [`asJavaArray`](js-util.md#转换类型) | 将 JavaScript 数组转换为 Java 数组。 |

### 类
(A)NTE 为 JavaScirpt 环境额外提供了以下类：

| 类名 | 说明 |
| --- | --- |
| 资源加载相关 | |
| [`Resources`](js-resources-env.md#资源) | 管理资源的加载与使用。 |
| 动态贴图相关 | |
| [`GraphicsTexture`](js-dynamic-texture.md#GraphicsTexture) | 动态贴图类。 | 
| 工具类 | |
| [`Timing`](js-util.md#Timing) | 管理时间的工具类。 |
| [`StateTracker`](js-util.md#StateTracker) | 管理状态的工具类。 |
| [`CycleTracker`](js-util.md#CycleTracker) | 管理循环的工具类。 |
| [`RateLimit`](js-util.md#RateLimit) | 管理流量限制的工具类。 |
| [`TextUtil`](js-util.md#TextUtil) | 分割文本的工具类。 |
| [`SoundHelper`](js-util.md#SoundHelper) | 声音助手。 |
| [`TickableSound`](js-util.md#TickableSound) | 可被更新的声音。 |
| [`ParticleHelper`](js-util.md#ParticleHelper) | 粒子助手。 |
| [`GlobalRegister`](js-util.md#GlobalRegister) | 全局注册器，用于打通不同脚本之间的通信。 |
| [`WrappedEntity`](js-util.md#WrappedEntity) | 包装了 Minecraft 实体的工具类。 |
| [`ComponentUtil`](js-util.md#ComponentUtil) | 文本组件工具类。 |
| [`Component`](js-util.md#componentutil) | 我的世界的文本组件。 |
| [`IScreen`](js-util.md#IScreen) | 屏幕接口。 |
| [`OrderedMap`](js-util.md#orderedmap-与-placementorder) | 有序的表。 |
| [`PlacementOrder`](js-util.md#OrderedMap-与-PlacementOrder) | 表的排序方式。 |
| [`ShapeSerializer`](js-util.md#ShapeSerializer) | 形状的序列化器。 |
| [`MinecraftClient`](js-util.md#MinecraftClient) | 客户端工具类。 |
| 交互相关 | |
| [`ConfigResponder`](js-custom-config.md#ConfigResponder) | 自定义配置响应器。 |
| [`ClientConfig`](js-custom-config.md#ClientConfig) | 客户端配置。 |
| 渲染相关 | |
| [`DrawCall`](js-draw-call.md) | 绘制调用接口。 |
| [`ClusterDrawCall`](js-draw-call.md#clusterdrawcall) | 集群绘制调用。 |
| [`WorldDrawCall`](js-draw-call.md#worlddrawcall) | 世界绘制调用。 |
| [`RailDrawCall`](js-rail.md#RailDrawCall) | 铁轨绘制调用。 |
| [`SimpleRailDrawCall`](js-rail.md#SimpleRailDrawCall) | 简单的铁轨绘制调用。 |
| [`ModelManager`](js-model-processing.md#ModelManager) | 管理模型的加载。 |
| [`RawMesh`](js-model-processing.md#RawMeshBuilder) | 原始网格。 |
| [`RawMeshBuilder`](js-model-processing.md#RawMeshBuilder) | 原始网格构建器。 |
| [`RawModel`](js-model-processing.md#RawModel) | 原始模型。 |
| [`ModelCluster`](js-model-processing.md#ModelCluster) | 上传后的模型集群。 |
| [`DynamicModelHolder`](js-model-processing.md#DynamicModelHolder) | 动态模型容器。 |
| 数学相关 | |
| [`Matrices`](js-math.md#Matrices) | 矩阵工具类。 |
| [`Matrix4f`](js-math.md#matrix4f) | 4x4 矩阵。 |
| [`Vector3f`](js-math.md#vector3f) | 3D 向量。 |
| 其他 | |
| [`MTRClientData`](js-util.md#mtrclientdata) | 客户端数据。 |
| [`UtilitiesClient`](js-util.md#utilitiesclient) | MTR的客户端工具类。 |
| [`IDrawing`](js-util.md#IDrawing) | MTR的绘制接口。 |
| [`Optional`](https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html) | Java 8 引入的 Optional 类。 |

## 资源

### ResourceLocation

Minecraft 采取一个叫做资源位置的东西来标识资源包内的文件。很多函数只接受 `ResourceLocation` 类型的路径，而不接受字符串。

- `static Resources.id(idStr: String): ResourceLocation`

  将一个字符串转为对应的 `ResourceLocation`。如 `Resources.id("mtr:path/absolute.js")`

- `static Resources.idr(relPath: String): ResourceLocation`
或
- `static Resources.idRelative(relPath: String): ResourceLocation`

  相对于这个 JS 文件的另一个文件的 `ResourceLocation`。如 `Resources.idr("ccc.png")`。目前不能在 create/render/dispose 函数内使用它，届时请改用 `Resources.id`。



### 加载代码与资源

此处是一些 Rhino 引擎内置的函数，用于加载代码和资源，更多的方法请查看[Rhino 文档](https://p-bakker.github.io/rhino/tutorials/scripting_java/)。

- `static include(relPath: String): void` 

  载入并运行位置相对于这个 JS 文件的另一个 JS 文件。

- `static include(path: ResourceLocation): void`

  载入并运行资源包中一个位置的 JS 文件。如 `include(Resources.id("mtr:path/absolute.js"))`

- `static importPackage(src: String): void`

加载一个 Java 包。如 `importPackage(java.util.Set)` 相当于 `java` 中的 `import java.util.Set.*` 
                或 `importPackage(java.awt)` 相当于 `java` 中的 `import java.awt.*`

- `static importClass(src: String): void`

  导入一个 Java 类。如 `importClass("java.util.ArrayList")` 相当于 `import java.util.ArrayList;`



### 载入 AWT 资源

这些函数加载用于通过 Java AWT 来绘制动态贴图的资源。

- `static Resources.getSystemFont(name: String): Font`

  获取一个系统或者 MTR 内置的字体。

  | 字体名称   | 说明                                                         |
  | ---------- | ------------------------------------------------------------ |
  | Noto Serif | MTR 内置的衬线字体 (类似宋体)。在各种系统上相同。            |
  | Noto Sans  | NTE 内置的无衬线字体 (类似黑体)。在各种系统上相同。          |
  | Serif      | 由 AWT 选择这台计算机上安装的一款衬线字体。在不同的设备上可能不同。 |
  | SansSerif  | 由 AWT 选择这台计算机上安装的一款无衬线字体。在不同的设备上可能不同。 |
  | Monospaced | 由 AWT 选择这台计算机上安装的一款等宽字体。在不同的设备上可能不同。 |

- `static Resources.readBufferedImage(path: ResourceLocation): BufferedImage`

  加载一张图片为 BufferedImage。

- `static Resources.readFont(path: ResourceLocation): Font`

  加载一个 TTF 或 OTF 字体为 Font。

- `static Resources.getFontRenderContext(): FontRenderContext`

  获取一个 AWT FontRenderContext。



### 直接读取资源文件

- `static Resources.readString(location: ResourceLocation): String`

  将一个资源文件的内容作为字符串读出。读取失败时返回 null。




### 杂项

- `static Resources.parseNbtString(nbtStr: String): CompoundTag`

  用来获取 Minecraft 原版的 NBT 类型 CompoundTag。使用类似命令方块中的写法，返回 CompoundTag。

