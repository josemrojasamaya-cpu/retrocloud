import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

// Mirrors the PS1 tables in windows-input.mjs. Both controllers are driven by
// synthetic key presses, so each pad needs its own non-overlapping bindings.
const pads = {
  Pad1: {
    Type: 'AnalogController',
    Up: 'Keyboard/UpArrow', Down: 'Keyboard/DownArrow', Left: 'Keyboard/LeftArrow', Right: 'Keyboard/RightArrow',
    LUp: 'Keyboard/W', LDown: 'Keyboard/S', LLeft: 'Keyboard/A', LRight: 'Keyboard/D',
    Triangle: 'Keyboard/I', Square: 'Keyboard/J', Circle: 'Keyboard/L', Cross: 'Keyboard/K',
    L1: 'Keyboard/Q', R1: 'Keyboard/E', L2: 'Keyboard/1', R2: 'Keyboard/3',
    Start: 'Keyboard/Return', Select: 'Keyboard/Backspace'
  },
  Pad2: {
    Type: 'AnalogController',
    Up: 'Keyboard/T', Down: 'Keyboard/G', Left: 'Keyboard/F', Right: 'Keyboard/H',
    LUp: 'Keyboard/Y', LDown: 'Keyboard/N', LLeft: 'Keyboard/V', LRight: 'Keyboard/B',
    Triangle: 'Keyboard/O', Square: 'Keyboard/U', Circle: 'Keyboard/P', Cross: 'Keyboard/M',
    L1: 'Keyboard/R', R1: 'Keyboard/Z', L2: 'Keyboard/4', R2: 'Keyboard/6',
    Start: 'Keyboard/5', Select: 'Keyboard/7'
  }
};

/**
 * Rewrites only the [Pad1] and [Pad2] sections of DuckStation's settings.ini so
 * the user's graphics and audio preferences survive untouched.
 */
export async function prepareDuckStation(executable) {
  const ini = path.join(process.env.LOCALAPPDATA, 'DuckStation', 'settings.ini');
  let text;
  try { text = await readFile(ini, 'utf8'); } catch { return; }
  for (const [section, bindings] of Object.entries(pads)) {
    const body = Object.entries(bindings).map(([k, v]) => `${k} = ${v}`).join('\n');
    const block = `[${section}]\n${body}\n`;
    const existing = new RegExp(`\\[${section}\\]\\r?\\n[\\s\\S]*?(?=\\r?\\n\\[|$)`);
    text = existing.test(text) ? text.replace(existing, block.trimEnd()) : `${text.trimEnd()}\n\n${block}`;
  }
  await writeFile(ini, text);
}
