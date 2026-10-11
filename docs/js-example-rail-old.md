# ~~轨道渲染样例 已过时~~

这是一个简单的例子，和 ANTE 内置的逻辑一样。如果您希望模型垂直于地面或者如何, 您可以通过调整矩阵来实现。

```javascript
    let model = ModelManager.uploadVertArrays(ModelManager.loadRawModel(Resources.manager(), Resources.idRelative("main.obj"), null));
    const interval = 0.5;
    const yOffset = -5;
    function create(ctx, state, rail) {
        let reverse = rail.getRenderReversed();
        function draw(matrix) {
            ctx.drawCalls.add(new SimpleRailDrawCall(model, matrix));
        }

        function getLookAtMatrix(pos, last, next, roll) {

            // 如果是反向的则交换方向
            if (reverse) {
                let temp = next;
                next = last;
                last = temp;
            }

            let yaw = Math.atan2(next.x() - last.x(), next.z() - last.z());
            let pitch = Math.atan2(next.y() - last.y(), Math.sqrt((next.x() - last.x()) * (next.x() - last.x()) + (next.z() - last.z()) * (next.z() - last.z())));

            let mat = new Matrix4f();
            mat.translate(pos);
            mat.rotateY(yaw);
            mat.rotateX(-pitch);
            mat.translate(0, yOffset, 0);
            mat.rotateZ(reverse? -roll : roll);
        }

        let length = rail.getLength();
        let num = Math.floor(length / interval);
        let ins = (length - num * interval) / num + interval;
        let pre = rail.getPosition(0)
        for (let i = ins; i < length * 0.8; i += ins) {
            let thi = rail.getPosition(i);
            let mid = rail.getPosition(i - ins / 2);
            let roll = RailExtraSupplier.getRollAngle(rail, i - interval / 2);
            draw(getLookAtMatrix(mid, pre, thi, roll));
            pre = thi;
        }
    }
```