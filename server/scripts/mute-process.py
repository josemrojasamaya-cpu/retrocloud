"""Mute a specific process in the Windows Volume Mixer using pycaw."""
import sys, time, json
from pycaw.pycaw import AudioUtilities, ISimpleAudioVolume

def mute_pid(pid):
    for attempt in range(30):
        sessions = AudioUtilities.GetAllSessions()
        for s in sessions:
            if s.Process and s.Process.pid == pid:
                vol = s._ctl.QueryInterface(ISimpleAudioVolume)
                vol.SetMute(1, None)
                print(json.dumps({"muted": True, "pid": pid}), file=sys.stderr, flush=True)
                return True
        time.sleep(1)
    print(json.dumps({"error": "process audio session not found", "pid": pid}), file=sys.stderr, flush=True)
    return False

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: mute-process.py <pid>"}), file=sys.stderr)
        sys.exit(1)
    mute_pid(int(sys.argv[1]))
