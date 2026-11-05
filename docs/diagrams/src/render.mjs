// Renders branded diagrams (LinkedIn-carousel look) to PNG.
// usage: node render.mjs spec.json out.png
import fs from 'node:fs';
import path from 'node:path';
import puppeteer from 'puppeteer';

const [specPath, outPath] = process.argv.slice(2);
const spec = JSON.parse(fs.readFileSync(specPath, 'utf8'));
const mermaidJs = fs.readFileSync(path.resolve('node_modules/mermaid/dist/mermaid.min.js'), 'utf8');

const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
const title = esc(spec.title).replace(/\*(.+?)\*/g, '<em>$1</em>');

const html = `<!doctype html><html><head><meta charset="utf-8"><style>
  :root { --bg:#0a1020; --ink:#e8eef8; --muted:#8b9bb8; --orange:#f5a03a; --line:#1d2742; }
  * { margin:0; box-sizing:border-box; }
  body { background:var(--bg); width:${spec.width || 1600}px; }
  .frame {
    padding:72px 80px 48px;
    background-color:var(--bg);
    background-image:
      linear-gradient(rgba(148,163,184,.055) 1px, transparent 1px),
      linear-gradient(90deg, rgba(148,163,184,.055) 1px, transparent 1px);
    background-size:40px 40px;
    font-family:'Inter', sans-serif; color:var(--ink);
  }
  .kicker { font-family:'DejaVu Sans Mono', monospace; color:var(--orange); letter-spacing:.28em; font-size:20px; font-weight:700; }
  h1 { font-size:64px; font-weight:800; letter-spacing:-.02em; line-height:1.05; margin-top:20px; max-width:1300px; }
  h1 em { font-style:normal; color:var(--orange); }
  .sub { color:var(--muted); font-size:24px; margin-top:20px; max-width:1200px; line-height:1.45; }
  .sub b { color:var(--ink); }
  .diagram { margin:48px 0 40px; }
  .diagram pre { width:100%; display:flex; justify-content:center; margin:0; }
  .diagram svg { width:100% !important; max-width:none !important; height:auto !important; max-height:${spec.maxH || 1400}px; }
  .diagram .node rect { rx:12px; ry:12px; }
  footer { border-top:1px solid var(--line); padding-top:24px; display:flex; justify-content:space-between; font-size:20px; color:var(--muted); }
  footer b { color:var(--ink); }
  footer .cta { font-family:'DejaVu Sans Mono', monospace; color:var(--orange); font-weight:700; letter-spacing:.12em; }
</style></head><body><div class="frame">
  <div class="kicker">${esc(spec.kicker)}</div>
  <h1>${title}</h1>
  <p class="sub">${spec.subtitle}</p>
  <div class="diagram"><pre class="mermaid">${esc(spec.mermaid)}</pre></div>
  <footer><span><b>Vani Santos</b> · Senior Front-end · Angular · Micro frontends · AWS · AI</span><span class="cta">${esc(spec.cta || 'github.com/vanilds')}</span></footer>
</div>
<script>${mermaidJs}</script>
<script>
  mermaid.initialize({
    startOnLoad:false, theme:'base', securityLevel:'loose',
    flowchart:{ curve:'basis', padding:18, nodeSpacing:50, rankSpacing:70, htmlLabels:true, wrappingWidth:420 },
    themeVariables:{
      fontFamily:'Inter, sans-serif', fontSize:'18px',
      background:'#0a1020', primaryColor:'#111a2e', primaryTextColor:'#e8eef8',
      primaryBorderColor:'#38bdf8', lineColor:'#5b6b8c', textColor:'#e8eef8',
      clusterBkg:'rgba(17,26,46,0.35)', clusterBorder:'#2a3654',
      edgeLabelBackground:'#0a1020', titleColor:'#8b9bb8'
    }
  });
  mermaid.run({ querySelector:'.mermaid' }).then(() => { document.body.dataset.done = '1'; });
</script></body></html>`;

const browser = await puppeteer.launch({ args: ['--no-sandbox'] });
const page = await browser.newPage();
await page.setViewport({ width: spec.width || 1600, height: 900, deviceScaleFactor: 2 });
await page.setContent(html, { waitUntil: 'load' });
await page.waitForSelector('body[data-done="1"]', { timeout: 30000 });
const frame = await page.$('.frame');
await frame.screenshot({ path: outPath });
await browser.close();
console.log('ok', outPath);
