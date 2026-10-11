package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.data.RelativePosition;
import cn.zbx1425.mtrsteamloco.data.RelativePosition.Combination;
import cn.zbx1425.mtrsteamloco.data.Tree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Runs unchanged against the original Java release and the selected Kotlin output. */
public final class ResourceTreeCompatibilityCheck {
    private static final List<String> records = new ArrayList<>();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath();
        boolean kotlin = Files.isDirectory(source);
        for (Class<?> type : List.of(Tree.class, Tree.Node.class, Tree.Branch.class, Tree.Root.class, Tree.Data.class,
                RelativePosition.class, RelativePosition.Suit.class, Combination.class)) {
            require(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Wrong source for " + type);
            require(Arrays.stream(type.getDeclaredAnnotations()).anyMatch(annotation -> annotation.annotationType().getName().equals("kotlin.Metadata")) == kotlin, "Wrong source language for " + type);
        }
        if (args.length == 3) require(args[2].equals("--record") && !kotlin, "Record only the original Java release");
        positions(); trees();
        String actual = String.join("\n", records) + "\n";
        if (args.length == 3) Files.writeString(Path.of(args[0]), actual);
        else require(Files.readString(Path.of(args[0])).replace("\r\n", "\n").equals(actual), "Resource-tree Java golden differs:\n" + actual);
        System.out.println("PASS: " + assertions + " resource-tree assertions / " + records.size() + " original-Java records; coordinates, encode order, tree ownership, collisions and seeded hierarchies");
    }

    private static void positions() throws Exception {
        Random random = new Random(0x26_2_2026);
        StringBuilder samples = new StringBuilder();
        for (int i = 0; i < 4096; i++) {
            int x = random.nextInt(256) - 128, y = random.nextInt(256) - 128, z = random.nextInt(256) - 128;
            RelativePosition value = new RelativePosition(x, y, z), equal = new RelativePosition(x, y, z);
            require(value.x == x && value.y == y && value.z == z, "Byte coordinates changed");
            require(value.equals(equal) && equal.equals(value) && !value.equals(null) && !value.equals(new Object()), "Position equality changed");
            require(value.equals(new RelativePosition(x, y, z) {}), "Subclass equality changed");
            require(value.hashCode() == Objects.hash((byte) x, (byte) y, (byte) z), "Byte hash changed");
            BlockPos base = new BlockPos(random.nextInt(), random.nextInt(), random.nextInt());
            for (Direction direction : Direction.values()) {
                int nx = x, nz = z;
                for (int turn = direction.get2DDataValue(); turn > 0; turn--) { int previous = nx; nx = -nz; nz = previous; }
                BlockPos actual = value.transform(base, direction);
                require(actual.equals(new BlockPos(base.getX() + nx, base.getY() + y, base.getZ() + nz)), "Rotation/overflow changed");
                samples.append(value).append(direction).append(actual).append(value.hashCode()).append('\n');
            }
        }
        records.add("position-seeded\t" + digest(samples.toString()));
        for (int invalid : new int[]{Integer.MIN_VALUE, -129, 128, Integer.MAX_VALUE}) for (int axis = 0; axis < 3; axis++) {
            int x = axis == 0 ? invalid : 0, y = axis == 1 ? invalid : 0, z = axis == 2 ? invalid : 0;
            records.add("bounds\t" + fails(IllegalArgumentException.class, () -> new RelativePosition(x, y, z)));
        }
        fails(NullPointerException.class, () -> RelativePosition.ZERO.transform(null, Direction.UP));
        fails(NullPointerException.class, () -> RelativePosition.ZERO.transform(BlockPos.ZERO, null));
        for (String input : List.of("", ";", ";;", "0,0,0", "1,2,3;1,2,3;bad;4,5;", "-128,127,0;0,-1,2", ",1,2,3", "1,2,3,", "1,2,3;;4,5,6")) {
            Combination combination = Combination.decode(input);
            require(combination.positions.contains(RelativePosition.ZERO), "Decoded combination lost origin");
            fails(UnsupportedOperationException.class, () -> combination.positions.add(new RelativePosition(5, 6, 7)));
            String encoded = Combination.encode(combination);
            require(Combination.decode(encoded).positions.equals(combination.positions), "Combination round trip changed");
            var transformed = combination.transform(Direction.WEST, new BlockPos(9, 2, -3));
            require(transformed.size() == combination.positions.size(), "Suit cardinality changed");
            for (var suit : transformed) require(combination.positions.stream().anyMatch(rp -> rp == suit.rp) && suit.bp.equals(suit.rp.transform(new BlockPos(9, 2, -3), Direction.WEST)), "Suit reference/rotation changed");
            records.add("combination:" + input + "\t" + encoded);
        }
        for (String input : List.of("128,0,0", "-129,0,0", " 1,2,3", ",2,3", "x,2,3")) records.add("invalid-combination\t" + fails(NumberFormatException.class, () -> Combination.decode(input)));
        fails(NullPointerException.class, () -> Combination.decode(null));
        fails(NullPointerException.class, () -> Combination.encode(null));
        Set<RelativePosition> live = new LinkedHashSet<>(); Combination combination = new Combination(live);
        require(combination.positions == live && combination.transform(null, null).isEmpty(), "Empty combination eagerly dereferenced arguments or copied set");
        live.add(RelativePosition.ZERO); live.add(new RelativePosition(1, 2, 3));
        records.add("combination-live\t" + Combination.encode(combination));
        RelativePosition.Suit suit = new RelativePosition.Suit(null, null); require(suit.rp == null && suit.bp == null, "Suit rejected nullable references");
        require(new Combination(null).positions == null, "Combination rejected null constructor input");
        live.add(null); fails(NullPointerException.class, () -> combination.transform(Direction.NORTH, BlockPos.ZERO));
    }

    private static void trees() throws Exception {
        new Tree<>();
        Probe<Object> root = new Probe<>("root", "Root", null);
        Object payload = new Object();
        Tree.Branch<Object> branch = root.addBranch("branch", "Same");
        Tree.Data<Object> leaf = root.addLeaf("leaf", "Same", payload);
        require(root.addBranch("branch", "Ignored") == branch && root.addLeaf("leaf", "Ignored", new Object()) == leaf, "computeIfAbsent no longer retains first value");
        require(root.getBranch("missing") == null && root.getLeaf(null) == null, "Missing lookup changed");
        require(root.hasSubBranches() && !branch.hasSubBranches(), "Branch presence changed");
        root.resolve(); records.add("tree-duplicate\t" + snapshot(root));
        root.resolve(); records.add("tree-duplicate-repeat\t" + snapshot(root));
        Tree.Data<Object> child = branch.addLeaf("child", "Child", payload);
        require(child.getDepth() == 2 && child.getPathKey().equals("root/branch/child"), "Path depth/key changed");
        String before = child.toString(); child.getPathName().append("MUTATION"); require(child.toString().equals(before), "Path name aliases stored component");
        Tree.Branch<Object> copy = root.copy();
        require(copy != root && copy.branches != root.branches && copy.leaves != root.leaves, "Copy retained mutable maps");
        require(copy.getBranch("branch").parent == root && copy.getBranch("branch").getLeaf("child").parent == branch, "Legacy copy parent ownership changed");
        require(copy.getLeaf("leaf").data == payload && copy.getLeaf("leaf").name != leaf.name, "Copy payload/name identity changed");
        records.add("tree-copy\t" + snapshot(copy));
        Map<String, Tree.Node<Object>> nodes = root.getNodes(); require(nodes.get("branch") == branch && nodes.get("leaf") == leaf, "Nodes copied elements");
        nodes.clear(); require(!root.branches.isEmpty() && !root.leaves.isEmpty(), "Nodes map aliases source");
        root.addLeaf("branch", "Shadow", null);
        require(root.getNodes().get("branch") instanceof Tree.Data, "Leaf no longer overwrites branch key");
        require(root.getNodes().keySet().stream().toList().equals(List.of("branch", "leaf")), "LinkedHashMap insertion order changed");
        root.branches = new LinkedHashMap<>(); root.leaves = new LinkedHashMap<>();
        Tree.Data<Object> same = root.addLeaf("a", "Alias", payload); root.leaves.put("b", same);
        root.resolve(); records.add("tree-alias\t" + snapshot(root));
        root.addLeaf("c", "Alias(a)", null).name = same.name;
        root.resolve(); records.add("tree-component-alias\t" + snapshot(root));
        Tree.Data<Object> nullable = new Tree.Data<>(null, "Null key", null, null);
        require(nullable.getPathKey() == null && nullable.getDepth() == 0 && nullable.data == null, "Nullable node fields changed");
        root.leaves.put(null, nullable); require(root.getLeaf(null) == nullable, "Null map key lost");
        nullable.name = null; fails(NullPointerException.class, nullable::copy);
        fails(NullPointerException.class, () -> root.addBranch(null, "name"));
        fails(NullPointerException.class, () -> root.addBranch("a", null));
        fails(NullPointerException.class, () -> root.addLeaf("a", null, payload));
        require(new Tree.Root<>("Root").copy().getClass() == Tree.Branch.class, "Root copy subtype changed");

        Map<String, String> paths = new LinkedHashMap<>();
        for (String path : List.of("", "/", "a//", "a/b", "a/c", "a", "other/c", "深层/列车", "trailing/")) paths.put(path, "Same");
        int[] calls = {0};
        Tree.Root<String> loaded = Tree.loadTree("Root", paths, value -> { calls[0]++; return value; });
        require(calls[0] == paths.size(), "Name callback count changed");
        records.add("tree-paths\t" + snapshot(loaded));
        records.add("tree-merged\t" + describe(loaded.mergeLevel()));
        records.add("tree-after-merge\t" + snapshot(loaded));
        require(Tree.loadTree("Root", new LinkedHashMap<>(), null).getNodes().isEmpty(), "Empty tree invokes null callback");
        fails(NullPointerException.class, () -> Tree.loadTree("Root", null, value -> "name"));
        fails(NullPointerException.class, () -> Tree.loadTree("Root", paths, value -> null));
        Map<String, String> nullKey = new HashMap<>(); nullKey.put(null, "name"); fails(NullPointerException.class, () -> Tree.loadTree("Root", nullKey, value -> value));
        RuntimeException marker = new RuntimeException("marker");
        try { Tree.loadTree("Root", paths, value -> { throw marker; }); throw new AssertionError("Expected callback failure"); }
        catch (RuntimeException error) { require(error == marker, "Callback exception identity changed"); }

        Random random = new Random(0x262);
        for (int graph = 0; graph < 128; graph++) {
            Map<String, String> generated = new LinkedHashMap<>();
            for (int i = 0; i < 64; i++) generated.put(random.nextInt(8) + "/" + random.nextInt(8) + "/" + random.nextInt(8), "Name" + random.nextInt(8));
            Tree.Root<String> tree = Tree.loadTree("Root", generated, value -> value);
            String description = snapshot(tree) + "|" + snapshot(tree.copy()) + "|" + describe(tree.mergeLevel()) + "|" + snapshot(tree);
            records.add("tree-seeded:" + graph + "\t" + digest(description));
            for (Tree.Node<String> node : tree.getNodes().values()) require(node.parent == tree, "Loaded tree parent changed");
        }
    }

    private static String snapshot(Tree.Branch<?> tree) {
        StringBuilder result = new StringBuilder(tree.getPathKey()).append(':').append(tree.name.getString()).append('{');
        for (var entry : tree.getNodes().entrySet()) {
            Tree.Node<?> node = entry.getValue();
            result.append(entry.getKey()).append('=');
            if (node instanceof Tree.Branch<?> branch) result.append(snapshot(branch));
            else result.append(node.getPathKey()).append(':').append(node.name.getString()).append(':').append(node.getDepth());
            result.append(';');
        }
        return result.append('}').toString();
    }
    private static String describe(Map<String, ? extends Tree.Node<?>> nodes) {
        StringBuilder result = new StringBuilder(); nodes.forEach((key, node) -> result.append(key).append(':').append(node.getPathKey()).append(':').append(node.name.getString()).append(';')); return result.toString();
    }
    private static String digest(String text) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    private static String fails(Class<? extends Throwable> type, Runnable action) { try { action.run(); } catch (Throwable error) { require(type.isInstance(error), "Unexpected failure: " + error); return error.getClass().getSimpleName() + ":" + error.getMessage(); } throw new AssertionError("Expected " + type); }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    public static final class Probe<T> extends Tree.Branch<T> {
        public Probe(String key, String name, Tree.Node<T> parent) { super(key, name, parent); }
        public void resolve() { dealWithDuplicateNames(); }
    }
}
