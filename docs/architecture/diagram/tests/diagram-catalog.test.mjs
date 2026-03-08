import test from 'node:test';
import assert from 'node:assert/strict';
import { access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { diagramCatalog } from '../src/diagrams.js';

const thisFile = fileURLToPath(import.meta.url);
const testsDir = path.dirname(thisFile);
const rootDir = path.resolve(testsDir, '..');

test('diagram catalog entries are unique and point to real files', async () => {
  assert.ok(diagramCatalog.length > 0, 'diagram catalog must not be empty');

  const ids = new Set();
  for (const item of diagramCatalog) {
    assert.ok(item.id, 'diagram id is required');
    assert.ok(!ids.has(item.id), `duplicate id: ${item.id}`);
    ids.add(item.id);

    assert.ok(item.path.startsWith('/diagrams/'), `unexpected diagram path: ${item.path}`);
    assert.ok(item.path.endsWith('.mmd'), `diagram path must end with .mmd: ${item.path}`);

    const fullPath = path.join(rootDir, item.path);
    await access(fullPath);
  }
});
