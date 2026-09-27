package cn.zbx1425.sowcerext.model.loader

import cn.zbx1425.sowcer.batch.MaterialProp
import cn.zbx1425.sowcerext.model.Face
import cn.zbx1425.sowcerext.model.RawMesh
import cn.zbx1425.sowcerext.model.RawModel
import cn.zbx1425.sowcerext.model.Vertex
import cn.zbx1425.sowcer.vertex.VertAttrState
import cn.zbx1425.sowcerext.reuse.AtlasManager
import cn.zbx1425.sowcerext.util.Logging
import cn.zbx1425.sowcerext.util.ResourceUtil
import cn.zbx1425.sowcer.math.Vector3f
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import org.apache.commons.lang3.StringUtils
import java.io.IOException
import java.util.ArrayList
import java.util.Locale
import java.util.function.Function

open class CsvModelLoader {
    companion object {
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic @Throws(IOException::class)
        open fun loadModel(resourceManager: ResourceManager?, objLocation: Identifier?, atlasManager: AtlasManager?): RawModel {
            val rawModelData = ResourceUtil.readResource(resourceManager, objLocation)
            val rawModelLines = splitJava(rawModelData, "[\\r\\n]+")
            val builtMeshList = ArrayList<RawMesh>()
            var isGLCoords = false
            var buildingMesh = RawMesh(MaterialProp("rendertype_entity_cutout"))
            for (rawLine in rawModelLines) {
                var line = rawLine
                try {
                    if (line.contains(';')) line = splitJava(line, ";", 2)[0]
                    // The legacy format uses the caller's locale and Java's ASCII trim.
                    line = line.trim { it <= ' ' }.lowercase(Locale.getDefault())
                    if (StringUtils.isEmpty(line)) continue
                    val tokens = splitJava(line, ",")
                    for (i in tokens.indices) tokens[i] = tokens[i].trim { it <= ' ' }
                    if (tokens.isEmpty() || StringUtils.isEmpty(tokens[0])) continue
                    when (tokens[0]) {
                        "createmeshbuilder" -> {
                            buildingMesh.validateVertIndex()
                            if (buildingMesh.faces!!.isNotEmpty()) builtMeshList.add(buildingMesh)
                            buildingMesh = RawMesh(MaterialProp("rendertype_entity_cutout"))
                        }
                        "addvertex" -> {
                            when (tokens.size) {
                                4 -> buildingMesh.vertices!!.add(Vertex(Vector3f(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())))
                                7 -> buildingMesh.vertices!!.add(Vertex(
                                    Vector3f(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat()),
                                    Vector3f(tokens[4].toFloat(), tokens[5].toFloat(), tokens[6].toFloat())))
                                else -> throw IllegalArgumentException("Invalid AddVertex command.")
                            }
                        }
                        "addface", "addface2" -> {
                            if (tokens.size < 4) throw IllegalArgumentException("Invalid AddFace/AddFace2 command.")
                            val vertIndices = IntArray(tokens.size - 1)
                            for (i in 1 until tokens.size) vertIndices[i - 1] = tokens[i].toInt()
                            buildingMesh.faces!!.addAll(Face.triangulate(vertIndices, tokens[0] == "addface2"))
                        }
                        "setcolor", "setcolorall" -> {
                            val params = parseParams(tokens, arrayOf(0, 0, 0, 255), Integer::parseInt)
                            if (tokens[0] == "setcolorall") for (mesh in builtMeshList) {
                                mesh.materialProp!!.attrState!!.setColor(params[0], params[1], params[2], params[3])
                            }
                            buildingMesh.materialProp!!.attrState!!.setColor(params[0], params[1], params[2], params[3])
                        }
                        "loadtexture" -> {
                            if (tokens.size < 2) throw IllegalArgumentException("Invalid LoadTexture command.")
                            buildingMesh.materialProp!!.texture = ResourceUtil.resolveRelativePath(objLocation, tokens[1], ".png")
                        }
                        "settexturecoordinates" -> {
                            if (tokens.size < 4) throw IllegalArgumentException("Invalid SetTextureCoordinates command.")
                            val vertId = tokens[1].toInt()
                            if (vertId < 0 || vertId >= buildingMesh.vertices!!.size) throw IndexOutOfBoundsException("Invalid vertex index in SetTextureCoordinates.")
                            buildingMesh.vertices!![vertId]!!.u = tokens[2].toFloat()
                            buildingMesh.vertices!![vertId]!!.v = tokens[3].toFloat()
                        }
                        "generatenormals" -> Unit
                        "cube" -> {
                            val params = parseParams(tokens, arrayOf(1F, 0F, 0F), java.lang.Float::parseFloat)
                            if (params[1] == 0F) params[1] = params[0]
                            if (params[2] == 0F) params[2] = params[0]
                            createCube(buildingMesh, params[0], params[1], params[2])
                        }
                        "cylinder" -> {
                            val params = parseParams(tokens, arrayOf(8F, 1F, 1F, 1F), java.lang.Float::parseFloat)
                            createCylinder(buildingMesh, Math.round(params[0]), params[1], params[2], params[3])
                        }
                        "translate", "translateall" -> {
                            val params = parseParams(tokens, arrayOf(0F, 0F, 0F), java.lang.Float::parseFloat)
                            if (tokens[0] == "translateall") for (mesh in builtMeshList) mesh.applyTranslation(params[0], params[1], params[2])
                            buildingMesh.applyTranslation(params[0], params[1], params[2])
                        }
                        "scale", "scaleall" -> {
                            val params = parseParams(tokens, arrayOf(1F, 1F, 1F), java.lang.Float::parseFloat)
                            if (tokens[0] == "scaleall") for (mesh in builtMeshList) mesh.applyScale(params[0], params[1], params[2])
                            buildingMesh.applyScale(params[0], params[1], params[2])
                        }
                        "rotate", "rotateall" -> {
                            val params = parseParams(tokens, arrayOf(0F, 0F, 0F, 0F), java.lang.Float::parseFloat)
                            if (tokens[0] == "rotateall") for (mesh in builtMeshList) mesh.applyRotation(Vector3f(params[0], params[1], params[2]), params[3])
                            buildingMesh.applyRotation(Vector3f(params[0], params[1], params[2]), params[3])
                        }
                        "shear", "shearall" -> {
                            val params = parseParams(tokens, arrayOf(0F, 0F, 0F, 0F, 0F, 0F, 0F), java.lang.Float::parseFloat)
                            if (tokens[0] == "shearall") for (mesh in builtMeshList) mesh.applyShear(
                                Vector3f(params[0], params[1], params[2]), Vector3f(params[3], params[4], params[5]), params[6])
                            buildingMesh.applyShear(Vector3f(params[0], params[1], params[2]), Vector3f(params[3], params[4], params[5]), params[6])
                        }
                        "mirror", "mirrorall" -> {
                            val params = parseParams(tokens, arrayOf(0, 0, 0, 0, 0, 0), Integer::parseInt)
                            if (tokens.size < 5) {
                                params[3] = params[0]
                                params[4] = params[1]
                                params[5] = params[2]
                            }
                            if (tokens[0] == "mirrorall") for (mesh in builtMeshList) mesh.applyMirror(
                                params[0] != 0, params[1] != 0, params[2] != 0, params[3] != 0, params[4] != 0, params[5] != 0)
                            buildingMesh.applyMirror(params[0] != 0, params[1] != 0, params[2] != 0, params[3] != 0, params[4] != 0, params[5] != 0)
                        }
                        "setemissivecolor", "setemissivecolorall", "setblendmode", "setwrapmode", "setdecaltransparentcolor", "enablecrossfading" -> Unit
                        "setrendertype", "setrendertypeall" -> {
                            // Extension.
                            if (tokens[0] == "setrendertypeall") for (mesh in builtMeshList) mesh.setRenderType(tokens[1])
                            buildingMesh.setRenderType(tokens[1])
                        }
                        "setbillboard" -> buildingMesh.setMatixProcess(VertAttrState.BILLBOARD)
                        "setisglcoords" -> isGLCoords = tokens[1] == "true"
                        "uvmirror", "uvmirrorall" -> {
                            val params = parseParams(tokens, arrayOf(0, 0), Integer::parseInt)
                            if (tokens[0] == "uvmirrorall") for (mesh in builtMeshList) mesh.applyUVMirror(params[0] != 0, params[1] != 0)
                            buildingMesh.applyUVMirror(params[0] != 0, params[1] != 0)
                        }
                        else -> Logging.LOGGER.warn("Unknown CSV command: " + tokens[0])
                    }
                } catch (ex: Exception) {
                    Logging.LOGGER.error("Failed loading CSV model $objLocation, line \"$line\": $ex")
                }
            }
            buildingMesh.validateVertIndex()
            if (buildingMesh.faces!!.isNotEmpty()) builtMeshList.add(buildingMesh)
            val model = RawModel()
            model.sourceLocation = objLocation
            for (mesh in builtMeshList) {
                if (!isGLCoords) mesh.applyScale(-1F, 1F, 1F) // Convert DirectX coords to OpenGL coords.
                if (atlasManager != null) atlasManager.applyToMesh(mesh)
                model.append(mesh)
            }
            model.generateNormals()
            model.distinct()
            return model
        }

        private fun <T> parseParams(tokens: Array<String>, defaults: Array<T>, parser: Function<String, T>): Array<T> {
            val result = defaults.clone()
            for (i in defaults.indices) {
                if (i + 1 >= tokens.size) return result
                val value = tokens[i + 1].trim { it <= ' ' }
                if (StringUtils.isEmpty(value)) continue
                result[i] = parser.apply(value)
            }
            return result
        }

        private fun createCube(mesh: RawMesh, sx: Float, sy: Float, sz: Float) {
            val vertices = mesh.vertices!!
            val faces = mesh.faces!!
            val v = vertices.size
            vertices.add(Vertex(Vector3f(sx, sy, -sz)))
            vertices.add(Vertex(Vector3f(sx, -sy, -sz)))
            vertices.add(Vertex(Vector3f(-sx, -sy, -sz)))
            vertices.add(Vertex(Vector3f(-sx, sy, -sz)))
            vertices.add(Vertex(Vector3f(sx, sy, sz)))
            vertices.add(Vertex(Vector3f(sx, -sy, sz)))
            vertices.add(Vertex(Vector3f(-sx, -sy, sz)))
            vertices.add(Vertex(Vector3f(-sx, sy, sz)))
            faces.add(Face(intArrayOf(v, v + 1, v + 2)))
            faces.add(Face(intArrayOf(v, v + 2, v + 3)))
            faces.add(Face(intArrayOf(v, v + 4, v + 5)))
            faces.add(Face(intArrayOf(v, v + 5, v + 1)))
            faces.add(Face(intArrayOf(v, v + 3, v + 7)))
            faces.add(Face(intArrayOf(v, v + 7, v + 4)))
            faces.add(Face(intArrayOf(v + 6, v + 5, v + 4)))
            faces.add(Face(intArrayOf(v + 6, v + 4, v + 7)))
            faces.add(Face(intArrayOf(v + 6, v + 7, v + 3)))
            faces.add(Face(intArrayOf(v + 6, v + 3, v + 2)))
            faces.add(Face(intArrayOf(v + 6, v + 2, v + 1)))
            faces.add(Face(intArrayOf(v + 6, v + 1, v + 5)))
        }

        // Create cylinder, retaining the original cap topology and float operation order.
        private fun createCylinder(mesh: RawMesh, n: Int, radius1: Float, radius2: Float, h: Float) {
            val upperCap = radius1 > 0.0
            val lowerCap = radius2 > 0.0
            val m = (if (upperCap) 1 else 0) + (if (lowerCap) 1 else 0)
            val r1 = Math.abs(radius1)
            val r2 = Math.abs(radius2)
            val ns = if (h >= 0F) 1F else -1F
            val d = (2.0 * Math.PI / n).toFloat()
            val g = 0.5F * h
            var t = 0F
            val a = (if (h != 0F) Math.atan(((r2 - r1) / h).toDouble()) else 0.0).toFloat()
            val vertices = mesh.vertices!!
            val faces = mesh.faces!!
            val v = vertices.size
            for (i in 0 until n) {
                val dx = Math.cos(t.toDouble()).toFloat()
                val dz = Math.sin(t.toDouble()).toFloat()
                val normal = Vector3f(dx * ns, 0F, dz * ns)
                val s = normal.copy()
                s.cross(Vector3f(0F, -1F, 0F))
                normal.rot(s, a)
                vertices.add(Vertex(Vector3f(dx * r1, g, dz * r1), normal))
                vertices.add(Vertex(Vector3f(dx * r2, -g, dz * r2), normal))
                t += d
            }
            for (i in 0 until n) {
                val i0 = (2 * i + 2) % (2 * n)
                val i1 = (2 * i + 3) % (2 * n)
                val i2 = 2 * i + 1
                val i3 = 2 * i
                faces.add(Face(intArrayOf(v + i0, v + i1, v + i2)))
                faces.add(Face(intArrayOf(v + i0, v + i2, v + i3)))
            }
            for (i in 0 until m) {
                val verts = ArrayList<Int>()
                for (j in 0 until n) {
                    if (verts.size > 2) {
                        verts.add(verts[0])
                        verts.add(verts[verts.size - 2])
                    }
                    if ((i == 0) and lowerCap) verts.add(v + 2 * j + 1)
                    else verts.add(v + 2 * (n - j - 1))
                }
                verts.add(verts[0])
                verts.add(verts[verts.size - 1])
                verts.add(verts[1])
                for (j in 0 until verts.size / 3) faces.add(Face(intArrayOf(verts[j * 3], verts[j * 3 + 1], verts[j * 3 + 2])))
            }
        }

        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private fun splitJava(value: String, regex: String, limit: Int = 0): Array<String> = (value as java.lang.String).split(regex, limit)
    }
}
