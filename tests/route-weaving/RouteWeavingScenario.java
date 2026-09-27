package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.data.IRoute;
import io.netty.buffer.Unpooled;
import mtr.data.*;
import mtr.path.PathData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.msgpack.core.MessagePack;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Loaded beside the woven Route. No path-data or RouteMixin implementation is copied here. */
public final class RouteWeavingScenario {
    public static String run() throws Exception {
        List<String> records = new ArrayList<>();
        for (Route route : List.of(new Route(TransportMode.TRAIN), new Route(5, TransportMode.BOAT), new Route(new HashMap<>()), new Route(new CompoundTag()))) {
            require(api(route).getPathData().isEmpty() && route.platformIds != null && route.messagePackLength() == 12, "Mixin initializer missing or shadow field overwritten");
        }
        Route route = new Route(12, TransportMode.TRAIN);
        route.name = "Route|路线";
        List<PathData> initial = api(route).getPathData();
        require(initial != api(new Route(13, TransportMode.TRAIN)).getPathData(), "Different routes share the path list");
        records.add("empty\t" + hex(packed(route)) + "\t" + wire(route));
        List<PathData> path = new ArrayList<>(List.of(part(0, 1, 11, RailType.PLATFORM), part(1, 0, 11, RailType.PLATFORM), part(0, 2, 0, RailType.IRON), part(2, 3, 22, RailType.PLATFORM)));
        api(route).setPathData(path);
        require(api(route).getPathData() == path && route.platformIds.stream().map(entry -> entry.platformId).toList().equals(List.of(11L, 22L)), "Setter lost list identity or turn-back filtering");
        require(route.messagePackLength() == 13, "Nonempty ANTE path must add one map entry");
        records.add("populated\t" + hex(packed(route)) + "\t" + wire(route));
        byte[] packed = packed(route);
        Route restored = new Route(unpack(packed));
        require(Arrays.equals(packed, packed(restored)) && api(restored).getPathData().size() == 4
                && api(restored).getPathData() != path && api(restored).getPathData().getFirst() != path.getFirst(), "Map constructor lost injected data or copied-list ownership");
        try (Buffer buffer = buffer()) {
            route.writePacket(buffer.packet); buffer.packet.writeByte(77);
            Route fromPacket = new Route(buffer.packet);
            require(Arrays.equals(packed, packed(fromPacket)) && buffer.packet.readUnsignedByte() == 77, "Packet tail framing changed");
        }
        api(route).setPathData(path);
        require(route.platformIds.size() == 4 && api(route).getPathData() == path, "Legacy repeated setter must append IDs again");
        List<PathData> empty = new ArrayList<>();
        api(route).setPathData(empty);
        require(api(route).getPathData() == empty && route.platformIds.size() == 4 && route.messagePackLength() == 12, "Empty setter must replace path but retain platform IDs");
        records.add("empty-replacement\t" + hex(packed(route)) + "\t" + wire(route));
        expectNull(() -> api(route).setPathData(null));
        require(api(route).getPathData() == empty, "Null setter replaced the old path before failing");
        List<PathData> partial = new ArrayList<>(Arrays.asList(path.getFirst(), null));
        expectNull(() -> api(route).setPathData(partial));
        require(api(route).getPathData() == empty && route.platformIds.size() == 5, "Failed setter lost partial platform append or replaced original path");
        expectNull(() -> api(route).setPathData(List.of(new PathData(null, 1, 20, BlockPos.ZERO, BlockPos.ZERO, 0))));
        records.add("partial-setter\t" + route.platformIds.stream().map(entry -> Long.toString(entry.platformId)).toList());

        Map<String, Value> broken = unpack(packed);
        Value valid = broken.get("ante_path").asArrayValue().get(0);
        broken.put("ante_path", ValueFactory.newArray(valid, ValueFactory.newInteger(99)));
        PrintStream previousOut = System.out, previousErr = System.err;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream(), stderr = new ByteArrayOutputStream();
        Route damaged;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8); PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(out); System.setErr(err); damaged = new Route(broken);
        } finally { System.setOut(previousOut); System.setErr(previousErr); }
        require(api(damaged).getPathData().size() == 1 && stdout.toString(StandardCharsets.UTF_8).contains("Error while loading path data:")
                && !stderr.toString(StandardCharsets.UTF_8).isEmpty(), "Malformed map must retain the parsed prefix and diagnostics");
        records.add("partial-map\t" + hex(packed(damaged)));
        Route bare = new Route(19, TransportMode.TRAIN);
        try (Buffer buffer = buffer()) {
            bare.writePacket(buffer.packet);
            buffer.packet.setInt(buffer.packet.writerIndex() - 4, -3); buffer.packet.writeByte(52);
            require(api(new Route(buffer.packet)).getPathData().isEmpty() && buffer.packet.readUnsignedByte() == 52, "Negative path count must leave trailing bytes untouched");
        }
        try (Buffer buffer = buffer()) {
            bare.writePacket(buffer.packet); buffer.packet.writerIndex(buffer.packet.writerIndex() - 4);
            try { new Route(buffer.packet); throw new AssertionError("Missing path count was ignored"); }
            catch (IndexOutOfBoundsException expected) { }
        }
        return String.join("\n", records) + "\n";
    }

    private static IRoute api(Route route) { return (IRoute) (Object) route; }
    private static PathData part(int a, int b, long id, RailType type) {
        BlockPos start = new BlockPos(a * 10, 64, 0), end = new BlockPos(b * 10, 64, 0);
        Rail rail = new Rail(start, a < b ? RailAngle.E : RailAngle.W, end, a < b ? RailAngle.W : RailAngle.E, type, TransportMode.TRAIN);
        return new PathData(rail, id, 20, start, end, 3);
    }
    private static byte[] packed(Route route) throws Exception {
        try (var packer = MessagePack.newDefaultBufferPacker()) { packer.packMapHeader(route.messagePackLength()); route.toMessagePack(packer); return packer.toByteArray(); }
    }
    private static Map<String, Value> unpack(byte[] bytes) throws Exception {
        try (var unpacker = MessagePack.newDefaultUnpacker(bytes)) { return RailwayData.castMessagePackValueToSKMap(unpacker.unpackValue()); }
    }
    private static String wire(Route route) {
        try (Buffer buffer = buffer()) { route.writePacket(buffer.packet); byte[] bytes = new byte[buffer.packet.readableBytes()]; buffer.packet.getBytes(0, bytes); return hex(bytes); }
    }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }
    private static Buffer buffer() { return new Buffer(new FriendlyByteBuf(Unpooled.buffer())); }
    private record Buffer(FriendlyByteBuf packet) implements AutoCloseable { public void close() { packet.release(); } }
    private static void expectNull(Runnable action) { try { action.run(); } catch (NullPointerException expected) { return; } throw new AssertionError("Expected deferred null failure"); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
