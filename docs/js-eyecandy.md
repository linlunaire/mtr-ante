# JavaScript 装饰物件相关

NTE 支持通过 JavaScript 控制装饰物件的渲染。但只能选择完全用 JavaScript 控制所有部件的渲染，或不使用 JavaScript 控制渲染。（至少现在是这样的）



## 添加 JavaScript 控制渲染的装饰物件

您可以通过在 `assets/mtrsteamloco/eyecandies` 文件夹内添加 JSON 文件来添加用JavaScript控制渲染的装饰物件。其写法大致如下：

```json
{
    "key1": {
    "name": "name1",
    "scriptFiles": ["mtrsteamloco:eyecandies/script1.js"]
    },
    "key2": {
    "name": "name2",
    "scriptFiles": ["mtrsteamloco:eyecandies/script2.js", "mtrsteamloco:eyecandies/script3.js", "mtrsteamloco:eyecandies/script4.js"]
    }
}
```

其中，`key` 是装饰物件的唯一标识符，`name` 是装饰物件的名称，`scriptFiles` 是装饰物件的 JavaScript 文件列表。注意key只能由小写字母或下划线组成，scriptFiles需要有[]包裹，不含有能有"model"项否则会使用model文件不加载scriptFiles。



## 全局环境

同一个key的装饰物件共享同一个运行环境（即全局变量等）。

写在函数以外的顶层空间内的代码会在资源包加载时运行，可用于加载模型、贴图等资源。推荐将不需要每次都重新加载的的资源（如模型等）存储在全局变量，避免相同内容每放置新放块都加载一份带来过多内存占用。



## 您要定义的函数

您的脚本中应包含以下函数，(A)NTE 会按需调用它们：

```javascript
function create(ctx, state, entity) { ... }
function render(ctx, state, entity) { ... }
function dispose(ctx, state, entity) { ... }
function use(ctx, state, entity, player) { ... }
```

| 函数    | 说明                                                         |
| ------- | ------------------------------------------------------------ |
| create  | 在列车最开始被加载时调用，可用于进行一些初始化的操作，例如创建动态贴图。 |
| render  | *大致*每帧调用一次。用于主要的显示逻辑。代码在单独线程上运行以便不拖低 FPS。如果代码耗时太长，就可能实际上好几帧才运行一次。 |
| dispose | 超出可视范围时调用。可用于释放动态贴图之类的操作。     |
| use | 玩家使用装饰物件时调用。(除了手持刷子使用) |

NTE 调用这几个函数时会使用三个参数，稍后介绍其各自的内容。

| 参数 (本文中称呼) | 说明                                                         |
| ----------------- | ------------------------------------------------------------ |
| 第一个 (`ctx`)    | 用于向 NTE 输出要如何渲染的相关操作。类型是 [EyeCandyScriptContext](#eyecandyscriptcontext) |
| 第二个 (`state`)  | 一个和某一个装饰物件方块关联的 JavaScript 对象。初始值是 `{}`，可随意设置其上的成员，用来存储一些需要每个方块都不同的内容。 |
| 第三个 (`entity`)  | 用于获取方块的状态。类型是 [BlockEntityEyeCandy](#blockentityeyecandy)。|
| 第四个 (`player`)  | 使用装饰物件的玩家。类型是 [WrappedEntity](js-util.md?id=WrappedEntity) |

接下来列出您可以进行的所有渲染控制操作，和可以获取到的所有关于方块的信息。



## EyeCandyScriptContext

### 以下方法仅在render中保证线程安全，请勿在其他线程使用
调用以下函数可以**控制渲染**。每次 `render` 时都需要为想绘制的模型调用相应的函数，

- `EyeCandyScriptContext.drawModel(model: ModelCluster, poseStack: Matrices): void`
或
- `EyeCandyScriptContext.drawModel(model: DynamicModelHolder, poseStack: Matrices): void`

  要求 NTE 绘制模型。

  `poseStack`：模型放置位置的变换，传入 `null` 表示就放在中心不变换。

调用以下函数可以**播放声音**。只在需要第一次播放声音时调用，重复播放会导致声音叠加。`

- `EyeCandyScriptContext.playSound(sound: ResourceLocation, volume: float, pitch: float): void`

  播放声音。

### 以下保证线程安全，可在所有线程调用

向下面的Map中添加、替换或删除键值对可以**控制渲染**。

- `EyeCandyScriptContext.drawCalls: Map<Object, DrawCall>`
  
  [绘制调用](https://aphrodite281.github.io/mtr-ante/#/js-draw-call)表。(此Map为java的Map，请使用[java-Map](https://docs.oracle.com/javase/8/docs/api/java/util/Map.html)的方法)。

此外，还有一组函数以 **辅助开发调试**。

- `EyeCandyScriptContext.setDebugInfo(key: String, value: Object...)`

    在屏幕左上角输出调试信息。需在设置中开启 “显示JS调试信息” 才会显示。`key` 为值的名称，`value` 为内容（`GraphicsTexture` 类型的会被显示出来，其他的会被转为字符串显示）。可传入多个值，若value有多个值则会转换为数组。

- `EyeCandyScriptContext.setDebugInfo(key: String, order: PlacementOrder, value: Object...)`

    添加一个调试信息，并按 [PlacementOrder](js-util.md?id=orderedMap-与-placementOrder) 的顺序添加。




## BlockEntityEyeCandy

| 属性                                          | 说明                                                         |
| --------------------------------------------- | ------------------------------------------------------------ |
| `BlockEntityEyeCandy.translateX: float` | 获取方块在 X 方向上的偏移量。YZ同理。 |
| `BlockEntityEyeCandy.rotateX: float` | 获取方块在 X 轴上的旋转。YZ同理。 |
| `BlockEntityEyeCandy.prefabId: String` | 获取方块的预制体 ID。 |
| `BlockEntityEyeCandy.fullLight: boolean` | 获取方块是否为全光照。 |
| `BlockEntityEyeCandy.asPlatform: boolean`| 获取方块是否为当作站台。 |
| `BlockEntityEyeCandy.isTicketBarrier: boolean`| 获取方块是否为闸机。 |
| `BlockEntityEyeCandy.isEntrance: boolean`| 是入口 |
| `BlockEntityEyeCandy.doorValue: float`| 附近列车门的值 |
| `BlockEntityEyeCandy.doorTarget: boolean`| 附近列车车门的目标状态(false为关闭，true为打开) |
| `BlockEntityEyeCandy.fixedMatrix: boolean`| 获取方块是否固定。 |

| 方法                                          | 说明                                                         |
| --------------------------------------------- | ------------------------------------------------------------ |
| `BlockEntityEyeCandy.setShape(shape: String): void` | 设置方块的形状。 |
| `BlockEntityEyeCandy.getShape(): String` | 获取方块的形状。 |
| `BlockEntityEyeCandy.setCollisionShape(shape: String): void` | 设置方块的碰撞形状。 |
| `BlockEntityEyeCandy.getCollisionShape(): String` | 获取方块的碰撞形状。 |
| `BlockEntityEyeCandy.getWorldPos(): BlockPos` | 获取方块坐标，返回的是原版的 BlockPos 类型。 |
| `BlockEntityEyeCandy.getWorldPosVector3f(): Vector3f` | 获取方块坐标，返回的是 Vector3f。 |
| `BlockEntityEyeCandy.getTransformPosVector3f(): Vector3f`| 获取方块坐标，返回的是 Vector3f，且已经应用了方块的变换。 |
| `BlockEntityEyeCandy.sendUpdateC2S(): void`| 发送方块状态更新。 |
| `BlockEntityEyeCandy.getBlockYRot(): float`| 获取方块在 Y 轴上的旋转。(摆放方向，0-270) |
| `BlockEntityEyeCandy.setLightLevel(level: int): void`| 设置发光等级(0-15)。 |
| `BlockEntityEyeCandy.getLightLevel(): int`| 获取发光等级(0-15)。 |

### 信息的保存与交互

`BlockEntityEyeCandy` 含有两个私有的 Map，用于保存配置信息。

- `BlockEntityEyeCandy.customConfig: Map<String, String>` 存储所有自定义配置项。
- `BlockEntityEyeCandy.customResponders: Map<String, ConfigResponder>` 可交互的配置项。

您无法直接访问或修改 `customConfig` 与 `customResponders`，但可以通过以下方法来操作：


- `BlockEntityEyeCandy.registerCustomConfig(responders: ConfigResponder): void`

  注册[响应器](js-custom-config.md)，如果原来没有值则会添加默认值，如果原来有则不会变动。

- `BlockEntityEyeCandy.removeCustomConfig(key: String): void`

  移除一项配置。

- `BlockEntityEyeCandy.putCustomConfig(key: String, value: String): void`

  添加或修改一项配置(如果没有注册响应器则在装饰物件的屏幕中无法修改)

- `BlockEntityEyeCandy.getCustomConfig(key: String): String`

  获取一项配置。