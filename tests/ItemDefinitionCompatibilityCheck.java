package cn.zbx1425.mtrsteamloco;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import mtr.mappings.SelectedItemModelProperty;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ConditionalItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** CPU-only checks using the actual 26.2 client-item codec and MTR selected property. */
public final class ItemDefinitionCompatibilityCheck {

	public static void main(String[] args) throws Exception {
		final Path anteRoot = Path.of(args[0]);
		final Path mtrRoot = Path.of(args[1]);
		final Map<String, JsonObject> definitions = new TreeMap<>();
		final Path resourceRoot = anteRoot.resolve("common/src/main/resources");
		try (var files = Files.list(resourceRoot.resolve("assets/mtrsteamloco/items"))) {
			for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
				definitions.put("assets/mtrsteamloco/items/" + file.getFileName(), JsonParser.parseString(Files.readString(file)).getAsJsonObject());
			}
		}
		final Set<String> names = new TreeSet<>();
		definitions.keySet().forEach(path -> {
			require(path.startsWith("assets/mtrsteamloco/items/") && path.endsWith(".json"), "Unexpected definition path: " + path);
			names.add(path.substring("assets/mtrsteamloco/items/".length(), path.length() - 5));
		});
		require(names.equals(Set.of("bridge_creator_1", "compound_creator", "departure_bell", "direct_node", "displacement_tool", "eye_candy", "rail_path_editor", "route_path_creator")), "Registered item coverage changed: " + names);
		require(definitions.size() == 8, "Selected submodels or unregistered legacy models became item roots");
		final Set<String> registered = new TreeSet<>();
		final var registrations = Pattern.compile("\\bregistries\\.(?:registerItem|registerBlockAndItem)\\s*\\(\\s*\"([a-z0-9_/.-]+)\"")
				.matcher(Files.readString(anteRoot.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/Main.java")));
		while (registrations.find()) require(registered.add(registrations.group(1)), "Duplicate item registration");
		require(names.equals(registered), "Native item definitions do not match registrations: " + registered);

		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup()).forEach(pending -> pending.apply());
		ItemModels.bootstrap();
		ConditionalItemModelProperties.bootstrap();
		// Standalone checks do not apply MTR mixins. Register the identical existing property
		// to exercise the real dispatcher codec, without claiming loader weaving is tested.
		final Field mapperField = ConditionalItemModelProperties.class.getDeclaredField("ID_MAPPER");
		mapperField.setAccessible(true);
		final var mapper = (ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ConditionalItemModelProperty>>) mapperField.get(null);
		mapper.put(SelectedItemModelProperty.ID, SelectedItemModelProperty.CODEC);
		checkSelection();

		try (Resources resources = new Resources(anteRoot, mtrRoot, args.length > 2 ? Path.of(args[2]) : null)) {
			int selected = 0;
			for (String name : names) {
				final JsonObject json = definitions.get("assets/mtrsteamloco/items/" + name + ".json");
				final ClientItem item = ClientItem.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
				final JsonObject legacy = resources.model("mtrsteamloco:item/" + name);
				final JsonObject model = json.getAsJsonObject("model");
				if (legacy.has("overrides")) {
					require(legacy.getAsJsonArray("overrides").size() == 1, "Review the new legacy override chain for " + name);
					require(item.model() instanceof ConditionalItemModel.Unbaked condition && condition.property() instanceof SelectedItemModelProperty, "Selected property did not decode for " + name);
					require(model.get("property").getAsString().equals("mtr:selected"), "Selected property namespace changed");
					require(model.getAsJsonObject("on_true").get("model").equals(legacy.getAsJsonArray("overrides").get(0).getAsJsonObject().get("model")), "Selected target changed for " + name);
					require(model.getAsJsonObject("on_false").get("model").getAsString().equals("mtrsteamloco:item/" + name), "Unselected target changed for " + name);
					selected++;
				} else {
					require(model.get("type").getAsString().equals("minecraft:model") && model.get("model").getAsString().equals("mtrsteamloco:item/" + name), "Ordinary item model changed for " + name);
				}
				resources.checkDefinition(model);
			}
			require(selected == 2, "Selected item coverage changed: " + selected);
		}
		System.out.println("PASS: 8 real ANTE client-item codecs, 2 MTR selected branches, selected-pos semantics, recursive model/texture references; no legacy submodels promoted to item roots");
	}

	private static void checkSelection() {
		final ItemStack stack = new ItemStack(Items.STICK);
		final var property = SelectedItemModelProperty.INSTANCE;
		require(!property.get(stack, null, null, 0, ItemDisplayContext.GUI), "Unselected item became selected");
		final CompoundTag tag = new CompoundTag();
		tag.putString("unrelated", "pos");
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		require(!property.get(stack, null, null, 0, ItemDisplayContext.GUI), "Unrelated custom data became selected");
		for (int i = 0; i < 3; i++) {
			if (i == 0) tag.putLong("pos", 0); else if (i == 1) tag.putLong("pos", Long.MIN_VALUE); else tag.putString("pos", "legacy wrong type");
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			require(property.get(stack, null, null, 0, ItemDisplayContext.GUI), "Selected must retain legacy pos-key presence semantics");
		}
	}

	private static final class Resources implements AutoCloseable {
		private final List<Path> roots;
		private final ZipFile client;
		private final Set<String> checkedModels = new HashSet<>();

		private Resources(Path anteRoot, Path mtrRoot, Path clientJar) throws IOException {
			roots = List.of(anteRoot.resolve("common/src/main/resources"), mtrRoot.resolve("common/src/main/resources"),
				mtrRoot.resolve("resources/common/normal"), mtrRoot.resolve("resources/common/lifts"));
			client = clientJar == null ? null : new ZipFile(clientJar.toFile());
		}

		private byte[] read(String path) throws IOException {
			for (Path root : roots) {
				if (Files.isRegularFile(root.resolve(path))) return Files.readAllBytes(root.resolve(path));
			}
			if (client != null && client.getEntry(path) != null) {
				try (InputStream stream = client.getInputStream(client.getEntry(path))) { return stream.readAllBytes(); }
			}
			try (InputStream stream = ItemDefinitionCompatibilityCheck.class.getClassLoader().getResourceAsStream(path)) {
				require(stream != null, "Missing model/texture resource: " + path);
				return stream.readAllBytes();
			}
		}

		private JsonObject model(String id) throws IOException {
			return JsonParser.parseString(new String(read(path(id, "models/", ".json")), StandardCharsets.UTF_8)).getAsJsonObject();
		}

		private void checkDefinition(JsonObject definition) throws IOException {
			if (definition.get("type").getAsString().equals("minecraft:condition")) {
				checkDefinition(definition.getAsJsonObject("on_true"));
				checkDefinition(definition.getAsJsonObject("on_false"));
			} else {
				checkModel(definition.get("model").getAsString());
			}
		}

		private void checkModel(String id) throws IOException {
			final Identifier identifier = Identifier.parse(id);
			if (!checkedModels.add(identifier.toString())) return;
			// This special parent is provided by Minecraft's model generator, not a JSON file.
			if (identifier.equals(Identifier.parse("minecraft:builtin/generated"))) return;
			final JsonObject json = model(id);
			if (json.has("parent")) checkModel(json.get("parent").getAsString());
			if (json.has("textures")) {
				for (var texture : json.getAsJsonObject("textures").entrySet()) {
					final String value = texture.getValue().getAsString();
					if (!value.startsWith("#")) read(path(value, "textures/", ".png"));
				}
			}
		}

		private static String path(String id, String prefix, String suffix) {
			final Identifier identifier = Identifier.parse(id);
			return "assets/" + identifier.getNamespace() + "/" + prefix + identifier.getPath() + suffix;
		}

		@Override public void close() throws IOException { if (client != null) client.close(); }
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
