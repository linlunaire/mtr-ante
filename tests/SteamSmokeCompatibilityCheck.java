package cn.zbx1425.mtrsteamloco.render.train;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

import java.util.ArrayList;

/** Real smoke tick/extraction code with a CPU-only sprite and no world collision query or GPU. */
public final class SteamSmokeCompatibilityCheck {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        var sprite = sprite();
        var smoke = new InspectableSmoke(sprite);
        require(smoke.getLifetime() >= 30 && smoke.getLifetime() <= 39, "Legacy smoke lifetime range changed");
        require(smoke.physics() && Math.abs(smoke.getBoundingBox().getXsize() - 0.25) < 0.00001, "Smoke collision settings changed");
        smoke.prepareTick();
        float before = smoke.getQuadSize(0);
        smoke.tick();
        require(smoke.isAlive() && smoke.age() == 1 && smoke.y() > 20, "Smoke failed to rise/tick");
        close(smoke.getQuadSize(0), before + 0.2F, "Early smoke growth");
        for (int i = 0; i < 3; i++) smoke.tick();
        close(smoke.getQuadSize(0), before + 0.7F, "Late smoke growth boundary");
        require(smoke.getLayer() == SingleQuadParticle.Layer.OPAQUE, "Smoke alpha-test layer changed");

        var state = new QuadParticleRenderState();
        var camera = new Camera();
        smoke.extract(state, camera, 0.5F);
        require(!state.isEmpty() && state.layers().equals(java.util.Set.of(SingleQuadParticle.Layer.OPAQUE)), "Smoke extraction did not queue the opaque layer");
        var capture = new Capture(); state.buildLayer(SingleQuadParticle.Layer.OPAQUE, capture);
        require(capture.vertices.size() == 4 && capture.uvs.size() == 4, "Smoke quad missing vertices/UVs");
        close(capture.vertices.get(0).distance(capture.vertices.get(1)), 2 * smoke.getQuadSize(0), "Smoke quad scale");
        var center = new Vector3f(); for (var point : capture.vertices) center.add(point); center.div(4);
        close(center.y(), (float) smoke.interpolatedY(), "Smoke tick interpolation");
        require(capture.uvs.stream().allMatch(uv -> (uv[0] == 0.25F || uv[0] == 0.5F) && (uv[1] == 0.125F || uv[1] == 0.375F)), "Particle atlas UVs changed");
        require(capture.colors.stream().allMatch(color -> color == -1) && capture.lights.stream().allMatch(light -> light == 0x00A00070), "Smoke tint/alpha/light missing");
        smoke.tick(); smoke.remove();
        var replay = new Capture(); state.buildLayer(SingleQuadParticle.Layer.OPAQUE, replay);
        require(replay.vertices.equals(capture.vertices), "Live particle mutation changed the deferred frame");
        state.clear(); require(state.isEmpty(), "Particle frame clear retained stale smoke");

        var expires = new InspectableSmoke(sprite); expires.prepareTick(); expires.setLifetime(1);
        expires.tick(); require(expires.isAlive(), "Smoke expired before its last visible tick");
        expires.tick(); require(!expires.isAlive(), "Smoke survived its lifetime");
        var invisible = new InspectableSmoke(sprite); invisible.fadeOut(); invisible.tick();
        require(!invisible.isAlive(), "Invisible smoke remained alive");
        var blocked = new InspectableSmoke(sprite); blocked.prepareTick(); blocked.cancelVerticalSpeed(); blocked.tick();
        require(!blocked.isAlive(), "Vertically blocked smoke did not terminate");

        RandomSource supplied = RandomSource.create(123);
        boolean[] picked = {false};
        SpriteSet sprites = new SpriteSet() {
            public TextureAtlasSprite get(int age, int lifetime) { throw new AssertionError("Expected random sprite selection"); }
            public TextureAtlasSprite get(RandomSource random) { require(random == supplied, "Provider discarded Minecraft's random source"); picked[0] = true; return sprite; }
            public TextureAtlasSprite first() { return sprite; }
        };
        var provided = new SteamSmokeParticle.Provider(sprites).createParticle(null, null, 0, 0, 0, 0, 0.1, 0, supplied);
        require(provided instanceof SteamSmokeParticle && picked[0] && provided.isAlive(), "26.2 particle provider failed to create smoke");
        System.out.println("PASS: real smoke lifetime, velocity/growth, opaque atlas quad, light/UV/color, delayed extraction and new provider API; " + assertions + " assertions (no world collision/GPU)");
    }

    private static final class InspectableSmoke extends SteamSmokeParticle {
        InspectableSmoke(TextureAtlasSprite sprite) { super(null, 10, 20, 30, 0, 0.1, 0, sprite); }
        void prepareTick() { hasPhysics = false; random.setSeed(123); setLifetime(10); }
        boolean physics() { return hasPhysics; }
        int age() { return age; }
        double y() { return y; }
        double interpolatedY() { return (y + yo) / 2; }
        void fadeOut() { alpha = 0; }
        void cancelVerticalSpeed() { yd = gravity; }
        @Override protected int getLightCoords(float delta) { return 0x00A00070; }
    }

    private static TextureAtlasSprite sprite() throws Exception {
        var type = Class.forName("sun.misc.Unsafe"); var field = type.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var sprite = (TextureAtlasSprite) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), TextureAtlasSprite.class);
        for (var entry : java.util.Map.of("u0", 0.25F, "u1", 0.5F, "v0", 0.125F, "v1", 0.375F).entrySet()) {
            var value = TextureAtlasSprite.class.getDeclaredField(entry.getKey()); value.setAccessible(true); value.setFloat(sprite, entry.getValue());
        }
        return sprite;
    }

    private static final class Capture implements VertexConsumer {
        final ArrayList<Vector3f> vertices = new ArrayList<>();
        final ArrayList<float[]> uvs = new ArrayList<>();
        final ArrayList<Integer> colors = new ArrayList<>(), lights = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) { vertices.add(new Vector3f(x, y, z)); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { colors.add(a << 24 | r << 16 | g << 8 | b); return this; }
        @Override public VertexConsumer setColor(int color) { colors.add(color); return this; }
        @Override public VertexConsumer setUv(float u, float v) { uvs.add(new float[]{u, v}); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { lights.add(u | v << 16); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }

    private static void close(float actual, float expected, String message) { require(Math.abs(actual - expected) < 0.00001F, message + ": " + actual + " != " + expected); }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
