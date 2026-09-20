"""WASAPI output loopback, never microphone input. PCM s16le to stdout."""
import json
import sys
import pyaudiowpatch as audio

def main():
    with audio.PyAudio() as host:
        device = host.get_default_wasapi_loopback()
        if not device.get("isLoopbackDevice"):
            raise RuntimeError("La salida seleccionada no es WASAPI loopback")
        rate = int(device["defaultSampleRate"])
        channels = min(2, int(device["maxInputChannels"]))
        if channels < 1:
            raise RuntimeError("Salida de audio sin canales disponibles")
        # A bounded blocking reader keeps stdout free of text and preserves PCM alignment.
        with host.open(format=audio.paInt16, channels=channels, rate=rate,
                       input=True, input_device_index=device["index"],
                       frames_per_buffer=max(128, rate // 100)) as stream:
            print(json.dumps({"ready": True, "sampleRate": rate, "channels": channels,
                              "encoding": "pcm_s16le"}), file=sys.stderr, flush=True)
            while True:
                data = stream.read(max(128, rate // 100), exception_on_overflow=False)
                sys.stdout.buffer.write(data)
                sys.stdout.buffer.flush()

if __name__ == "__main__":
    try:
        main()
    except (BrokenPipeError, KeyboardInterrupt):
        pass
    except Exception as exc:
        print(json.dumps({"error": str(exc)}), file=sys.stderr, flush=True)
        sys.exit(1)
