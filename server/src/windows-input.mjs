import { spawn } from 'node:child_process';

const arrows = { Up: 0x26, Down: 0x28, Left: 0x25, Right: 0x27 };
const base = { A: 0x58, B: 0x5a, L: 0x41, R: 0x53, Start: 0x0d, Select: 0x08, ...arrows };
const arrowStick = { up: 0x26, down: 0x28, left: 0x25, right: 0x27 };

// PS1 keys mirror DuckStation's Pad1/Pad2 bindings, written by duckstation-config.
// The two tables must stay disjoint so both controllers can be held at once.
const ps1Player1 = {
  buttons: {
    ...arrows,
    X: 0x49, Y: 0x4a, A: 0x4c, B: 0x4b,
    L: 0x51, R: 0x45, L2: 0x31, R2: 0x33,
    Start: 0x0d, Select: 0x08
  },
  stick: { up: 0x57, down: 0x53, left: 0x41, right: 0x44 }
};
const ps1Player2 = {
  buttons: {
    Up: 0x54, Down: 0x47, Left: 0x46, Right: 0x48,
    X: 0x4f, Y: 0x55, A: 0x50, B: 0x4d,
    L: 0x52, R: 0x5a, L2: 0x34, R2: 0x36,
    Start: 0x35, Select: 0x37
  },
  stick: { up: 0x59, down: 0x4e, left: 0x56, right: 0x42 }
};

export const ps1KeyTables = { 1: ps1Player1, 2: ps1Player2 };

function tableFor(platform, player) {
  if (platform === 'ps1') return player === 2 ? ps1Player2 : ps1Player1;
  if (platform === 'ds' || platform === 'psp') return { buttons: { ...base, X: 0x57, Y: 0x51 }, stick: arrowStick };
  return { buttons: base, stick: arrowStick };
}

export function allowedKeys(platform) {
  const keys = new Set();
  for (const player of platform === 'ps1' ? [1, 2] : [1]) {
    const table = tableFor(platform, player);
    for (const code of Object.values(table.buttons)) keys.add(code);
    for (const code of Object.values(table.stick)) keys.add(code);
  }
  return [...keys].sort((a, b) => a - b);
}

export class InputState {
  constructor(platform = 'gba', player = 1) {
    this.buttons = new Set(); this.stick = new Set();
    const table = tableFor(platform, player);
    this.keys = table.buttons;
    this.stickKeys = table.stick;
  }
  accept(event) {
    if (event.control === 'joystick' && Number.isFinite(event.x) && Number.isFinite(event.y)) {
      this.stick.clear();
      if (event.x < -.3) this.stick.add(this.stickKeys.left);
      if (event.x > .3) this.stick.add(this.stickKeys.right);
      if (event.y < -.3) this.stick.add(this.stickKeys.up);
      if (event.y > .3) this.stick.add(this.stickKeys.down);
    } else if (this.keys[event.control] !== undefined) {
      if (event.pressed) this.buttons.add(this.keys[event.control]); else this.buttons.delete(this.keys[event.control]);
    } else throw new Error('Control no compatible con esta plataforma');
    return this.held();
  }
  held() { return [...new Set([...this.buttons, ...this.stick])]; }
}

export class WindowsInput {
  constructor(python, script, hwnd, platform = 'gba') {
    this.platform = platform;
    this.players = new Map(); this.sequence = 0; this.pending = new Map();
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
  // Only PS1 has a second set of emulator bindings; elsewhere every controller
  // drives the same pad.
  slotFor(player) { return this.platform === 'ps1' && player === 2 ? 2 : 1; }
  stateFor(player) {
    const slot = this.slotFor(player);
    if (!this.players.has(slot)) this.players.set(slot, new InputState(this.platform, slot));
    return this.players.get(slot);
  }
  heldKeys() {
    const keys = new Set();
    for (const state of this.players.values()) for (const key of state.held()) keys.add(key);
    return [...keys];
  }
  send(event) {
    if (this.platform === 'ds' && /^ds-touch-(down|move|up)$/.test(event.control)) {
      if (![event.x, event.y].every(v => Number.isFinite(v) && v >= 0 && v <= 1)) throw new Error('Coordenadas táctiles inválidas');
      return this.write(undefined, { phase: event.control.slice(9), x: event.x, y: event.y });
    }
    this.stateFor(event.player).accept(event);
    return this.write(this.heldKeys());
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
