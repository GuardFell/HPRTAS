// Export every piStar drawing as the PNG that sits beside the other exports.
//
// piStar has no command line, so the drawing is exported the way the tool
// itself does it - the paper's SVG, rasterised onto a white canvas at the
// model's own size (File > Save image, high resolution off, transparent off) -
// but driven from a headless browser instead of by hand. The tool is loaded
// from disk, each model is loaded into it, and the PNG is written next to the
// models it belongs to.
//
// Usage (from the repository root):
//
//     node tools/export_istar_pngs.mjs                     # every drawing
//     node tools/export_istar_pngs.mjs --only sr-doctor    # one drawing
//     node tools/export_istar_pngs.mjs --scale 2           # piStar's 2x
//     node tools/export_istar_pngs.mjs --tool D:/path/to/piStar/tool/index.html
//
// Needs Node 22+ (its own fetch and WebSocket) and Chrome or Edge. Nothing to
// install.
import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = dirname(dirname(fileURLToPath(import.meta.url)));
const args = process.argv.slice(2);
const arg = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};

const SRC = resolve(arg('--src', join(REPO, 'models', 'socio-technical', 'pistar')));
const OUT = resolve(arg('--out', join(REPO, 'models', 'exports')));
const TOOL = resolve(arg('--tool', join(REPO, '..', 'piStar', 'tool', 'index.html')));
const SCALE = Number(arg('--scale', '1'));
const ONLY = arg('--only', null);
const PORT = Number(arg('--port', '9333'));

const BROWSERS = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
];

function browserPath() {
  const given = arg('--browser', null);
  if (given) return given;
  for (const candidate of BROWSERS) {
    if (existsSync(candidate)) return candidate;
  }
  throw new Error('no Chrome or Edge found; pass --browser <path>');
}

const sleep = (ms) => new Promise((done) => setTimeout(done, ms));

async function main() {
  if (!existsSync(TOOL)) {
    throw new Error(`piStar tool not found at ${TOOL}; pass --tool <path to tool/index.html>`);
  }
  const files = readdirSync(SRC).filter((f) => f.endsWith('.json'))
    .filter((f) => !ONLY || f === `${ONLY}.json` || f === ONLY).sort();
  if (!files.length) throw new Error(`no models to export in ${SRC}`);

  const profile = mkdtempSync(join(tmpdir(), 'pistar-export-'));
  const chrome = spawn(browserPath(), [
    '--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
    '--no-first-run', '--no-default-browser-check', '--disable-gpu',
    '--allow-file-access-from-files', '--window-size=1600,1200',
    'file:///' + TOOL.replace(/\\/g, '/'),
  ], { stdio: 'ignore' });

  const stop = () => { try { chrome.kill(); } catch { /* already gone */ } };
  process.on('exit', stop);

  // --- connect: the page target with the tool in it
  let page = null;
  for (let i = 0; i < 60 && !page; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
      page = list.find((t) => t.type === 'page' && t.url.includes('piStar'));
    } catch { /* devtools not up yet */ }
    if (!page) await sleep(500);
  }
  if (!page) throw new Error('the browser never answered on its debugging port');
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((open, fail) => { ws.onopen = open; ws.onerror = fail; });

  let next = 1;
  const waiting = new Map();
  ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    if (message.id && waiting.has(message.id)) {
      const { done, fail } = waiting.get(message.id);
      waiting.delete(message.id);
      if (message.error) fail(new Error(JSON.stringify(message.error)));
      else done(message.result);
    }
  };
  const send = (method, params = {}) => new Promise((done, fail) => {
    const id = next++;
    waiting.set(id, { done, fail });
    ws.send(JSON.stringify({ id, method, params }));
  });
  const run = async (expression) => {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.exceptionDetails) {
      throw new Error(JSON.stringify(result.exceptionDetails).slice(0, 2000));
    }
    return result.result.value;
  };

  // --- wait for the page itself: piStar loads its own example model when the
  // --- document is ready, and a model loaded before that is wiped by it
  for (let i = 0; i < 120; i++) {
    const ready = await run('document.readyState === "complete" && typeof istar !== "undefined"'
      + ' && !!istar.fileManager && !!istar.paper && !!istar.graph.prop("name")');
    if (ready) break;
    await sleep(500);
  }

  let exported = 0;
  for (const file of files) {
    const model = readFileSync(join(SRC, file), 'utf8');
    const wanted = JSON.parse(model).diagram.name;
    const shot = await run(`(async () => {
      // a page piStar reports a link on is still drawn in full; the report is
      // only a dialog, and this run has nobody to click it away
      istar.displayInvalidModelMessages = function () {};
      let loaded = null;
      for (let attempt = 0; attempt < 6; attempt++) {
        istar.fileManager.loadModel(${JSON.stringify(model)});
        await new Promise((r) => setTimeout(r, 250));
        loaded = istar.graph.prop('name');
        if (loaded === ${JSON.stringify(wanted)}) break;
      }
      if (loaded !== ${JSON.stringify(wanted)}) {
        return { error: 'loaded "' + loaded + '", wanted ' + ${JSON.stringify(JSON.stringify(wanted))} };
      }
      const area = istar.paper.getArea();
      const svg = istar.fileManager.saveSvg(istar.paper);
      const image = new Image();
      await new Promise((ok, bad) => { image.onload = ok; image.onerror = bad; image.src = svg; });
      const canvas = document.createElement('canvas');
      canvas.width = area.width * ${SCALE};
      canvas.height = area.height * ${SCALE};
      const context = canvas.getContext('2d');
      context.fillStyle = 'white';
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(image, 0, 0, canvas.width, canvas.height);
      return { data: canvas.toDataURL('image/png').split(',')[1],
               width: area.width, height: area.height,
               elements: istar.graph.getElements().length, links: istar.graph.getLinks().length };
    })()`);
    if (shot.error) throw new Error(`${file}: ${shot.error}`);
    const png = join(OUT, 'pistar-' + file.replace(/\.json$/, '') + '.png');
    writeFileSync(png, Buffer.from(shot.data, 'base64'));
    exported += 1;
    console.log(`${'pistar-' + file.replace(/\.json$/, '') + '.png'.padEnd(34)}`
      + ` ${String(shot.width).padStart(6)}x${String(shot.height).padEnd(6)} px`
      + `  ${shot.elements} cells, ${shot.links} links`);
  }
  console.log(`\n${exported} drawing(s) written to ${OUT}`);
  ws.close();
  stop();
}

main().catch((error) => { console.error(error.message); process.exit(1); });
