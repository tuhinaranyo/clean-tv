#!/usr/bin/env python3
"""disable.py pkg... : pm disable-user one package per call, logs to backup/disabled.txt, rewrites backup/restore.sh"""
import sys, os, re
sys.path.insert(0, os.path.dirname(__file__))
import tv
DENY = re.compile(r"^(android|com\.android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|systemui|shell|phone|settings|tv\.settings|providers\..*|inputdevices|bluetooth)|com\.google\.android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|gms|gsf|overlay\.modules.*|inputmethod\.latin|tv\.remote\.service)|com\.android\.vending|mitv\.service|com\.mediatek\.(tvinput|wwtv\.tvcenter|tv\..*|tvinputservice.*|hotkey.*)|mediatek\.factorymenu\.ui)$")
root = os.path.join(os.path.dirname(__file__), "..", "backup")
log = os.path.join(root, "disabled.txt")
d = tv.connect()
done = set(open(log).read().split()) if os.path.exists(log) else set()
for p in sys.argv[1:]:
    if DENY.match(p):
        print("REFUSED (denylist):", p); continue
    out = tv.sh(d, f"pm disable-user --user 0 {p}").strip()
    ok = "new state: disabled-user" in out
    print(("OK   " if ok else "FAIL ") + p, "" if ok else out)
    if ok: done.add(p)
open(log, "w").write("\n".join(sorted(done)) + "\n")
open(os.path.join(root, "restore.sh"), "w").write(
    "#!/bin/sh\n# Re-enables every package disabled by this project. Usage: TV=192.168.0.109 ./restore.sh\n"
    "adb connect ${TV:-192.168.0.109}:5555\n" +
    "".join(f"adb -s ${{TV:-192.168.0.109}}:5555 shell pm enable {p}\n" for p in sorted(done)))
os.chmod(os.path.join(root, "restore.sh"), 0o755)
disabled = tv.sh(d, "pm list packages -d").split()
print("currently disabled:", len(disabled))
