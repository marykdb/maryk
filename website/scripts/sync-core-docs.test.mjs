import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

import { map, rewriteLinks, syncDocs } from './sync-core-docs.mjs';

const repositoryRoot = fileURLToPath(new URL('../../', import.meta.url));
const sourceFile = path => resolve(repositoryRoot, path);

test('shared contracts resolve relative to module and architecture source files', () => {
  for (const [source, href] of [
    ['core/docs/README.md', 'query.md'],
    ['store/README.md', '../core/docs/query.md'],
    ['store/memory/README.md', '../../core/docs/query.md'],
    ['store/indexeddb/docs/architecture.md', '../../../core/docs/query.md'],
  ]) {
    for (const anchor of ['', '#atomic', '#serializable-transactions', '#choosing-a-write-boundary']) {
      assert.equal(rewriteLinks(`[contract](${href}${anchor})`, '../../', sourceFile(source)),
        `[contract](../../querying/${anchor})`);
    }
  }
});

test('syncDocs maps the shared store test documentation', () => {
  assert.ok(map.some(([target, source]) =>
    target === 'src/content/docs/stores/test.mdx' && source === '../store/test/README.md',
  ));

  const root = mkdtempSync(join(tmpdir(), 'maryk-docs-sync-'));
  const siteRoot = join(root, 'website');
  const coreDocs = join(root, 'core', 'docs');
  const source = join(root, 'store', 'test', 'README.md');
  const target = join(siteRoot, 'src/content/docs/stores/test.mdx');
  mkdirSync(join(root, 'store', 'test'), { recursive: true });
  mkdirSync(coreDocs, { recursive: true });
  writeFileSync(source, '# Store Test Suite\n\nShared store checks.\n');

  syncDocs({
    siteRoot,
    repoRoot: root,
    coreDocs,
    map: [['src/content/docs/stores/test.mdx', '../store/test/README.md']],
    removedTargets: [],
  });

  assert.match(readFileSync(target, 'utf8'), /title: "Store Test Suite"/);
  assert.match(readFileSync(target, 'utf8'), /Shared store checks/);
});

test('syncDocs keeps links in generated section indexes below the site base', () => {
  const root = mkdtempSync(join(tmpdir(), 'maryk-docs-sync-'));
  const siteRoot = join(root, 'website');
  const coreDocs = join(root, 'core', 'docs');
  const source = join(coreDocs, 'README.md');
  const target = join(siteRoot, 'src/content/docs/data-modeling/index.mdx');
  mkdirSync(coreDocs, { recursive: true });
  writeFileSync(source, '# Data modeling\n\n[Data models](datamodel.md)\n');

  syncDocs({
    siteRoot,
    repoRoot: root,
    coreDocs,
    map: [['src/content/docs/data-modeling/index.mdx', 'README.md']],
    removedTargets: [],
  });

  assert.match(
    readFileSync(target, 'utf8'),
    /\[Data models\]\(\.\.\/data-modeling\/datamodels\/\)/,
  );
});

test('syncDocs keeps normal generated pages at their existing relative depth', () => {
  const root = mkdtempSync(join(tmpdir(), 'maryk-docs-sync-'));
  const siteRoot = join(root, 'website');
  const coreDocs = join(root, 'core', 'docs');
  const source = join(coreDocs, 'datamodel.md');
  const target = join(siteRoot, 'src/content/docs/data-modeling/datamodels.mdx');
  mkdirSync(coreDocs, { recursive: true });
  writeFileSync(source, '# Data models\n\n[Data models](datamodel.md)\n');

  syncDocs({
    siteRoot,
    repoRoot: root,
    coreDocs,
    map: [['src/content/docs/data-modeling/datamodels.mdx', 'datamodel.md']],
    removedTargets: [],
  });

  assert.match(
    readFileSync(target, 'utf8'),
    /\[Data models\]\(\.\.\/\.\.\/data-modeling\/datamodels\/\)/,
  );
});

test('syncDocs links versioning to the generated update-history section', () => {
  const root = mkdtempSync(join(tmpdir(), 'maryk-docs-sync-'));
  const siteRoot = join(root, 'website');
  const coreDocs = join(root, 'core', 'docs');
  const target = join(siteRoot, 'src/content/docs/data-modeling/versioning.mdx');
  mkdirSync(coreDocs, { recursive: true });
  writeFileSync(join(coreDocs, 'versioning.md'),
    '# Versioning\n\n[Update-history paging](query.md#getscan-update-history)\n');

  syncDocs({
    siteRoot,
    repoRoot: root,
    coreDocs,
    map: [['src/content/docs/data-modeling/versioning.mdx', 'versioning.md']],
    removedTargets: [],
  });

  assert.match(readFileSync(target, 'utf8'),
    /\[Update-history paging\]\(\.\.\/\.\.\/querying\/#getscan-update-history\)/);
});

test('syncDocs links backend history documentation to the querying section', () => {
  const root = mkdtempSync(join(tmpdir(), 'maryk-docs-sync-'));
  const siteRoot = join(root, 'website');
  const coreDocs = join(root, 'core', 'docs');
  const sourceDirectory = join(root, 'store', 'memory');
  const target = join(siteRoot, 'src/content/docs/stores/memory.mdx');
  mkdirSync(coreDocs, { recursive: true });
  mkdirSync(sourceDirectory, { recursive: true });
  writeFileSync(join(sourceDirectory, 'README.md'),
    '# Memory\n\n[History paging](../../core/docs/query.md#getscan-update-history)\n');

  syncDocs({
    siteRoot,
    repoRoot: root,
    coreDocs,
    map: [['src/content/docs/stores/memory.mdx', '../store/memory/README.md']],
    removedTargets: [],
  });

  assert.match(readFileSync(target, 'utf8'),
    /\[History paging\]\(\.\.\/\.\.\/querying\/#getscan-update-history\)/);
});

test('syncDocs fails when a mapped source has been removed', () => {
  const root = mkdtempSync(join(tmpdir(), 'maryk-docs-sync-'));
  const siteRoot = join(root, 'website');
  const coreDocs = join(root, 'core', 'docs');
  const target = join(siteRoot, 'src/content/docs/example.mdx');

  mkdirSync(join(siteRoot, 'src/content/docs'), { recursive: true });
  mkdirSync(coreDocs, { recursive: true });
  writeFileSync(target, 'stale generated page\n');

  assert.throws(
    () => syncDocs({
      siteRoot,
      repoRoot: root,
      coreDocs,
      map: [['src/content/docs/example.mdx', 'missing.md']],
      removedTargets: [],
    }),
    /missing\.md/,
  );
});


test('the same local filename resolves to the correct backend and anchor', () => {
  for (const [engine, folder] of [['rocksdb', 'documentation'], ['foundationdb', 'docs'], ['indexeddb', 'docs']]) {
    assert.equal(rewriteLinks('[layout](storage.md#opening-storage-format-2)', '../../../',
      sourceFile(`store/${engine}/${folder}/migrations.md`)),
      `[layout](../../../stores/${engine}/storage/#opening-storage-format-2)`);
  }
});

test('extracted guides resolve from overviews and deep references', () => {
  for (const guide of ['backup-restore', 'store-behavior', 'storage-format-upgrades', 'encryption-key-rotation']) {
    for (const [source, href] of [
      ['store/shared/README.md', `docs/${guide}.md`],
      ['store/rocksdb/README.md', `../shared/docs/${guide}.md`],
      ['core/docs/properties/types/number.md', `../../../../store/shared/docs/${guide}.md`],
    ]) {
      assert.equal(rewriteLinks(`[guide](${href}#requirements)`, '../../', sourceFile(source)),
        `[guide](../../stores/shared/${guide}/#requirements)`);
    }
  }
});

test('source links retain anchors and ungenerated repository targets', () => {
  const source = sourceFile('store/foundationdb/README.md');
  assert.equal(rewriteLinks('[code](src/commonMain/kotlin/maryk/datastore/foundationdb/FoundationDBDataStore.kt#L20)', '../../', source),
    '[code](https://github.com/marykdb/maryk/blob/main/store/foundationdb/src/commonMain/kotlin/maryk/datastore/foundationdb/FoundationDBDataStore.kt#L20)');
  assert.equal(rewriteLinks('[code](src/commonMain/)', '../../', source),
    '[code](https://github.com/marykdb/maryk/tree/main/store/foundationdb/src/commonMain)');
});

test('external links, fragment links and fenced code examples remain unchanged', () => {
  const md = '[web](https://example.com) [email](mailto:a@example.com) [here](#local)\n' +
    '```markdown\n[example](query.md)\n```\n~~~markdown\n[example](query.md)\n~~~';
  assert.equal(rewriteLinks(md, '../../', sourceFile('core/docs/README.md')), md);
});


test('syncDocs preserves tilde and longer backtick fences through every transform', () => {
  const root = mkdtempSync(join(tmpdir(), 'maryk-docs-sync-'));
  const siteRoot = join(root, 'website');
  const coreDocs = join(root, 'core', 'docs');
  mkdirSync(coreDocs, { recursive: true });
  const examples = [
    '~~~kotlin', 'val example = "<T>{value}"', '[example](query.md)', '~~~',
    '````markdown', '```kotlin', '<T>{value}', '```', '[example](query.md)', '````',
  ].join('\n');
  writeFileSync(join(coreDocs, 'README.md'), `# Examples\n\n${examples}\n\n<T>{value} [query](query.md)\n`);
  syncDocs({
    siteRoot, repoRoot: root, coreDocs,
    map: [['src/content/docs/data-modeling/index.mdx', 'README.md']],
    removedTargets: [],
  });
  const generated = readFileSync(join(siteRoot, 'src/content/docs/data-modeling/index.mdx'), 'utf8');
  assert.ok(generated.includes(examples));
  assert.ok(generated.includes('&lt;T&gt;&#123;value&#125; [query](../querying/)'));
});

test('a closing fence requires an empty suffix and the matching marker', () => {
  const md = '~~~~markdown\n~~~\n~~~~ still an example\n```\n[example](query.md)\n~~~~\n[query](query.md)';
  assert.equal(rewriteLinks(md, '../', sourceFile('core/docs/README.md')),
    md.replace('[query](query.md)', '[query](../querying/)'));
});
