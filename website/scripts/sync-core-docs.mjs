import { readFileSync, writeFileSync, unlinkSync, existsSync, rmSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const siteRoot = resolve(__dirname, '..');
const repoRoot = resolve(siteRoot, '..');
const coreDocs = resolve(repoRoot, 'core', 'docs');

// Clean Astro cache to avoid stale references after renames
try { rmSync(resolve(siteRoot, '.astro'), { recursive: true, force: true }); } catch {}

const map = [
  // Core concepts
  ['src/content/docs/data-modeling/datamodels.mdx', 'datamodel.md'],
  ['src/content/docs/data-modeling/data-design.mdx', 'data-design.md'],
  ['src/content/docs/data-modeling/index.mdx', 'README.md'],
  ['src/content/docs/data-modeling/keys.mdx', 'key.md'],
  ['src/content/docs/data-modeling/versioning.mdx', 'versioning.md'],
  ['src/content/docs/querying/index.mdx', 'query.md'],
  ['src/content/docs/querying/reference-graphs.mdx', 'reference-graphs.md'],
  ['src/content/docs/querying/filters.mdx', 'filters.md'],
  ['src/content/docs/querying/aggregations.mdx', 'aggregations.md'],
  ['src/content/docs/querying/collect-inject.mdx', 'collectAndInject.md'],
  ['src/content/docs/serialization/index.mdx', 'serialization/README.md'],
  ['src/content/docs/serialization/yaml.mdx', 'serialization/yaml.md'],
  ['src/content/docs/serialization/json.mdx', 'serialization/json.md'],
  ['src/content/docs/serialization/protobuf.mdx', 'serialization/protobuf.md'],
  ['src/content/docs/data-modeling/properties/index.mdx', 'properties/README.md'],
  ['src/content/docs/data-modeling/properties/operations.mdx', 'properties/operations.md'],
  ['src/content/docs/data-modeling/properties/references.mdx', 'properties/references.md'],
  // Property types
  ['src/content/docs/data-modeling/properties/types/boolean.mdx', 'properties/types/boolean.md'],
  ['src/content/docs/data-modeling/properties/types/date.mdx', 'properties/types/date.md'],
  ['src/content/docs/data-modeling/properties/types/datetime.mdx', 'properties/types/datetime.md'],
  ['src/content/docs/data-modeling/properties/types/embedded-object.mdx', 'properties/types/embeddedObject.md'],
  ['src/content/docs/data-modeling/properties/types/embedded-values.mdx', 'properties/types/embeddedValues.md'],
  ['src/content/docs/data-modeling/properties/types/enum.mdx', 'properties/types/enum.md'],
  ['src/content/docs/data-modeling/properties/types/fixedbytes.mdx', 'properties/types/fixedBytes.md'],
  ['src/content/docs/data-modeling/properties/types/flexbytes.mdx', 'properties/types/flexBytes.md'],
  ['src/content/docs/data-modeling/properties/types/geopoint.mdx', 'properties/types/geopoint.md'],
  ['src/content/docs/data-modeling/properties/types/incrementing-map.mdx', 'properties/types/incrementingMap.md'],
  ['src/content/docs/data-modeling/properties/types/list.mdx', 'properties/types/list.md'],
  ['src/content/docs/data-modeling/properties/types/map.mdx', 'properties/types/map.md'],
  ['src/content/docs/data-modeling/properties/types/multi-type.mdx', 'properties/types/multi-type.md'],
  ['src/content/docs/data-modeling/properties/types/number.mdx', 'properties/types/number.md'],
  ['src/content/docs/data-modeling/properties/types/reference.mdx', 'properties/types/reference.md'],
  ['src/content/docs/data-modeling/properties/types/set.mdx', 'properties/types/set.md'],
  ['src/content/docs/data-modeling/properties/types/string.mdx', 'properties/types/string.md'],
  ['src/content/docs/data-modeling/properties/types/time.mdx', 'properties/types/time.md'],
  ['src/content/docs/data-modeling/properties/types/value-object.mdx', 'properties/types/valueObject.md'],

  // Stores
  ['src/content/docs/stores/index.mdx', '../store/README.md'],
  ['src/content/docs/stores/memory.mdx', '../store/memory/README.md'],
  ['src/content/docs/stores/rocksdb.mdx', '../store/rocksdb/README.md'],
  ['src/content/docs/stores/rocksdb/storage.mdx', '../store/rocksdb/documentation/storage.md'],
  ['src/content/docs/stores/foundationdb.mdx', '../store/foundationdb/README.md'],
  ['src/content/docs/stores/foundationdb/architecture.mdx', '../store/foundationdb/docs/architecture.md'],
  ['src/content/docs/stores/foundationdb/storage.mdx', '../store/foundationdb/docs/storage.md'],
  ['src/content/docs/stores/foundationdb/local-testing.mdx', '../store/foundationdb/docs/local-testing.md'],
];

  function rewriteLinks(md) {
    let out = md;
    const pairs = [
    ['(datamodel.md)', '(/data-modeling/datamodels/)'],
    ['(properties/README.md)', '(/data-modeling/properties/)'],
    ['(key.md)', '(/data-modeling/keys/)'],
    ['(versioning.md)', '(/data-modeling/versioning/)'],
    ['(query.md)', '(/querying/)'],
    ['(reference-graphs.md)', '(/querying/reference-graphs/)'],
    ['(filters.md)', '(/querying/filters/)'],
    ['(aggregations.md)', '(/querying/aggregations/)'],
    ['(collectAndInject.md)', '(/querying/collect-inject/)'],
    ['(serialization.md)', '(/serialization/)'],
    ['(protobuf.md)', '(/serialization/protobuf/)'],
    ['(yaml.md)', '(/serialization/yaml/)'],
    ['(json.md)', '(/serialization/json/)'],
    ['(serialization/README.md)', '(/serialization/)'],
    ['(serialization/yaml.md)', '(/serialization/yaml/)'],
    ['(serialization/json.md)', '(/serialization/json/)'],
    ['(serialization/yaml.md)', '(/serialization/yaml/)'],
    ['(serialization/json.md)', '(/serialization/json/)'],
    ['(properties/types/multiType.md)', '(/data-modeling/properties/types/multi-type/)'],
    ['(types/multiType.md)', '(/data-modeling/properties/types/multi-type/)'],
    ['(types/fixedBytes.md)', '(/data-modeling/properties/types/fixedbytes/)'],
    ['(types/flexBytes.md)', '(/data-modeling/properties/types/flexbytes/)'],
    ['(types/incrementingMap.md)', '(/data-modeling/properties/types/incrementing-map/)'],
    ['(types/valueObject.md)', '(/data-modeling/properties/types/value-object/)'],
    ['(../../yaml/README.md)', '(https://github.com/marykdb/maryk/blob/master/yaml/README.md)'],
    ['(../../json/README.md)', '(https://github.com/marykdb/maryk/blob/master/json/README.md)'],
    ['(../yaml/README.md)', '(https://github.com/marykdb/maryk/blob/master/yaml/README.md)'],
    ['(../json/README.md)', '(https://github.com/marykdb/maryk/blob/master/json/README.md)'],
    ['(../README.md)', '(/data-modeling/properties/)'],
    ['(../datamodel.md)', '(/data-modeling/datamodels/)'],
    ['(../../datamodel.md)', '(/data-modeling/datamodels/)'],
    ['(operations.md)', '(/data-modeling/properties/operations/)'],
    ['(references.md)', '(/data-modeling/properties/references/)'],
    // Stores links
    ['(./docs/storage.md)', '(/stores/foundationdb/storage/)'],
    ['(./docs/architecture.md)', '(/stores/foundationdb/architecture/)'],
    ['(./docs/local-testing.md)', '(/stores/foundationdb/local-testing/)'],
    ['(documentation/storage.md)', '(/stores/rocksdb/storage/)'],
    // Map code links to GitHub full URLs (main branch)
    ['(../src/', '(https://github.com/marykdb/maryk/blob/master/core/src/'],
    ['(../../src/', '(https://github.com/marykdb/maryk/blob/master/core/src/'],
    ['(../../core/src/', '(https://github.com/marykdb/maryk/blob/master/core/src/'],
    ['(core/src/', '(https://github.com/marykdb/maryk/blob/main/master/src/'],
    // Add relative link variants
    ['(../datamodel.md)', '(/data-modeling/datamodels/)'],
    ['(../../datamodel.md)', '(/data-modeling/datamodels/)'],
    ['(../properties/README.md)', '(/data-modeling/properties/)'],
    ['(../../properties/README.md)', '(/data-modeling/properties/)'],
    ['(../key.md)', '(/data-modeling/keys/)'],
    ['(../../key.md)', '(/data-modeling/keys/)'],
    ['(../versioning.md)', '(/data-modeling/versioning/)'],
    ['(../../versioning.md)', '(/data-modeling/versioning/)'],
    ['(../query.md)', '(/querying/)'],
    ['(../../query.md)', '(/querying/)'],
    ['(../reference-graphs.md)', '(/querying/reference-graphs/)'],
    ['(../../reference-graphs.md)', '(/querying/reference-graphs/)'],
    ['(../filters.md)', '(/querying/filters/)'],
    ['(../../filters.md)', '(/querying/filters/)'],
    ['(../aggregations.md)', '(/querying/aggregations/)'],
    ['(../../aggregations.md)', '(/querying/aggregations/)'],
    ['(../collectAndInject.md)', '(/querying/collect-inject/)'],
    ['(../../collectAndInject.md)', '(/querying/collect-inject/)'],
    ];
  for (const [from, to] of pairs) out = out.replaceAll(from, to);
  out = out.replaceAll('(properties/', '(/data-modeling/properties/');
  out = out.replaceAll('(../properties/', '(/data-modeling/properties/');
  out = out.replaceAll('(../../properties/', '(/data-modeling/properties/');
  out = out.replace(/\.md/g, '/');
  return out;
}

for (const [targetRel, srcRel] of map) {
  const target = resolve(siteRoot, targetRel);
  const targetMd = target.replace(/\.mdx$/, '.md');
  const src = srcRel.startsWith('../')
    ? resolve(repoRoot, srcRel.slice(3))
    : resolve(coreDocs, srcRel);
  try {
    let srcMd = readFileSync(src, 'utf8');
    // Extract title from source H1 if present
    const m = srcMd.match(/^#\s+(.+)$/m);
    const derivedTitle = m ? m[1].trim() : undefined;

    const fallback = derivedTitle || srcRel.replace(/^.*\/(.*)\.md$/, '$1').replace(/[-_]/g, ' ');
    const safeTitle = String(fallback).replace(/"/g, '\\"');
    const fm = `---\ntitle: "${safeTitle}"\n---\n`;

    // Drop the leading H1 to avoid duplicate titles in Starlight.
    srcMd = srcMd.replace(/^#\s+.*\n?/, '');
    let md = rewriteLinks(srcMd);
    // Escape raw angle brackets outside code fences to keep MDX happy.
    const lines = md.split(/\r?\n/);
    let inCode = false;
    for (let i = 0; i < lines.length; i++) {
      const l = lines[i];
      if (l.trim().startsWith('```')) { inCode = !inCode; continue; }
      if (!inCode) {
        lines[i] = l
          .replaceAll('<', '&lt;')
          .replaceAll('>', '&gt;')
          .replaceAll('{', '&#123;')
          .replaceAll('}', '&#125;');
      }
    }
    md = lines.join('\n');
    const out = `${fm}\n${md.trim()}\n`;
    // Ensure parent folders exist
    import('node:fs').then(({ mkdirSync }) => {
      const dir = dirname(target);
      mkdirSync(dir, { recursive: true });
      writeFileSync(target, out, 'utf8');
    }).catch(() => {
      // Fallback: try write directly
      writeFileSync(target, out, 'utf8');
    });
    if (existsSync(targetMd)) { try { unlinkSync(targetMd); } catch {} }
    console.log(`Inlined: ${srcRel} -> ${target.replace(siteRoot + '/', '')}`);
  } catch (e) {
    console.warn(`Skip ${targetRel}: ${e.message}`);
  }
}
