package probe;

import cn.zbx1425.mtrsteamloco.gui.BrushEditRailScreen;
import cn.zbx1425.mtrsteamloco.render.RailPicker;
import cn.zbx1425.mtrsteamloco.vendor.me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import cn.zbx1425.mtrsteamloco.vendor.me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import mtr.client.ClientData;
import mtr.data.Rail;
import mtr.data.RailAngle;
import mtr.data.RailType;
import mtr.data.TransportMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.network.chat.contents.TranslatableContents;
import mtr.mappings.ItemStackUtilities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Mod("ylte_brush_probe")
public final class BrushRuntimeProbe {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private int ticks, placedAt;
    private int stage, editedAt;
    private boolean starting, placed, complete;
    private volatile boolean serverReady;
    private static final BlockPos START = new BlockPos(0, 66, 0);
    private static final BlockPos END = new BlockPos(0, 66, 2);
    private static int labelCalls;
    private static final Map<String, Label> labels = new HashMap<>();
    private Rail rail, reverse;

    public BrushRuntimeProbe(IEventBus ignored) {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    public static void record(String text, float x, float y, Matrix4f pose) {
        var client = Minecraft.getInstance();
        Matrix4f transform = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose);
        var a = transform.transform(new Vector4f(x, y, 0, 1));
        var b = transform.transform(new Vector4f(x, y + client.font.lineHeight, 0, 1));
        var c = transform.transform(new Vector4f(x + client.font.width(text), y + client.font.lineHeight, 0, 1));
        a.div(a.w); b.div(b.w); c.div(c.w);
        float area = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
        labels.put(text.substring(0, 1), new Label(Math.min(a.x, c.x), Math.min(a.y, c.y), Math.max(a.x, c.x), Math.max(a.y, c.y), area));
        if (labelCalls++ > 5) return;
        var point = new Vector4f(x, y, 0, 1);
        new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose).transform(point);
        LOGGER.info("BRUSH_LABEL_PROBE: " + text + " clip=" + point + " normalized=" + point.x / point.w + "," + point.y / point.w + "," + point.z / point.w);
    }

    private void tick(ClientTickEvent.Post event) {
        if (complete) return;
        Minecraft client = Minecraft.getInstance();
        try {
            GLFW.glfwHideWindow(client.getWindow().getWindow());
            ticks++;
            if (ticks % 100 == 0) LOGGER.info("BRUSH_RUNTIME_STATE: ticks=" + ticks + " screen=" + client.screen + " level=" + client.level);
            if (ticks > 2400) throw new AssertionError("Probe timed out");
            if (!starting) {
                if (client.getOverlay() != null || ticks < 60) return;
                if (Boolean.getBoolean("ylte.brush.checkCtsPacks")) checkCtsPacks(client);
                starting = true;
                client.options.renderDistance().set(4);
                client.options.simulationDistance().set(6);
                client.options.pauseOnLostFocus = false;
                client.options.framerateLimit().set(60);
                client.createWorldOpenFlows().createFreshLevel("brush-probe-" + System.currentTimeMillis(),
                    new LevelSettings("Disposable brush fixture", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(0L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), client.screen);
                return;
            }
            if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
            if (!placed) {
                placed = true;
                client.getSingleplayerServer().execute(() -> {
                    var level = client.getSingleplayerServer().overworld();
                    for (int x = -6; x <= 6; x++) for (int z = -6; z <= 25; z++)
                        level.setBlockAndUpdate(new BlockPos(x, 65, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(START, mtr.Blocks.RAIL_NODE.get().defaultBlockState().setValue(mtr.block.BlockNode.IS_CONNECTED, true));
                    level.setBlockAndUpdate(END, mtr.Blocks.RAIL_NODE.get().defaultBlockState().setValue(mtr.block.BlockNode.IS_CONNECTED, true));
                    var data = mtr.data.RailwayData.getInstance(level);
                    data.addRail(null, TransportMode.TRAIN, START, END, new Rail(START, RailAngle.S, END, RailAngle.N, RailType.WOODEN, TransportMode.TRAIN), false);
                    data.addRail(null, TransportMode.TRAIN, END, START, new Rail(END, RailAngle.N, START, RailAngle.S, RailType.WOODEN, TransportMode.TRAIN), false);
                    level.setDayTime(6000);
                    serverReady = true;
                });
                rail = new Rail(START, RailAngle.S, END, RailAngle.N, RailType.WOODEN, TransportMode.TRAIN);
                reverse = new Rail(END, RailAngle.N, START, RailAngle.S, RailType.WOODEN, TransportMode.TRAIN);
                return;
            }
            if (!serverReady) return;
            if (placedAt == 0) {
                placedAt = ticks;
                client.setScreen(null);
                client.player.getAbilities().flying = true;
                client.player.setNoGravity(true);
                client.player.setPos(0.5, 66, -3.5);
                client.player.setYRot(0);
                client.player.setXRot(24);
                client.player.getInventory().setItem(0, new ItemStack(mtr.Items.BRUSH.get()));
            }
            client.player.setPos(0.5, 66, -3.5);
            client.player.setYRot(0);
            client.player.setXRot(24);
            client.player.setDeltaMovement(0, 0, 0);
            ClientData.RAILS.computeIfAbsent(START, unused -> new HashMap<>()).put(END, rail);
            ClientData.RAILS.computeIfAbsent(END, unused -> new HashMap<>()).put(START, reverse);
            if (stage == 1) {
                if (ticks - editedAt < 10) return;
                toggle(client, true);
                BrushEditRailScreen.updateBrushTag(tag -> tag.putFloat("VerticalCurveRadius", 4000F));
                stage = 2;
                editedAt = ticks;
                return;
            }
            if (stage == 2) {
                if (ticks - editedAt < 20) return;
                var serverTag = client.getSingleplayerServer().submit(() -> ItemStackUtilities.getCustomData(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst().getMainHandItem())).join();
                if (serverTag.getCompound("NTERailBrush").getFloat("VerticalCurveRadius") != 4000F)
                    throw new AssertionError("Real server did not receive edited brush settings");
                float serverRadius = client.getSingleplayerServer().submit(() -> {
                    var data = mtr.data.RailwayData.getInstance(client.getSingleplayerServer().overworld());
                    var rails = ((cn.zbx1425.mtrsteamloco.mixin.RailwayDataAccessor) data).getRails();
                    return ((cn.zbx1425.mtrsteamloco.data.RailExtraSupplier) rails.get(START).get(END)).getVerticalCurveRadius();
                }).join();
                if (serverRadius != 4000F) throw new AssertionError("Server rail did not receive the vertical-curve radius");
                toggle(client, false);
                stage = 3;
                editedAt = ticks;
                return;
            }
            if (stage == 3) {
                if (ticks - editedAt < 20) return;
                var serverTag = client.getSingleplayerServer().submit(() -> ItemStackUtilities.getCustomData(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst().getMainHandItem())).join();
                if (serverTag.getCompound("NTERailBrush").contains("VerticalCurveRadius"))
                    throw new AssertionError("Real server did not receive disabled brush settings");
                Path output = Path.of(System.getProperty("ylte.brush.evidence"));
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) { image.writeToFile(output.resolve("brush-editor.png")); }
                LOGGER.info("BRUSH_RUNTIME_PASS: real framebuffer L/R glyphs, Cloth vertical-curve toggle on/off, persisted components and integrated-server packet receive; " + output);
                complete = true;
                client.stop();
                return;
            }
            if (ticks - placedAt < 100) return;
            Path output = Path.of(System.getProperty("ylte.brush.evidence"));
            Files.createDirectories(output);
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(output.resolve("brush-world.png"));
                if (RailPicker.pickedRail == null) throw new AssertionError("Fixture rail was not selected");
                for (String name : Boolean.getBoolean("ylte.brush.skipRenderCheck") ? new String[0] : new String[]{"L", "R"}) {
                    Label label = labels.get(name);
                    if (label == null) throw new AssertionError("No " + name + " label submitted");
                    if (label.area <= 0) throw new AssertionError(name + " label faces away from camera; signed glyph winding=" + label.area);
                    int white = 0;
                    int left = Math.max(0, (int) ((label.left + 1) * image.getWidth() / 2) - 2);
                    int right = Math.min(image.getWidth(), (int) ((label.right + 1) * image.getWidth() / 2) + 3);
                    int top = Math.max(0, (int) ((1 - label.top) * image.getHeight() / 2) - 2);
                    int bottom = Math.min(image.getHeight(), (int) ((1 - label.bottom) * image.getHeight() / 2) + 3);
                    for (int x = left; x < right; x++) for (int y = top; y < bottom; y++) {
                        int pixel = image.getPixelRGBA(x, y);
                        int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
                        if (r >= 180 && g >= 180 && b >= 180 && Math.abs(r - g) <= 3 && Math.abs(r - b) <= 3) white++;
                    }
                    if (white < 10) throw new AssertionError(name + " glyphs not visible in native framebuffer: " + white + " bright neutral pixels");
                    LOGGER.info("BRUSH_LABEL_VISIBLE: " + name + " " + white + " bright neutral pixels; winding=" + label.area);
                }
            }
            LOGGER.info("BRUSH_RENDER_PASS: picked=" + (RailPicker.pickedRail != null) + " labels=" + labelCalls + " camera=" + client.gameRenderer.getMainCamera().getPosition() + " " + output);
            client.setScreen(BrushEditRailScreen.createScreen(rail, START, END, null));
            stage = 1;
            editedAt = ticks;
        } catch (Throwable failure) {
            LOGGER.error("BRUSH_RUNTIME_FAIL", failure);
            complete = true;
            client.stop();
        }
    }

    private static void toggle(Minecraft client, boolean enabled) {
        var screen = (ClothConfigScreen) client.screen;
        var entry = screen.getCategorizedEntries().values().stream().flatMap(java.util.Collection::stream)
            .filter(value -> value.getFieldName().getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("gui.mtrsteamloco.brush_edit_rail.enable_vertical_curve_radius"))
            .findFirst().orElseThrow(() -> new AssertionError("Missing vertical-curve checkbox"));
        var toggle = (BooleanListEntry) entry;
        ((AbstractButton) toggle.children().getFirst()).onPress();
        if (toggle.getValue() != enabled) throw new AssertionError("Checkbox value did not change");
        toggle.getTooltip(); // Executes the same callback as the supplied Rendering screen crash.
        if (BrushEditRailScreen.getBrushTag().contains("VerticalCurveRadius") != enabled)
            throw new AssertionError("Checkbox did not persist to held brush");
        LOGGER.info("BRUSH_GUI_TOGGLE_PASS: " + enabled);
    }

    private static void checkCtsPacks(Minecraft client) throws Exception {
        var manager = client.getResourceManager();
        int rails = 0, objects = 0, trains = 0, scriptedTrains = 0;
        for (String folder : new String[]{"rails/builtin.json", "eyecandies/objects.json"}) {
            try (var input = manager.getResourceOrThrow(net.minecraft.resources.ResourceLocation.parse("mtrsteamloco:" + folder)).open()) {
                var config = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var entry : config.entrySet()) {
                    var definition = entry.getValue().getAsJsonObject();
                    manager.getResourceOrThrow(net.minecraft.resources.ResourceLocation.parse(definition.get("model").getAsString()));
                    if (folder.startsWith("rails/")) {
                        var model = cn.zbx1425.mtrsteamloco.data.RailModelRegistry.ELEMENTS.get(entry.getKey());
                        if (model == null || model.rawModel == null || model.rawModel.meshList.isEmpty())
                            throw new AssertionError("CTS rail model absent/empty: " + entry.getKey());
                        rails++;
                    } else {
                        var model = cn.zbx1425.mtrsteamloco.data.EyeCandyRegistry.ELEMENTS.get(entry.getKey());
                        if (model == null || model.model == null || (model.model.opaqueParts.meshList.isEmpty() && model.model.translucentParts.meshList.isEmpty()))
                            throw new AssertionError("CTS decoration model absent/empty: " + entry.getKey());
                        objects++;
                    }
                }
            }
        }
        for (var resource : manager.getResourceStack(net.minecraft.resources.ResourceLocation.parse("mtr:mtr_custom_resources.json"))) {
            if (!resource.sourcePackId().contains("gzmtr3.5.1")) continue;
            try (var input = resource.open()) {
                var config = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var entry : config.getAsJsonObject("custom_trains").entrySet()) {
                    var definition = entry.getValue().getAsJsonObject();
                    if (java.util.Set.of("gzmtr_b8_1421", "gzmtr_b8_1421_express").contains(entry.getKey())) {
                        if (manager.getResource(net.minecraft.resources.ResourceLocation.parse(definition.get("model").getAsString())).isPresent())
                            throw new AssertionError("B8-new assets are now available; remove the missing-source exception and test them");
                        LOGGER.warn("CTS_UNAVAILABLE_ORIGINAL_TRAIN: " + entry.getKey() + "; declared b8_new model assets absent from the original pack");
                        continue;
                    }
                    var properties = mtr.client.TrainClientRegistry.getTrainProperties("mtr_custom_train_" + entry.getKey());
                    if (properties.baseTrainType.isEmpty() || properties.renderer == null)
                        throw new AssertionError("CTS train not registered: " + entry.getKey());
                    if (definition.has("script_files") || definition.has("script_texts")) {
                        if (!(properties.renderer instanceof cn.zbx1425.mtrsteamloco.scripting.train.ScriptedTrainRenderer scripted)
                                || scripted.typeScripting.functions.isEmpty() || scripted.typeScripting.failException != null)
                            throw new AssertionError("CTS scripted renderer absent/failed: " + entry.getKey());
                        scriptedTrains++;
                    }
                    trains++;
                }
            }
        }
        if (trains != Integer.getInteger("ylte.brush.expectedCtsTrains", 57) || scriptedTrains != (trains == 0 ? 0 : 55) || rails != 35 || objects != 17)
            throw new AssertionError("Unexpected CTS registry counts: " + trains + "/" + rails + "/" + objects);
        LOGGER.info("CTS_RESOURCE_RUNTIME_PASS: " + trains + " registered trains (" + scriptedTrains + " real script renderers), " + rails + " nonempty rail models, " + objects + " uploaded decoration models");
    }

    private record Label(float left, float bottom, float right, float top, float area) { }
}
