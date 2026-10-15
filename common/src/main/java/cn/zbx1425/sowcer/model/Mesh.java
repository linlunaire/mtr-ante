package cn.zbx1425.sowcer.model;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.object.IndexBuf;
import cn.zbx1425.sowcer.object.VertBuf;

import java.io.Closeable;

public class Mesh implements Closeable {

    public VertBuf vertBuf;
    public IndexBuf indexBuf;

    public MaterialProp materialProp;

    public Mesh(VertBuf vertBuf, IndexBuf indexBuf, MaterialProp materialProp) {
        this.vertBuf = vertBuf;
        this.indexBuf = indexBuf;
        this.materialProp = materialProp;
    }

    @Override
    public void close() {
        Throwable failure = null;
        try { vertBuf.close(); } catch (RuntimeException | Error ex) { failure = ex; }
        try { indexBuf.close(); } catch (RuntimeException | Error ex) {
            if (failure == null) failure = ex;
            else if (failure != ex) failure.addSuppressed(ex);
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) throw (Error) failure;
    }
}
