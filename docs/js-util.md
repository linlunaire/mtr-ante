# JavaScript 工具类

NTE 提供了一些工具类，以便获取一些信息或更简单地实现功能。更进一步的，您可以查看[源代码-util.*](https://github.com/aphrodite281/mtr-ante/blob/alpha/common/src/main/java/cn/zbx1425/mtrsteamloco/render/scripting/util)


## 输出测试信息

- `static print(params: Object...): void`

调用这个函数会在 Minecraft 日志里打出信息（在游戏内没有信息显示）。可以传入任意多个任意类型的参数。


## 转换类型

- `static asJavaArray(array: [](List<T>)): T[]`

把一个 `List` 转换成 Java 数组。
更优雅的把JS的 [] 转为 Java 的 [] 的方法。
其实只是调用了List.toArray()方法, 但是在JS环境中无法调用 [].toArray() 方法，所以提供了这个方法。


## 版本

提供了一些能用来获得版本号的函数，以便让作者能兼容不同版本的不同（如果有）。

| 函数                                         | 说明                                                         |
| -------------------------------------------- | ------------------------------------------------------------ |
| `static Resources.getMTRVersion(): String`   | MTR 的版本字符串，形如 `1.19.2-3.1.0-hotfix-1`               |
| `static Resources.getNTEVersion(): String`   | NTE 的版本字符串，形如 `0.4.0+1.19.2`                        |
| `static Resources.getNTEVersionInt(): int`   | NTE 的版本的数字形式，以便比较；例如 0.4.0 的是 4000，1.9.1 的会是 19100 |
| `static Resources.getNTEProtoVersion(): int` | NTE 的存档格式版本数字。                                     |



## TextUtil

MTR 采用了一个 `中文部分|English Part||EXTRA` 的车站命名方法，所以 NTE 提供了一些函数来把各个部分拆出来。

| 成员                                                         | 说明                           |
| ------------------------------------------------------------ | ------------------------------ |
| `static TextUtil.getCjkParts(src: String): String`           | 获取里面的中文部分。           |
| `static TextUtil.getNonCjkParts(src: String): String`        | 获取里面的英文部分。           |
| `static TextUtil.getExtraParts(src: String): String`         | 获取里面的隐藏部分。           |
| `static TextUtil.getNonExtraParts(src: String): String`      | 获取里面的中文和英文部分。     |
| `static TextUtil.getNonCjkAndExtraParts(src: String): String` | 获取里面的英文和隐藏部分。     |
| `static TextUtil.isCjk(src: String): String`                 | 检查一个字符串是否包含中文字。 |



## Timing

- `static Timing.elapsed(): double`

  游戏的运行时间。单位是秒，是递增的，游戏暂停时会停止增长。

- `static Timing.delta(): double`

  本次调用 `render` 和上次之间的时间差。可以用来计算例如轮子这段时间里应该转过的角度等。



## StateTracker

有时候需要计测状态的转换。例如，只在通过某个位置时播放一次广播（因为如果 `if (...distance < 300) ctx.play...` 的话就会在通过之后每帧都满足条件然后每帧都播放一次，造成几百个广播百花齐放的效果），或者在切换页面后前一秒钟里显示动画。

因为每列车都该有独立的逻辑，您大概会想把它存进列车的 `state` 里。

- `new StateTracker()`

  创建一个 StateTracker。

- `StateTracker.setState(value: String): void`

  告诉它目前的状态是这个状态。不调用的时候则是继续保持状态。

- `StateTracker.stateNow(): String`

  获取目前的状态。

- `StateTracker.stateLast(): String`

  获取上一个状态。没有的话，返回 `null`。

- `StateTracker.stateNowDuration(): double`

  获取目前的状态已经持续了的时间。

- `StateTracker.stateNowFirst(): boolean`

  是否是刚刚通过 `setState` 换进了这个状态。



## CycleTracker

这是一个按时间自动循环切换的 `StateTracker`。

因为每列车都该有独立的逻辑，您大概会想把它存进列车的 `state` 里。

- `new CycleTracker(params: Object[])`

  创建一个 CycleTracker。params 是它会循环切换的各个状态和每个状态应该持续的时间，单位是秒。
  例如 `new CycleTracker(["route", 5, "nextStation", 5])`

- `CycleTracker.tick(): void`

  根据现在的时间来更新状态。

- `CycleTracker.stateNow(): String`

  获取目前的状态。

- `CycleTracker.stateLast(): String`

  获取上一个状态。没有的话，返回 `null`。

- `CycleTracker.stateNowDuration(): double`

  获取目前的状态已经持续了的时间。

- `CycleTracker.stateNowFirst(): boolean`

  是否是刚刚通过 `tick` 换进了这个状态。



## RateLimit

有些工作不需要太频繁地进行，例如显示屏可能只需要每秒更新 10 次而不是每帧都更新。所以可以限制它们的频率来提升性能。

因为每列车都该有独立的逻辑，您大概会想把它存进列车的 `state` 里。

- `new RateLimit(interval: double)`

  创建一个 RateLimit。interval 是两次直接应该有的间隔，单位是秒，例如 interval 为 0.1 就代表应该一秒十次。

- `RateLimit.shouldUpdate(): boolean`

  距离上次运行之间是否已经经过了足够的时间。将所需的代码用 `if (state.rateLimitXXX.shouldUpdate()) { ... }` 包起来即可限制它的频率。

- `RateLimit.resetCoolDown(): void`

  重置时间，让它马上就可以再次运行。



## MTRClientData

MTR 的客户端数据，可以用来读取换乘线路等。参见 MTR 源码 [ClientData.java](https://github.com/aphrodite281/Minecraft-Transit-Railway/blob/master/common/src/main/java/mtr/client/ClientData.java)。



## MinecraftClient

由于混淆表原因，没办法直接把客户端的类搬出来让您使用。所以这里有一些辅助方法。

- `static MinecraftClient.worldIsRaining(): boolean`

  世界是否在下雨。

- `static MinecraftClient.worldIsRainingAt(pos: Vector3f): boolean`

  世界的某个方块处是否正在下雨而且被淋到。

- `static MinecraftClient.worldDayTime(): int`

  世界的一天内时间，单位是 Tick。

- `static MinecraftClient.narrate(text: String): void`

  调用系统“讲述人”读出一段文本。

- `static MinecraftClient.displayMessage(message: String,actionBar :boolean): void`

  在聊天框或在操作栏（物品栏上方）显示一段文本。当 `actionBar` 为 `true` 时，显示在操作栏，否则显示在聊天框。

- `static MinecraftClient.execute(task: Runnable): void`

  在主线程中执行一个任务。

- `static MinecraftClient.levelEvent(p_109534_: int , p_109535_: Vector3f , p_109536_: int ): void`

  触发一个世界事件。

- `static MinecraftClient.getOccupiedAspect(vPos: Vector3f, facing: float, aspects: int): int`

  获取某一个位置的通过等级。

- `static MinecraftClient.getStationAt(pos: Vector3f): Station`

  获取玩家所在的车站。

- `static MinecraftClient.getPlatformAt(pos: Vector3f, radius: int, lower: int, upper: int): Platform`

  获取某个范围内的站台。

- `static MinecraftClient.getNodeAt(vPos: Vector3f, fFacing: float): Vector3f`

  获取某个位置周围的节点的坐标。

- `static int MinecraftClient.getLightColor(pos: Vector3f)`
  
  获取某个位置的亮度颜色。

— `static MinecraftClient.getCameraPos(): Vector3f`

  获取相机的位置。

- `static MinecraftClient.getCameraDistance(from: Vector3f): float`

  获取相机距离某个位置的距离。

- `static MinecraftClient.getCameraEntity(): WrappedEntity`

  获取相机的实体。

- `static MinecraftClient.getPlayer(): WrappedEntity`

  获取玩家的实体。

- `static MinecraftClient.getLevel(): Level`

  获取当前的世界。

- `static MinecraftClient.canOpenDoorsAt(p1: Vector3f, p2: Vector3f): boolean[]`

  检查从p1到p2的两侧是否可以开门。返回{doorLeftOpen, doorRightOpen}。
  这是一个暂时的办法，若客户端没有相关数据，则会返回 false。

- `static MinecraftClient.packLightTexture(int a, int b): int`

  包装一个光照贴图。

- `static MinecraftClient.setScreen(screen: Screen): void`

  设置屏幕。(若想关闭屏幕可传入 `null`)

- `static MinecraftClient.reloadResourcePacks()`

  重新加载资源包。

- `static MinecraftClient.markRendererAllChanged(): void`

  标记渲染器所有数据都已改变。



## TickableSound

这个类提供了一个可以实时更新参数的声音方法。

- `new TickableSound(sound :ResourceLocation)`

  以 BLOCK 声源 创建一个 `TickableSound`。

- `new TickableSound(sound :ResourceLocation, source :SoundSource)`

  以指定声源创建 `TickableSound`。

- `TickableSound.setData(volume :float, pitch :float, pos :Vector3f): void`

  设置声音的音量、音调、位置。

- `TickableSound.setLooping(looping :boolean): void`

  设置是否循环播放。

- `TickableSound.setDelay(delay :int): void`

  设置延迟播放的时间。

- `TickableSound.setAttenuation(attenuation :boolean): void`

  设置声音衰减。

- `TickableSound.setRelative(relative :boolean): void`

  设置声音是否相对于玩家。

- `TickableSound.play(): void`

  播放声音。(相当于 `SoundHelper.play(this)`)

- `TickableSound.quit(): void`

  停止播放声音。(相当于 `SoundHelper.stop(this)`)

- `TickableSound.pause(): void`

  暂停播放声音。(与上一个效果相近)

- `TickableSound.getSound(): Sound`

  获取声音。

- `TickableSound.getLocation(): ResourceLocation`

  获取声音的标签位置。

- `TickableSound.getSource(): SoundSource`

  获取声音的声源。

- `TickableSound.isLooping(): boolean`

  是否循环播放。

- `TickableSound.getVolume(): float`

  获取声音的音量。

- `TickableSound.getPitch(): float`

  获取声音的音调。

- `TickableSound.getDelay(): int`

  获取延迟播放的时间。

- `TickableSound。getAttenuation(): SoundInstance.Attenuation`

  获取声音衰减。

- `TickableSound.isRelative(): boolean`

  是否声音相对于玩家。

- `TickableSound.toString(): String`

  转为字符串（调试时自动调用）。



## SoundHelper

这个类提供了一些声音相关的功能。

- `static SoundHelper.play(sound :SoundInstance): void`

  播放一个声音实例，如 `TickableSound` 或 `TrainLoopingSoundInstance`

- `static SoundHelper.play(sound :SoundInstance, delay :int): void`

  延迟一段时间后播放声音。

- `static SoundHelper.play(sound :SoundEvent, pos :Vector3f,  volume :float, pitch :float): void`

  播放一个 `SoundEvent`, 类似 ctx.playSound......

- `static SoundHelper.play(sound :SoundEvent, pos :Vector3f, source :SoundSource, volume :float, pitch :float): void`

  以指定声源播播放一个 `SoundEvent。`

- `static SoundHelper.stop(): void`

  停止所有声音。

- `static SoundHelper.stop(sound :SoundInstance): void`

  停止某个声音实例。

- `static SoundHelper.stop(sound :SoundEvent, source :SoundSource): void`

  停止使用某个声源播放的某一个 `SoundEvent` 的所有声音。

- `static SoundHelper.stop(sound :SoundEvent): void`

 停止使用 BLOCK 声源播放的某一个 `SoundEvent` 的所有声音。

- `static SoundHelper.getSoundSource(str :String): SoundSource`

  根据字符串获取一个 `SoundSource`。

## ParticleHelper

这个类提供了一些粒子相关的功能。

- `static ParticleHelper.addParticle(particle :ParticleOptions, pos :Vector3f, config :Vector3f): void`

  在某个位置添加一个粒子。

- `static ParticleHelper.addParticle(particle :ParticleOptions, isOverrideLimiter :boolean, pos :Vector3f, config :Vector3f): void`

  在某个位置添加一个粒子，并忽略粒子限制器。

- `static ParticleHelper.addParticle(particle :ParticleOptions, isOverrideLimiter :boolean, isOnGround :boolean, pos :Vector3f, config :Vector3f): void`

  在某个位置添加一个粒子，并忽略粒子限制器和是否在地上。

- `static ParticleHelper.getParticleType(particleName :String): <T> SimpleParticleType`

  根据名字获取一个 `SimpleParticleType` 或 `ParticleType`。名称可以查看 [Minecraft Wiki](https://zh.minecraft.wiki/wiki/%E7%B2%92%E5%AD%90) 的 `JAVA版ID名` 一栏。

## ComponentUtil

为了方便创建我的世界(文本)组件 `Component`，我向JS环境中添加了 `ComponentUtil` 类，含有以下方法：

- `static translatable(text: String, ...objects: Object): Component`
  获取一个可翻译的组件，参数为文本和可变参数，返回一个可翻译的组件。

- `static literal(text: String): Component`
将字符串转化为组件

- `static getString(component: Component): String`
将组件转化为字符串

### 示例

例如在aph:lang文件夹中放入了 zh_cn.json 文件，内容如下：
```json
{
    "text.aph.qssnn": "晴纱是男娘",
    "text.aph.is": "%s是%s"
}
```

在aph:lang文件夹中放入了 en_us.json 文件，内容如下：
```json
{
    "text.aph.qssnn": "Qingsha is a male girl",
    "text.aph.is": "%s is %s"
}
```

```javascript
// 若为英语环境 则组件内容为 "Qingsha is a male girl"，若为中文环境 则组件内容为 "晴纱是男娘"。
const qssnn = ComponentUtil.translatable("text.aph.qssnn");

// 若为英语环境 则组件内容为 "A is B"，若为中文环境 则组件内容为 "A是B"。
const is = ComponentUtil.translatable("text.aph.is", "A", "B");

const text = "Hello, world!";
const component = ComponentUtil.literal(text);// 转换为 Component
const result = ComponentUtil.getString(component);// 转换为 String
```

## OrderedMap 与 PlacementOrder

为了更好的排序调试信息，信息使用 `OrderedMap` 来存储，这是一个 ANTE 实现的 Map 类。用法与 Map 类基本相同，添加了一些其他方法。

`PlacementOrder` 是 `OrderedMap` 里的一个枚举类，用于描述方块的放置顺序。
含有以下属性：

- `PlacementOrder.UPPER`

  上面。

- `PlacementOrder.CENTRAL`

  中间。

- `PlacementOrder.LOWER`

  下面。

更进一步的，请查看[源代码-OrderedMap.java](https://github.com/aphrodite281/mtr-ante/blob/alpha/common/src/main/java/cn/zbx1425/mtrsteamloco/render/scripting/AbstractDrawCalls.java)

## WrappedEntity

由于混淆表原因，我的世界的Player等实体对象不能直接被使用，这里 ANTE 提供了 `WrappedEntity` 类来处理这些问题，并提供了一些方法来获取实体的相关信息。

- `new WrappedEntity(entity :Entity)`

  创建一个 `WrappedEntity`。

- `WrappedEntity.getX(): double`

  获取实体的 X 坐标。

- `WrappedEntity.getY(): double`

  获取实体的 Y 坐标。

- `WrappedEntity.getZ(): double`

  获取实体的 Z 坐标。

- `WrappedEntity.getLookAngle(): Vector3f`

  获取实体的视线方向。

- `WrappedEntity.getPosition(): Vector3f`

  获取实体的位置。



## IScreen

`ANTE` 通过 `IScreen` 提供屏幕支持。更进一步的，请查看[源代码-IScreen.java](https://github.com/aphrodite281/mtr-ante/blob/alpha/common/src/main/java/cn/zbx1425/mtrsteamloco/render/scripting/util/IScreen.java)

`IScreen` 提供了以下方法：

- `static IScreen.newButton(x: int, y: int, width: int, height: int, text: Component, onPress: Button.OnPress): Button`
或
- `static IScreen.newButton(x: int, y: int, width: int, height: int, text: Component, onPress: Button.OnPress, onTooltip: Button.OnTooltip): Button`

  创建一个按钮。(我的世界的按钮)

### IScreen.ClothConfig2

[`ClothConfig2`](https://shedaniel.gitbook.io/cloth-config) 是一个优秀的开源项目，它可以方便的创建配置屏幕。`ANTE`的装饰物件屏幕和客户端配置屏幕都是使用的 `ClothConfig2`。

`IScreen.ClothConfig2` 有以下方法：

- `static IScreen.ClothConfig2.createConfigBuilder(): ConfigBuilder`

  创建一个 `ConfigBuilder`。

- `static IScreen.ClothConfig2.newButtonListEntry(name: Component, button: Button, processor: ButtonListEntry.Processor, tooltipSupplier: Supplier<Optional<Component[]>>, requiresRestart: boolean): ButtonListEntry`

  这是 `ANTE` 添加的 ListEntry，用于创建按钮列表。
  创建一个 `ButtonListEntry`，`tooltipSupplier` 可以为 `null`，表示没有提示。

### IScreen.WithTexture

通过 `WithTexture` 您可以高度自定义一个 `Screen`，它向您提供了一个 [`GraphicsTexture`](js-dynamic-texture.md) 实例，您可以用他来绘制自己屏幕。以下用 `WithTexture` 指代 `IScreen.WithTexture`：

`WithTexture` 继承自 `Screen` 类，但是由于 `Screen` 类被混淆了，所以如果您不去查映射表的话，您无法使用 `Screen` 类的方法。

`WithTexture` 提供了以下几个属性：

- `new WithTexture(title: Component)`

  创建一个 `WithTexture`。

- `WithTexture.texture: GraphicsTexture`

  一个 `GraphicsTexture` 实例，您可以用它来绘制自己屏幕。
  请注意，`GraphicsTexture` 的宽高是实际屏幕的宽高，其他地方的宽高是被缩放后的。
  可能您需要进行转换。

- `WithTexture.state: Object`

  一个对象，您可以用它来存储一些状态。

- `WithTexture.isPauseScreen: boolean`

  一个布尔值，表示当前是否是暂停界面。

- `WithTexture.initFunction: InitFunction`

  一个初始化函数，它会在 `WithTexture` 被创建时或者调整窗口大小时被调用。
InitFunction: (screen: WithTexture, width: int, height: int) => void

- `WithTexture.keyPressResponder: KeyPressResponder`

  一个键盘按下响应器，它会在用户按下某个键时被调用。
KeyPressResponder: (screen: WithTexture, i1: int, i2: int, i3: int) -> boolean

- `WithTexture.insertTextFunction: InsertTextFunction`

  一个插入文本函数，它会在用户输入文本时被调用。
InsertTextFunction: (screen: WithTexture, text: String, i1: int) -> void

- `WithTexture.renderFunction: RenderFunction`

  一个渲染函数，它会在屏幕渲染时被调用。
RenderFunction: (screen: WithTexture, mouseX: int, mouseY: int, delta: float) -> void

- `WithTexture.tickFunction: Consumer<WithTexture>`

  一个 tick 函数，它会在每一帧被调用。

- `WithTexture.onFilesDropFunction: BiConsumer<WithTexture, List<Path>>`

  一个文件拖放函数，它会在用户拖放文件时被调用。

- `WithTexture.onCloseFunction: Consumer<WithTexture>`

  一个关闭函数，它会在用户关闭窗口时被调用。

- `WithTexture.mouseClickedFunction: MouseClickedFunction`

  一个鼠标点击函数，它会在用户点击鼠标时被调用。
MouseClickedFunction: (screen: WithTexture, x: double, y: double, i: int) -> boolean

- `WithTexture.mouseMovedFunction: MouseMovedFunction`

  一个鼠标移动函数，它会在鼠标移动时被调用。
MouseMovedFunction: (screen: WithTexture, x: double, y: double) -> void

- `WithTexture.isMouseOverFunction: IsMouseOverFunction`

  一个鼠标是否在某个组件上函数，它会在鼠标移动时被调用。
IsMouseOverFunction: (screen: WithTexture, x: double, y: double) -> boolean

- `WithTexture.charTypedFunction: CharTypedFunction`

  一个字符输入函数，它会在用户输入字符时被调用。
CharTypedFunction: (screen: WithTexture, p_94732_: char, p_94733_: int) -> boolean

- `WithTexture.keyReleasedFunction: KeyReleasedFunction`

  一个键盘释放函数，它会在用户释放某个键时被调用。
KeyReleasedFunction: (screen: WithTexture, p_94750_: int, p_94751_: int, p_94752_: int) -> void

- `WithTexture.mouseScrolledFunction: MouseScrolledFunction`

  一个鼠标滚动函数，它会在用户滚动鼠标时被调用。
MouseScrolledFunction: (screen: WithTexture, x: double, y: double, value: double) -> boolean

- `WithTexture.mouseDraggedFunction: MouseDraggedFunction`

  一个鼠标拖拽函数，它会在用户拖拽鼠标时被调用。
MouseDraggedFunction: (screen: WithTexture, sx: double, sy: double, ex: double, ey: double, i: int) -> void

- `WithTexture.mouseReleasedFunction: MouseReleasedFunction`

  一个鼠标释放函数，它会在用户释放鼠标时被调用。
MouseReleasedFunction: (screen: WithTexture, x: double, y: double, i: int) -> void

#### 示例

下面是一个简单的例子，在使用装饰物件时，会打开一个自定义屏幕，它会在您的鼠标下面绘制一个方块，并将您输入的文本打印出来。(暂时没做删除或者移动光标的功能，因为我懒得写了)

```javascript
importPackage(java.awt);

let font = new Font("宋体", Font.PLAIN, 200);
let str = "";

function use(ctx, state, entity, player) {
  const screen = new IScreen.WithTexture(ComponentUtil.literal("screen"));
  screen.initFunction = (screen, w, h) => {
      let state = screen.state;
      if (state.str == null) state.str = "";
      let tex = screen.texture;
      let w0 = tex.width, h0 = tex.height;
      state.fx = x => x * w0 / w;
      state.fy = y => y * h0 / h;
  }
  screen.renderFunction = (screen, mx, my, d) => {
      let state = screen.state;
      let tex = screen.texture;
      let g = tex.graphics;
      g.setComposite(AlphaComposite.Clear);
      g.fillRect(0, 0, tex.width, tex.height);
      g.setComposite(AlphaComposite.SrcOver);
      g.setColor(Color.WHITE);
      g.setFont(font);
      g.drawString(state.str, 10, 220);
      g.fillRect(state.fx(mx) - 10, state.fy(my) - 10, 20, 20);
      g.drawString(str, 10, 120);
      tex.upload();
  }
  screen.charTypedFunction = (screen, c, b) => {
      screen.state.str += c;
      return true;
  }
    MinecraftClient.setScreen(screen);
}
```

## GlobalRegister

为了不重复加载资源，您可以使用 `GlobalRegister` 来实现单实例化。

`GlobalRegister` 提供了以下方法：

- `static GlobalRegister.put(key: string, value: Object): void`
向注册表中添加一个对象。

- `static GlobalRegister.get(key: string): Object`
从注册表中获取一个对象。

详见源码 [GlobalRegister.java](https://github.com/aphrodite281/mtr-ante/blob/alpha/common/src/main/java/cn/zbx1425/mtrsteamloco/render/scripting/util/GlobalRegister.java)

## ShapeSerializer 

`ShapeSerializer` 是 `ANTE` 序列化装饰物件形状的工具类，详见源码 [ShapeSerializer.java](https://github.com/aphrodite281/mtr-ante/blob/alpha/common/src/main/java/cn/zbx1425/mtrsteamloco/data/ShapeSerializer.java)。一般来说您用不到这个类。

## IDrawing

`IDrawing` 是 `MTR` 的绘制接口，详见源码 [IDrawing.java](https://github.com/aphrodite281/Minecraft-Transit-Railway/blob/master/common/src/main/java/mtr/client/IDrawing.java)

## UtilitiesClient

`UtilitiesClient` 是 `MTR` 的客户端工具类，详见源码[UtilitiesClient.java](https://github.com/aphrodite281/Minecraft-Transit-Railway/blob/master/common/src/main/java/mtr/mappings/UtilitiesClient.java)