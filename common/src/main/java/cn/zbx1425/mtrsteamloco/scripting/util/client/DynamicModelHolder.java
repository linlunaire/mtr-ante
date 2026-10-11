package cn.zbx1425.mtrsteamloco.scripting.util.client;

import cn.zbx1425.sowcerext.model.ModelCluster;
import cn.zbx1425.sowcerext.model.RawModel;
import cn.zbx1425.sowcerext.reuse.ModelManager;
import java.util.function.Consumer;

/** CPU model ownership; deferred replacements are consumed when the next frame requests the model. */
public class DynamicModelHolder implements AutoCloseable {
    private ModelCluster uploadedModel;
    private RawModel pending;
    private boolean closed;

    public synchronized void uploadLater(RawModel rawModel) {
        requireOpen();
        pending = snapshot(rawModel);
    }

    public synchronized void uploadNow(RawModel rawModel) {
        requireOpen();
        final ModelCluster next = new ModelCluster(snapshot(rawModel), ModelManager.DEFAULT_MAPPING);
        pending = null;
        replace(next);
    }

    public synchronized ModelCluster getUploadedModel() {
        if (closed) return null;
        if (pending != null) {
            final ModelCluster next = new ModelCluster(pending, ModelManager.DEFAULT_MAPPING);
            pending = null;
            replace(next);
        }
        return uploadedModel;
    }

    /** Keep replacement/close out of the frame's acquisition of retained draw data. */
    public synchronized void withUploadedModel(Consumer<ModelCluster> draw) {
        final ModelCluster model = getUploadedModel();
        if (model != null) draw.accept(model);
    }

    private static RawModel snapshot(RawModel rawModel) {
        final RawModel copy = rawModel.copy();
        copy.sourceLocation = null;
        return copy;
    }

    private void replace(ModelCluster next) {
        final ModelCluster previous = uploadedModel;
        uploadedModel = next;
        if (previous != null) previous.close();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Dynamic model holder is closed");
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        pending = null;
        replace(null);
    }
}
