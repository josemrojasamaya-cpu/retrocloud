import { mkdir, readFile, writeFile, copyFile } from 'node:fs/promises';
import { constants } from 'node:fs';
import path from 'node:path';

// melonDS 1.1 official Qt frontend settings. Preserve unrelated user settings.
export function setTomlValues(text, section, values) {
  const lines = text.split(/\r?\n/);
  let start = section ? lines.findIndex(line => line.trim().replaceAll('"', '') === `[${section.replaceAll('"', '')}]`) : -1;
  if (section && start < 0) { lines.push('', `[${section}]`); start = lines.length - 1; }
  let end = start + 1;
  while (end < lines.length && !/^\s*\[/.test(lines[end])) end++;
  for (const [key, value] of Object.entries(values)) {
    const entry = `${key} = ${JSON.stringify(value)}`;
    const index = lines.findIndex((line, i) => i > start && i < end && new RegExp(`^\\s*${key}\\s*=`).test(line));
    if (index >= 0) lines[index] = entry;
    else { lines.splice(end, 0, entry); end++; }
  }
  return lines.join('\n');
}

export async function prepareMelonDS(executable, saveDirectory) {
  const portable = path.join(path.dirname(executable), 'portable');
  await mkdir(portable, { recursive: true });
  const config = path.join(portable, 'melonDS.toml');
  let text = '';
  try {
    text = await readFile(config, 'utf8');
    await copyFile(config, `${config}.amayomi-backup`, constants.COPYFILE_EXCL).catch(e => { if (e.code !== 'EEXIST') throw e; });
  } catch (e) { if (e.code !== 'ENOENT') throw e; }
  const settings = {
    '': { LimitFPS: true, PauseLostFocus: false },
    Emu: { ConsoleType: 0, DirectBoot: true, ExternalBIOSEnable: false },
    Screen: { UseGL: false, Filter: false },
    '"3D"': { Renderer: 0 },
    JIT: { Enable: true },
    Instance0: { SaveFilePath: saveDirectory, SavestatePath: saveDirectory },
    'Instance0.Firmware': { Language: 5 },
    'Instance0.Window0': { ScreenLayout: 0, ScreenSizing: 0, ScreenGap: 0, ScreenRotation: 0, ScreenSwap: false, IntegerScaling: false, ScreenAspectTop: 0, ScreenAspectBot: 0, ShowOSD: false },
    'Instance0.Keyboard': { A: 88, B: 90, X: 87, Y: 81, L: 65, R: 83, Start: 16777220, Select: 16777219, Up: 16777235, Down: 16777237, Left: 16777234, Right: 16777236 }
  };
  for (const [section, values] of Object.entries(settings)) text = setTomlValues(text, section, values);
  await writeFile(config, text);
}
