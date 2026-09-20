import { access, mkdir } from "node:fs/promises";
import { constants } from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

/**
 * Starts an emulator that remains on the private Windows host. It deliberately
 * receives only paths resolved by SessionManager and never exposes them in API
 * responses. Input injection is kept as a capability flag: mGBA's Windows CLI
 * has no documented network-control endpoint, so this class must not claim
 * that input reached the emulator until a documented bridge is configured.
 */
export class WindowsEmulatorRunner {
  constructor({ mgbaExecutable = process.env.MGBA_EXECUTABLE, melondsExecutable = process.env.MELONDS_EXECUTABLE, biosDirectory }) {
    this.executables = { gba: mgbaExecutable, ds: melondsExecutable };
    this.biosDirectory = path.resolve(biosDirectory);
    this.processes = new Map();
  }

  async start(session) {
    const executable = this.executables[session.platform];
    if (!executable) throw new EmulatorLaunchError(`Falta configurar ${session.platform === "gba" ? "MGBA_EXECUTABLE" : "MELONDS_EXECUTABLE"}`);
    await executableFile(executable, "El ejecutable configurado no existe o no se puede leer");
    await mkdir(session.saveDirectory, { recursive: true });

    // Both frontends accept the private game as their final argument. BIOS and
    // other emulator preferences remain in their own local configuration.
    const child = spawn(executable, [session.gamePath], { windowsHide: false, stdio: ["ignore", "pipe", "pipe"] });
    const record = { child, output: "", started: false };
    this.processes.set(session.id, record);
    child.stdout?.on("data", chunk => { record.output = `${record.output}${chunk}`.slice(-4096); });
    child.stderr?.on("data", chunk => { record.output = `${record.output}${chunk}`.slice(-4096); });
    child.once("error", error => { record.error = error; });
    child.once("exit", (code, signal) => { record.exited = { code, signal }; });
    await nextTick();
    if (record.error || record.exited) {
      this.processes.delete(session.id);
      throw new EmulatorLaunchError(`No se pudo iniciar el emulador: ${record.error?.message ?? `salió con código ${record.exited?.code}`}`);
    }
    record.started = true;
    return { input: "bridge-required", media: "capture-required" };
  }

  controlsSupported() { return false; }
  async control() { return { delivered: false, reason: "Se requiere un puente de controles documentado para el emulador Windows" }; }
  async pause() { return { applied: false, reason: "Se requiere un puente de controles documentado para el emulador Windows" }; }
  async save() { return { applied: false, reason: "Se requiere un puente de guardado documentado para el emulador Windows" }; }
  async close(sessionId) {
    const record = this.processes.get(sessionId);
    if (!record) return;
    this.processes.delete(sessionId);
    if (!record.child.killed) record.child.kill();
  }
}

export class EmulatorLaunchError extends Error {}

async function executableFile(file, message) {
  try { await access(file, constants.R_OK); } catch { throw new EmulatorLaunchError(message); }
}
function nextTick() { return new Promise(resolve => setTimeout(resolve, 250)); }
