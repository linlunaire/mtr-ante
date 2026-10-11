package cn.zbx1425.sowcer.vertex

enum class VertAttrType(
    /** Location of the first OpenGL vertex attribute, matching the Minecraft vertex format. */
    @JvmField val location: Int,
    /** GL_FLOAT, GL_BYTE, GL_UNSIGNED_BYTE or GL_SHORT. */
    @JvmField val type: Int,
    /** Elements per OpenGL attribute. */
    @JvmField val size: Int,
    /** Number of OpenGL attributes: four for a 4x4 matrix, one otherwise. */
    @JvmField val span: Int,
    @JvmField val normalized: Boolean,
    @JvmField val iPointer: Boolean,
) {
    // Locations must stay consistent with MC_FORMAT_ENTITY_MAT in ShaderManager.
    POSITION(0, 0x1406, 3, 1, false, false),
    COLOR(1, 0x1401, 4, 1, true, false),
    UV_TEXTURE(2, 0x1406, 2, 1, false, false),
    UV_OVERLAY(3, 0x1402, 2, 1, false, true),
    UV_LIGHTMAP(4, 0x1402, 2, 1, false, true),
    NORMAL(5, 0x1400, 3, 1, true, false),
    MATRIX_MODEL(6, 0x1406, 4, 4, false, false);

    /** Total packed size in bytes. */
    @JvmField val byteSize: Int = when (type) {
        0x1406 -> 4
        0x1401, 0x1400 -> 1
        0x1402 -> 2
        else -> 0
    } * size * span
}
