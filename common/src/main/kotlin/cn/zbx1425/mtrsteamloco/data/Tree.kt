package cn.zbx1425.mtrsteamloco.data

import mtr.mappings.Text
import net.minecraft.network.chat.MutableComponent
import java.util.function.Function
import java.util.regex.Pattern

@JvmSuppressWildcards
open class Tree<T> {
    abstract class Node<T>(@JvmField val key: String?, name: String?, @JvmField val parent: Node<T>?) : Tree<T>() {
        @JvmField var name: MutableComponent? = Text.translatable(name)

        protected open fun dealWithDuplicateNames() {}
        abstract fun copy(): Node<T>

        open fun getPathName(): MutableComponent {
            var result = name!!.copy()
            var current = parent
            while (current != null) {
                result = current.name!!.copy().append(Text.literal("/")).append(result)
                current = current.parent
            }
            return result
        }

        open fun getPathKey(): String? {
            var result = key
            var current = parent
            while (current != null) {
                result = current.key + "/" + result
                current = current.parent
            }
            return result
        }

        open fun getDepth(): Int {
            var result = 0
            var current = parent
            while (current != null) { result++; current = current.parent }
            return result
        }

        override fun toString(): String = getPathName().string

        // Kotlin cannot call a protected method on a sibling subtype. This
        // synthetic bridge preserves virtual dispatch without expanding Java API.
        @JvmSynthetic internal fun resolveDuplicateNames() = dealWithDuplicateNames()
    }

    open class Branch<T>(key: String?, name: String?, parent: Node<T>?) : Node<T>(key, name, parent) {
        @JvmField var branches: MutableMap<String?, Branch<T>?>? = HashMap()
        @JvmField var leaves: MutableMap<String?, Data<T>?>? = HashMap()

        open fun addBranch(key: String?, name: String?): Branch<T> {
            java.util.Objects.requireNonNull(name)
            java.util.Objects.requireNonNull(key)
            return branches!!.computeIfAbsent(key) { Branch(it, name, this) }!!
        }

        open fun addLeaf(key: String?, name: String?, data: T): Data<T> {
            java.util.Objects.requireNonNull(name)
            java.util.Objects.requireNonNull(key)
            return leaves!!.computeIfAbsent(key) { Data(it, name, this, data) }!!
        }

        open fun getBranch(key: String?): Branch<T>? = branches!![key]
        open fun getLeaf(key: String?): Data<T>? = leaves!![key]

        override fun dealWithDuplicateNames() {
            val counts = HashMap<String, Int>()
            for ((_, branch) in branches!!) countName(counts, branch!!.name!!.string)
            for ((_, leaf) in leaves!!) countName(counts, leaf!!.name!!.string)
            for (branch in branches!!.values) suffixDuplicate(counts, branch!!)
            for (leaf in leaves!!.values) suffixDuplicate(counts, leaf!!)
        }

        open fun getNodes(): MutableMap<String?, Node<T>?> {
            val result = LinkedHashMap<String?, Node<T>?>()
            result.putAll(branches!!)
            result.putAll(leaves!!)
            return result
        }

        open fun hasSubBranches(): Boolean = branches!!.isNotEmpty()

        open fun mergeLevel(): MutableMap<String?, Data<T>?> {
            val result = HashMap<String?, Data<T>?>()
            for ((key, leaf) in leaves!!) result[key] = leaf
            for ((_, branch) in branches!!) result.putAll(branch!!.mergeLevel())
            val counts = HashMap<String, Int>()
            for (data in result.values) countName(counts, data!!.name!!.string)
            for (data in result.values) suffixDuplicate(counts, data!!)
            return result
        }

        override fun copy(): Branch<T> {
            val result = Branch(key, name!!.string, parent)
            for ((key, branch) in branches!!) result.branches!![key] = branch!!.copy()
            for ((key, leaf) in leaves!!) result.leaves!![key] = leaf!!.copy()
            return result
        }
    }

    open class Root<T>(name: String?) : Branch<T>("root", name, null)

    open class Data<T>(key: String?, name: String?, parent: Node<T>?, @JvmField var data: T) : Node<T>(key, name, parent) {
        override fun copy(): Data<T> = Data(key, name!!.string, parent, data)
    }

    companion object {
        private val pathSeparator = Pattern.compile("/")

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic
        open fun <T> loadTree(rootName: String?, map: MutableMap<String?, T>?, funGetName: Function<T, String?>?): Root<T> {
            val root = Root<T>(rootName)
            for ((key, data) in map!!) {
                val path = pathSeparator.split(key!!, -1)
                if (path.isEmpty()) continue
                var branch: Branch<T> = root
                for (i in 0 until path.size - 1) branch = branch.addBranch(path[i], path[i])
                branch.addLeaf(path.last(), funGetName!!.apply(data), data)
            }
            root.resolveDuplicateNames()
            return root
        }

        private fun countName(counts: MutableMap<String, Int>, name: String) {
            counts[name] = (counts[name] ?: 0) + 1
        }

        private fun suffixDuplicate(counts: Map<String, Int>, node: Node<*>) {
            // Look up the live name again: aliased components can have changed
            // during an earlier suffix, matching the original snapshot behavior.
            if ((counts[node.name!!.string] ?: 0) > 1) node.name!!.append(Text.literal("(" + node.key + ")"))
        }
    }
}
