export function rewriteCoreLinks(md: string): string {
  let out = md;
  const pairs: [string, string][] = [
    ['(datamodel.md)', '(/core-concepts/datamodels/)'],
    ['(properties/README.md)', '(/core-concepts/properties/)'],
    ['(key.md)', '(/core-concepts/keys/)'],
    ['(versioning.md)', '(/core-concepts/versioning/)'],
    ['(query.md)', '(/core-concepts/querying/)'],
    ['(reference-graphs.md)', '(/core-concepts/querying/reference-graphs/)'],
    ['(filters.md)', '(/core-concepts/querying/filters/)'],
    ['(aggregations.md)', '(/core-concepts/querying/aggregations/)'],
    ['(collectAndInject.md)', '(/core-concepts/querying/collect-inject/)'],
    ['(serialization.md)', '(/core-concepts/serialization/)'],
    ['(protobuf.md)', '(/core-concepts/serialization/protobuf-transport/)'],
    // Module readmes → support pages
    ['(../../yaml/README.md)', '(/support/yaml/)'],
    ['(../../json/README.md)', '(/support/json/)'],
  ];
  for (const [from, to] of pairs) out = out.replaceAll(from, to);
  // Generic redirects for properties subpaths
  out = out.replaceAll('(properties/', '(/core-concepts/properties/');
  return out;
}

