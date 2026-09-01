import { writeFileSync } from 'node:fs';

const api = process.env.EMBW_API_URL ?? 'http://localhost:8080/v1';
const ws = process.env.EMBW_WS_URL ?? 'http://localhost:8080/v1/ws';

const body = `window.ENV = {\n  EMBW_API_URL: ${JSON.stringify(api)},\n  EMBW_WS_URL: ${JSON.stringify(ws)}\n};\n`;
writeFileSync('dist/env-config.js', body);
console.log('wrote dist/env-config.js', { api, ws });
