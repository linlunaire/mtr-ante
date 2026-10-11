package cn.zbx1425.mtrsteamloco.data;

import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcerext.model.ModelCluster;
import cn.zbx1425.sowcerext.model.RawModel;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import cn.zbx1425.mtrsteamloco.scripting.ScriptHolderBase;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import sun.misc.Unsafe;
import java.nio.file.*;
import java.util.*;

/** The actual Java/Kotlin property object and close dispatch, without initializing GPU models. */
public final class EyeCandyPropertiesCompatibilityCheck {
    private static final RuntimeException FAILURE = new RuntimeException("close fixture");
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath();
        require(Path.of(EyeCandyProperties.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Wrong properties source");
        boolean kotlin = Files.isDirectory(source);
        require(Arrays.stream(EyeCandyProperties.class.getDeclaredAnnotations()).anyMatch(annotation -> annotation.annotationType().getName().equals("kotlin.Metadata")) == kotlin, "Wrong properties language");
        var field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); Unsafe unsafe = (Unsafe) field.get(null);
        CloseProbe main = (CloseProbe) unsafe.allocateInstance(CloseProbe.class), item = (CloseProbe) unsafe.allocateInstance(CloseProbe.class);
        var name = Component.literal("Mutable"); var positions = RelativePosition.Combination.decode(""); var transform = new Matrix4f();
        var id = Identifier.parse("ante:test"); ScriptHolderBase script = (ScriptHolderBase) unsafe.allocateInstance(ScriptProbe.class);
        List<String> records = new ArrayList<>();
        for (String key : Arrays.asList(null, "", "key/part")) for (String group : Arrays.asList(null, "", "Group")) {
            EyeCandyProperties properties = new EyeCandyProperties(key, name, main, item, transform, id, script, null, "collision", true, -9, true, false, true, group, positions);
            require(properties.key == key && properties.group == group && properties.name == name && properties.model == main && properties.itemModel == item && properties.itemModelId == id && properties.itemTransform == transform && properties.script == script && properties.positions == positions, "Constructor copied/changed mutable references");
            require(properties.shape == null && properties.collisionShape.equals("collision") && properties.fixedMatrix && properties.lightLevel == -9 && properties.isTicketBarrier && !properties.isEntrance && properties.asPlatform, "Constructor flags changed");
            String path = properties.path; properties.key = "changed"; properties.group = "changed";
            require(properties.path.equals(path), "Legacy precomputed path became reactive");
            properties.close(); properties.close();
            records.add("path\t" + path + ":" + main.closes + ":" + item.closes);
            main.fail = true;
            try { properties.close(); throw new AssertionError("Close failure swallowed"); } catch (RuntimeException error) { require(error == FAILURE, "Close failure identity changed"); }
            main.fail = false;
            properties.model = null; properties.close(); require(item.closes == 0, "Item model unexpectedly owned by property close");
        }
        EyeCandyProperties defaults = EyeCandyProperties.DEFAULT;
        require(defaults == EyeCandyProperties.DEFAULT && defaults.positions != null, "Default singleton changed");
        records.add("default\t" + defaults.key + ":" + defaults.name.getString() + ":" + defaults.path + ":" + defaults.shape + ":" + defaults.collisionShape + ":" + defaults.fixedMatrix + ":" + defaults.lightLevel + ":" + defaults.isTicketBarrier + ":" + defaults.isEntrance + ":" + defaults.asPlatform + ":" + RelativePosition.Combination.encode(defaults.positions));
        require(defaults.model == null && defaults.itemModel == null && defaults.script == null && defaults.itemModelId == null && defaults.itemTransform == null, "Default optional fields changed");
        String actual = String.join("\n", records) + "\n";
        if (Arrays.asList(args).contains("--record")) { require(!kotlin, "Only record original Java"); Files.writeString(Path.of(args[0]), actual); }
        else require(Files.readString(Path.of(args[0])).replace("\r\n", "\n").equals(actual), "Eye-candy properties differ from Java");
        System.out.println("PASS: eye-candy properties, " + assertions + " assertions / " + records.size() + " records; mutable fields, default singleton and close ownership/failure");
    }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    public static final class CloseProbe extends ModelCluster {
        int closes; boolean fail;
        private CloseProbe() { super((RawModel) null, (VertAttrMapping) null); }
        @Override public void close() { closes++; if (fail) throw FAILURE; }
    }
    public static final class ScriptProbe extends ScriptHolderBase { private ScriptProbe() { super("fixture"); } }
}
