package cn.zbx1425.sowcerext.model.integration;

import mtr.mappings.RenderBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.HashMap;
import java.util.Map;

public class BufferSourceProxy {

    private final RenderBufferSource bufferSource;
    private final Map<RenderType, FaceList> builders = new HashMap<>();

    public BufferSourceProxy(RenderBufferSource bufferSource) {
        this.bufferSource = bufferSource;
    }

    public FaceList getBuffer(RenderType renderType, boolean needSorting) {
        return builders.computeIfAbsent(renderType,
                type -> new FaceList(renderType, needSorting));
    }

    public void commit() {
        for (FaceList builder : builders.values()) {
            builder.commit(bufferSource);
        }
        builders.clear();
    }
}
