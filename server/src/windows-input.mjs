import { spawn } from 'node:child_process';

const keys = { A: 0x58, B: 0x5a, L: 0x41, R: 0x53, Start: 0x0d, Select: 0x08, Up: 0x26, Down: 0x28, Left: 0x25, Right: 0x27 };
export class InputState {
  constructor(platform = 'gba') {
    this.buttons = new Set(); this.stick = new Set();
    if (platform === 'ds') this.keys = { ...keys, X: 0x57, Y: 0x51 };
    else if (platform === 'ps1') this.keys = { ...keys, X: 0x57, Y: 0x51, L2: 0x44, R2: 0x43 };
    else if (platform === 'psp') this.keys = { ...keys, X: 0x57, Y: 0x51 };
    else this.keys = keys;
  }
  accept(event) {
    if (event.control === 'joystick' && Number.isFinite(event.x) && Number.isFinite(event.y)) {
      this.stick.clear();
      if (event.x < -.3) this.stick.add(keys.Left);
      if (event.x > .3) this.stick.add(keys.Right);
      if (event.y < -.3) this.stick.add(keys.Up);
      if (event.y > .3) this.stick.add(keys.Down);
    } else if (this.keys[event.control] !== undefined) {
      if (event.pressed) this.buttons.add(this.keys[event.control]); else this.buttons.delete(this.keys[event.control]);
    } else throw new Error('Control no compatible con esta plataforma');
    return [...new Set([...this.buttons, ...this.stick])];
  }
}

export class WindowsInput {
  constructor(python, script, hwnd, platform = 'gba') {
    this.platform = platform;
    this.state = new InputState(platform); this.sequence = 0; this.pending = new Map();
    this.child = spawn(python, ['-u', script, String(hwnd), platform], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let text = '';
    this.child.stdout.on('data', chunk => {
      text += chunk;
      let end;
      while ((end = text.indexOf('\n')) >= 0) {
        const line = text.slice(0, end); text = text.slice(end + 1);
        try {
          const reply = JSON.parse(line); const pending = this.pending.get(reply.id);
          if (pending) { clearTimeout(pending.timer); this.pending.delete(reply.id); reply.ok ? pending.resolve() : pending.reject(new Error(reply.error)); }
        } catch {}
      }
    });
    this.child.on('error', () => this.fail());
    this.child.on('exit', () => this.fail());
    this.child.stdin.on('error', () => this.fail());
  }
  fail() { for (const p of this.pending.values()) { clearTimeout(p.timer); p.reject(new Error('Puente de controles desconectado')); } this.pending.clear(); }
  send(event) {
    if (this.platform === 'ds' && /^ds-touch-(down|move|up)$/.test(event.control)) {
      if (![event.x, event.y].every(v => Number.isFinite(v) && v >= 0 && v <= 1)) throw new Error('Coordenadas táctiles inválidas');
      return this.write(undefined, { phase: event.control.slice(9), x: event.x, y: event.y });
    }
    return this.write(this.state.accept(event));
  }
  write(keys, touch) {
    const id = ++this.sequence;
    return new Promise((resolve, reject) => {
      if (this.child.exitCode !== null || this.child.killed) { reject(new Error('Puente de controles cerrado')); return; }
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('Sin respuesta del puente de controles')); }, 2500);
      this.pending.set(id, { resolve, reject, timer });
      this.child.stdin.write(JSON.stringify({ id, keys, touch }) + '\n');
    });
  }
  async close() {
    try { await this.write([]); } catch {}
    this.child.stdin.end();
    const timeout = setTimeout(() => this.child.kill(), 1500); timeout.unref();
    this.fail();
  }
}
