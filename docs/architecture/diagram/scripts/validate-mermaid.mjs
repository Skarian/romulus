import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const thisFile = fileURLToPath(import.meta.url);
const scriptsDir = path.dirname(thisFile);
const rootDir = path.resolve(scriptsDir, '..');
const diagramsDir = path.join(rootDir, 'diagrams');

const IDENT = '[A-Za-z0-9_]+';
const NODE = `${IDENT}(?:\\[[^\\]]*\\]|\\([^)]*\\)|\\{[^}]*\\})?`;
const EDGE_RE = new RegExp(`^${NODE}\\s*-+>?\\s*${NODE}$`);
const SIMPLE_NODE_RE = new RegExp(`^${NODE}$`);
const SUBGRAPH_RE = /^subgraph\s+[A-Za-z0-9_]+(?:\[[^\]]*\])?$/;

function lintFlowchart(content) {
  const lines = content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith('%%'));

  if (lines.length === 0) {
    throw new Error('diagram is empty');
  }
  if (!lines[0].startsWith('flowchart ')) {
    throw new Error('first non-empty line must start with "flowchart "');
  }

  let subgraphDepth = 0;
  for (let i = 1; i < lines.length; i += 1) {
    const line = lines[i];
    if (line.startsWith('subgraph ')) {
      if (!SUBGRAPH_RE.test(line)) {
        throw new Error(`invalid subgraph declaration at line ${i + 1}: ${line}`);
      }
      subgraphDepth += 1;
      continue;
    }
    if (line === 'end') {
      subgraphDepth -= 1;
      if (subgraphDepth < 0) {
        throw new Error(`unexpected end at line ${i + 1}`);
      }
      continue;
    }

    if (line.includes('-->')) {
      if (!EDGE_RE.test(line)) {
        throw new Error(`invalid edge syntax at line ${i + 1}: ${line}`);
      }
      continue;
    }

    if (!SIMPLE_NODE_RE.test(line)) {
      throw new Error(`unsupported statement at line ${i + 1}: ${line}`);
    }
  }

  if (subgraphDepth !== 0) {
    throw new Error('unclosed subgraph block(s)');
  }
}

export async function validateMermaidFiles() {
  const entries = await readdir(diagramsDir, { withFileTypes: true });
  const files = entries
    .filter((entry) => entry.isFile() && entry.name.endsWith('.mmd'))
    .map((entry) => entry.name)
    .sort();

  if (files.length === 0) {
    throw new Error(`No Mermaid files found in ${diagramsDir}`);
  }

  const passed = [];
  const failed = [];

  for (const file of files) {
    const fullPath = path.join(diagramsDir, file);
    const content = await readFile(fullPath, 'utf8');
    try {
      lintFlowchart(content);
      passed.push(file);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      failed.push({ file, message });
    }
  }

  return { passed, failed };
}

async function main() {
  const result = await validateMermaidFiles();
  if (result.failed.length > 0) {
    const lines = result.failed.map((item) => `- ${item.file}: ${item.message}`);
    throw new Error(`Mermaid lint failed:\n${lines.join('\n')}`);
  }
  console.log(`Mermaid lint OK (${result.passed.length} files)`);
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]) : '';
if (invokedPath === thisFile) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
