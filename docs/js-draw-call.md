# JavaScript DrawCall

在 ANTE 中，使用 `DrawCall` 接口表示绘制调用。更进一步的，您可以查看[源代码-AbstractDrawCalls.java](https://github.com/aphrodite281/mtr-ante/blob/alpha/common/src/main/java/cn/zbx1425/mtrsteamloco/render/scripting/AbstractDrawCalls.java)

`DrawCall` 仅含有一个方法:

- `void commit(drawScheduler: DrawScheduler, basePose: Matrix4f, worldPose: Matrix4f, light: int)`

该方法的作用是提交绘制调用。


## DrawScheduler

`DrawScheduler` 是 ANTE 的渲染调度员，负责管理渲染任务。ANTE会定期调用 `DrawCall.commit` 更新渲染内容，您被期望在 `commit` 中提交渲染任务。

- `DrawScheduler.enqueue(model: ModelCluster, pose: Matrix4f, light: int)`

  向渲染队列中添加一个 `ModelCluster` 实例，使用 `pose` 作为模型的世界坐标。
  `light` 貌似是光照贴图索引。


## 自定义实现

ANTE 允许您自己实现 `DrawCall` 接口，可以使用自己的变化逻辑。
下面是一个示例：

```javascript
    let drawCalls;// java.util.Map<Object, DrawCall>
    let model, pose, key;
    let drawCall = new DrawCall({commit: (drawScheduler, basePose, worldPose, light) => {
        let finalPose = worldPose.copy();
        finalPose.multiply(pose);
        // ···
        drawScheduler.enqueue(model, finalPose, light);
    }})
    drawCalls.put(key, drawCall);
```

在这段示例中，我们制作了一个实现了 `DrawCall` 接口的对象，它可以在需要 `DrawCall` 的地方当做 `DrawCall` 使用。
每次渲染都会调用 `commit` 方法，可以在 `commit` 里放入自己的变化逻辑。

或者，由于 `DrawCall` 仅有有个 `commit` 方法，您也可以直接传入一个 `function` 在需要 `DrawCall` 的地方。

```javascript
    let drawCalls;// java.util.Map<Object, DrawCall>
    let model, pose, key;
    drawCalls.put(key, function (drawScheduler, basePose, worldPose, light) {
        let finalPose = worldPose.copy();
        finalPose.multiply(pose);
        // ···
        drawScheduler.enqueue(model, finalPose, light);
    });
```

## ClusterDrawCall

`ClusterDrawCall` 是 `DrawCall` 的一个实现，使用 `basePose`。
它有以下两种构造函数：

- `new ClusterDrawCall(model: ModelCluster, pose: Matrix4f)`
- `new ClusterDrawCall(model: DynamicModelHolder, pose: Matrix4f)`

## WorldDrawCall

`WorldDrawCall` 是 `DrawCall` 的一个实现，使用 `worldPose`。
它有以下两种构造函数：

- `new WorldDrawCall(model: Model, pose: Matrix4f)`
- `new WorldDrawCall(model: DynamicModel, pose: Matrix4f)`