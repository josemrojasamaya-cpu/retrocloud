import { access, mkdir, readFile, writeFile, copyFile } from "node:fs/promises";
import { constants } from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from 'node:url';
import { JpegFrames, jpegPart } from './media-stream.mjs';
import { WindowsInput } from './windows-input.mjs';
import { prepareMelonDS } from './melonds-config.mjs';
import { prepareDuckStation } from './duckstation-config.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export class WindowsEmulatorRunner {
  constructor({ mgbaExecutable = process.env.MGBA_EXECUTABLE, melondsExecutable = process.env.MELONDS_EXECUTABLE, duckstationExecutable = process.env.DUCKSTATION_EXECUTABLE, ppssppExecutable = process.env.PPSSPP_EXECUTABLE, biosDirectory, ffmpegExecutable = process.env.FFMPEG_EXECUTABLE }) {
    this.executables = { gba: mgbaExecutable, ds: melondsExecutable, ps1: duckstationExecutable, psp: ppssppExecutable };
    this.biosDirectory = path.resolve(biosDirectory);
    this.ffmpeg = ffmpegExecutable || "ffmpeg";
    this.processes = new Map();
    this._mjpegClients = new Set();
    this._wsClients = new Set();
    this._audioClients = new Set();
    this.python = process.env.PYTHON_EXECUTABLE || 'python.exe';
    this.lastFrame = null;
    this.lastRawFrame = null;
    this.media = { video: 'idle', audio: 'idle', frames: 0, audioBytes: 0 };
    this.starting = false;
  }

  async start(session) {
    if (this.starting || this.processes.size) throw new EmulatorLaunchError('Ya hay una sesión abierta. Ciérrala antes de iniciar otra.');
    this.starting = true;
    try {
    const executable = this.executables[session.platform];
    const platformNames = { gba: 'MGBA_EXECUTABLE', ds: 'MELONDS_EXECUTABLE', ps1: 'DUCKSTATION_EXECUTABLE', psp: 'PPSSPP_EXECUTABLE' };
    if (!executable) throw new EmulatorLaunchError(`Falta configurar ${platformNames[session.platform] || session.platform}`);
    await executableFile(executable, "El ejecutable configurado no existe o no se puede leer");
    await mkdir(session.saveDirectory, { recursive: true });
    if (session.platform === 'gba') await prepareSoftwareDisplay(executable);
    else if (session.platform === 'ds') await prepareMelonDS(executable, session.saveDirectory);
    else if (session.platform === 'ps1') await prepareDuckStation(executable);

    let args;
    if (session.platform === 'gba') args = ['-C', `savegamePath=${session.saveDirectory}`, '-C', `savestatePath=${session.saveDirectory}`, session.gamePath];
    // -fullscreen makes DuckStation render into a separate window, leaving the
    // captured main window empty and showing the desktop behind it.
    else if (session.platform === 'ps1') args = ['-nogui', session.gamePath];
    else if (session.platform === 'psp') args = [session.gamePath, '--fullscreen'];
    else args = [session.gamePath];

    const child = spawn(executable, args, { windowsHide: false, stdio: ["ignore", "pipe", "pipe"] });
    const record = { child, output: "", started: false, platform: session.platform };
    this.processes.set(session.id, record);
    child.stdout?.on("data", chunk => { record.output = `${record.output}${chunk}`.slice(-4096); });
    child.stderr?.on("data", chunk => { record.output = `${record.output}${chunk}`.slice(-4096); });
    child.once("error", error => { record.error = error; });
    child.once("exit", (code, signal) => {
      record.exited = { code, signal }; this._stopCapture(session.id);
      this.processes.delete(session.id);
      if (!this.processes.size) this.media.video = 'disconnected';
    });
    await wait(600);
    if (record.error || record.exited) {
      this.processes.delete(session.id);
      throw new EmulatorLaunchError(`No se pudo iniciar el emulador: ${record.error?.message ?? `salió con código ${record.exited?.code}`}`);
    }
    record.started = true;

    this.media = { video: 'connecting', audio: 'connecting', frames: 0, audioBytes: 0 };
    const winInfo = (await powershell('emulator-window.ps1', ['-EmulatorProcessId', String(child.pid), '-Platform', session.platform])).trim();
    const parts = winInfo.split(/\s+/);
    const hwnd = parts[0];
    if (!/^\d+$/.test(hwnd) || hwnd === '0') throw new Error('No se encontró la ventana del emulador');
    record.winRect = parts.length >= 5 ? { x: +parts[1], y: +parts[2], w: +parts[3], h: +parts[4] } : null;
    record.input = new WindowsInput(this.python, path.join(root, 'scripts', 'input-bridge.py'), hwnd, session.platform);
    await record.input.write([]);
    await this._startCapture(session.id, hwnd);
    this._startAudio(session.id);
    return { input: 'windows-keyboard', media: 'mjpeg-pcm', video: 'ready' };
    } catch (error) {
      await this.close(session.id);
      throw new EmulatorLaunchError(error.message);
    } finally { this.starting = false; }
  }

  async _startCapture(sessionId, hwnd) {
    const record = this.processes.get(sessionId);
    const platform = record?.platform;
    const isDS = platform === 'ds';
    const rect = record?.winRect;
    // gdigrab's hwnd input returns stale/incorrect pixels for GPU-rendered
    // windows, so those are grabbed from the desktop at the window's location.
    const useDesktop = (platform === 'ps1' || platform === 'psp') && rect;
    const ffArgs = [
      '-hide_banner', '-loglevel', 'error', '-nostdin',
      '-fflags', 'nobuffer', '-flags', 'low_delay',
      '-probesize', '32', '-analyzeduration', '0',
      "-f", "gdigrab", '-draw_mouse', '0',
      "-framerate", '25',
    ];
    if (useDesktop) {
      ffArgs.push('-offset_x', String(rect.x), '-offset_y', String(rect.y),
        '-video_size', `${rect.w}x${rect.h}`, '-i', 'desktop');
    } else {
      ffArgs.push('-i', `hwnd=${hwnd}`);
    }
    const scale = useDesktop ? 'scale=640:-2' : (isDS ? 'crop=in_w:in_h-30:0:30,scale=384:-2' : 'scale=320:-2');
    ffArgs.push(
      "-vf", scale,
      '-pix_fmt', 'yuvj420p', '-threads', '1',
      "-f", "mjpeg",
      "-q:v", isDS ? "2" : (useDesktop ? "4" : "5"),
      "-an",
      "pipe:1"
    );
    const ff = spawn(this.ffmpeg, ffArgs, { stdio: ["ignore", "pipe", "pipe"] });
    if (record) record.ffmpeg = ff;

    let ready;
    let failed;
    const firstFrame = new Promise((resolve, reject) => { ready = resolve; failed = reject; });
    const timer = setTimeout(() => failed(new Error('La captura no produjo imagen en 10 segundos')), 10000);
    const frames = new JpegFrames(frame => {
      this.media.video = 'ready'; this.media.frames++;
      this.lastRawFrame = frame;
      this.lastFrame = jpegPart(frame);
      this._broadcastFrame(this.lastFrame);
      this._broadcastWsFrame(frame);
      ready();
    });
    ff.stderr?.on('data', chunk => { if (record) record.output = `${record.output}${chunk}`.slice(-4096); });
    ff.stdout?.on('data', chunk => frames.push(chunk));
    ff.once('error', error => { this.media.video = 'error'; failed(new Error('No se pudo abrir FFmpeg: ' + error.code)); });
    ff.once('exit', code => {
      if (record) record.ffmpeg = null;
      if (record?.stopping) return;
      this.media.video = 'disconnected';
      failed(new Error(`La captura se cerró (${code}). Comprueba que el emulador no esté minimizado.`));
      for (const client of this._mjpegClients) client.end();
    });
    try { await firstFrame; } finally { clearTimeout(timer); }
  }

  _broadcastFrame(chunk) {
    for (const client of this._mjpegClients) {
      if (client.destroyed) { this._mjpegClients.delete(client); continue; }
      // Drop whole frames for slow consumers; never build an unbounded queue.
      if (client.writableLength < 128 * 1024) client.write(chunk);
    }
  }

  _broadcastWsFrame(frame) {
    for (const ws of this._wsClients) {
      if (ws.readyState !== 1) { this._wsClients.delete(ws); continue; }
      if (ws.bufferedAmount < 128 * 1024) ws.send(frame);
    }
  }

  addWsClient(ws) {
    this._wsClients.add(ws);
    if (this.lastRawFrame) ws.send(this.lastRawFrame);
    if (this.lastStatusText) ws.send(this.lastStatusText);
    ws.on('close', () => this._wsClients.delete(ws));
  }

  broadcastText(text) {
    this.lastStatusText = text;
    for (const ws of this._wsClients) {
      if (ws.readyState !== 1) { this._wsClients.delete(ws); continue; }
      ws.send(text);
    }
  }

  addStreamClient(res) {
    res.writeHead(200, {
      "Content-Type": "multipart/x-mixed-replace; boundary=jpegframe",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive"
    });
    this._mjpegClients.add(res);
    res.flushHeaders();
    if (this.lastFrame) res.write(this.lastFrame);
    res.on("close", () => this._mjpegClients.delete(res));
  }

  _startAudio(sessionId) {
    const record = this.processes.get(sessionId);
    if (!record || record.stopping) return;
    this.media.audio = 'connecting';
    delete this.media.audioFormat;
    const emulatorPid = String(record.child.pid);
    const captureExe = path.join(root, 'scripts', 'bin', 'ProcessAudioCapture.exe');
    const child = spawn(captureExe, [emulatorPid], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    record.audio = child;
    spawn(this.python, ['-u', path.join(root, 'scripts', 'unmute-process.py'), emulatorPid], { windowsHide: true, stdio: 'ignore' });
    let text = '';
    child.stderr.on('data', chunk => {
      text += chunk;
      let newline;
      while ((newline = text.indexOf('\n')) >= 0) {
        const line = text.slice(0, newline); text = text.slice(newline + 1);
        try {
          const state = JSON.parse(line);
          if (state.ready) this.media.audioFormat = state;
          if (state.error) { this.media.audio = 'error'; console.error('Captura de audio:', state.error); }
        } catch { /* diagnostic output is never transmitted as PCM */ }
      }
    });
    child.stdout.on('data', chunk => {
      this.media.audio = 'ready'; this.media.audioBytes += chunk.length;
      for (const res of this._audioClients) {
        // Slow audio consumers reconnect instead of accumulating seconds of delay.
        if (res.writableLength > 64 * 1024) { res.destroy(); continue; }
        res.write(chunk);
      }
    });
    child.once('error', () => { this.media.audio = 'error'; });
    child.once('exit', () => {
      if (record.stopping) return;
      this.media.audio = 'disconnected';
      for (const res of this._audioClients) res.end();
      this._audioClients.clear();
      // USB/Bluetooth output devices may disappear or change. Re-open the
      // current default loopback and force clients to read its new format.
      record.audioRetry = setTimeout(() => this._startAudio(sessionId), 2000);
      record.audioRetry.unref();
    });
  }

  addAudioClient(res) {
    const format = this.media.audioFormat;
    if (!format || this.media.audio !== 'ready') {
      res.writeHead(503, { 'content-type': 'application/json', 'retry-after': '1' });
      res.end(JSON.stringify({ error: 'Audio no disponible todavía' })); return;
    }
    res.writeHead(200, { 'content-type': 'application/octet-stream', 'cache-control': 'no-store',
      'x-audio-encoding': 'pcm_s16le', 'x-audio-rate': String(format.sampleRate), 'x-audio-channels': String(format.channels) });
    res.flushHeaders();
    this._audioClients.add(res);
    res.once('close', () => this._audioClients.delete(res));
  }

  mediaStatus() { return { ...this.media, clients: this._mjpegClients.size, wsClients: this._wsClients.size, audioClients: this._audioClients.size }; }

  controlsSupported() { return true; }

  async control(sessionId, event) {
    const record = this.processes.get(sessionId);
    if (!record?.started) return { delivered: false, reason: "El puente de controles no está disponible" };
    try {
      await record.input.send(event);
      return { delivered: true };
    } catch (error) {
      return { delivered: false, reason: error.message };
    }
  }

  async pause(sessionId) { return { applied: false, reason: "Usa el botón de pausa en el emulador" }; }
  async save(sessionId) { return { applied: false, reason: "Usa Ctrl+S en el emulador" }; }

  _stopCapture(sessionId) {
    const record = this.processes.get(sessionId);
    if (!record || record.stopping) return;
    record.stopping = true;
    clearTimeout(record.audioRetry);
    if (record?.ffmpeg && !record.ffmpeg.killed) record.ffmpeg.kill();
    if (record?.audio && !record.audio.killed) record.audio.kill();
    record?.input?.close();
    this.lastFrame = null;
    this.lastRawFrame = null;
    for (const res of this._mjpegClients) res.end();
    for (const ws of this._wsClients) ws.close();
    for (const res of this._audioClients) res.end();
    this._mjpegClients.clear(); this._wsClients.clear(); this._audioClients.clear();
    this.media.video = 'idle'; this.media.audio = 'idle';
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
function powershell(script, args) {
  return new Promise((resolve, reject) => {
    const child = spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(root, 'scripts', script), ...args], { windowsHide: true });
    let output = '';
    child.stdout.on('data', chunk => { output += chunk; });
    child.once('error', reject);
    child.once('exit', code => code === 0 ? resolve(output) : reject(new Error('No se encontró la ventana de captura')));
  });
}

async function prepareSoftwareDisplay(executable) {
  // Qt's native renderer can be captured by gdigrab; its default OpenGL surface
  // produces black images. Preserve the existing INI and its first backup.
  let directory = path.join(process.env.APPDATA, 'mGBA');
  try { await access(path.join(path.dirname(executable), 'portable.ini')); directory = path.dirname(executable); } catch {}
  const ini = path.join(directory, 'qt.ini');
  await mkdir(directory, { recursive: true });
  let text = '';
  try { text = await readFile(ini, 'utf8'); await copyFile(ini, `${ini}.amayomi-backup`, constants.COPYFILE_EXCL).catch(error => { if (error.code !== 'EEXIST') throw error; }); } catch (error) { if (error.code !== 'ENOENT') throw error; }
  const general = /\[General\]([\s\S]*?)(?=\r?\n\[|$)/;
  const settings = '\ndisplayDriver=0\nmaximized=false\n';
  if (general.test(text)) text = text.replace(general, (_, body) => '[General]' + body.replace(/^displayDriver=.*$|^maximized=.*$/gm, '') + settings);
  else text = '[General]' + settings + '\n' + text;
  await writeFile(ini, text);
}
