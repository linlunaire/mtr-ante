package cn.zbx1425.sowcer.model;

import java.io.Closeable;
import java.util.ArrayList;

public class Model implements Closeable {

    public ArrayList<Mesh> meshList = new ArrayList<>();

    @Override
    public void close() {
        Throwable failure = null;
        for (Mesh mesh : meshList) {
            try { mesh.close(); } catch (RuntimeException | Error ex) {
                if (failure == null) failure = ex;
                else if (failure != ex) failure.addSuppressed(ex);
            }
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) throw (Error) failure;
    }
}
