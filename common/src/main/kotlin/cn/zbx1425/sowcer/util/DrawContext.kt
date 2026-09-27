package cn.zbx1425.sowcer.util

import cn.zbx1425.sowcer.batch.BatchManager

open class DrawContext {
    @JvmField var drawWithBlaze: Boolean = false
    @JvmField var sortTranslucentFaces: Boolean = false
    @JvmField var drawCallCount: Int = 0
    @JvmField var batchCount: Int = 0
    @JvmField var singleFaceCount: Int = 0
    @JvmField var instancedFaceCount: Int = 0
    @JvmField var blazeFaceCount: Int = 0

    private var drawCallCountCF = 0
    private var batchCountCF = 0
    private var singleFaceCountCF = 0
    private var instancedFaceCountCF = 0
    private var blazeFaceCountCF = 0

    @JvmField var debugInfo: MutableList<String?>? = ArrayList()
    private var debugInfoCF: MutableList<String?>? = ArrayList()

    open fun resetFrameProfiler() {
        drawCallCount = drawCallCountCF
        batchCount = batchCountCF
        singleFaceCount = singleFaceCountCF
        instancedFaceCount = instancedFaceCountCF
        blazeFaceCount = blazeFaceCountCF
        drawCallCountCF = 0
        batchCountCF = 0
        singleFaceCountCF = 0
        instancedFaceCountCF = 0
        blazeFaceCountCF = 0
    }

    open fun recordBatches(batchCount: Int) { batchCountCF += batchCount }

    open fun recordDrawCall(renderCall: BatchManager.RenderCall?) {
        drawCallCountCF++
        if (renderCall!!.instanced) instancedFaceCountCF += renderCall.faceCount
        else singleFaceCountCF += renderCall.faceCount
    }

    open fun recordBlazeAction(faceCount: Int) { blazeFaceCountCF += faceCount }
}
