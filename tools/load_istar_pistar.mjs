// Load every piStar drawing into the piStar tool itself, in a headless browser, and report
// whether the tool accepted it.
//
// `verify_hprtas_pistar.py` proves the files are what the sources generate and that their
// layout is sound; it cannot prove the tool will open them. The tool has its own ideas - a
// model it reports as invalid pops a dialog, and the file picker refuses anything the
// browser does not label `text/plain` - and a drawing a reader cannot open is a drawing
// that may as well not exist. This script is that check: no source is read, no layout is
// recomputed, the tool is simply asked to load each file and its verdict is reported.
//
// Usage (from the repository root):
//
//     node tools/load_istar_pistar.mjs                # every JSON under models/socio-technical/pistar/
//     node tools/load_istar_pistar.mjs --only sr-doctor
//     node tools/load_istar_pistar.mjs --tool D:/path/to/piStar/tool/index.html
//
// Needs Node 22+ and Chrome or Edge. Nothing to install. Exits non-zero if any model was
// refused or reported invalid, so it can gate a commit the same way the Python checks do.
import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, readdirSync, readFileSync } from 'node:fs';
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
const TOOL = resolve(arg('--tool', join(REPO, '..', 'piStar', 'tool', 'index.html')));
const ONLY = arg('--only', null);
const PORT = Number(arg('--port', '9335'));

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
  if (!files.length) throw new Error(`no models to load in ${SRC}`);

  const profile = mkdtempSync(join(tmpdir(), 'pistar-load-'));
  const chrome = spawn(browserPath(), [
    '--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
    '--no-first-run', '--no-default-browser-check', '--disable-gpu',
    '--allow-file-access-from-files', '--window-size=1600,1200',
    'file:///' + TOOL.replace(/\\/g, '/'),
  ], { stdio: 'ignore' });
  const stop = () => { try { chrome.kill(); } catch { /* already gone */ } };
  process.on('exit', stop);

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

  // piStar loads its own example model when the document is ready; a model loaded before
  // that is wiped by it, so wait for the tool's own boot first
  for (let i = 0; i < 120; i++) {
    const ready = await run('document.readyState === "complete" && typeof istar !== "undefined"'
      + ' && !!istar.fileManager && !!istar.graph.prop("name")');
    if (ready) break;
    await sleep(500);
  }

  // the verdicts are what the tool reports, so collect them rather than let a dialog block
  // the headless page; the same code path the Load model button runs is clicked below
  await run('window.__loadAlerts = [];'
    + 'istar.displayInvalidModelMessages = function () {};'
    + 'ui.alert = function (msg, title) { window.__loadAlerts.push((title || "") + ": " + msg); };');

  const doc = await send('DOM.getDocument', {});
  const input = await send('DOM.querySelector', { nodeId: doc.root.nodeId, selector: '#input-file-to-load' });

  let refused = 0;
  const rows = [];
  for (const file of files) {
    const source = JSON.parse(readFileSync(join(SRC, file), 'utf8'));
    await run('window.__loadAlerts = [];');
    await send('DOM.setFileInputFiles', { files: [join(SRC, file)], nodeId: input.nodeId });
    const fileType = await run('(function(){var f=document.querySelector("#input-file-to-load").files[0];'
      + 'return f ? f.name + " | " + JSON.stringify(f.type) : "no file selected";})()');
    await run('document.getElementById("modal-button-load-model").click();');
    await sleep(900);
    const verdict = JSON.parse(await run('JSON.stringify({ alerts: window.__loadAlerts,'
      + ' name: istar.graph.prop("name"), cells: istar.graph.getElements().length,'
      + ' links: istar.graph.getLinks().length })'));
    // accepted means: no alert raised, and the graph now holds this model - a load that
    // silently did nothing would leave the previous model's name in place
    const accepted = verdict.alerts.length === 0 && verdict.name === source.diagram.name;
    if (!accepted) refused += 1;
    rows.push([accepted ? 'OK  ' : 'FAIL', file, fileType, verdict]);
  }

  for (const [status, file, fileType, verdict] of rows) {
    console.log(`${status}  ${file.padEnd(22)} ${fileType.padEnd(34)}`
      + ` cells=${String(verdict.cells).padStart(3)} links=${String(verdict.links).padStart(3)}`
      + `  ${JSON.stringify(verdict.name)}`
      + (status === 'OK  ' ? '' : `  ALERTS=${JSON.stringify(verdict.alerts)}`));
  }
  console.log(refused === 0
    ? `\n${rows.length} model(s) loaded by the tool, none refused`
    : `\n${refused} of ${rows.length} model(s) the tool did not accept`);
  ws.close();
  stop();
  process.exit(refused === 0 ? 0 : 1);
}

main().catch((error) => { console.error(error.message); process.exit(1); });
