package cn.zbx1425.mtrsteamloco.scripting.util;

import com.google.common.collect.Lists;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.*;

import java.util.Collections;
import java.util.List;

public class JsonStringTagVisitor implements TagVisitor {
    private final StringBuilder builder = new StringBuilder();

    public String visit(Tag p_178188_) {
        p_178188_.accept(this);
        return this.builder.toString();
    }

    public void visitString(StringTag p_178186_) {
        this.builder.append(new JsonPrimitive(p_178186_.value()));
    }

    public void visitByte(ByteTag p_178164_) {
        this.builder.append(p_178164_.box());
    }

    public void visitShort(ShortTag p_178184_) {
        this.builder.append(p_178184_.box());
    }

    public void visitInt(IntTag p_178176_) {
        this.builder.append(p_178176_.box());
    }

    public void visitLong(LongTag p_178182_) {
        this.builder.append(p_178182_.box());
    }

    public void visitFloat(FloatTag p_178172_) {
        this.builder.append(p_178172_.value());
    }

    public void visitDouble(DoubleTag p_178168_) {
        this.builder.append(p_178168_.value());
    }

    public void visitByteArray(ByteArrayTag p_178162_) {
        this.builder.append("[");
        byte[] abyte = p_178162_.getAsByteArray();

        for(int i = 0; i < abyte.length; ++i) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append((int)abyte[i]);
        }

        this.builder.append(']');
    }

    public void visitIntArray(IntArrayTag p_178174_) {
        this.builder.append("[");
        int[] aint = p_178174_.getAsIntArray();

        for(int i = 0; i < aint.length; ++i) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(aint[i]);
        }

        this.builder.append(']');
    }

    public void visitLongArray(LongArrayTag p_178180_) {
        this.builder.append("[");
        long[] along = p_178180_.getAsLongArray();

        for(int i = 0; i < along.length; ++i) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(along[i]);
        }

        this.builder.append(']');
    }

    public void visitList(ListTag p_178178_) {
        this.builder.append('[');

        for(int i = 0; i < p_178178_.size(); ++i) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append((new JsonStringTagVisitor()).visit(p_178178_.get(i)));
        }

        this.builder.append(']');
    }

    public void visitCompound(CompoundTag p_178166_) {
        this.builder.append('{');
        List<String> list = Lists.newArrayList(p_178166_.keySet());
        Collections.sort(list);

        for(String s : list) {
            if (this.builder.length() != 1) {
                this.builder.append(',');
            }

            this.builder.append(new JsonPrimitive(s)).append(':').append((new JsonStringTagVisitor()).visit(p_178166_.get(s)));
        }

        this.builder.append('}');
    }

    public void visitEnd(EndTag p_178170_) {
        this.builder.append("END");
    }
}
