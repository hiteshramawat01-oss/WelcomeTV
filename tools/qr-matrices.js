// Writes QR matrices made by samsung-tv/qr.js for QrJsCompatTest: node tools/qr-matrices.js samsung-tv/qr.js out.json
const qr = require(require('path').resolve(process.argv[2]));
const payloads = [
  'A',
  qr.wifiPayload({ ssid: 'Pichola-Guest', password: 'lakeview2026', security: 'WPA' }),
  qr.wifiPayload({ ssid: 'हवेली अतिथि', password: 'x\;y,z:"q', security: 'WPA', hidden: true }),
  qr.wifiPayload({ ssid: 'Open Cafe', security: 'OPEN' }),
  'x'.repeat(60), 'y'.repeat(120), 'z'.repeat(200), 'w'.repeat(260), 'v'.repeat(400),
  qr.wifiPayload({ ssid: 'S'.repeat(32), password: ';'.repeat(63), security: 'WPA', hidden: true }),
];
const out = payloads.map((text) => { const c = qr.encode(text); const rows = []; for (let y = 0; y < c.size; y++) { let r = ''; for (let x = 0; x < c.size; x++) r += c.isDark(x, y) ? '1' : '0'; rows.push(r); } return { text, size: c.size, rows }; });
require('fs').writeFileSync(process.argv[3], JSON.stringify(out));
console.log(out.map((o) => `${o.text.length}ch→v${(o.size - 17) / 4}`).join(' '));
