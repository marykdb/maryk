import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { map, syncDocs } from './sync-core-docs.mjs';

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
