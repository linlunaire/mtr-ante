package cn.zbx1425.sowcer.vertex;


import java.util.HashMap;
import java.util.Objects;

public class VertAttrMapping {

    public final HashMap<VertAttrType, VertAttrSrc> sources;
    public final HashMap<VertAttrType, Integer> pointers = new HashMap<>();
    public final int strideVertex, strideInstance;
    public final int paddingVertex, paddingInstance;

    private VertAttrMapping(HashMap<VertAttrType, VertAttrSrc> sources) {
        this.sources = new HashMap<>(sources);

        int strideVertex = 0, strideInstance = 0;
        for (VertAttrType attrType : VertAttrType.values()) {
            switch (sources.get(attrType)) {
                case VERTEX_BUF:
                case VERTEX_BUF_OR_GLOBAL:
                    pointers.put(attrType, strideVertex);
                    strideVertex += attrType.byteSize;
                    break;
                case INSTANCE_BUF:
                case INSTANCE_BUF_OR_GLOBAL:
                    pointers.put(attrType, strideInstance);
                    strideInstance += attrType.byteSize;
                    break;
            }
        }
        // Align stride to 4 bytes
        if (strideVertex % 4 != 0) {
            paddingVertex = 4 - strideVertex % 4;
            strideVertex += paddingVertex;
        } else {
            paddingVertex = 0;
        }
        if (strideInstance % 4 != 0) {
            paddingInstance = 4 - strideInstance % 4;
            strideInstance += paddingInstance;
        } else {
            paddingInstance = 0;
        }

        this.strideVertex = strideVertex;
        this.strideInstance = strideInstance;
    }

    public static class Builder {

        private final HashMap<VertAttrType, VertAttrSrc> sources;

        public Builder() {
            sources = new HashMap<>();
        }

        public Builder set(VertAttrType type, VertAttrSrc src) {
            sources.put(type, src);
            return this;
        }

        public VertAttrMapping build() {
            return new VertAttrMapping(sources);
        }
    }
}
