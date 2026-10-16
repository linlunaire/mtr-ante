package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.ClientConfig;
import cn.zbx1425.mtrsteamloco.scripting.AbstractDrawCalls.DrawCallMap;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.util.DrawContext;
import cn.zbx1425.sowcerext.model.ModelCluster;
import cn.zbx1425.sowcerext.model.RawModel;
import cn.zbx1425.sowcerext.model.integration.BufferSourceProxy;
import cn.zbx1425.sowcerext.reuse.DrawScheduler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exercises production configuration and frame submission without starting a game client. */
public final class ClientStateRegressionCheck {
    private static int assertions;
    private static int failures;

    public static void main(String[] args) throws Exception {
        String selection = args.length == 0 ? "all" : args[0];
        if (!selection.equals("all") && !selection.equals("config") && !selection.equals("scheduler")) {
            throw new IllegalArgumentException("Expected all, config or scheduler");
        }
        if (!selection.equals("scheduler")) {
            System.out.println("ClientConfig source=" + ClientConfig.class.getProtectionDomain().getCodeSource().getLocation());
            checkConfigRoundTrips();
        }
        if (!selection.equals("config")) {
            System.out.println("DrawScheduler source=" + DrawScheduler.class.getProtectionDomain().getCodeSource().getLocation());
            checkFrameSubmission();
        }
        if (failures != 0) throw new AssertionError(failures + " of " + assertions + " client state assertions failed");
        System.out.println("PASS: client state regression; " + assertions + " assertions");
    }

    private static void checkConfigRoundTrips() {
        ClientConfig.Entry edited = directNodeEntry();
        edited.min = -45;
        edited.max = 90;
        edited.step = 12;
        edited.modes[0] = 2;
        JsonObject saved = new JsonObject();
        edited.save(saved);
        require(!saved.has("min") && saved.getAsJsonObject(edited.key).get("min").getAsFloat() == -45,
                "saved values retain the existing nested JSON schema");
        ClientConfig.Entry restarted = directNodeEntry();
        restarted.init(jsonRoundTrip(saved));
        sameEntry(edited, restarted, "direct-node restart");

        // A parent's unrelated keys must not override this entry's own saved values.
        saved.addProperty("min", -90);
        saved.addProperty("max", 360);
        saved.addProperty("step", 24);
        ClientConfig.Entry isolated = directNodeEntry();
        isolated.init(jsonRoundTrip(saved));
        sameEntry(edited, isolated, "child fields take precedence over unrelated parent fields");

        checkGroup(new ClientConfig.EyecandyScreenGroup(), new ClientConfig.EyecandyScreenGroup());
        checkGroup(new ClientConfig.RollAnglesListEntryGroup(), new ClientConfig.RollAnglesListEntryGroup());

        ClientConfig.Entry defaults = directNodeEntry();
        defaults.init(new JsonObject());
        sameEntry(directNodeEntry(), defaults, "absent entry preserves defaults");
        JsonObject partial = new JsonObject();
        JsonObject child = new JsonObject();
        child.addProperty("min", -15);
        partial.add(defaults.key, child);
        defaults.init(partial);
        require(defaults.min == -15 && defaults.max == 180 && defaults.step == 180,
                "partial entry loads supplied fields while keeping missing defaults");
        require(Arrays.equals(defaults.modes, new int[]{1}), "absent modes preserve the existing modes");

        ClientConfig.Entry modes = new ClientConfig.Entry("translation", -1, 1, 40, 1, 3);
        JsonObject modeRoot = new JsonObject();
        JsonObject modeChild = new JsonObject();
        JsonArray modeValues = new JsonArray();
        modeValues.add(2);
        modeChild.add("modes", modeValues);
        modeRoot.add(modes.key, modeChild);
        modes.init(modeRoot);
        require(Arrays.equals(modes.modes, new int[]{2, 1, 1}), "short modes arrays retain per-axis default fallback");
    }

    private static ClientConfig.Entry directNodeEntry() {
        return new ClientConfig.Entry("direct_node_screen", "rotation", 0, 180, 180, 1, 1);
    }

    private static void checkGroup(ClientConfig.EntryGroup edited, ClientConfig.EntryGroup restarted) {
        for (int index = 0; index < edited.entries.length; index++) {
            ClientConfig.Entry entry = edited.entries[index];
            entry.min = -10.5F - index;
            entry.max = 20.5F + index;
            entry.step = 7 + index;
            for (int axis = 0; axis < entry.modes.length; axis++) entry.modes[axis] = (index + axis) % 3;
        }
        JsonObject saved = new JsonObject();
        edited.save(saved);
        restarted.init(jsonRoundTrip(saved));
        for (int index = 0; index < edited.entries.length; index++) {
            sameEntry(edited.entries[index], restarted.entries[index], edited.key + "/" + edited.entries[index].key);
        }
    }

    private static JsonObject jsonRoundTrip(JsonObject object) {
        return JsonParser.parseString(object.toString()).getAsJsonObject();
    }

    private static void sameEntry(ClientConfig.Entry expected, ClientConfig.Entry actual, String label) {
        require(actual.min == expected.min, label + " min: expected " + expected.min + ", got " + actual.min);
        require(actual.max == expected.max, label + " max: expected " + expected.max + ", got " + actual.max);
        require(actual.step == expected.step, label + " step: expected " + expected.step + ", got " + actual.step);
        require(Arrays.equals(actual.modes, expected.modes), label + " modes");
    }

    private static void checkFrameSubmission() {
        DrawContext context = new DrawContext();
        context.drawWithBlaze = true;
        Matrix4f pose = new Matrix4f();
        List<Submission> rendered = new ArrayList<>();
        RecordingRawModel raw = new RecordingRawModel(rendered);
        RecordingCluster cluster = new RecordingCluster(rendered);

        DrawScheduler empty = new DrawScheduler();
        empty.commit(null, context);
        empty.commit(null, context);
        require(rendered.isEmpty(), "empty frames produce no submissions");

        DrawScheduler rawOnly = new DrawScheduler();
        rawOnly.enqueue(raw, pose, 11);
        rawOnly.enqueue(raw, pose, 12, 13);
        rawOnly.commit(null, context);
        List<Submission> expectedRaw = List.of(
                new Submission("raw", pose, 11, OverlayTexture.NO_OVERLAY),
                new Submission("raw", pose, 12, 13));
        require(rendered.equals(expectedRaw), "raw-only frame renders both overloads with their original pose/light/overlay: " + rendered);
        rawOnly.commit(null, context);
        require(rendered.equals(expectedRaw), "a repeated empty commit cannot replay a raw-only frame");

        rendered.clear();
        DrawScheduler clusterOnly = new DrawScheduler();
        clusterOnly.enqueue(cluster, pose, 21);
        clusterOnly.enqueue(cluster, pose, 22, 23);
        clusterOnly.commit(null, context);
        List<Submission> expectedClusters = List.of(
                new Submission("opaque", pose, 21, OverlayTexture.NO_OVERLAY),
                new Submission("opaque", pose, 22, 23),
                new Submission("translucent", pose, 21, OverlayTexture.NO_OVERLAY),
                new Submission("translucent", pose, 22, 23));
        require(rendered.equals(expectedClusters), "cluster-only frame keeps both overloads and opaque/translucent passes");
        clusterOnly.commit(null, context);
        require(rendered.equals(expectedClusters), "a repeated empty commit cannot replay a cluster-only frame");

        rendered.clear();
        DrawScheduler mixed = new DrawScheduler();
        mixed.enqueue(raw, pose, 31, 32);
        mixed.enqueue(cluster, pose, 33, 34);
        mixed.commit(null, context);
        require(rendered.equals(List.of(
                new Submission("opaque", pose, 33, 34),
                new Submission("translucent", pose, 33, 34),
                new Submission("raw", pose, 31, 32))), "mixed frame keeps cluster passes before raw submissions");
        rendered.clear();
        mixed.enqueue(cluster, pose, 35, 36);
        mixed.commit(null, context);
        require(rendered.equals(List.of(
                new Submission("opaque", pose, 35, 36),
                new Submission("translucent", pose, 35, 36))), "a later cluster-only frame cannot replay the previous raw submission: " + rendered);

        rendered.clear();
        DrawScheduler repeated = new DrawScheduler();
        for (int frame = 1; frame <= 4; frame++) {
            repeated.enqueue(raw, pose, frame, frame + 100);
            repeated.enqueue(cluster, pose, frame, frame + 100);
            repeated.commit(null, context);
            require(rendered.stream().filter(submission -> submission.pass().equals("raw")).count() == frame,
                    "frame " + frame + " consumes only its own raw submission");
        }
        require(rendered.size() == 12, "four mixed frames produce exactly four submissions of each pass");

        rendered.clear();
        DrawScheduler scriptScheduler = new DrawScheduler();
        DrawCallMap callbacks = new DrawCallMap();
        callbacks.put("raw", (scheduler, basePose, worldPose, light) -> scheduler.enqueue(raw, basePose, light));
        callbacks.put("cluster", (scheduler, basePose, worldPose, light) -> scheduler.enqueue(cluster, basePose, light));
        callbacks.commit(scriptScheduler, pose, pose, 41);
        scriptScheduler.commit(null, context);
        rendered.clear();
        callbacks.remove("raw");
        callbacks.commit(scriptScheduler, pose, pose, 42);
        scriptScheduler.commit(null, context);
        require(rendered.equals(List.of(
                new Submission("opaque", pose, 42, OverlayTexture.NO_OVERLAY),
                new Submission("translucent", pose, 42, OverlayTexture.NO_OVERLAY))),
                "removing a custom RawModel callback removes its next-frame output: " + rendered);
        rendered.clear();
        callbacks.clear();
        callbacks.commit(scriptScheduler, pose, pose, 43);
        scriptScheduler.commit(null, context);
        require(rendered.isEmpty(), "clearing all custom callbacks leaves no frame output");
    }

    private record Submission(String pass, Matrix4f pose, int light, int overlay) { }

    // Count submissions at the render endpoints only. Scheduler and DrawCallMap are unmodified;
    // no GL context, mesh upload, vertex buffering or final pixels are covered by this check.
    private static final class RecordingRawModel extends RawModel {
        private final List<Submission> rendered;
        RecordingRawModel(List<Submission> rendered) { this.rendered = rendered; }
        @Override
        public void writeBlazeBuffer(BufferSourceProxy consumers, Matrix4f pose, int light, int overlay, DrawContext context) {
            rendered.add(new Submission("raw", pose, light, overlay));
        }
    }

    private static final class RecordingCluster extends ModelCluster {
        private final List<Submission> rendered;
        RecordingCluster(List<Submission> rendered) {
            super(new RawModel(), null);
            this.rendered = rendered;
        }
        @Override
        public void enqueueOpaqueBlaze(BufferSourceProxy consumers, Matrix4f pose, int light, int overlay, DrawContext context) {
            rendered.add(new Submission("opaque", pose, light, overlay));
        }
        @Override
        public void enqueueTranslucentBlaze(BufferSourceProxy consumers, Matrix4f pose, int light, int overlay, DrawContext context) {
            rendered.add(new Submission("translucent", pose, light, overlay));
        }
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) {
            failures++;
            System.err.println("FAIL: " + message);
        }
    }
}
