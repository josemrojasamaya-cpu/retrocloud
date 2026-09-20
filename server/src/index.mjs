import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { SessionError, SessionManager } from "./session-manager.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const manager = new SessionManager({
  gamesDirectory: process.env.GAMES_DIRECTORY ?? path.join(root, "games-private"),
  savesDirectory: process.env.SAVES_DIRECTORY ?? path.join(root, "saves-private"),
  sessionsDirectory: process.env.SESSIONS_DIRECTORY ?? path.join(root, "sessions")
});
const port = Number(process.env.PORT ?? 8080);

http.createServer(async (request, response) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host}`);
    if (request.method === "GET" && url.pathname === "/health") return reply(response, 200, { ok: true, webrtc: "pending" });
    const body = await jsonBody(request);
    if (request.method === "POST" && url.pathname === "/v1/sessions") return reply(response, 201, await manager.create(body.gameId));
    const match = url.pathname.match(/^\/v1\/sessions\/([\w-]+)\/(controls|pause|save|close)$/);
    if (request.method === "POST" && match) {
      const [, id, action] = match;
      const result = action === "controls" ? manager.control(id, body) : manager[action](id);
      return reply(response, 200, result);
    }
    const session = url.pathname.match(/^\/v1\/sessions\/([\w-]+)$/);
    if (request.method === "GET" && session) return reply(response, 200, manager.get(session[1]));
    return reply(response, 404, { error: "ruta no encontrada" });
  } catch (error) {
    return reply(response, error instanceof SessionError ? error.status : 500, { error: error.message ?? "error interno" });
  }
}).listen(port, "0.0.0.0", () => console.log(`RetroSala session API listening on ${port}`));

function reply(response, status, payload) { response.writeHead(status, { "content-type": "application/json" }); response.end(JSON.stringify(payload)); }
function jsonBody(request) {
  return new Promise((resolve, reject) => {
    let raw = "";
    request.on("data", chunk => { raw += chunk; if (raw.length > 16_384) request.destroy(); });
    request.on("end", () => { try { resolve(raw ? JSON.parse(raw) : {}); } catch { reject(new SessionError(400, "JSON inválido")); } });
    request.on("error", reject);
  });
}
