package cn.zbx1425.sowcerext.model.loader

import cn.zbx1425.mtrsteamloco.BuildConfig
import cn.zbx1425.sowcer.batch.MaterialProp
import cn.zbx1425.sowcerext.model.Face
import cn.zbx1425.sowcerext.model.RawMesh
import cn.zbx1425.sowcerext.model.RawModel
import cn.zbx1425.sowcerext.model.Vertex
import cn.zbx1425.sowcerext.reuse.AtlasManager
import cn.zbx1425.sowcerext.util.ResourceUtil
import cn.zbx1425.sowcer.math.Vector3f
import de.javagl.obj.*
import mtr.mappings.Utilities
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.io.InputStream

@JvmSuppressWildcards
open class ObjModelLoader {
    companion object {
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic @Throws(IOException::class)
        open fun loadModel(obj: InputStream?, mtl: InputStream?, location: Identifier?, atlasManager: AtlasManager?): RawModel {
            val srcObj = ObjReader.read(obj)
            val materials = loadMaterials(mtl)
            val model = loadModel(srcObj, location, materials, atlasManager)
            model.sourceLocation = location
            return model
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic @Throws(IOException::class)
        open fun loadModels(obj: InputStream?, mtl: InputStream?, location: Identifier?, atlasManager: AtlasManager?): MutableMap<String, RawModel> {
            val srcObj = ObjReader.read(obj)
            val materials = loadMaterials(mtl)
            val result = HashMap<String, RawModel>()
            val groupObjs = ObjSplitting.splitByGroups(srcObj)
            for (groupEntry in groupObjs.entries) {
                val model = loadModel(groupEntry.value, location, materials, atlasManager)
                val compliantKey = compliantPath(groupEntry.key)
                model.sourceLocation = Identifier.fromNamespaceAndPath(location!!.namespace, location.path + "/" + compliantKey)
                result[groupEntry.key] = model
            }
            return result
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic @Throws(IOException::class)
        open fun loadModel(resourceManager: ResourceManager?, objLocation: Identifier?, atlasManager: AtlasManager?): RawModel {
            val srcObj = ObjReader.read(Utilities.getInputStream(getResource(resourceManager, objLocation)))
            val materials = loadMaterials(resourceManager, srcObj, objLocation)
            val model = loadModel(srcObj, objLocation, materials, atlasManager)
            model.sourceLocation = objLocation
            return model
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic @Throws(IOException::class)
        open fun loadModels(resourceManager: ResourceManager?, objLocation: Identifier?, atlasManager: AtlasManager?): MutableMap<String, RawModel> {
            val srcObj = ObjReader.read(Utilities.getInputStream(getResource(resourceManager, objLocation)))
            val materials = loadMaterials(resourceManager, srcObj, objLocation)
            val result = HashMap<String, RawModel>()
            val groupObjs = ObjSplitting.splitByGroups(srcObj)
            for (groupEntry in groupObjs.entries) {
                val model = loadModel(groupEntry.value, objLocation, materials, atlasManager)
                val compliantKey = compliantPath(groupEntry.key)
                model.sourceLocation = Identifier.fromNamespaceAndPath(objLocation!!.namespace, objLocation.path + "/" + compliantKey)
                result[groupEntry.key] = model
            }
            return result
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic @Throws(IOException::class)
        open fun loadExternalModels(path: String?, atlasManager: AtlasManager?): MutableMap<String, RawModel> {
            BufferedInputStream(FileInputStream(path)).use { fis ->
                val srcObj = ObjReader.read(fis)
                val result = HashMap<String, RawModel>()
                val groupObjs = ObjSplitting.splitByGroups(srcObj)
                for (groupEntry in groupObjs.entries) {
                    val model = loadModel(groupEntry.value, null, null, atlasManager)
                    val compliantPath = compliantPath(path!!)
                    val compliantKey = compliantPath(groupEntry.key)
                    model.sourceLocation = Identifier.fromNamespaceAndPath("mtrsteamloco-external", compliantPath + "/" + compliantKey)
                    result[groupEntry.key] = model
                }
                return result
            }
        }

        private fun loadModel(srcObj: Obj, objLocation: Identifier?, materials: MutableMap<String, Mtl>?, atlasManager: AtlasManager?): RawModel {
            val mtlObjs = ObjSplitting.splitByMaterialGroups(srcObj)
            val model = RawModel()
            for (entry in mtlObjs.entries) {
                if (entry.value.numFaces == 0) continue
                val materialOptions = splitMaterialOptions(entry.key)
                val materialGroupName = materialOptions[""]
                val meshRenderType = materialOptions.getOrDefault("#", "exterior").lowercase(Locale.ROOT)
                val flipV = materialOptions.getOrDefault("flipv", "0") == "1"
                val materialProp = MaterialProp()
                if ((materials != null && materials.size > 0) && objLocation != null) {
                    val objMaterial = materials.getOrDefault(entry.key, null)
                    if (objMaterial != null) {
                        if (!StringUtils.isEmpty(objMaterial.mapKd)) {
                            materialProp.texture = ResourceUtil.resolveRelativePath(objLocation, objMaterial.mapKd, ".png")
                        }
                        val color = objMaterial.kd
                        materialProp.attrState!!.setColor((color.x * 255).toInt(), (color.y * 255).toInt(), (color.z * 255).toInt(), (objMaterial.d * 255).toInt())
                    }
                } else if (objLocation != null) {
                    materialProp.texture = if (materialGroupName!! == "_") null else ResourceUtil.resolveRelativePath(objLocation, materialGroupName, ".png")
                    materialProp.attrState!!.setColor(255, 255, 255, 255)
                } else {
                    materialProp.texture = null
                    materialProp.attrState!!.setColor(255, 255, 255, 255)
                }
                val renderObjMesh = ObjUtils.convertToRenderable(entry.value)
                val mesh = RawMesh(materialProp)
                mesh.setRenderType(meshRenderType)
                var i = 0
                while (i < renderObjMesh.numVertices) {
                    val pos = renderObjMesh.getVertex(i)
                    val normal = if (i < renderObjMesh.numNormals) renderObjMesh.getNormal(i) else ZeroFloatTuple.ZERO3
                    val uv = if (i < renderObjMesh.numTexCoords) renderObjMesh.getTexCoord(i) else ZeroFloatTuple.ZERO2
                    val seVertex = Vertex(Vector3f(pos.x, pos.y, pos.z), Vector3f(normal.x, normal.y, normal.z))
                    seVertex.u = uv.x
                    seVertex.v = if (flipV) 1 - uv.y else uv.y
                    mesh.vertices!!.add(seVertex)
                    i++
                }
                i = 0
                while (i < renderObjMesh.numFaces) {
                    val face = renderObjMesh.getFace(i)
                    mesh.faces!!.add(Face(intArrayOf(face.getVertexIndex(0), face.getVertexIndex(1), face.getVertexIndex(2))))
                    i++
                }
                if (atlasManager != null) atlasManager.applyToMesh(mesh)
                mesh.validateVertIndex()
                model.append(mesh)
            }
            model.generateNormals()
            model.distinct()
            return model
        }

        @Throws(IOException::class)
        private fun loadMaterials(resourceManager: ResourceManager?, srcObj: Obj, objLocation: Identifier?): MutableMap<String, Mtl> {
            val materials = HashMap<String, Mtl>()
            for (mtlFileName in srcObj.mtlFileNames) {
                materials.putAll(loadMaterials(Utilities.getInputStream(getResource(resourceManager, ResourceUtil.resolveRelativePath(objLocation, mtlFileName, ".mtl")))))
            }
            return materials
        }

        @Throws(IOException::class)
        private fun loadMaterials(mtlIn: InputStream?): MutableMap<String, Mtl> {
            val srcMtls = MtlReader.read(mtlIn)
            val materials = HashMap<String, Mtl>()
            for (mtl in srcMtls) materials[mtl.name] = mtl
            return materials
        }

        private fun splitMaterialOptions(src: String): MutableMap<String, String> {
            val result = HashMap<String, String>()
            val majorParts = splitJava(src, "#", 2)
            result[""] = majorParts[0]
            if (majorParts.size > 1) {
                for (minorPart in splitJava(majorParts[1], ",", 0)) {
                    val tokens = splitJava(minorPart, "=", 2)
                    if (tokens.size > 1) result[tokens[0]] = tokens[1]
                    else if (!result.containsKey("#")) result["#"] = tokens[0]
                    else result[tokens[0].lowercase(Locale.ROOT)] = "1"
                }
            }
            return result
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic @Throws(IOException::class)
        open fun saveModels(models: MutableMap<String?, RawModel?>?, objOutputFile: Path?, mtlOutputFile: Path?, withNormal: Boolean) {
            val mtlFileName = mtlOutputFile!!.fileName!!.toString()
            PrintWriter(Files.newOutputStream(objOutputFile)).use { objFile ->
                PrintWriter(Files.newOutputStream(mtlOutputFile)).use { mtlFile ->
                    objFile.println("# Generated by MTR-ANTE " + BuildConfig.MOD_VERSION)
                    mtlFile.println("# Generated by MTR-ANTE " + BuildConfig.MOD_VERSION)
                    val writtenMaterials = HashSet<String>()
                    var vertOffset = 1
                    objFile.println("mtllib " + mtlFileName)
                    for (groupEntry in models!!.entries) {
                        objFile.println("g " + groupEntry.key)
                        for (matEntry in groupEntry.value!!.meshList!!.entries) {
                            val textureName = if (matEntry.key!!.texture == null) "_" else FilenameUtils.getBaseName(matEntry.key!!.texture!!.path)
                            val renderType = when (matEntry.key!!.shaderName!!) {
                                "rendertype_entity_cutout" -> if (matEntry.key!!.attrState!!.lightmapUV != null) "interior" else "exterior"
                                "rendertype_entity_translucent_cull" -> if (matEntry.key!!.attrState!!.lightmapUV != null) "interiortranslucent" else "exteriortranslucent"
                                "rendertype_beacon_beam" -> if (matEntry.key!!.translucent) "lighttranslucent" else "light"
                                else -> "exterior"
                            }
                            if (!writtenMaterials.contains(textureName + "#" + renderType)) {
                                writtenMaterials.add(textureName + "#" + renderType)
                                mtlFile.println("newmtl " + textureName + "#" + renderType)
                                mtlFile.println("Kd 1.0 1.0 1.0")
                                mtlFile.println("map_Kd " + textureName + ".png")
                            }
                            objFile.println("usemtl " + textureName + "#" + renderType)
                            val mesh = matEntry.value
                            for (vertex: Vertex? in mesh!!.vertices!!) {
                                objFile.printf("v %f %f %f\n", vertex!!.position!!.x(), vertex.position!!.y(), vertex.position!!.z())
                                if (withNormal) objFile.printf("vn %f %f %f\n", vertex.normal!!.x(), vertex.normal!!.y(), vertex.normal!!.z())
                                objFile.printf("vt %f %f\n", vertex.u, 1 - vertex.v)
                            }
                            for (face: Face? in mesh.faces!!) {
                                if (withNormal) {
                                    objFile.print("f")
                                    for (vertex in face!!.vertices!!) objFile.printf(" %d/%d/%d", vertex + vertOffset, vertex + vertOffset, vertex + vertOffset)
                                    objFile.println()
                                } else {
                                    objFile.print("f")
                                    for (vertex in face!!.vertices!!) objFile.printf(" %d/%d", vertex + vertOffset, vertex + vertOffset)
                                    objFile.println()
                                }
                            }
                            vertOffset += mesh.vertices!!.size
                        }
                    }
                }
            }
        }

        private fun compliantPath(value: String): String = value.lowercase(Locale.ROOT).replace('\\', '/').replace(Regex("[^a-z0-9/._-]"), "_")

        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private fun splitJava(value: String, regex: String, limit: Int): Array<String> = (value as java.lang.String).split(regex, limit)

        // Evaluate a relative-resource argument before failing a null manager receiver.
        private fun getResource(manager: ResourceManager?, location: Identifier?) = manager!!.getResource(forwardNullable(location))

        @Suppress("UNCHECKED_CAST")
        private fun <T> forwardNullable(value: T?): T = value as T
    }

    private class ZeroFloatTuple(private val dimensions: Int) : FloatTuple {
        override fun getX(): Float = 0F
        override fun getY(): Float = 0F
        override fun getZ(): Float = 0F
        override fun getW(): Float = 0F
        override fun get(index: Int): Float = 0F
        override fun getDimensions(): Int = dimensions
        companion object {
            val ZERO2 = ZeroFloatTuple(2)
            val ZERO3 = ZeroFloatTuple(3)
        }
    }
}
