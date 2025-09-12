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
  ['src/content/docs/core-concepts/datamodels.mdx', 'datamodel.md'],
  ['src/content/docs/core-concepts/index.mdx', 'README.md'],
  ['src/content/docs/core-concepts/keys.mdx', 'key.md'],
  ['src/content/docs/core-concepts/versioning.mdx', 'versioning.md'],
  ['src/content/docs/core-concepts/querying/index.mdx', 'query.md'],
  ['src/content/docs/core-concepts/querying/reference-graphs.mdx', 'reference-graphs.md'],
  ['src/content/docs/core-concepts/querying/filters.mdx', 'filters.md'],
  ['src/content/docs/core-concepts/querying/aggregations.mdx', 'aggregations.md'],
  ['src/content/docs/core-concepts/querying/collect-inject.mdx', 'collectAndInject.md'],
  ['src/content/docs/core-concepts/serialization/index.mdx', 'serialization.md'],
  ['src/content/docs/core-concepts/serialization/protobuf-transport.mdx', 'protobuf.md'],
  ['src/content/docs/core-concepts/properties/index.mdx', 'properties/README.md'],
  ['src/content/docs/core-concepts/properties/operations.mdx', 'properties/operations.md'],
  ['src/content/docs/core-concepts/properties/references.mdx', 'properties/references.md'],
  // Property types
  ['src/content/docs/core-concepts/properties/types/boolean.mdx', 'properties/types/boolean.md'],
  ['src/content/docs/core-concepts/properties/types/date.mdx', 'properties/types/date.md'],
  ['src/content/docs/core-concepts/properties/types/datetime.mdx', 'properties/types/datetime.md'],
  ['src/content/docs/core-concepts/properties/types/embedded-object.mdx', 'properties/types/embeddedObject.md'],
  ['src/content/docs/core-concepts/properties/types/embedded-values.mdx', 'properties/types/embeddedValues.md'],
  ['src/content/docs/core-concepts/properties/types/enum.mdx', 'properties/types/enum.md'],
  ['src/content/docs/core-concepts/properties/types/fixedbytes.mdx', 'properties/types/fixedBytes.md'],
  ['src/content/docs/core-concepts/properties/types/flexbytes.mdx', 'properties/types/flexBytes.md'],
  ['src/content/docs/core-concepts/properties/types/geopoint.mdx', 'properties/types/geopoint.md'],
  ['src/content/docs/core-concepts/properties/types/incrementing-map.mdx', 'properties/types/incrementingMap.md'],
  ['src/content/docs/core-concepts/properties/types/list.mdx', 'properties/types/list.md'],
  ['src/content/docs/core-concepts/properties/types/map.mdx', 'properties/types/map.md'],
  ['src/content/docs/core-concepts/properties/types/multi-type.mdx', 'properties/types/multiType.md'],
  ['src/content/docs/core-concepts/properties/types/number.mdx', 'properties/types/number.md'],
  ['src/content/docs/core-concepts/properties/types/reference.mdx', 'properties/types/reference.md'],
  ['src/content/docs/core-concepts/properties/types/set.mdx', 'properties/types/set.md'],
  ['src/content/docs/core-concepts/properties/types/string.mdx', 'properties/types/string.md'],
  ['src/content/docs/core-concepts/properties/types/time.mdx', 'properties/types/time.md'],
  ['src/content/docs/core-concepts/properties/types/value-object.mdx', 'properties/types/valueObject.md'],

  // Supporting libraries
  ['src/content/docs/support/library.mdx', '../lib/README.md'],
  ['src/content/docs/support/json.mdx', '../json/README.md'],
  ['src/content/docs/support/yaml.mdx', '../yaml/README.md'],
  ['src/content/docs/support/generator.mdx', '../generator/README.md'],
  ['src/content/docs/support/dataframe.mdx', '../dataframe/README.md'],
  ['src/content/docs/support/testlib.mdx', '../testlib/README.md'],
  ['src/content/docs/support/testmodels.mdx', '../testmodels/README.md'],

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
    ['(../../yaml/README.md)', '(/support/yaml/)'],
    ['(../../json/README.md)', '(/support/json/)'],
    // Stores links
    ['(./docs/storage.md)', '(/stores/foundationdb/storage/)'],
    ['(./docs/architecture.md)', '(/stores/foundationdb/architecture/)'],
    ['(./docs/local-testing.md)', '(/stores/foundationdb/local-testing/)'],
    ['(documentation/storage.md)', '(/stores/rocksdb/storage/)'],
  ];
  for (const [from, to] of pairs) out = out.replaceAll(from, to);
  out = out.replaceAll('(properties/', '(/core-concepts/properties/');
  return out;
}

function takeFrontmatter(text) {
  if (!text.startsWith('---')) return { fm: '---\n---\n', rest: text };
  const end = text.indexOf('\n---', 3);
  if (end === -1) return { fm: '---\n---\n', rest: text };
  const fm = text.slice(0, end + 4);
  const rest = text.slice(end + 4);
  return { fm, rest };
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
