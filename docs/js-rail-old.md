# ~~JavaScript 轨道相关 已过时~~

ANTE 支持通过 JavaScript 控制轨道的渲染。既可以完全用 JavaScript 控制所有的渲染，也可以在默认的基础上叠加用 JavaScript 控制的显示内容。
依靠 JavaScript 现在您可以用轨道做桥、做接触网或者什么其他的东西。

## 添加轨道
您可以通过在 `assets/mtrsteamloco/rails` 文件夹内添加 JSON 文件来添加轨道。其写法大致如下：

```json
{
    "key1": {
        "name": "JavaScript",
        "model": "mtrsteamloco:rails/cube_yellow.obj",
        "repeatInterval": 1.0,
        "flipV": true,
        "yOffset": 1, 
        "scriptFiles": ["mtrsteamloco:rails/test/main.js"]
    },
    "key2": {
        "name": "JavaScript Only",
        "scriptFiles": ["mtrsteamloco:rails/test/main0.js", "mtrsteamloco:rails/test/main1.js"]
    }
}
```

## 全局环境

同一个key的轨道件共享同一个运行环境（即全局变量等）。

写在函数以外的顶层空间内的代码会在资源包加载时运行，可用于加载模型、贴图等资源。推荐将不需要每次都重新加载的的资源（如模型等）存储在全局变量，避免相同内容每放置新放块都加载一份带来过多内存占用。

## 您要定义的函数

您的脚本中应包含以下函数，(A)NTE 会按需调用它们：
```javascript
function create(ctx, state, rail) { ... }
function render(ctx, state, rail) { ... }
function dispose(ctx, state, rail) { ... }
```

| 函数    | 说明                                                         |
| ------- | ------------------------------------------------------------ |
| create  | 在轨道最开始被加载时调用，可用于进行一些初始化的操作，例如创建动态贴图。建议在此添加固定的模型 |
| render  | *大致*每帧调用一次。用于主要的显示逻辑。代码在单独线程上运行以便不拖低 FPS。如果代码耗时太长，就可能实际上好几帧才运行一次。 |
| dispose | 超出可视范围时调用。可用于释放动态贴图之类的操作。     |

| 参数 (本文中称呼) | 说明                                                         |
| ----------------- | ------------------------------------------------------------ |
| 第一个 (`ctx`)    | 用于向 NTE 输出要如何渲染的相关操作。类型是 [RailScriptContext](#railscriptcontext) |
| 第二个 (`state`)  | 一个和某一个轨道关联的 JavaScript 对象。初始值是 `{}`，可随意设置其上的成员，用来存储一些需要每个轨道都不同的内容。 |
| 第三个 (`rail`)  | 获取`Rail`的相关功能。类型是 [RailWrapper](#railwrapper)。|

## RailScriptContext

### 以下方法仅在render中保证线程安全，请勿在其他线程使用
调用以下函数可以**控制渲染**。每次 `render` 时都需要为想绘制的模型调用相应的函数，

- `EyeCandyScriptContext.drawModel(model: ModelCluster, poseStack: Matrices): void`
或
- `EyeCandyScriptContext.drawModel(model: DynamicModelHolder, poseStack: Matrices): void`


要求 NTE 绘制模型。

`poseStack`：模型放置位置的变换，传入 `null` 表示就放在中心不变换。

### 以下保证线程安全，可在所有线程调用

向下面的Map中添加、替换或删除键值对可以**控制渲染**。

- `EyeCandyScriptContext.drawCalls: Map<Object, RailDrawCall>`
  
  [轨道绘制调用](js-rail.md?id=RailDrawCall)表。(此Map为java的Map，请使用[java-Map](https://docs.oracle.com/javase/8/docs/api/java/util/Map.html)的方法)。

此外，还有一组函数以 **辅助开发调试**。

- `EyeCandyScriptContext.setDebugInfo(key: String, value: Object...)`

    在屏幕左上角输出调试信息。需在设置中开启 “显示JS调试信息” 才会显示。`key` 为值的名称，`value` 为内容（`GraphicsTexture` 类型的会被显示出来，其他的会被转为字符串显示）。可传入多个值，若value有多个值则会转换为数组。

- `EyeCandyScriptContext.setDebugInfo(key: String, order: PlacementOrder, value: Object...)`

    添加一个调试信息，并按 [PlacementOrder](js-util.md?id=orderedMap-与-placementOrder) 的顺序添加。


## RailDrawCall

ANTE 通过 `RailDrawCall` 接口管理轨道的绘制调用。  
不同于列车和装饰物件使用的[DrawCall](js-draw-call.md)，轨道由于可能非常长超出视野范围，所以需要添加相应的逻辑。同时，`RailDrawCall` 不提供 `basePose` 和 `light`。

`RailDrawCall` 有一个需要被实现的方法：

- `Impl.commit(drawScheduler: DrawScheduler, world: Matrix4f, frustum: Frustum, cameraPos: Vector3f, maxRailDistance: int)`


### DrawScheduler

`DrawScheduler` 是 ANTE 的渲染调度员，负责管理渲染任务。ANTE会定期调用 `DrawCall.commit` 更新渲染内容，您被期望在 `commit` 中提交渲染任务。

- `DrawScheduler.enqueue(model: ModelCluster, pose: Matrix4f, light: int)`

  向渲染队列中添加一个 `ModelCluster` 实例，使用 `pose` 作为模型的世界坐标。
  `light` 貌似是光照贴图索引。


### SimpleRailDrawCall 

ANTE 提供了一个简单的实现：`SimpleRailDrawCall` 它实现了 `RailDrawCall` 。

您可以通过:

- `new SimpleRailDrawCall(model: ModelCluster, matrix: Matrix4f)`
或
- `new SimpleRailDrawCall(holder: DynamicModelHolder, matrix: Matrix4f)`
  
创建一个实例。
`SimpleRailDrawCall` 仅在模型在渲染距离内时提交模型。

`RailScriptContext` 中的 `RailScriptContext.drawModel( ... )` 方法也是将参数转为 `SimpleRailDrawCall` 保存的。

### 自定义实现

与[DrawCall](js-draw-call.md)相同，您可以自己实现 `RailDrawCall` 接口，使用自己的逻辑提交模型。
下面是一个示例：

```javascript
    let model = ...(ModelCluster)
    let matrix = new Matrix4f();
    matrix.translate(11, 45, 14);
    let pos = matrix.getTranslationPart();
    let drawCall = new RailDrawCall({
        commit: function(drawScheduler, world, frustum, cameraPos, maxRailDistance) {
            let d = maxRailDistance * maxRailDistance;
            if (d >= pos.distanceSq(cameraPos)) {
                let light = MinecraftClent.getLightColor(pos);
                drawScheduler.enqueue(model, matrix, light);
            }
        }
    });

```

## RailWrapper  

| 方法 | 说明 |
| ----------------------------------- | --------------------------------------|
| `RailWrapper.getCustomConfigs(): java.util.Map<String, String>` | 获取自定义配置表 |
| `RailWrapper.setCustomConfigs(map: java.util.Map<String, String>): void` | 设置自定义配置表 |
| `RailWrapper.sendUpdateC2S(): void` | 发送更新包(同步更改) |
| `RailWrapper.getLength(): double` | 获取长度 |
| `RailWrapper.getPosition(value: double): Vector3f` | 获取某一值的位置 |
| `RailWrapper.getRollAngle(value: double): float` | 获取某一点的滚转值 |
| `RailWrapper.getRenderReversed(): boolean` | 获取是否为反向的 |


`