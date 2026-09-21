import {copyFileSync, existsSync, mkdirSync, readdirSync, statSync} from 'node:fs';
import {dirname, extname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const siteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const docsRoot = resolve(siteRoot, '..', 'docs');
const buildIndex = join(siteRoot, 'build', 'search-index.json');
const staticIndex = join(siteRoot, 'static', 'search-index.json');

function latestMarkdownTime(directory) {
  let latest = 0;
  for (const entry of readdirSync(directory, {withFileTypes: true})) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      latest = Math.max(latest, latestMarkdownTime(path));
    } else if (extname(entry.name).toLowerCase() === '.md' || extname(entry.name).toLowerCase() === '.mdx') {
      latest = Math.max(latest, statSync(path).mtimeMs);
    }
  }
  return latest;
}

const needsBuild = !existsSync(buildIndex)
  || !existsSync(staticIndex)
  || statSync(buildIndex).mtimeMs < latestMarkdownTime(docsRoot)
  || statSync(staticIndex).mtimeMs < latestMarkdownTime(docsRoot);

if (needsBuild) {
  console.log('文档内容已变化，正在构建本地搜索索引...');
  const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
  const result = spawnSync(npmCommand, ['run', 'build'], {
    cwd: siteRoot,
    stdio: 'inherit',
  });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

mkdirSync(dirname(staticIndex), {recursive: true});
copyFileSync(buildIndex, staticIndex);
console.log('本地搜索索引已同步。');
