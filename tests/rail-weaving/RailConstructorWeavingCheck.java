package cn.zbx1425.mtrsteamloco.compatibility;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.msgpack.core.MessagePack;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.transformers.MixinClassWriter;

/** Execute actually woven Rail constructors. A supplied save is read only; no server or graphics start. */
public final class RailConstructorWeavingCheck {
    public static void main(String[] args) throws Exception {
        System.setProperty("mixin.service", CameraWeavingCheck.HeadlessService.class.getName());
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("rail-weaving.mixins.json");
        var service = (CameraWeavingCheck.HeadlessService) MixinService.getService();
        var transformer = service.transformerFactory().createTransformer();
        Map<String, byte[]> woven = new HashMap<>();
        for (String name : List.of("mtr.data.Rail", "mtr.data.RailAngle", "mtr.data.RailType")) {
            ClassNode node = service.getClassNode(name);
            require(transformer.transformClass(environment, name, node), "Mixin did not transform " + name);
            var writer = new MixinClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            woven.put(name, writer.toByteArray());
        }
        ClassLoader loader = new ClassLoader(RailConstructorWeavingCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("mtr.data.") && !name.startsWith("cn.zbx1425.mtrsteamloco.data.")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        byte[] bytes = woven.get(name);
                        if (bytes == null) try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (input == null) throw new ClassNotFoundException(name);
                            bytes = input.readAllBytes();
                        } catch (IOException ex) { throw new ClassNotFoundException(name, ex); }
                        type = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };
        Class<?> rail = loader.loadClass("mtr.data.Rail");
        checkNewConnections(loader, rail);
        Map<String, Value> record = straightRail();
        Object restored = construct(rail, Map.class, record);
        checkPosition(rail, restored);
        System.out.println("PASS: woven Rail(Map) constructor restores portable straight fixture without initialization-order failure");
        Map<String,Value> rolled = new HashMap<>(record);
        rolled.put("roll_angle_map", ValueFactory.newString("0.0:0.2,10000.0:0.2"));
        rolled.put("rolling_offset", ValueFactory.newFloat(0.75F));
        Object tilted = construct(rail, Map.class, rolled);
        @SuppressWarnings("unchecked") Map<Double,Float> rolls=(Map<Double,Float>) rail.getMethod("getRollAngleMap").invoke(tilted);
        require(rolls.equals(Map.of(0D,0.2F,10000D,0.2F)), "Saved roll map lost during constructor tail");
        require((float) rail.getMethod("getRollingOffset").invoke(tilted)==0.75F, "Saved rolling offset lost");
        checkPosition(rail, tilted);
        var packer=MessagePack.newDefaultBufferPacker();
        packer.packMapHeader((int) rail.getMethod("messagePackLength").invoke(tilted));
        rail.getMethod("toMessagePack", org.msgpack.core.MessagePacker.class).invoke(tilted,packer);
        Map<String,Value> roundTrip=unpack(packer.toByteArray());
        Object reloaded=construct(rail,Map.class,roundTrip);
        require(rolls.equals(rail.getMethod("getRollAngleMap").invoke(reloaded)),"MessagePack round trip dropped roll map");
        System.out.println("PASS: nonzero saved roll map and offset survive construction and actual MessagePack round trip");
        var rawTag=new net.minecraft.nbt.CompoundTag();
        for(var entry:record.entrySet()) {
            Value value=entry.getValue();
            if(value.isBooleanValue()) rawTag.putBoolean(entry.getKey(),value.asBooleanValue().getBoolean());
            else if(value.isIntegerValue()) rawTag.putInt(entry.getKey(),value.asIntegerValue().asInt());
            else if(value.isFloatValue()) rawTag.putDouble(entry.getKey(),value.asFloatValue().toDouble());
            else if(value.isStringValue()) rawTag.putString(entry.getKey(),value.asStringValue().asString());
        }
        Object fromNbt=construct(rail,net.minecraft.nbt.CompoundTag.class,rawTag);
        checkPosition(rail,fromNbt);
        require(((Map<?,?>)rail.getMethod("getRollAngleMap").invoke(fromNbt)).isEmpty(),"Legacy NBT defaults changed");
        System.out.println("PASS: woven legacy NBT constructor initializes default roll map and valid geometry");
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var packet=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            rail.getMethod("writePacket",net.minecraft.network.FriendlyByteBuf.class).invoke(tilted,packet);
            Object fromPacket=construct(rail,net.minecraft.network.FriendlyByteBuf.class,packet);
            checkPosition(rail,fromPacket);
            require(packet.readableBytes()==0,"Packet tail was not fully restored");
            require(rolls.equals(rail.getMethod("getRollAngleMap").invoke(fromPacket)),"Packet constructor dropped roll map");
            require((float)rail.getMethod("getRollingOffset").invoke(fromPacket)==0.75F,"Packet constructor dropped rolling offset");
        } finally { packet.release(); }
        System.out.println("PASS: woven packet constructor restores nonzero roll metadata through actual write/read packet round trip");
        if(args.length>0) {
            int count=0;
            for(Map<String,Value> saved:records(Path.of(args[0]))) {
                Object item=construct(rail,Map.class,saved);
                checkPosition(rail,item);
                count++;
            }
            require(count>0,"No real rail connections tested");
            System.out.println("PASS: actually constructed "+count+" connections from read-only real save rail records");
        }
    }
    private static void checkNewConnections(ClassLoader loader, Class<?> rail) throws Exception {
        Class<?> angle = loader.loadClass("mtr.data.RailAngle");
        Class<?> railType = loader.loadClass("mtr.data.RailType");
        Class<?> transport = loader.loadClass("mtr.data.TransportMode");
        Class<?> angleHelper = loader.loadClass("cn.zbx1425.mtrsteamloco.data.RailAngleExtra");
        Object facingStart = angleHelper.getMethod("fromDegrees", double.class).invoke(null, 0D);
        Object facingEnd = angleHelper.getMethod("fromDegrees", double.class).invoke(null, 180D);
        var constructor = rail.getConstructor(net.minecraft.core.BlockPos.class, angle,
                net.minecraft.core.BlockPos.class, angle, railType, transport);
        var start = new net.minecraft.core.BlockPos(0, 64, 0);
        var end = new net.minecraft.core.BlockPos(16, 64, 0);
        Object train = transport.getField("TRAIN").get(null);
        Object iron = railType.getField("IRON").get(null);
        for (boolean reversed : new boolean[]{false, true}) {
            Object connected = constructor.newInstance(reversed ? end : start, reversed ? facingEnd : facingStart,
                    reversed ? start : end, reversed ? facingStart : facingEnd, iron, train);
            // ItemRailModifier.onConnect calls isValid before it can add either rail or send it to clients.
            require((boolean) rail.getMethod("isValid").invoke(connected), "New straight connection is invalid");
            checkPosition(rail, connected);
            require("".equals(rail.getMethod("getModelKey").invoke(connected)), "New rail lost default model key");
            for (String getter : List.of("getRollAngleMap", "getCustomConfigs", "getCustomResponders")) {
                require(rail.getMethod(getter).invoke(connected) instanceof Map<?, ?> map && map.isEmpty(),
                        "New rail lost default collection: " + getter);
            }
            require((float) rail.getMethod("getRollingOffset").invoke(connected) == 1.435F / 2F,
                    "New rail lost default rolling offset");
        }
        System.out.println("PASS: both newly connected rails validate with initialized extension metadata");
    }
    private static Object construct(Class<?> type,Class<?> argument,Object value) throws Exception {
        try { return type.getConstructor(argument).newInstance(value); }
        catch(InvocationTargetException ex) { throw new AssertionError("Woven Rail constructor failed",ex.getCause()); }
    }
    private static void checkPosition(Class<?> rail,Object instance) throws Exception {
        double length=(double)rail.getMethod("getLength").invoke(instance);
        require(Double.isFinite(length)&&length>0,"Invalid restored length");
        for(double distance:new double[]{0,length/2,length}) {
            var point=(net.minecraft.world.phys.Vec3)rail.getMethod("getPosition",double.class).invoke(instance,distance);
            require(Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z),"Invalid restored position");
        }
        Method getAngle=rail.getDeclaredMethod("getRailAngle",boolean.class);
        getAngle.setAccessible(true);
        require(getAngle.invoke(instance,false)!=null,"Missing restored facing");
    }
    private static List<Map<String,Value>> records(Path path) throws IOException {
        List<Map<String,Value>> result=new ArrayList<>();
        try(var files=Files.walk(path)) {
            for(Path file:files.filter(Files::isRegularFile).sorted().toList()) {
                Map<String,Value> entry=unpack(Files.readAllBytes(file));
                Value connections=entry.get("rail_connections");
                if(connections!=null&&connections.isArrayValue()) for(Value connection:connections.asArrayValue()) {
                    Map<String,Value> candidate=new HashMap<>();
                    connection.asMapValue().map().forEach((k,v)->candidate.put(k.asStringValue().asString(),v));
                    if(candidate.containsKey("h_1")) result.add(candidate);
                }
            }
        }
        return result;
    }
    private static Map<String,Value> unpack(byte[] bytes) throws IOException {
        try(var unpacker=MessagePack.newDefaultUnpacker(bytes)) {
            Map<String,Value> result=new HashMap<>();
            int count=unpacker.unpackMapHeader();
            for(int i=0;i<count;i++) result.put(unpacker.unpackString(),unpacker.unpackValue());
            return result;
        }
    }
    private static Map<String,Value> straightRail() {
        Map<String,Value> map=new HashMap<>();
        for(String key:List.of("h_1","k_1","h_2","k_2","r_1","r_2","t_start_1","t_start_2","t_end_1","t_end_2")) map.put(key,ValueFactory.newFloat(0D));
        map.put("h_1",ValueFactory.newFloat(1D)); map.put("t_end_1",ValueFactory.newFloat(16D));
        map.put("y_start",ValueFactory.newInteger(64));map.put("y_end",ValueFactory.newInteger(64));
        for(String key:List.of("reverse_t_1","reverse_t_2","is_straight_1","is_straight_2")) map.put(key,ValueFactory.newBoolean(key.startsWith("is_")));
        map.put("rail_type",ValueFactory.newString("IRON"));map.put("transport_mode",ValueFactory.newString("TRAIN"));
        return map;
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
