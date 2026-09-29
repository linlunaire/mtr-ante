package cn.zbx1425.mtrsteamloco.data

import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.AbstractPackResources
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.pack.PackMetadataSection
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.FallbackResourceManager
import net.minecraft.server.packs.resources.IoSupplier
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.server.packs.resources.ReloadableResourceManager
import net.minecraft.util.InclusiveRange
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Optional

open class DynamicResource {
    companion object {
        private var MPRM: MultiPackResourceManager? = null
        private var DYNAMIC_PACK: DynamicPack? = null
        private val ADDED_NAMESPACES = HashSet<String>()

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun addResourcesClient(loc: Identifier?, funGetStream: IoSupplier<InputStream>?) {
            addResources((Minecraft.getInstance().resourceManager as ReloadableResourceManager).resources as MultiPackResourceManager, loc, funGetStream)
        }

        // Kotlin has no package-private visibility. Keep this internal seam hidden from Java source.
        @JvmStatic @JvmSynthetic @JvmName("addResources")
        internal fun addResources(manager: MultiPackResourceManager?, loc: Identifier?, funGetStream: IoSupplier<InputStream>?) {
            if (MPRM !== manager) {
                MPRM = manager
                DYNAMIC_PACK = DynamicPack()
                MPRM!!.packs = ArrayList(MPRM!!.packs)
                MPRM!!.packs.add(DYNAMIC_PACK!!)
                ADDED_NAMESPACES.clear()
            }
            DYNAMIC_PACK!!.addResource(PackType.CLIENT_RESOURCES, loc, funGetStream)
            if (ADDED_NAMESPACES.add(loc!!.namespace)) {
                MPRM!!.namespacedManagers.computeIfAbsent(loc.namespace) { namespace ->
                    FallbackResourceManager(PackType.CLIENT_RESOURCES, namespace)
                }.push(DYNAMIC_PACK!!)
            }
        }
    }

    private class DynamicPack : AbstractPackResources(
        PackLocationInfo(NAME, Component.literal(DISPLAY_NAME), PackSource.DEFAULT, Optional.empty())
    ) {
        private val metadata: ByteArray
        private val resources = HashMap<PackType?, MutableMap<Identifier?, IoSupplier<InputStream>?>>()
        private val namespaces = HashMap<PackType?, MutableSet<String>>()

        init {
            val section = PackMetadataSection(Component.literal(DISPLAY_NAME), InclusiveRange(SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES)))
            val root = JsonObject()
            root.add(PackMetadataSection.CLIENT_TYPE.name(), PackMetadataSection.CLIENT_TYPE.codec().encodeStart(JsonOps.INSTANCE, section).orThrow)
            metadata = root.toString().toByteArray(StandardCharsets.UTF_8)
        }

        fun addResource(type: PackType?, loc: Identifier?, supplier: IoSupplier<InputStream>?) {
            resources.computeIfAbsent(type) { HashMap() }[loc] = supplier
            namespaces.computeIfAbsent(type) { HashSet() }.add(loc!!.namespace)
        }

        override fun getRootResource(vararg path: String): IoSupplier<InputStream>? =
            if (path.size == 1 && PackResources.PACK_META == path[0]) IoSupplier { ByteArrayInputStream(metadata) } else null

        override fun getResource(type: PackType, loc: Identifier): IoSupplier<InputStream>? = resources[type]?.get(loc)

        override fun listResources(type: PackType, namespace: String, path: String, output: PackResources.ResourceOutput) {
            val map = resources[type] ?: return
            val prefix = if (path.isEmpty() || path.endsWith('/')) path else "$path/"
            for ((loc, supplier) in map) {
                if (loc!!.namespace == namespace && loc.path.startsWith(prefix)) output.accept(loc, forwardNullable(supplier))
            }
        }

        override fun getNamespaces(type: PackType): Set<String> = java.util.Set.copyOf(namespaces[type] ?: emptySet())

        override fun close() {}

        companion object {
            private const val NAME = "ANTE Virtual Dynamic Pack"
            private const val DISPLAY_NAME = "YLM-ANTE Virtual Dynamic Pack"
            // A legacy nullable supplier is forwarded unchanged despite ResourceOutput's annotation.
            @Suppress("UNCHECKED_CAST") private fun <T> forwardNullable(value: T?): T = value as T
        }
    }
}
