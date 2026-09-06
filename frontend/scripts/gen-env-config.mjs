import { readFileSync, writeFileSync } from 'node:fs';

const api = process.env.EMBW_API_URL ?? 'http://localhost:8080/v1';
const ws = process.env.EMBW_WS_URL ?? 'http://localhost:8080/v1/ws';

const body = `window.ENV = {\n  EMBW_API_URL: ${JSON.stringify(api)},\n  EMBW_WS_URL: ${JSON.stringify(ws)}\n};\n`;
writeFileSync('dist/env-config.js', body);
console.log('wrote dist/env-config.js', { api, ws });

// The shared index.html loads env-config.js with a RELATIVE src, required by the Hub build
// (`vite build --base=/app/`). On Cloudflare Pages the SPA fallback then returns index.html
// for `/<route>/env-config.js` on any deep link or hard refresh, so the script body is HTML,
// `window.ENV` never gets defined, and the API client falls back to http://localhost:8080.
// This build is always served from the domain root, so pin the tag to an absolute path.
const indexPath = 'dist/index.html';
const html = readFileSync(indexPath, 'utf8');
if (!html.includes('src="env-config.js"')) {
  throw new Error(
    `${indexPath}: expected <script src="env-config.js"> not found — did index.html change?`,
  );
}
writeFileSync(indexPath, html.replace('src="env-config.js"', 'src="/env-config.js"'));
console.log('rewrote', indexPath, 'env-config.js src -> /env-config.js');
