import groovy.json.JsonSlurper

/** Generates client-item roots from registrations, not from the legacy model directory. */
class ItemDefinitionPort {

	static Map<String, Map> generate(File sharedRoot) {
		def source = new File(sharedRoot, 'common/src/main/java/cn/zbx1425/mtrsteamloco/Main.java').getText('UTF-8')
		def registered = new TreeSet<String>()
		(source =~ /\bregistries\.(?:registerItem|registerBlockAndItem)\s*\(\s*"([a-z0-9_\/.-]+)"\s*,/).each { declaration ->
			if (!registered.add(declaration[1])) {
				throw new IllegalStateException("Duplicate ANTE item registration: ${declaration[1]}")
			}
		}
		if (registered.isEmpty()) {
			throw new IllegalStateException('No ANTE item registrations found; review the registration parser.')
		}

		def models = new File(sharedRoot, 'common/src/main/resources/assets/mtrsteamloco/models/item')
		def definitions = new TreeMap<String, Map>()
		registered.each { name ->
			def input = new File(models, "${name}.json")
			if (!input.isFile()) {
				throw new IllegalStateException("Registered ANTE item ${name} has no legacy model: ${input}")
			}
			def legacy = new JsonSlurper().parse(input, 'UTF-8')
			def model = [type: 'minecraft:model', model: "mtrsteamloco:item/${name}".toString()]
			if (legacy.containsKey('overrides') && !(legacy.overrides instanceof List)) {
				throw new IllegalStateException("Unsupported legacy item overrides in ${input}")
			}
			(legacy.overrides ?: []).each { override ->
				if (!(override instanceof Map) || override.keySet() != ['predicate', 'model'].toSet()
					|| override.predicate != ['mtr:selected': 1] || !(override.model instanceof String)) {
					throw new IllegalStateException("Unsupported legacy item override in ${input}; migrate it explicitly.")
				}
				// Preserve old override precedence; the last matching override wins.
				model = [type: 'minecraft:condition', property: 'mtr:selected',
					on_true: [type: 'minecraft:model', model: override.model], on_false: model]
			}
			definitions["assets/mtrsteamloco/items/${name}.json".toString()] = [model: model]
		}
		return definitions
	}
}
