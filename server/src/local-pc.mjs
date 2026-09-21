import http from "node:http";
import path from "node:path";
import { createReadStream, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { WebSocketServer } from "ws";
import { SessionError, SessionManager } from "./session-manager.mjs";
import { WindowsEmulatorRunner } from "./windows-emulator-runner.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const bindAddress = process.env.LOCAL_PC_BIND_ADDRESS;
const apiToken = process.env.SESSION_API_TOKEN;
if (!bindAddress) throw new Error("LOCAL_PC_BIND_ADDRESS es obligatorio; usa la IPv4 privada de esta computadora.");
if (!apiToken) throw new Error("SESSION_API_TOKEN es obligatorio en modo local-pc.");

const runner = new WindowsEmulatorRunner({
  biosDirectory: process.env.BIOS_DIRECTORY ?? path.join(root, "bios-private")
});

const manager = new SessionManager({
  gamesDirectory: process.env.GAMES_DIRECTORY ?? path.join(root, "games-private"),
  savesDirectory: process.env.SAVES_DIRECTORY ?? path.join(root, "saves-private"),
  sessionsDirectory: process.env.SESSIONS_DIRECTORY ?? path.join(root, "sessions"),
  runner
});
const port = Number(process.env.PORT ?? 8080);

const server = http.createServer(async (request, response) => {
  try {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type");
    if (request.method === "OPTIONS") { response.writeHead(204); response.end(); return; }

    const url = new URL(request.url, `http://${request.headers.host}`);

    if (request.method === "GET" && url.pathname === "/health") return reply(response, 200, { ok: true, mode: "local-pc", streaming: "ws+mjpeg-pcm" });

    if (request.method === "GET" && url.pathname === "/apk") {
      const apkPath = path.join(root, '..', 'amayomi-retro-0.4.2.apk');
      try {
        const size = statSync(apkPath).size;
        response.writeHead(200, { "content-type": "application/vnd.android.package-archive", "content-length": size, "content-disposition": "attachment; filename=amayomi-retro-0.4.2.apk" });
        createReadStream(apkPath).pipe(response);
      } catch { reply(response, 404, { error: "APK no encontrada" }); }
      return;
    }

    if (request.method === "GET" && url.pathname === "/control") {
      response.writeHead(200, { "content-type": "text/html; charset=utf-8" });
      response.end(controllerPage());
      return;
    }

    if (request.method === "GET" && url.pathname === "/v1/stream") {
      runner.addStreamClient(response);
      return;
    }

    if (url.pathname.startsWith("/v1/") && request.headers.authorization !== `Bearer ${apiToken}`) return reply(response, 401, { error: "token de sesión inválido" });
    if (request.method === 'GET' && url.pathname === '/v1/media/status') return reply(response, 200, runner.mediaStatus());
    if (request.method === 'GET' && url.pathname === '/v1/audio') { runner.addAudioClient(response); return; }
    if (request.method === "GET" && url.pathname === "/v1/catalog") return reply(response, 200, { games: await manager.listCatalog() });
    const body = await jsonBody(request);
    if (request.method === "POST" && url.pathname === "/v1/sessions") return reply(response, 201, await manager.create(body.gameId));
    const match = url.pathname.match(/^\/v1\/sessions\/([\w-]+)\/(controls|pause|save|close)$/);
    if (request.method === "POST" && match) return reply(response, 200, await manager[match[2] === "controls" ? "control" : match[2]](match[1], body));
    const session = url.pathname.match(/^\/v1\/sessions\/([\w-]+)$/);
    if (request.method === "GET" && session) return reply(response, 200, manager.get(session[1]));
    return reply(response, 404, { error: "ruta no encontrada" });
  } catch (error) { return reply(response, error instanceof SessionError ? error.status : 500, { error: error.message ?? "error interno" }); }
});

const wss = new WebSocketServer({ noServer: true });
wss.on("connection", (ws, req) => {
  const auth = req.headers["authorization"] ?? new URL(req.url, "http://x").searchParams.get("token");
  if (auth !== `Bearer ${apiToken}` && auth !== apiToken) { ws.close(4001, "token inválido"); return; }

  runner.addWsClient(ws);

  ws.on("message", async (data) => {
    try {
      const event = JSON.parse(data);
      const activeSession = manager.activeSessionId();
      if (!activeSession) { ws.send(JSON.stringify({ ok: false, error: "sin sesión activa" })); return; }
      const result = await manager.control(activeSession, event);
      ws.send(JSON.stringify({ ok: result.delivered }));
    } catch (error) {
      ws.send(JSON.stringify({ ok: false, error: error.message }));
    }
  });
});

const controlWss = new WebSocketServer({ noServer: true });
const MAX_PLAYERS = 2;
const takenSlots = new Set();

// Slots are reused on disconnect so a reconnecting phone reclaims player 1
// instead of pushing the numbering past the emulator's two pads.
function claimSlot() {
  for (let slot = 1; slot <= MAX_PLAYERS; slot++) if (!takenSlots.has(slot)) { takenSlots.add(slot); return slot; }
  return 0;
}

function announceControllers() {
  runner.broadcastText(JSON.stringify({ type: "controllers", players: [...takenSlots].sort() }));
}

controlWss.on("connection", (ws) => {
  const player = claimSlot();
  if (!player) { ws.close(4002, "sala llena"); return; }
  ws.send("player:" + player);
  const platform = manager.activePlatform();
  if (platform) ws.send("platform:" + platform);
  announceControllers();
  ws.on("message", async (data) => {
    const msg = data.toString();
    const activeSession = manager.activeSessionId();
    if (!activeSession) return;
    const parsed = parseControlMessage(player, msg);
    if (parsed) await manager.control(activeSession, parsed).catch(() => {});
  });
  ws.on("close", () => { takenSlots.delete(player); announceControllers(); });
});

function parseControlMessage(player, msg) {
  const parts = msg.split(":");
  if (parts[0] === "joystick" && parts.length >= 3) {
    const x = parseFloat(parts[1]), y = parseFloat(parts[2]);
    if (!isNaN(x) && !isNaN(y)) return { player, control: "joystick", pressed: true, x, y };
  }
  if (parts[0] === "ds-touch" && parts.length >= 4) {
    const phase = parts[1], x = parseFloat(parts[2]), y = parseFloat(parts[3]);
    if (!isNaN(x) && !isNaN(y)) return { player, control: `ds-touch-${phase}`, pressed: phase !== "up", x, y };
  }
  if (parts.length === 2 && (parts[1] === "down" || parts[1] === "up")) {
    return { player, control: parts[0], pressed: parts[1] === "down" };
  }
  return null;
}

server.on("upgrade", (req, socket, head) => {
  const pathname = new URL(req.url, "http://x").pathname;
  if (pathname === "/ws") {
    wss.handleUpgrade(req, socket, head, (ws) => wss.emit("connection", ws, req));
  } else if (pathname === "/ws-control") {
    controlWss.handleUpgrade(req, socket, head, (ws) => controlWss.emit("connection", ws, req));
  } else {
    socket.destroy();
  }
});

server.listen(port, bindAddress, () => {
  console.log(`\n  ╔══════════════════════════════════════════════════╗`);
  console.log(`  ║        AMAYOMI RETRO — Servidor Local PC        ║`);
  console.log(`  ╠══════════════════════════════════════════════════╣`);
  console.log(`  ║  API:      http://${bindAddress}:${port}              ║`);
  console.log(`  ║  WS:       ws://${bindAddress}:${port}/ws             ║`);
  console.log(`  ║  Stream:   http://${bindAddress}:${port}/v1/stream    ║`);
  console.log(`  ║  Health:   http://${bindAddress}:${port}/health       ║`);
  console.log(`  ╚══════════════════════════════════════════════════╝\n`);
});

function reply(response, status, payload) { response.writeHead(status, { "content-type": "application/json" }); response.end(JSON.stringify(payload)); }
function jsonBody(request) { return new Promise((resolve, reject) => { let raw = ""; request.on("data", chunk => { raw += chunk; if (raw.length > 16_384) request.destroy(); }); request.on("end", () => { try { resolve(raw ? JSON.parse(raw) : {}); } catch { reject(new SessionError(400, "JSON inválido")); } }); request.on("error", reject); }); }

function controllerPage() {
  return `<!doctype html><html lang=es><head><meta charset=utf-8><title>Amayomi Retro — Mando</title>
<meta name=viewport content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>
<meta name=theme-color content='#111'>
<style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent;touch-action:none;user-select:none}
html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#111;color:#eee;font-family:system-ui,sans-serif}

/* === GLOBAL LAYOUT === */
.ctrl{height:100%;display:grid;grid-template-rows:32px auto 1fr auto 22px;padding:6px 10px;gap:2px}
.bar{display:flex;align-items:center;justify-content:space-between}
.brand{font-size:13px;font-weight:800;letter-spacing:2px;color:#7c3aed}
.st{font-size:10px;color:#666}.dot{display:inline-block;width:6px;height:6px;border-radius:50%;background:#f43f5e;margin-right:4px}
.on .dot{background:#22d3ee;box-shadow:0 0 6px #22d3ee}
.pill{font-size:11px;font-weight:800;letter-spacing:1px;padding:3px 9px;border-radius:999px;background:#1a1a2e;border:1px solid #333;color:#555}
.pill.p1{background:#2a1147;border-color:#a78bfa;color:#c4b5fd}
.pill.p2{background:#06364a;border-color:#22d3ee;color:#7dd3fc}

/* === SHOULDERS === */
.sh{display:flex;justify-content:space-between;gap:6px;padding:0 2px}
.sh button{flex:1;padding:8px 0;border:1px solid #333;border-radius:10px;background:#1a1a2e;color:#ccc;font-weight:700;font-size:14px}
.sh button:active{background:#2a2a4e;transform:scale(.96)}

/* === MAIN ZONE === */
.zone{display:grid;grid-template-columns:1fr 1fr;align-items:center;gap:0;min-height:0}
.side{display:flex;align-items:center;justify-content:center}

/* === D-PAD (cross shape) === */
.dp{position:relative;width:140px;height:140px}
.dp button{position:absolute;width:46px;height:46px;border:2px solid #444;border-radius:8px;background:#1a1a2e;color:#ccc;font-size:18px;display:flex;align-items:center;justify-content:center}
.dp button:active{background:#3a3a5e;transform:scale(.92)}
.dp .u{left:47px;top:0}.dp .d{left:47px;bottom:0}.dp .l{left:0;top:47px}.dp .r{right:0;top:47px}
.dp .mid{position:absolute;left:47px;top:47px;width:46px;height:46px;background:#151525;border-radius:50%}

/* === GBA FACE: A upper-right, B lower-left (diagonal like real GBA) === */
.gba-ab{position:relative;width:130px;height:110px}
.gba-ab button{position:absolute;width:58px;height:58px;border-radius:50%;font-size:20px;font-weight:700;color:#fff;border:2px solid}
.gba-ab button:active{transform:scale(.9)}
.gba-ab .a{right:0;top:0;background:#7c3aed;border-color:#a78bfa;box-shadow:0 0 14px #7c3aed44}
.gba-ab .b{left:0;bottom:0;background:#be185d;border-color:#f472b6;box-shadow:0 0 14px #be185d44}

/* === DS/PS FACE: diamond layout === */
.diamond{position:relative;width:140px;height:140px}
.diamond button{position:absolute;width:50px;height:50px;border-radius:50%;font-size:16px;font-weight:700;color:#fff;border:2px solid}
.diamond button:active{transform:scale(.9)}
.diamond .top{left:45px;top:0}.diamond .right{right:0;top:45px}.diamond .bottom{left:45px;bottom:0}.diamond .left{left:0;top:45px}
/* DS colors */
.diamond .ds-x{background:#0891b2;border-color:#22d3ee;box-shadow:0 0 12px #0891b244}
.diamond .ds-y{background:#059669;border-color:#34d399;box-shadow:0 0 12px #05966944}
.diamond .ds-a{background:#7c3aed;border-color:#a78bfa;box-shadow:0 0 12px #7c3aed44}
.diamond .ds-b{background:#be185d;border-color:#f472b6;box-shadow:0 0 12px #be185d44}
/* PS1 colors */
.diamond .ps-tri{background:#059669;border-color:#34d399;box-shadow:0 0 12px #05966944}
.diamond .ps-o{background:#ef4444;border-color:#f87171;box-shadow:0 0 12px #ef444444}
.diamond .ps-x{background:#3b82f6;border-color:#60a5fa;box-shadow:0 0 12px #3b82f644}
.diamond .ps-sq{background:#ec4899;border-color:#f472b6;box-shadow:0 0 12px #ec489944}

/* === JOYSTICK === */
.stick{width:130px;height:130px;border-radius:50%;background:radial-gradient(circle,#222 0 27%,#0a0a14 28%);border:2px solid #7c3aed;box-shadow:0 0 20px #7c3aed33;position:relative}
.thumb{width:38%;aspect-ratio:1;border-radius:50%;background:linear-gradient(135deg,#22d3ee,#7c3aed);position:absolute;left:31%;top:31%;box-shadow:0 4px 14px #0008}

/* === CENTER BUTTONS === */
.ctr{display:flex;gap:6px;justify-content:center;flex-wrap:wrap;padding:0 4px}
.ctr button{border:0;border-radius:8px;padding:7px 14px;background:#1a1a2e;border:1px solid #333;color:#888;font-weight:700;font-size:11px}
.ctr button:active{background:#2a2a4e;color:#eee}
.ctr .exit{background:#3b1132;border-color:#f43f5e66;color:#f43f5e}
.ctr .toggle{background:#0e3320;border-color:#22c55e44;color:#4ade80;font-size:10px}

/* === DS TOUCH === */
.touch-area{display:none;border:2px dashed #7c3aed66;border-radius:14px;background:#0a0a1a;align-items:center;justify-content:center;color:#555;min-height:100px;grid-column:1/-1;font-size:13px}
.touch-area.on{display:flex}

.hint{font-size:9px;color:#444;text-align:center;letter-spacing:1px}
.hidden{display:none!important}
@media(max-height:380px){.dp{width:120px;height:120px}.dp button{width:38px;height:38px;font-size:15px}.dp .u{left:41px;top:0}.dp .d{left:41px;bottom:0}.dp .l{left:0;top:41px}.dp .r{right:0;top:41px}.dp .mid{left:41px;top:41px;width:38px;height:38px}.gba-ab button{width:50px;height:50px}.diamond{width:120px;height:120px}.diamond button{width:42px;height:42px;font-size:14px}.diamond .top{left:39px;top:0}.diamond .right{right:0;top:39px}.diamond .bottom{left:39px;bottom:0}.diamond .left{left:0;top:39px}.stick{width:110px;height:110px}}
</style></head><body>
<main class=ctrl>
<header class=bar>
  <span class=brand>AMAYOMI RETRO</span>
  <span id=state class=st><i class=dot></i>Conectando</span>
  <span id=player class=pill>--</span>
</header>

<!-- ===== SHOULDERS ===== -->
<div id=sh-gba class="sh layout-gba"><button data-c=L>L</button><button data-c=R>R</button></div>
<div id=sh-ds class="sh layout-ds hidden"><button data-c=L>L</button><button data-c=R>R</button></div>
<div id=sh-ps1 class="sh layout-ps1 hidden"><button data-c=L2>L2</button><button data-c=L>L1</button><button data-c=R>R1</button><button data-c=R2>R2</button></div>
<div id=sh-psp class="sh layout-psp hidden"><button data-c=L>L</button><button data-c=R>R</button></div>

<!-- ===== MAIN CONTROLS ===== -->
<section class=zone>

  <!-- GBA: D-pad left, A/B diagonal right -->
  <div class="side layout-gba">
    <div class=dp>
      <button class=u data-c=Up>▲</button>
      <button class=l data-c=Left>◀</button><div class=mid></div><button class=r data-c=Right>▶</button>
      <button class=d data-c=Down>▼</button>
    </div>
  </div>
  <div class="side layout-gba">
    <div class=gba-ab>
      <button class=a data-c=A>A</button>
      <button class=b data-c=B>B</button>
    </div>
  </div>

  <!-- DS: D-pad left, X/Y/A/B diamond right -->
  <div class="side layout-ds hidden">
    <div class=dp>
      <button class=u data-c=Up>▲</button>
      <button class=l data-c=Left>◀</button><div class=mid></div><button class=r data-c=Right>▶</button>
      <button class=d data-c=Down>▼</button>
    </div>
  </div>
  <div class="side layout-ds hidden">
    <div class=diamond>
      <button class="top ds-x" data-c=X>X</button>
      <button class="left ds-y" data-c=Y>Y</button>
      <button class="right ds-a" data-c=A>A</button>
      <button class="bottom ds-b" data-c=B>B</button>
    </div>
  </div>

  <!-- PS1: Joystick/D-pad left, △□○✕ diamond right -->
  <div class="side layout-ps1 hidden">
    <div id=ps1-stick class=stick><div id=ps1-thumb class=thumb></div></div>
    <div id=ps1-dpad class="dp hidden">
      <button class=u data-c=Up>▲</button>
      <button class=l data-c=Left>◀</button><div class=mid></div><button class=r data-c=Right>▶</button>
      <button class=d data-c=Down>▼</button>
    </div>
  </div>
  <div class="side layout-ps1 hidden">
    <div class=diamond>
      <button class="top ps-tri" data-c=X>△</button>
      <button class="left ps-sq" data-c=Y>□</button>
      <button class="right ps-o" data-c=A>○</button>
      <button class="bottom ps-x" data-c=B>✕</button>
    </div>
  </div>

  <!-- PSP: D-pad + joystick left, △□○✕ right -->
  <div class="side layout-psp hidden">
    <div style="display:flex;flex-direction:column;align-items:center;gap:8px">
      <div class=dp>
        <button class=u data-c=Up>▲</button>
        <button class=l data-c=Left>◀</button><div class=mid></div><button class=r data-c=Right>▶</button>
        <button class=d data-c=Down>▼</button>
      </div>
      <div id=psp-stick class=stick style="width:90px;height:90px"><div id=psp-thumb class=thumb></div></div>
    </div>
  </div>
  <div class="side layout-psp hidden">
    <div class=diamond>
      <button class="top ps-tri" data-c=X>△</button>
      <button class="left ps-sq" data-c=Y>□</button>
      <button class="right ps-o" data-c=A>○</button>
      <button class="bottom ps-x" data-c=B>✕</button>
    </div>
  </div>

  <!-- DS touch overlay -->
  <div id=touch class=touch-area>Toca aquí para pantalla táctil DS</div>
</section>

<!-- ===== CENTER ===== -->
<div class=ctr>
  <button data-c=Select>Select</button>
  <button data-c=Start>Start</button>
  <button id=btnTouch class="layout-ds hidden">Táctil</button>
  <button id=btnToggle class="toggle layout-ps1 hidden">⬅ Flechas</button>
  <button id=btnPspToggle class="toggle layout-psp hidden">⬅ Flechas</button>
  <button data-c=Salir class=exit>Salir</button>
</div>

<footer class=hint>CREADO Y FUNDADO POR JOSÉ AMAYA</footer>
</main>

<script>
let ws,player=0,platform='gba',touchOn=false,dpadOn=false;
const stateEl=document.getElementById('state'),playerEl=document.getElementById('player');

function connect(){
  const proto=location.protocol==='https:'?'wss:':'ws:';
  ws=new WebSocket(proto+'//'+location.host+'/ws-control');
  ws.onopen=()=>{stateEl.className='st on';stateEl.innerHTML='<i class=dot></i>Conectado'};
  ws.onclose=ev=>{stateEl.className='st';
    if(ev.code===4002){stateEl.innerHTML='<i class=dot></i>Sala llena (2 controles)';return}
    stateEl.innerHTML='<i class=dot></i>Reconectando';setTimeout(connect,1200)};
  ws.onmessage=e=>{
    if(e.data.startsWith('player:')){player=+e.data.slice(7);playerEl.textContent='CONTROL '+player;playerEl.className='pill p'+player}
    if(e.data.startsWith('platform:'))setPlatform(e.data.slice(9));
  };
}connect();

function setPlatform(p){
  platform=p;
  document.querySelectorAll('.layout-gba').forEach(el=>el.classList.toggle('hidden',p!=='gba'));
  document.querySelectorAll('.layout-ds').forEach(el=>el.classList.toggle('hidden',p!=='ds'));
  document.querySelectorAll('.layout-ps1').forEach(el=>el.classList.toggle('hidden',p!=='ps1'));
  document.querySelectorAll('.layout-psp').forEach(el=>el.classList.toggle('hidden',p!=='psp'));
  touchOn=false;document.getElementById('touch').classList.remove('on');
}

function send(v){if(ws&&ws.readyState===1)ws.send(v)}
function buzz(){if(navigator.vibrate)navigator.vibrate(8)}

document.querySelectorAll('[data-c]').forEach(b=>{
  const c=b.dataset.c;
  b.addEventListener('pointerdown',e=>{e.preventDefault();b.setPointerCapture&&b.setPointerCapture(e.pointerId);send(c+':down');buzz()});
  b.addEventListener('pointerup',e=>{e.preventDefault();send(c+':up')});
  b.addEventListener('pointercancel',e=>{e.preventDefault();send(c+':up')});
  b.addEventListener('pointerleave',e=>{if(e.buttons){e.preventDefault();send(c+':up')}});
});

// PS1 joystick
const ps1S=document.getElementById('ps1-stick'),ps1T=document.getElementById('ps1-thumb');
function mvJ(e){const r=ps1S.getBoundingClientRect(),x=Math.max(-1,Math.min(1,(e.clientX-r.left-r.width/2)/(r.width/2))),y=Math.max(-1,Math.min(1,(e.clientY-r.top-r.height/2)/(r.height/2)));ps1T.style.left=(31+x*25)+'%';ps1T.style.top=(31+y*25)+'%';send('joystick:'+x.toFixed(3)+':'+y.toFixed(3))}
ps1S.addEventListener('pointerdown',e=>{ps1S.setPointerCapture(e.pointerId);mvJ(e)});
ps1S.addEventListener('pointermove',e=>{if(e.buttons)mvJ(e)});
['pointerup','pointercancel'].forEach(n=>ps1S.addEventListener(n,()=>{ps1T.style.left='31%';ps1T.style.top='31%';send('joystick:0:0')}));

// PS1 toggle stick/dpad
document.getElementById('btnToggle').onclick=()=>{
  dpadOn=!dpadOn;
  document.getElementById('ps1-stick').classList.toggle('hidden',dpadOn);
  document.getElementById('ps1-dpad').classList.toggle('hidden',!dpadOn);
  document.getElementById('btnToggle').textContent=dpadOn?'\\u{1F3AE} Joystick':'\\u2B05 Flechas';
};

// PSP joystick
const pspS=document.getElementById('psp-stick'),pspT=document.getElementById('psp-thumb');
function mvP(e){const r=pspS.getBoundingClientRect(),x=Math.max(-1,Math.min(1,(e.clientX-r.left-r.width/2)/(r.width/2))),y=Math.max(-1,Math.min(1,(e.clientY-r.top-r.height/2)/(r.height/2)));pspT.style.left=(31+x*25)+'%';pspT.style.top=(31+y*25)+'%';send('joystick:'+x.toFixed(3)+':'+y.toFixed(3))}
pspS.addEventListener('pointerdown',e=>{pspS.setPointerCapture(e.pointerId);mvP(e)});
pspS.addEventListener('pointermove',e=>{if(e.buttons)mvP(e)});
['pointerup','pointercancel'].forEach(n=>pspS.addEventListener(n,()=>{pspT.style.left='31%';pspT.style.top='31%';send('joystick:0:0')}));

// DS touch
const tch=document.getElementById('touch');
document.getElementById('btnTouch').onclick=()=>{touchOn=!touchOn;tch.classList.toggle('on',touchOn)};
function tp(e,ph){const r=tch.getBoundingClientRect();send('ds-touch:'+ph+':'+Math.max(0,Math.min(1,(e.clientX-r.left)/r.width)).toFixed(4)+':'+Math.max(0,Math.min(1,(e.clientY-r.top)/r.height)).toFixed(4))}
tch.addEventListener('pointerdown',e=>{tch.setPointerCapture(e.pointerId);tp(e,'down')});
tch.addEventListener('pointermove',e=>{if(e.buttons)tp(e,'move')});
['pointerup','pointercancel'].forEach(n=>tch.addEventListener(n,e=>tp(e,'up')));
</script></body></html>`;
}
