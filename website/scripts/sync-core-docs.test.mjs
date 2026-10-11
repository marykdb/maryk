import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { map, rewriteLinks, syncDocs } from './sync-core-docs.mjs';

test('shared write contracts resolve from module and architecture pages', () => {
  for (const prefix of ['../../', '../../../']) {
    for (const source of ['query.md', '../core/docs/query.md', '../../core/docs/query.md', '../../../core/docs/query.md']) {
      for (const anchor of ['', '#atomic', '#serializable-transactions', '#choosing-a-write-boundary']) {
        assert.equal(rewriteLinks(`[contract](${source}${anchor})`, prefix),
          `[contract](${prefix}querying/${anchor})`);
      }
    }
    for (const source of ['../sql/README.md', '../../sql/README.md']) {
      assert.equal(rewriteLinks(`[SQL](${source})`, prefix), `[SQL](${prefix}querying/sql/)`);
    }
    assert.equal(rewriteLinks('[App](../app/docs/README.md#sql-queries-and-writes)', prefix),
      `[App](${prefix}app/#sql-queries-and-writes)`);
    assert.equal(rewriteLinks('[Generator](../../../generator/README.md#generate-proto3-schema)', prefix),
      `[Generator](${prefix}generator/#generate-proto3-schema)`);
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
