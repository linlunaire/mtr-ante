package probe;

import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.render.ShadersModHandler;
import cn.zbx1425.sowcer.object.VertArray;
import com.mojang.logging.LogUtils;
import mtr.block.BlockNode;
import mtr.client.ClientData;
import mtr.data.Rail;
import mtr.data.RailAngle;
import mtr.data.RailType;
import mtr.data.TransportMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

/** Observes real packaged rail draws and vanilla/Sodium face decisions; never replaces production behavior. */
@Mod("ylte_render_regression")
public final class RenderRuntimeRegression {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static final String MODE = System.getProperty("ylte.render.mode", "shader");
    private static final Path EVIDENCE = Path.of(System.getProperty("ylte.render.evidence"));
    public static boolean inRaw;
    private static boolean measuring, queryOpen, shadow;
    private static int query, mainCalls, mainPositive, shadowCalls, shadowPositive;
    private static long mainSamples, shadowSamples;
    private int ticks, phase, readyAt;
    private boolean closing;
    private Rail rail;
    private BlockPos start, end;

    public RenderRuntimeRegression(IEventBus ignored) { NeoForge.EVENT_BUS.addListener(this::tick); }

    public static void beforeDraw(VertArray array) {
        if (!measuring || !inRaw || queryOpen || array.indexBuf == null || array.id == 0) return;
        shadow = ShadersModHandler.isRenderingShadowPass();
        if (query == 0) query = GL33.glGenQueries();
        GL33.glBeginQuery(GL33.GL_SAMPLES_PASSED, query);
        queryOpen = true;
    }

    public static void afterDraw() {
        if (!queryOpen) return;
        GL33.glEndQuery(GL33.GL_SAMPLES_PASSED);
        int samples = GL33.glGetQueryObjecti(query, GL33.GL_QUERY_RESULT);
        queryOpen = false;
        if (shadow) { shadowCalls++; shadowSamples += samples; if (samples > 0) shadowPositive++; }
        else { mainCalls++; mainSamples += samples; if (samples > 0) mainPositive++; }
    }

    private void tick(ClientTickEvent.Post ignored) {
        if (closing) return;
        Minecraft client = Minecraft.getInstance();
        try {
            GLFW.glfwHideWindow(client.getWindow().getWindow());
            if (++ticks > 1800) throw new AssertionError("Runtime regression exceeded 1800 ticks");
            if (phase == 0) {
                if (client.getOverlay() != null || ticks < 20) return;
                phase = 1;
                client.options.renderDistance().set(4);
                client.options.simulationDistance().set(5);
                client.options.pauseOnLostFocus = false;
                client.options.framerateLimit().set(60);
                client.options.hideGui = true;
                client.createWorldOpenFlows().createFreshLevel("render-regression-" + System.currentTimeMillis(),
                        new LevelSettings("Disposable render fixture", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(0L, false, false),
                        registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getOrThrow(WorldPresets.FLAT).createWorldDimensions(), client.screen);
                return;
            }
            if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
            if (readyAt == 0) {
                readyAt = ticks + 120;
                start = new BlockPos(0, -59, 0); end = new BlockPos(24, -59, 0);
                rail = new Rail(start, RailAngle.E, end, RailAngle.W, RailType.IRON, TransportMode.TRAIN);
                client.getSingleplayerServer().execute(() -> {
                    var server = client.getSingleplayerServer();
                    var level = server.overworld();
                    level.setDayTime(6000);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.teleportTo(12, -53, -8);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    if (MODE.equals("node")) {
                        for (int x = 10; x <= 14; x++) for (int z = 1; z <= 5; z++)
                            level.setBlockAndUpdate(new BlockPos(x, -61, z), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(new BlockPos(12, -60, 3), Main.BLOCK_DIRECT_NODE.get().defaultBlockState());
                        // Water may reach a node, but empty collision/occlusion must not let it replace the block.
                        level.setBlockAndUpdate(new BlockPos(30, -60, 0), Main.BLOCK_DIRECT_NODE.get().defaultBlockState());
                        level.setBlockAndUpdate(new BlockPos(31, -60, 0), Blocks.WATER.defaultBlockState());
                    }
                });
            }
            client.player.setPos(MODE.equals("node") ? 12.5 : 12, MODE.equals("node") ? -56 : -53,
                    MODE.equals("node") ? -0.5 : phase == 3 ? 8 : -8);
            client.player.setYRot(phase == 3 ? 180 : 0); client.player.yRotO = client.player.getYRot();
            client.player.setXRot(MODE.equals("node") ? 55 : 35); client.player.xRotO = client.player.getXRot();
            client.player.setDeltaMovement(0, 0, 0);
            client.player.setNoGravity(true);
            client.player.getAbilities().flying = true;
            if (!MODE.equals("node")) ClientData.RAILS.computeIfAbsent(start, key -> new HashMap<>()).put(end, rail);
            if (ticks < readyAt) return;
            if (MODE.equals("node")) {
                require(client.level.getBlockState(new BlockPos(12, -61, 3)).is(Blocks.STONE), "Node fixture stone was not synchronized");
                require(client.level.getBlockState(new BlockPos(12, -60, 3)).is(Main.BLOCK_DIRECT_NODE.get()), "Node fixture was not synchronized");
                screenshot(client, "direct-node");
                checkNodeFaces(client);
                require(client.level.getBlockState(new BlockPos(30, -60, 0)).is(Main.BLOCK_DIRECT_NODE.get()),
                        "Flowing water replaced the direct node");
                finish(client);
                return;
            }
            if (phase == 1) {
                require(ShadersModHandler.isShaderPackInUse() == MODE.equals("shader"), "Unexpected shader activation");
                LOG.info("RENDER_RUNTIME_BEGIN: mode={} customShader={}", MODE, ShadersModHandler.canUseCustomShader());
                phase = 2;
            }
            if (!measuring) {
                mainCalls = mainPositive = shadowCalls = shadowPositive = 0;
                mainSamples = shadowSamples = 0;
                measuring = true;
            }
            if (ticks < readyAt + 80) return;
            measuring = false;
            String view = phase == 2 ? "south" : "north";
            screenshot(client, "rail-" + view);
            LOG.info("RENDER_RUNTIME_SAMPLES: view={} mainCalls={} mainPositive={} mainSamples={} shadowCalls={} shadowPositive={} shadowSamples={}",
                    view, mainCalls, mainPositive, mainSamples, shadowCalls, shadowPositive, shadowSamples);
            require(mainCalls > 0 && mainSamples > 0, "Rail has no visible main-view samples from " + view);
            if (MODE.equals("shader")) require(shadowCalls > 0 && shadowSamples > 0, "Rail lost its shadow from " + view);
            require(GL33.glGetError() == GL33.GL_NO_ERROR, "OpenGL error after real rail draws");
            if (phase == 2) {
                phase = 3;
                readyAt = ticks + 20;
            } else finish(client);
        } catch (Throwable failure) {
            measuring = false;
            closing = true;
            LOG.error("RENDER_RUNTIME_FAIL: mode=" + MODE, failure);
            client.stop();
        }
    }

    private void checkNodeFaces(Minecraft client) throws Exception {
        require(!ShadersModHandler.isShaderPackInUse(), "Node culling test must run without shaders");
        Object config = null;
        java.lang.reflect.Field toggle = null;
        try {
            config = Class.forName("ca.fxco.moreculling.MoreCulling").getField("CONFIG").get(null);
            toggle = config.getClass().getField("useBlockStateCulling");
        } catch (ClassNotFoundException ignored) { }
        require((config != null) == Boolean.getBoolean("ylte.render.moreCulling"), "Unexpected More Culling presence");
        LOG.info("RENDER_RUNTIME_NODE_ENV: moreCulling={}", config != null);
        boolean saved = config != null && toggle.getBoolean(config);
        try {
            if (config == null) runNodeFaces(client, "without-moreculling");
            else {
                toggle.setBoolean(config, true); runNodeFaces(client, "enabled");
                toggle.setBoolean(config, false); runNodeFaces(client, "disabled-only-culling");
                toggle.setBoolean(config, true); runNodeFaces(client, "reenabled");
            }
        } finally { if (config != null) toggle.setBoolean(config, saved); }
    }

    private void runNodeFaces(Minecraft client, String name) throws Exception {
        BlockPos below = new BlockPos(12, -61, 3), node = below.above();
        client.level.setBlock(below, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var sodiumCache = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache");
        var sodiumMethod = sodiumCache.getMethod("shouldDrawSide", BlockState.class, BlockGetter.class, BlockPos.class, Direction.class);
        int checked = 0;
        for (BlockState state : Main.BLOCK_DIRECT_NODE.get().getStateDefinition().getPossibleStates()) {
            client.level.setBlock(node, state, Block.UPDATE_ALL);
            boolean vanilla = Block.shouldRenderFace(Blocks.STONE.defaultBlockState(), client.level, below, Direction.UP, node);
            boolean sodium = (boolean)sodiumMethod.invoke(sodiumCache.getConstructor().newInstance(), Blocks.STONE.defaultBlockState(), client.level, below, Direction.UP);
            LOG.info("RENDER_RUNTIME_NODE_FACE: case={} state={} vanilla={} sodium={}", name, state, vanilla, sodium);
            require(vanilla && sodium, "Stone top incorrectly culled under direct node: " + state + " in " + name);
            require(state.getOcclusionShape(client.level, node).isEmpty(), "Node selection shape leaked into occlusion");
            require(state.getCollisionShape(client.level, node).isEmpty(), "Node acquired a collision box");
            double outlineHeight = state.getShape(client.level, node).bounds().maxY;
            require(outlineHeight == (state.getValue(BlockNode.IS_CONNECTED) ? 1.0 / 16 : 1), "Node selection outline changed");
            checked++;
        }
        client.level.setBlock(node, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        boolean solid = (boolean)sodiumMethod.invoke(sodiumCache.getConstructor().newInstance(), Blocks.STONE.defaultBlockState(), client.level, below, Direction.UP);
        client.level.setBlock(node, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        boolean air = (boolean)sodiumMethod.invoke(sodiumCache.getConstructor().newInstance(), Blocks.STONE.defaultBlockState(), client.level, below, Direction.UP);
        require(!solid && air, "Invalid culling controls");
        client.level.setBlock(node, Main.BLOCK_DIRECT_NODE.get().defaultBlockState(), Block.UPDATE_ALL);
        LOG.info("RENDER_RUNTIME_NODE_PASS: case={} states={} solidControl={} airControl={}", name, checked, solid, air);
    }

    private static void screenshot(Minecraft client, String name) throws Exception {
        Files.createDirectories(EVIDENCE);
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(EVIDENCE.resolve(name + ".png"));
        }
    }

    private void finish(Minecraft client) {
        measuring = false;
        closing = true;
        LOG.info("RENDER_RUNTIME_PASS: mode={}", MODE);
        client.stop();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
