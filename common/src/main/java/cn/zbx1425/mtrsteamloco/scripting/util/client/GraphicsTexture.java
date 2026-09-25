package cn.zbx1425.mtrsteamloco.scripting.util.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class GraphicsTexture implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger("MTR-ANTE");

    public final Identifier identifier;
    public final BufferedImage bufferedImage;
    public final Graphics2D graphics;
    public final int width, height;
    private final Consumer<Runnable> clientQueue;
    private final BiConsumer<Runnable, Integer> delayedQueue;
    private volatile boolean isClosed;
    // Accessed only by jobs on the client/render thread.
    private TextureBackend backend;

    public GraphicsTexture(int width, int height, Identifier path) {
        this(width, height, path, task -> Minecraft.getInstance().execute(task),
                (task, delay) -> CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(task),
                () -> new MinecraftTexture(width, height, path));
    }

    public GraphicsTexture(int width, int height) {
        this(width, height, Identifier.fromNamespaceAndPath("mtrsteamloco", "dynamic/graphics/" + UUID.randomUUID().toString().replace('-', '_')));
    }

    // The GPU/dispatch boundary is replaceable for headless lifetime checks; script APIs stay unchanged.
    GraphicsTexture(int width, int height, Identifier path, Consumer<Runnable> clientQueue,
                    BiConsumer<Runnable, Integer> delayedQueue, Supplier<TextureBackend> factory) {
        this.width = width;
        this.height = height;
        identifier = path;
        this.clientQueue = clientQueue;
        this.delayedQueue = delayedQueue;
        bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        graphics = bufferedImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        clientQueue.accept(() -> {
            if (!isClosed) backend = factory.get();
        });
    }

    public static BufferedImage createArgbBufferedImage(BufferedImage src) {
        BufferedImage result = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(src, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    public void upload() {
        upload(bufferedImage);
    }

    public synchronized void upload(BufferedImage image) {
        if (isClosed) return;
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            LOGGER.warn("Invalid image dimensions for GraphicsTexture {} (expected {} x {})", identifier, width, height);
            return;
        }
        // getRGB respects raster offsets/stride and yields ARGB for every BufferedImage type.
        // The queued GPU upload must not observe later script writes to the image.
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        clientQueue.accept(() -> {
            if (isClosed || backend == null) return;
            try {
                backend.upload(pixels);
            } catch (Exception e) {
                LOGGER.error("Failed to upload GraphicsTexture {}", identifier, e);
            }
        });
    }

    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public void close() {
        close(10000);
    }

    public synchronized void close(int delay) {
        if (isClosed) return;
        isClosed = true;
        graphics.dispose();
        delayedQueue.accept(() -> clientQueue.accept(() -> {
            if (backend == null) return;
            try {
                backend.close();
            } finally {
                backend = null;
            }
        }), Math.max(0, delay));
    }

    interface TextureBackend {
        void upload(int[] pixels);
        void close();
    }

    private static final class MinecraftTexture implements TextureBackend {
        private final Identifier identifier;
        private final DynamicTexture texture;

        private MinecraftTexture(int width, int height, Identifier identifier) {
            this.identifier = identifier;
            NativeImage pixels = new NativeImage(width, height, false);
            try {
                pixels.fillRect(0, 0, width, height, 0);
                texture = new DynamicTexture(identifier::toString, pixels);
            } catch (RuntimeException | Error failure) {
                pixels.close();
                throw failure;
            }
            try {
                Minecraft.getInstance().getTextureManager().register(identifier, texture);
            } catch (RuntimeException | Error failure) {
                texture.close();
                throw failure;
            }
        }

        @Override
        public void upload(int[] pixels) {
            NativeImage image = texture.getPixels();
            if (image == null) return;
            for (int y = 0, index = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) image.setPixel(x, y, pixels[index++]);
            }
            texture.upload();
        }

        @Override
        public void close() {
            // TextureManager.release already closes both the native image and the GPU texture.
            Minecraft.getInstance().getTextureManager().release(identifier);
        }
    }
}
