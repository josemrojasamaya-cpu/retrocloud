import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { SessionManager, SessionError } from "../src/session-manager.mjs";

async function fixture() {
  const root = await mkdtemp(path.join(os.tmpdir(), "retrosala-"));
  const games = path.join(root, "games"); await mkdir(games); await mkdir(path.join(root, "saves")); await mkdir(path.join(root, "sessions"));
  await writeFile(path.join(games, "catalog.json"), JSON.stringify({ "gba-demo": { platform: "gba", file: "demo.gba" } }));
  await writeFile(path.join(games, "demo.gba"), "test");
  return new SessionManager({ gamesDirectory: games, savesDirectory: path.join(root, "saves"), sessionsDirectory: path.join(root, "sessions") });
}
test("creates a private GBA session and accepts controls", async () => {
  const manager = await fixture(); const session = await manager.create("gba-demo");
  assert.equal(session.platform, "gba"); assert.equal(manager.control(session.id, { control: "A", pressed: true }).accepted, true);
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
