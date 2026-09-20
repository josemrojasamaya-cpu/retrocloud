import test from 'node:test';
import assert from 'node:assert/strict';
import { JpegFrames, jpegPart } from '../src/media-stream.mjs';
import { InputState } from '../src/windows-input.mjs';

test('fragmented JPEG markers produce complete multipart frames', () => {
  const frames = [];
  const parser = new JpegFrames(f => frames.push(f));
  const jpeg = Buffer.from([255,216,1,2,3,255,217]);
  for (const byte of Buffer.concat([Buffer.from('garbage'), jpeg, jpeg])) parser.push(Buffer.from([byte]));
  assert.equal(frames.length, 2);
  assert.deepEqual(frames[0], jpeg);
  assert.equal(jpegPart(jpeg).subarray(0, 13).toString(), '--jpegframe\r\n');
  assert.match(jpegPart(jpeg).toString('latin1'), /Content-Length: 7\r\n/);
});
test('broken oversized frames do not accumulate memory', () => {
  const frames = []; const parser = new JpegFrames(f => frames.push(f), 20);
  parser.push(Buffer.concat([Buffer.from([255,216]), Buffer.alloc(80)]));
  parser.push(Buffer.from([255,216,9,255,217]));
  assert.equal(frames.length, 1); assert.ok(parser.buffer.length <= 20);
});
test('joystick diagonal, neutral release, simultaneous button and key mapping', () => {
  const state = new InputState();
  assert.deepEqual(state.accept({control:'A',pressed:true}), [0x58]);
  assert.deepEqual(new Set(state.accept({control:'joystick',x:1,y:-1})), new Set([0x58,0x27,0x26]));
  assert.deepEqual(state.accept({control:'joystick',x:0,y:0}), [0x58]);
  assert.deepEqual(state.accept({control:'A',pressed:false}), []);
  assert.deepEqual(state.accept({control:'B',pressed:true}), [0x5a]);
  assert.throws(() => state.accept({control:'ds-touch',x:1,y:1}));
});
