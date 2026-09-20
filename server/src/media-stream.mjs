// Convert arbitrarily fragmented FFmpeg MJPEG output to complete HTTP parts.
export class JpegFrames {
  constructor(onFrame, limit = 2 * 1024 * 1024) {
    this.onFrame = onFrame; this.limit = limit; this.buffer = Buffer.alloc(0);
  }
  push(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (true) {
      const start = this.buffer.indexOf(Buffer.from([0xff, 0xd8]));
      if (start < 0) { this.buffer = this.buffer.subarray(-1); return; }
      if (start > 0) this.buffer = this.buffer.subarray(start);
      const end = this.buffer.indexOf(Buffer.from([0xff, 0xd9]), 2);
      if (end < 0) {
        if (this.buffer.length > this.limit) this.buffer = Buffer.alloc(0);
        return;
      }
      const frame = this.buffer.subarray(0, end + 2);
      this.buffer = this.buffer.subarray(end + 2);
      if (frame.length <= this.limit) this.onFrame(frame);
    }
  }
}
export function jpegPart(frame) {
  return Buffer.concat([Buffer.from(`--jpegframe\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.length}\r\n\r\n`), frame, Buffer.from('\r\n')]);
}
