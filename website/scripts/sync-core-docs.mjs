import { readFileSync, writeFileSync, unlinkSync, existsSync, rmSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname, relative, sep } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const siteRoot = resolve(__dirname, '..');
const repoRoot = resolve(siteRoot, '..');
const coreDocs = resolve(repoRoot, 'core', 'docs');

export const map = [
  // Core concepts
  ['src/content/docs/data-modeling/datamodels.mdx', 'datamodel.md'],
  ['src/content/docs/data-modeling/data-design.mdx', 'data-design.md'],
  ['src/content/docs/data-modeling/index-design.mdx', 'index-design.md'],
  ['src/content/docs/data-modeling/index.mdx', 'README.md'],
  ['src/content/docs/data-modeling/keys.mdx', 'key.md'],
  ['src/content/docs/data-modeling/versioning.mdx', 'versioning.md'],
  ['src/content/docs/querying/index.mdx', 'query.md'],
  ['src/content/docs/querying/sql.mdx', '../sql/README.md'],
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
  ['src/content/docs/data-modeling/properties/types/decimal.mdx', 'properties/types/decimal.md'],
  ['src/content/docs/data-modeling/properties/types/embedded-object.mdx', 'properties/types/embeddedObject.md'],
  ['src/content/docs/data-modeling/properties/types/embedded-values.mdx', 'properties/types/embeddedValues.md'],
  ['src/content/docs/data-modeling/properties/types/enum.mdx', 'properties/types/enum.md'],
  ['src/content/docs/data-modeling/properties/types/fixedbytes.mdx', 'properties/types/fixedBytes.md'],
  ['src/content/docs/data-modeling/properties/types/flexbytes.mdx', 'properties/types/flexBytes.md'],
  ['src/content/docs/data-modeling/properties/types/geopoint.mdx', 'properties/types/geopoint.md'],
  ['src/content/docs/data-modeling/properties/types/incrementing-map.mdx', 'properties/types/incrementingMap.md'],
  ['src/content/docs/data-modeling/properties/types/list.mdx', 'properties/types/list.md'],
  ['src/content/docs/data-modeling/properties/types/map.mdx', 'properties/types/map.md'],
  ['src/content/docs/data-modeling/properties/types/multi-type.mdx', 'properties/types/multiType.md'],
  ['src/content/docs/data-modeling/properties/types/number.mdx', 'properties/types/number.md'],
  ['src/content/docs/data-modeling/properties/types/reference.mdx', 'properties/types/reference.md'],
  ['src/content/docs/data-modeling/properties/types/set.mdx', 'properties/types/set.md'],
  ['src/content/docs/data-modeling/properties/types/string.mdx', 'properties/types/string.md'],
  ['src/content/docs/data-modeling/properties/types/time.mdx', 'properties/types/time.md'],
  ['src/content/docs/data-modeling/properties/types/value-object.mdx', 'properties/types/valueObject.md'],

  // Stores
  ['src/content/docs/stores/index.mdx', '../store/README.md'],
  ['src/content/docs/stores/shared.mdx', '../store/shared/README.md'],
  ['src/content/docs/stores/shared/encryption-key-rotation.mdx', '../store/shared/docs/encryption-key-rotation.md'],
  ['src/content/docs/stores/shared/backup-restore.mdx', '../store/shared/docs/backup-restore.md'],
  ['src/content/docs/stores/shared/store-behavior.mdx', '../store/shared/docs/store-behavior.md'],
  ['src/content/docs/stores/shared/storage-format-upgrades.mdx', '../store/shared/docs/storage-format-upgrades.md'],
  ['src/content/docs/stores/memory.mdx', '../store/memory/README.md'],
  ['src/content/docs/stores/indexeddb.mdx', '../store/indexeddb/README.md'],
  ['src/content/docs/stores/indexeddb/architecture.mdx', '../store/indexeddb/docs/architecture.md'],
  ['src/content/docs/stores/indexeddb/storage.mdx', '../store/indexeddb/docs/storage.md'],
  ['src/content/docs/stores/indexeddb/migrations.mdx', '../store/indexeddb/docs/migrations.md'],
  ['src/content/docs/stores/rocksdb.mdx', '../store/rocksdb/README.md'],
  ['src/content/docs/stores/rocksdb/architecture.mdx', '../store/rocksdb/documentation/architecture.md'],
  ['src/content/docs/stores/rocksdb/migrations.mdx', '../store/rocksdb/documentation/migrations.md'],
  ['src/content/docs/stores/rocksdb/storage.mdx', '../store/rocksdb/documentation/storage.md'],
  ['src/content/docs/stores/rocksdb/backups.mdx', '../store/rocksdb/documentation/backups.md'],
  ['src/content/docs/stores/foundationdb.mdx', '../store/foundationdb/README.md'],
  ['src/content/docs/stores/foundationdb/architecture.mdx', '../store/foundationdb/docs/architecture.md'],
  ['src/content/docs/stores/foundationdb/cluster-updates.mdx', '../store/foundationdb/docs/cluster-updates.md'],
  ['src/content/docs/stores/foundationdb/migrations.mdx', '../store/foundationdb/docs/migrations.md'],
  ['src/content/docs/stores/foundationdb/storage.mdx', '../store/foundationdb/docs/storage.md'],
  ['src/content/docs/stores/foundationdb/local-testing.mdx', '../store/foundationdb/docs/local-testing.md'],
  ['src/content/docs/stores/remote.mdx', '../store/remote/README.md'],
  ['src/content/docs/stores/test.mdx', '../store/test/README.md'],

  // Focused references linked from the module overviews.
  ['src/content/docs/querying/atomic.mdx', 'query/atomic.md'],
  ['src/content/docs/querying/transactions.mdx', 'query/transactions.md'],
  ['src/content/docs/querying/sql/queries.mdx', '../sql/docs/queries.md'],
  ['src/content/docs/querying/sql/writes.mdx', '../sql/docs/writes.md'],
  ['src/content/docs/querying/sql/execution.mdx', '../sql/docs/execution.md'],
  ['src/content/docs/serialization/protobuf-compatibility.mdx', 'serialization/protobuf-compatibility.md'],
  ['src/content/docs/stores/remote/server.mdx', '../store/remote/docs/server.md'],
  ['src/content/docs/stores/remote/client.mdx', '../store/remote/docs/client.md'],
  ['src/content/docs/stores/remote/protocol.mdx', '../store/remote/docs/protocol.md'],

  ['src/content/docs/querying/reads-writes.mdx', 'query/reads-writes.md'],
  ['src/content/docs/querying/history.mdx', 'query/history.md'],
  ['src/content/docs/querying/updates.mdx', 'query/updates.md'],
  ['src/content/docs/data-modeling/index-reference.mdx', 'index-reference.md'],
  ['src/content/docs/data-modeling/index-recipes.mdx', 'index-recipes.md'],
  ['src/content/docs/stores/shared/migrations.mdx', '../store/shared/docs/migrations.md'],

  // CLI
  ['src/content/docs/cli/commands.mdx', '../cli/docs/commands.md'],
  // Generator
  ['src/content/docs/generator.mdx', '../generator/README.md'],
  // App
  ['src/content/docs/app.mdx', '../app/docs/README.md'],
];

function sourceFile(source, repositoryRoot, coreDocs) {
  return source.startsWith('../')
    ? resolve(repositoryRoot, source.slice(3))
    : resolve(coreDocs, source);
}

function documentationRoutes(repositoryRoot, coreDocs, entries = map) {
  return new Map(entries.map(([target, source]) => [
    sourceFile(source, repositoryRoot, coreDocs),
    target.replace(/^src\/content\/docs\//, '').replace(/(^|\/)index\.mdx$/, '$1').replace(/\.mdx$/, '/'),
  ]));
}

/** Apply prose transforms while preserving fenced examples verbatim. */
function outsideCodeFences(md, transform) {
  let fence;
  return md.split('\n').map(line => {
    const marker = line.match(/^ {0,3}(`{3,}|~{3,})(.*)$/);
    if (fence) {
      if (marker && marker[1][0] === fence[0] && marker[1].length >= fence.length && !marker[2].trim()) {
        fence = undefined;
      }
      return line;
    }
    if (marker) {
      fence = marker[1];
      return line;
    }
    return transform(line);
  }).join('\n');
}

/** Resolve links from their repository source, using the same map that generates pages. */
export function rewriteLinks(md, prefix, source, routes = documentationRoutes(repoRoot, coreDocs), repositoryRoot = repoRoot) {
  return outsideCodeFences(md, line => {
    return line.replace(/\]\(([^)\s]+)([^)]*)\)/g, (match, href, title) => {
      if (/^(?:[a-z][a-z\d+.-]*:|[#/])/i.test(href)) return match;
      const [, path, suffix = ''] = href.match(/^([^?#]*)(.*)$/);
      const absolute = resolve(dirname(source), path);
      const route = routes.get(absolute);
      if (route !== undefined) return `](${prefix}${route}${suffix}${title})`;
      const repositoryPath = relative(repositoryRoot, absolute).split(sep).join('/');
      if (repositoryPath.startsWith('../') || repositoryPath === '..') return match;
      const kind = path.endsWith('/') ? 'tree' : 'blob';
      return `](https://github.com/marykdb/maryk/${kind}/main/${repositoryPath}${suffix}${title})`;
    });
  });
}

export const removedTargets = [
  'src/content/docs/stores/indexeddb/operations.mdx',
  'src/content/docs/stores/indexeddb/migrations-encryption-testing.mdx',
];

export function syncDocs({
  siteRoot,
  repoRoot,
  coreDocs,
  map: entries,
  removedTargets,
}) {
  const routes = new Map([...documentationRoutes(repoRoot, coreDocs), ...documentationRoutes(repoRoot, coreDocs, entries)]);
  // Clean Astro cache to avoid stale references after renames.
  rmSync(resolve(siteRoot, '.astro'), { recursive: true, force: true });

  for (const targetRel of removedTargets) {
    const target = resolve(siteRoot, targetRel);
    if (existsSync(target)) unlinkSync(target);
  }

  for (const [targetRel, srcRel] of entries) {
    const target = resolve(siteRoot, targetRel);
    const targetMd = target.replace(/\.mdx$/, '.md');
    const src = sourceFile(srcRel, repoRoot, coreDocs);
    let srcMd = readFileSync(src, 'utf8');
    // Extract title from source H1 if present
    const m = srcMd.match(/^#\s+(.+)$/m);
    const derivedTitle = m ? m[1].trim() : undefined;

    const fallback = derivedTitle || srcRel.replace(/^.*\/(.*)\.md$/, '$1').replace(/[-_]/g, ' ');
    const safeTitle = String(fallback).replace(/"/g, '\\"');
    const fm = `---\ntitle: "${safeTitle}"\n---\n`;

    // Drop the leading H1 to avoid duplicate titles in Starlight.
    srcMd = srcMd.replace(/^#\s+.*\n?/, '');
    const docsRelative = targetRel.replace(/^src\/content\/docs\//, '');
    const docsDir = dirname(docsRelative);
    const depth = docsDir === '.' ? 0 : docsDir.split('/').length;
    const routeDepth = targetRel.endsWith('/index.mdx') ? depth : depth + 1;
    const prefix = '../'.repeat(routeDepth);
    let md = rewriteLinks(srcMd, prefix, src, routes, repoRoot);
    // Escape prose for MDX without changing examples inside either fence style.
    md = outsideCodeFences(md, line => line
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('{', '&#123;')
      .replaceAll('}', '&#125;'));
    const out = `${fm}\n${md.trim()}\n`;
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, out, 'utf8');
    if (existsSync(targetMd)) unlinkSync(targetMd);
    console.log(`Inlined: ${srcRel} -> ${target.replace(siteRoot + '/', '')}`);
  }
}

if (resolve(process.argv[1]) === __filename) {
  syncDocs({ siteRoot, repoRoot, coreDocs, map, removedTargets });
}
