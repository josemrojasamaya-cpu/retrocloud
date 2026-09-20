import { randomUUID } from "node:crypto";
import { access, readFile, stat } from "node:fs/promises";
import path from "node:path";
import { constants } from "node:fs";

const allowedPlatforms = new Set(["gba", "ds"]);

export class SessionManager {
  constructor({ gamesDirectory, savesDirectory, sessionsDirectory, now = () => new Date().toISOString() }) {
    this.gamesDirectory = path.resolve(gamesDirectory);
    this.savesDirectory = path.resolve(savesDirectory);
    this.sessionsDirectory = path.resolve(sessionsDirectory);
    this.now = now;
    this.sessions = new Map();
  }

  async create(gameId) {
    if (!isIdentifier(gameId)) throw new SessionError(400, "gameId inválido");
    const catalog = await this.#loadCatalog();
    const game = catalog[gameId];
    if (!game) throw new SessionError(404, "gameId no registrado en el catálogo privado");
    if (!allowedPlatforms.has(game.platform)) throw new SessionError(400, "plataforma no admitida");
    const gamePath = privateFile(this.gamesDirectory, game.file);
    await ensureRegularFile(gamePath, "archivo privado del juego no encontrado");
    const id = randomUUID();
    const session = {
      id, gameId, platform: game.platform, status: "ready", createdAt: this.now(),
      gamePath, saveDirectory: path.join(this.savesDirectory, id),
      capture: capturePlan(game.platform), controls: [], pauseRequested: false, saveRequested: false
    };
    this.sessions.set(id, session);
    return publicSession(session);
  }

  async listCatalog() {
    const catalog = await this.#loadCatalog();
    const games = [];
    for (const [gameId, game] of Object.entries(catalog)) {
      if (!isIdentifier(gameId) || !allowedPlatforms.has(game?.platform)) continue;
      const gamePath = privateFile(this.gamesDirectory, game.file);
      try {
        await ensureRegularFile(gamePath, "archivo privado del juego no encontrado");
        games.push({
          gameId,
          title: typeof game.title === "string" && game.title.trim() ? game.title.trim() : gameId,
          platform: game.platform,
          language: typeof game.language === "string" ? game.language : "es",
          emulationServer: typeof game.emulationServer === "string" ? game.emulationServer : "default",
          streamingAvailable: false,
          players: Number.isInteger(game.players) && game.players > 0 ? game.players : 1,
          description: typeof game.description === "string" ? game.description : "Sesión privada remota."
        });
      } catch (error) {
        if (!(error instanceof SessionError)) throw error;
      }
    }
    return games;
  }

  get(id) { return publicSession(this.#session(id)); }
  control(id, input) {
    const session = this.#session(id);
    if (session.status !== "ready" && session.status !== "paused") throw new SessionError(409, "sesión no acepta controles");
    if (!isControl(input?.control) || typeof input?.pressed !== "boolean") throw new SessionError(400, "control inválido");
    session.controls.push({ control: input.control, pressed: input.pressed, at: this.now() });
    return { accepted: true };
  }
  pause(id) { const s = this.#session(id); s.status = s.status === "paused" ? "ready" : "paused"; return publicSession(s); }
  save(id) { const s = this.#session(id); s.saveRequested = true; return { accepted: true, saveDirectory: s.saveDirectory }; }
  close(id) { const s = this.#session(id); s.status = "closed"; s.closedAt = this.now(); return publicSession(s); }

  #session(id) { const session = this.sessions.get(id); if (!session) throw new SessionError(404, "sesión no encontrada"); return session; }
  async #loadCatalog() {
    const catalogPath = path.join(this.gamesDirectory, "catalog.json");
    try { return JSON.parse(await readFile(catalogPath, "utf8")); }
    catch { throw new SessionError(503, "catálogo privado no configurado"); }
  }
}

export class SessionError extends Error { constructor(status, message) { super(message); this.status = status; } }

function capturePlan(platform) {
  return {
    status: "pending-webrtc", platform,
    video: { source: "Xvfb/Wayland virtual display", encoder: "FFmpeg H.264 or VP8" },
    audio: { source: "PulseAudio virtual sink", encoder: "Opus" },
    signaling: "No implementado; integrar WebRTC en la siguiente fase"
  };
}
function isIdentifier(value) { return typeof value === "string" && /^[a-z0-9][a-z0-9-]{1,80}$/.test(value); }
function isControl(value) { return typeof value === "string" && /^[A-Za-z0-9_-]{1,32}$/.test(value); }
function privateFile(root, relative) {
  if (typeof relative !== "string" || relative.includes("..") || path.isAbsolute(relative)) throw new SessionError(400, "ruta privada inválida");
  const resolved = path.resolve(root, relative);
  if (!resolved.startsWith(`${root}${path.sep}`)) throw new SessionError(400, "ruta privada inválida");
  return resolved;
}
async function ensureRegularFile(file, message) {
  try { await access(file, constants.R_OK); if (!(await stat(file)).isFile()) throw new Error(); }
  catch { throw new SessionError(404, message); }
}
function publicSession(s) { return { id: s.id, gameId: s.gameId, platform: s.platform, status: s.status, createdAt: s.createdAt, closedAt: s.closedAt, capture: s.capture }; }
