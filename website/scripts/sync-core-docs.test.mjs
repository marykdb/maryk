import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { syncDocs } from './sync-core-docs.mjs';

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
