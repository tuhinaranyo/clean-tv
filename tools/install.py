#!/usr/bin/env python3
"""install.py app.apk : push via base64 chunks (md5-verified) then pm install -r"""
import sys, os, base64, hashlib
sys.path.insert(0, os.path.dirname(__file__))
import tv
apk = sys.argv[1]; data = open(apk, "rb").read()
d = tv.connect(); dst = "/data/local/tmp/app.apk"
tv.sh(d, f"rm -f {dst}.b64 {dst}")
for i in range(0, len(data), 24000):
    ch = base64.b64encode(data[i:i+24000]).decode()
    tv.sh(d, f"echo -n {ch} >> {dst}.b64")
tv.sh(d, f"base64 -d {dst}.b64 > {dst}; rm {dst}.b64")
got = tv.sh(d, f"md5sum {dst}").split()[0]
want = hashlib.md5(data).hexdigest()
print("md5", got, "OK" if got == want else f"MISMATCH want {want}")
if got != want: sys.exit(1)
print(tv.sh(d, f"pm install -r {dst}").strip())
tv.sh(d, f"rm {dst}")
