"""Mute/unmute the default Windows audio output device (speakers) using pycaw.

Unlike muting a specific process, this does NOT affect process loopback capture.
The emulator's audio is still captured at full volume by ProcessAudioCapture.exe
even though the PC speakers are silent.
"""
import sys, json
from pycaw.pycaw import AudioUtilities

def set_speakers_mute(mute):
    speakers = AudioUtilities.GetSpeakers()
    volume = speakers.EndpointVolume
    volume.SetMute(1 if mute else 0, None)
    state = "muted" if mute else "unmuted"
    print(json.dumps({"speakers": state}), file=sys.stderr, flush=True)

if __name__ == "__main__":
    unmute = "--unmute" in sys.argv
    set_speakers_mute(not unmute)
