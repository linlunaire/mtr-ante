package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.scripting.ScriptHolderBase;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Exercises the released loader's real engine setup without game/GPU imports. */
public final class ScriptLoaderStartupCheck {
    public static void main(String[] args) throws Exception {
        Path artifact = Path.of(args[0]).toRealPath();
        Path loaded = Path.of(ScriptHolderBase.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toRealPath();
        if (!artifact.equals(loaded)) throw new AssertionError("Wrong script loader: " + loaded);
        // The runtime mixin selects this interpreter. This check does not weave mixins.
        System.setProperty("truffle.TruffleRuntime", "com.oracle.truffle.api.impl.DefaultTruffleRuntime");
        Files.createDirectories(Path.of("ante/script_data"));
        Files.createDirectories(Path.of("logs"));
        TestHolder holder = new TestHolder();
        try {
            holder.load("engine-startup-check", "train", null, Map.of(), new JsonObject(), "check");
        } finally {
            holder.close();
        }
        System.out.println("PASS: released ScriptHolderBase.load, GraalJS, legacy imports and host interop (no game/GPU)");
    }

    private static final class TestHolder extends ScriptHolderBase {
        private TestHolder() { super("client"); }

        @Override protected void appendImporter() {
            eval(PRETREATMENT);
            eval("importPackage(java.util); var m = new HashMap(); m.put('answer', 6 * 7); "
                    + "if (m.get('answer') != 42) throw new Error('Broken host interop');");
        }
    }
}
