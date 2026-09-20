import { access, mkdir } from "node:fs/promises";
import { constants } from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Z]:)/, "$1")), "..");

export class WindowsEmulatorRunner {
  constructor({ mgbaExecutable = process.env.MGBA_EXECUTABLE, melondsExecutable = process.env.MELONDS_EXECUTABLE, biosDirectory, ffmpegExecutable = process.env.FFMPEG_EXECUTABLE }) {
    this.executables = { gba: mgbaExecutable, ds: melondsExecutable };
    this.biosDirectory = path.resolve(biosDirectory);
    this.ffmpeg = ffmpegExecutable || "ffmpeg";
    this.processes = new Map();
    this._mjpegClients = new Set();
  }

  async start(session) {
    const executable = this.executables[session.platform];
    if (!executable) throw new EmulatorLaunchError(`Falta configurar ${session.platform === "gba" ? "MGBA_EXECUTABLE" : "MELONDS_EXECUTABLE"}`);
    await executableFile(executable, "El ejecutable configurado no existe o no se puede leer");
    await mkdir(session.saveDirectory, { recursive: true });

    // The official Windows mGBA 0.10.5 build exposes Lua through its UI, not
    // through a supported --script command-line flag. Start the private game
    // directly and relay controls as Windows keyboard messages instead.
    const args = [session.gamePath];

    const child = spawn(executable, args, { windowsHide: false, stdio: ["ignore", "pipe", "pipe"] });
    const record = { child, output: "", started: false, platform: session.platform };
    this.processes.set(session.id, record);
    child.stdout?.on("data", chunk => { record.output = `${record.output}${chunk}`.slice(-4096); });
    child.stderr?.on("data", chunk => { record.output = `${record.output}${chunk}`.slice(-4096); });
    child.once("error", error => { record.error = error; });
    child.once("exit", (code, signal) => { record.exited = { code, signal }; this._stopCapture(session.id); });
    await wait(1500);
    if (record.error || record.exited) {
      this.processes.delete(session.id);
      throw new EmulatorLaunchError(`No se pudo iniciar el emulador: ${record.error?.message ?? `salió con código ${record.exited?.code}`}`);
    }
    record.started = true;

    try {
      this._startCapture(session.id, session.platform);
    } catch (error) {
      record.captureError = error.message;
    }
    return { input: "windows-keyboard", media: "mjpeg-stream" };
  }

  _startCapture(sessionId, platform) {
    const windowTitle = platform === "gba" ? "mGBA" : "melonDS";
    const ffArgs = [
      "-f", "gdigrab",
      "-framerate", "30",
      "-i", `title=${windowTitle}`,
      "-vf", "scale=480:-2",
      "-f", "mjpeg",
      "-q:v", "4",
      "-an",
      "pipe:1"
    ];
    const ff = spawn(this.ffmpeg, ffArgs, { stdio: ["ignore", "pipe", "pipe"] });
    const record = this.processes.get(sessionId);
    if (record) record.ffmpeg = ff;

    ff.stderr?.on("data", chunk => { if (record) record.output = `${record.output}${chunk}`.slice(-4096); });
    ff.stdout?.on("data", chunk => { this._broadcastFrame(chunk); });
    ff.once("exit", () => { if (record) record.ffmpeg = null; });
  }

  _broadcastFrame(chunk) {
    for (const client of this._mjpegClients) {
      try { if (!client.destroyed) client.write(chunk); } catch { this._mjpegClients.delete(client); }
    }
  }

  addStreamClient(res) {
    res.writeHead(200, {
      "Content-Type": "multipart/x-mixed-replace; boundary=--jpegframe",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive"
    });
    this._mjpegClients.add(res);
    res.on("close", () => this._mjpegClients.delete(res));
  }

  controlsSupported() { return true; }

  async control(sessionId, event) {
    const record = this.processes.get(sessionId);
    if (!record?.started || record.platform !== "gba") return { delivered: false, reason: "El puente de controles GBA no está disponible" };
    const key = event.x != null && event.y != null ? joystickKey(event.x, event.y) : keyFor(event.control);
    if (!key) return { delivered: false, reason: "Control no compatible con mGBA" };
    try {
      await postKey("mGBA", key, event.pressed);
      return { delivered: true };
    } catch {
      return { delivered: false, reason: "Error enviando control" };
    }
  }

  async pause(sessionId) { return { applied: false, reason: "Usa el botón de pausa en el emulador" }; }
  async save(sessionId) { return { applied: false, reason: "Usa Ctrl+S en el emulador" }; }

  _stopCapture(sessionId) {
    const record = this.processes.get(sessionId);
    if (record?.ffmpeg && !record.ffmpeg.killed) record.ffmpeg.kill();
  }

  async close(sessionId) {
    const record = this.processes.get(sessionId);
    if (!record) return;
    this._stopCapture(sessionId);
    this.processes.delete(sessionId);
    if (!record.child.killed) record.child.kill();
  }
}

export class EmulatorLaunchError extends Error {}

async function executableFile(file, message) {
  try { await access(file, constants.R_OK); } catch { throw new EmulatorLaunchError(message); }
}
function wait(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
const keyboard = { A: "Z", B: "X", L: "A", R: "S", Start: "ENTER", Select: "BACK", Up: "UP", Down: "DOWN", Left: "LEFT", Right: "RIGHT" };
function keyFor(control) { return keyboard[control]; }
function joystickKey(x, y) { return Math.abs(x) > Math.abs(y) ? (x > .3 ? "RIGHT" : x < -.3 ? "LEFT" : null) : (y > .3 ? "DOWN" : y < -.3 ? "UP" : null); }
function postKey(title, key, down) {
  const script = path.join(root, "scripts", "send-mgba-key.ps1");
  return new Promise((resolve, reject) => {
    const child = spawn("powershell.exe", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script, "-WindowTitle", title, "-Key", key, "-Down", String(down)], { windowsHide: true });
    child.once("exit", code => code === 0 ? resolve() : reject(new Error("No se pudo entregar la tecla a mGBA")));
    child.once("error", reject);
  });
}
