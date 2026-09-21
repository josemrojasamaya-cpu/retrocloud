"""Ensure a process is NOT muted in the Windows Volume Mixer."""
import sys, time, json
from pycaw.pycaw import AudioUtilities, ISimpleAudioVolume

def unmute_pid(pid):
    for attempt in range(30):
        sessions = AudioUtilities.GetAllSessions()
        for s in sessions:
            if s.Process and s.Process.pid == pid:
                vol = s._ctl.QueryInterface(ISimpleAudioVolume)
                was_muted = vol.GetMute()
                vol.SetMute(0, None)
                vol.SetMasterVolume(1.0, None)
                print(json.dumps({"unmuted": True, "pid": pid, "wasMuted": bool(was_muted)}), file=sys.stderr, flush=True)
                return True
        time.sleep(1)
    print(json.dumps({"error": "process audio session not found", "pid": pid}), file=sys.stderr, flush=True)
    return False

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: unmute-process.py <pid>"}), file=sys.stderr)
        sys.exit(1)
    unmute_pid(int(sys.argv[1]))
