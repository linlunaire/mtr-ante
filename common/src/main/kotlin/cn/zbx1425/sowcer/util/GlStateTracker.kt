package cn.zbx1425.sowcer.util

/** Nested preparation scopes are thread-local even though the legacy diagnostic flag is global. */
class GlStateTracker {
    companion object {
        private val DEPTH: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }
        @Volatile @JvmField var isStateProtected: Boolean = false

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun capture() {
            DEPTH.set(DEPTH.get() + 1)
            isStateProtected = true
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun restore() {
            val depth = DEPTH.get()
            if (depth == 0) throw IllegalStateException("Render preparation scope not captured")
            DEPTH.set(depth - 1)
            isStateProtected = depth > 1
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun assertProtected() {
            if (DEPTH.get() == 0) throw IllegalStateException("Render preparation scope not protected")
        }
    }
}
