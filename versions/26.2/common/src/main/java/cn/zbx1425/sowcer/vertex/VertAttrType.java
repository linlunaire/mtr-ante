package cn.zbx1425.sowcer.vertex;


public enum VertAttrType {

    // Location must be consistent with MC_FORMAT_ENTITY_MAT in ShaderManager
    POSITION(0, 0x1406, 3, 1, false, false),
    COLOR(1, 0x1401, 4, 1, true, false),
    UV_TEXTURE(2, 0x1406, 2, 1, false, false),
    UV_OVERLAY(3, 0x1402, 2, 1, false, true),
    UV_LIGHTMAP(4, 0x1402, 2, 1, false, true),
    NORMAL(5, 0x1400, 3, 1, true, false),
    MATRIX_MODEL(6, 0x1406, 4, 4, false, false);

    /** The location of the first OpenGL vertex attribute, corresponding to the one assigned by the Minecraft VertexFormat. */
    public final int location;
    /** The OpenGL type of each element in this attribute. GL_FLOAT/GL_BYTE/GL_UNSIGNED_BYTE/GL_SHORT. */
    public final int type;
    /** The count of elements in each of the OpenGL vertex attribute. */
    public final int size;
    /**
     * The amount of OpenGL vertex attributes compositing this attribute.
     * 1 for pretty much everything, while a 4x4 matrix is represented by 4 attributes, each of which has a size of 4.
     * */
    public final int span;
    /** The total size this attribute occupies in the buffer. sizeof(type) * size * span. */
    public final int byteSize;
    /** Whether to normalize the attribute in glVertexAttribPointer. */
    public final boolean normalized;
    /** Whether to use glVertexAttribIPointer instead of glVertexAttribPointer. */
    public final boolean iPointer;

    VertAttrType(int location, int type, int size, int span, boolean normalized, boolean iPointer) {
        this.location = location;
        this.type = type;
        this.size = size;
        this.span = span;
        this.normalized = normalized;
        this.iPointer = iPointer;

        int singleSize;
        switch (type) {
            case 0x1406:
                singleSize = 4;
                break;
            case 0x1401:
            case 0x1400:
                singleSize = 1;
                break;
            case 0x1402:
                singleSize = 2;
                break;
            default:
                singleSize = 0;
                break;
        };
        this.byteSize = singleSize * size * span;
    }

}
