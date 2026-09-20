// Sync Vue build output into Spring Boot static resources.
// Usage: from repo root — node scripts/sync-web-to-backend.mjs
// Or: npm run build:backend --prefix wms-web

import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const webDir = join(root, 'wms-web')
const distDir = join(webDir, 'dist')
const staticDir = join(root, 'wms-backend', 'src', 'main', 'resources', 'static')

const skipBuild = process.argv.includes('--skip-build')
if (!skipBuild) {
  console.log('[1/2] Building wms-web ...')
  const r = spawnSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['run', 'build'], {
    cwd: webDir,
    stdio: 'inherit',
    shell: true,
  })
  if (r.status !== 0) {
    process.exit(r.status || 1)
  }
}

if (!existsSync(distDir)) {
  console.error('Missing wms-web/dist. Run npm run build in wms-web first.')
  process.exit(1)
}

console.log('[2/2] Copying dist -> wms-backend/src/main/resources/static ...')
if (existsSync(staticDir)) {
  rmSync(staticDir, { recursive: true, force: true })
}
mkdirSync(staticDir, { recursive: true })
cpSync(distDir, staticDir, { recursive: true })
console.log('Done. Start backend and open http://localhost:9980/')
