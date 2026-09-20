import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";
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

http.createServer(async (request, response) => {
  try {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type");
    if (request.method === "OPTIONS") { response.writeHead(204); response.end(); return; }

    const url = new URL(request.url, `http://${request.headers.host}`);

    if (request.method === "GET" && url.pathname === "/health") return reply(response, 200, { ok: true, mode: "local-pc", streaming: "mjpeg-pcm" });

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
}).listen(port, bindAddress, () => {
  console.log(`\n  ╔══════════════════════════════════════════════════╗`);
  console.log(`  ║        AMAYOMI RETRO — Servidor Local PC        ║`);
  console.log(`  ╠══════════════════════════════════════════════════╣`);
  console.log(`  ║  API:      http://${bindAddress}:${port}              ║`);
  console.log(`  ║  Stream:   http://${bindAddress}:${port}/v1/stream    ║`);
  console.log(`  ║  Health:   http://${bindAddress}:${port}/health       ║`);
  console.log(`  ╚══════════════════════════════════════════════════╝\n`);
});

function reply(response, status, payload) { response.writeHead(status, { "content-type": "application/json" }); response.end(JSON.stringify(payload)); }
function jsonBody(request) { return new Promise((resolve, reject) => { let raw = ""; request.on("data", chunk => { raw += chunk; if (raw.length > 16_384) request.destroy(); }); request.on("end", () => { try { resolve(raw ? JSON.parse(raw) : {}); } catch { reject(new SessionError(400, "JSON inválido")); } }); request.on("error", reject); }); }
