import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { SessionManager, SessionError } from "../src/session-manager.mjs";

async function fixture() {
  const root = await mkdtemp(path.join(os.tmpdir(), "retrosala-"));
  const games = path.join(root, "games"); await mkdir(games); await mkdir(path.join(root, "saves")); await mkdir(path.join(root, "sessions"));
  await writeFile(path.join(games, "catalog.json"), JSON.stringify({ "gba-demo": { title: "Ejemplo GBA", platform: "gba", file: "demo.gba", players: 1 } }));
  await writeFile(path.join(games, "demo.gba"), "test");
  return new SessionManager({ gamesDirectory: games, savesDirectory: path.join(root, "saves"), sessionsDirectory: path.join(root, "sessions") });
}
test("creates a private GBA session and accepts controls", async () => {
  const manager = await fixture(); const session = await manager.create("gba-demo");
  assert.equal(session.platform, "gba"); assert.equal((await manager.control(session.id, { control: "A", pressed: true })).accepted, true);
  assert.equal((await manager.control(session.id, { control: "joystick", pressed: true, x: 0.25, y: -0.5 })).accepted, true);
  assert.equal(manager.get(session.id).status, "ready");
});
test("lists only private catalog games whose files are present", async () => {
  const manager = await fixture();
  const catalogPath = path.join(manager.gamesDirectory, "catalog.json");
  await writeFile(catalogPath, JSON.stringify({
    "gba-demo": { title: "Ejemplo GBA", platform: "gba", file: "demo.gba", players: 1 },
    "not-ready": { title: "No disponible", platform: "ds", file: "not-present.nds" }
  }));
  const games = await manager.listCatalog();
  assert.deepEqual(games.map(game => game.gameId), ["gba-demo"]);
  assert.equal(games[0].available, true);
  assert.equal(games[0].platform, "gba");
});
test("rejects an unknown gameId", async () => {
  const manager = await fixture(); await assert.rejects(() => manager.create("missing"), SessionError);
});
test("rejects a catalog entry whose private file is missing", async () => {
  const manager = await fixture();
  const catalogPath = path.join(manager.gamesDirectory, "catalog.json");
  await writeFile(catalogPath, JSON.stringify({ "missing-file": { platform: "ds", file: "not-present.nds" } }));
  await assert.rejects(() => manager.create("missing-file"), SessionError);
});

test("failed real controls and unsupported actions never report success", async () => {
  const manager = await fixture();
  manager.runner = {
    start: async () => ({ media: 'mjpeg-pcm' }),
    control: async () => ({ delivered: false }),
    pause: async () => ({ applied: false }),
    save: async () => ({ applied: false }),
    close: async () => {}
  };
  const session = await manager.create('gba-demo');
  assert.equal(session.capture.status, 'local-streaming');
  await assert.rejects(manager.control(session.id, { control: 'A', pressed: true }), { status: 503 });
  await assert.rejects(manager.pause(session.id), { status: 501 });
  await assert.rejects(manager.save(session.id), { status: 501 });
  assert.equal(manager.get(session.id).status, 'live');
  assert.equal((await manager.close(session.id)).status, 'closed');
});
