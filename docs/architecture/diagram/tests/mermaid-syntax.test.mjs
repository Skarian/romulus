import test from 'node:test';
import assert from 'node:assert/strict';
import { validateMermaidFiles } from '../scripts/validate-mermaid.mjs';

test('all Mermaid diagrams in architecture/diagrams parse', async () => {
  const result = await validateMermaidFiles();
  assert.equal(
    result.failed.length,
    0,
    result.failed.map((item) => `${item.file}: ${item.message}`).join('\n')
  );
  assert.ok(result.passed.length > 0, 'expected at least one .mmd diagram file');
});
