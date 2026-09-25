package cn.zbx1425.mtrsteamloco.scripting.util.client;

import net.minecraft.resources.Identifier;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runs the real image/lifetime code with only GPU allocation and dispatch replaced. */
public final class GraphicsTextureCompatibilityCheck {
    private static int assertions;
    public static void main(String[] args) {
        var queued = new ArrayDeque<Runnable>();
        var delayed = new ArrayDeque<Runnable>();
        var sink = new Sink();
        int[] allocations = {0};
        var texture = new GraphicsTexture(2, 2, Identifier.parse("mtrsteamloco:test"), queued::add,
                (task, delay) -> { require(delay == 25, "Delay changed"); delayed.add(task); },
                () -> { allocations[0]++; return sink; });
        require(allocations[0] == 0, "GPU texture allocated on the scripting thread");
        texture.bufferedImage.setRGB(0, 0, 0x80123456);
        texture.upload();
        texture.bufferedImage.setRGB(0, 0, 0xFFABCDEF);
        while (!queued.isEmpty()) queued.remove().run();
        require(allocations[0] == 1 && sink.uploads.size() == 1, "Initialization/upload order changed");
        require(sink.uploads.get(0)[0] == 0x80123456, "Queued upload read mutable pixels or swapped color/alpha");

        var parent = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        parent.setRGB(1, 1, 0x11223344); parent.setRGB(2, 1, 0x55667788);
        parent.setRGB(1, 2, 0x99AABBCC); parent.setRGB(2, 2, 0xDDEEFF00);
        texture.upload(parent.getSubimage(1, 1, 2, 2));
        queued.remove().run();
        require(Arrays.equals(sink.uploads.get(1), new int[]{0x11223344, 0x55667788, 0x99AABBCC, 0xDDEEFF00}), "Subimage offset/stride was ignored");
        var rgb = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(0, 0, 0x123456); texture.upload(rgb); queued.remove().run();
        require(sink.uploads.get(2)[0] == 0xFF123456, "RGB image lost opaque alpha");
        texture.upload();
        texture.close(25); texture.close(25); texture.upload();
        require(texture.isClosed() && delayed.size() == 1 && sink.closes == 0, "Close is not idempotent/delayed");
        while (!queued.isEmpty()) queued.remove().run();
        require(sink.uploads.size() == 3, "Upload ran after close");
        delayed.remove().run();
        require(sink.closes == 0, "GPU release ran off the client thread");
        queued.remove().run();
        require(sink.closes == 1, "Closed texture was leaked or released twice");

        var early = new GraphicsTexture(1, 1, Identifier.parse("mtrsteamloco:early"), queued::add,
                (task, delay) -> delayed.add(task), () -> { allocations[0]++; return new Sink(); });
        early.close(0);
        while (!queued.isEmpty()) queued.remove().run();
        delayed.remove().run(); queued.remove().run();
        require(allocations[0] == 1, "Close before initialization still allocated a GPU texture");
        System.out.println("PASS: real GraphicsTexture deferred allocation, immutable ARGB/RGB/subimage uploads and idempotent delayed close; " + assertions + " assertions (no GPU)");
    }
    private static final class Sink implements GraphicsTexture.TextureBackend {
        final List<int[]> uploads = new ArrayList<>();
        int closes;
        public void upload(int[] pixels) { uploads.add(pixels); }
        public void close() { closes++; }
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
