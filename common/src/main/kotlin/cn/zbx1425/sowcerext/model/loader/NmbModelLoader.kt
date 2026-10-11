package cn.zbx1425.sowcerext.model.loader

import cn.zbx1425.sowcerext.model.RawMesh
import cn.zbx1425.sowcerext.model.RawModel
import cn.zbx1425.sowcerext.reuse.AtlasManager
import mtr.mappings.Utilities
import mtr.mappings.UtilitiesClient
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays

open class NmbModelLoader {
    companion object {
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic @Throws(IOException::class)
        open fun loadModel(resourceManager: ResourceManager?, location: Identifier?, atlasManager: AtlasManager?): RawModel {
            val resources: List<Resource> = UtilitiesClient.getResources(resourceManager, location)
            if (resources.size < 1) throw FileNotFoundException()
            val dis = DataInputStream(Utilities.getInputStream(resources[0]))
            // Header failures have historically occurred before the stream-closing region.
            dis.skipNBytes(8)
            dis.readInt()
            dis.readInt()
            val dContent: ByteArray
            try {
                val sha256 = MessageDigest.getInstance("SHA-256")
                val key = dis.readNBytes(32)
                val aesKey = SecretKeySpec(key, "AES")
                val iv = Arrays.copyOfRange(sha256.digest(key), 0, 16)
                val aesIv = IvParameterSpec(iv)
                val len = dis.readInt()
                val eContent = dis.readNBytes(len)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIv)
                dContent = cipher.doFinal(eContent)
            } catch (ex: Exception) {
                throw IOException(ex)
            } finally {
                // Deliberately not use(): a close failure replaces the decrypt failure.
                dis.close()
            }
            val model = RawModel(DataInputStream(ByteArrayInputStream(dContent)))
            model.sourceLocation = location
            if (atlasManager != null) {
                for (mesh: RawMesh? in model.meshList!!.values) atlasManager.applyToMesh(mesh)
            }
            return model
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic @Throws(IOException::class)
        open fun serializeModel(model: RawModel?, os: OutputStream?, withRaw: Boolean) {
            val bos = ByteArrayOutputStream(8192)
            model!!.serializeTo(DataOutputStream(bos))
            val eContent: ByteArray
            val key: ByteArray
            try {
                val sha256 = MessageDigest.getInstance("SHA-256")
                val keyGenerator = KeyGenerator.getInstance("AES")
                keyGenerator.init(256)
                key = keyGenerator.generateKey().encoded
                val aesKey = SecretKeySpec(key, "AES")
                val iv = Arrays.copyOfRange(sha256.digest(key), 0, 16)
                val aesIv = IvParameterSpec(iv)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
                eContent = cipher.doFinal(bos.toByteArray())
            } catch (ex: Exception) {
                throw IOException(ex)
            }
            val dos = DataOutputStream(os)
            dos.write("ZBXNMB10".toByteArray(StandardCharsets.UTF_8))
            dos.writeInt(1)
            dos.writeInt(0)
            dos.write(key)
            dos.writeInt(eContent.size)
            dos.write(eContent)
            if (withRaw) {
                for (i in 0 until 16) dos.writeInt(0)
                dos.write(bos.toByteArray())
            }
        }
    }
}
