#!/usr/bin/env python3
"""
AniFlow update channel: build, sign, check and verify app updates. No GitHub involved.

What it produces is a plain folder of static files (dist/update/) that you upload to any HTTPS
host -- or to several, they are interchangeable mirrors:

    update.json                  signed envelope: which version is current and where its APK is
    AniFlow-<name>-<code>.apk    the APK, named per version so a cached old copy is never wrong
    _headers                     cache rules (Cloudflare Pages and Netlify read this file)

Installed apps fetch update.json from every mirror in UpdateConfig.MANIFEST_MIRRORS, verify its
ECDSA P-256 signature against the public keys compiled into the app, and only then trust a word of
it. A mirror can be hacked, go stale or vanish without being able to push anything to users.

Commands
    keygen                         make the signing keys (once, ever). Back them up offline.
    publish APK --notes-file F     check an APK to the hilt, then write a signed manifest for it
            [--rollout PCT] [--min-version CODE]
    set [--rollout PCT | --halt] [--maintenance MSG --hours H | --clear-maintenance]
                                   re-sign the current manifest with a change (no new APK)
    verify [--dir DIR | --live]    check a built folder, or what every mirror is serving right now

Run from the repo root. Needs Python 3.9+ and the `cryptography` package, plus the Android SDK
build-tools (apksigner, aapt2) for `publish`.
"""
import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

ROOT = Path(__file__).resolve().parent.parent
CONFIG = json.loads((ROOT / "tools" / "update_channel.json").read_text(encoding="utf-8"))
KEY_DIR = ROOT / "keystore" / "update-keys"          # gitignored with the rest of keystore/
DIST = ROOT / "dist" / "update"
MAX_HOSTED_FILE = 2 * 1024 * 1024 * 1024 - 1  # GitHub Releases: assets must be under 2 GiB
FORMAT = "aniflow-update/1"
SCHEMA = 1


def die(msg):
    print(f"\n  REFUSED: {msg}\n", file=sys.stderr)
    sys.exit(1)


def ok(msg):
    print(f"  ok  {msg}")


# ---------------------------------------------------------------------------------------- keys

def spki_b64(public_key):
    der = public_key.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    return base64.b64encode(der).decode()


def load_private(key_id):
    path = KEY_DIR / f"{key_id}.pem"
    if not path.exists():
        die(f"signing key {path} is missing. Restore it from your offline backup; without it no "
            f"installed app will accept a new manifest signed by '{key_id}'.")
    return serialization.load_pem_private_key(path.read_bytes(), password=None)


def cmd_keygen(_):
    KEY_DIR.mkdir(parents=True, exist_ok=True)
    for key_id in CONFIG["keys"]:
        path = KEY_DIR / f"{key_id}.pem"
        if path.exists():
            die(f"{path} already exists. Keys are made once; replacing one cuts off every app that trusts it.")
    print("Generated. Put these lines in UpdateConfig.MANIFEST_KEYS (they are public):\n")
    for key_id in CONFIG["keys"]:
        key = ec.generate_private_key(ec.SECP256R1())
        (KEY_DIR / f"{key_id}.pem").write_bytes(key.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        print(f'        "{key_id}" to "{spki_b64(key.public_key())}",')
    print(f"\nPrivate keys: {KEY_DIR}\nBACK THIS FOLDER UP OFFLINE NOW (USB stick + a second place). "
          f"'{CONFIG['keys'][0]}' signs every release; '{CONFIG['keys'][1]}' is the spare if the first is lost or leaked.")


def sign_envelope(manifest, key_id):
    payload = json.dumps(manifest, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()
    sig = load_private(key_id).sign(payload, ec.ECDSA(hashes.SHA256()))
    return {"format": FORMAT, "key": key_id, "payload": base64.b64encode(payload).decode(),
            "sig": base64.b64encode(sig).decode()}


def open_envelope(env, trusted=None):
    """Same checks the app does. Returns the manifest dict or raises ValueError."""
    if env.get("format") != FORMAT:
        raise ValueError(f"unknown format {env.get('format')!r}")
    payload = base64.b64decode(env["payload"])
    sig = base64.b64decode(env["sig"])
    keys = trusted or {k: load_private(k).public_key() for k in CONFIG["keys"] if (KEY_DIR / f"{k}.pem").exists()}
    for key_id, pub in keys.items():
        try:
            pub.verify(sig, payload, ec.ECDSA(hashes.SHA256()))
            return json.loads(payload)
        except InvalidSignature:
            continue
    raise ValueError("signature does not verify with any trusted key")


# ---------------------------------------------------------------------------------- APK checks

def build_tools():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or \
        str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk")
    bt = Path(sdk) / "build-tools"
    if not bt.is_dir():
        die(f"Android build-tools not found under {sdk}; set ANDROID_HOME.")
    latest = sorted(bt.iterdir(), key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", p.name)])[-1]
    ext = ".bat" if os.name == "nt" else ""
    return latest / f"apksigner{ext}", latest / ("aapt2.exe" if os.name == "nt" else "aapt2")


def inspect_apk(apk):
    apksigner, aapt2 = build_tools()
    out = subprocess.run([str(apksigner), "verify", "--print-certs", str(apk)], capture_output=True, text=True)
    if out.returncode != 0:
        die(f"apksigner says the APK is not validly signed:\n{out.stdout}{out.stderr}")
    signers = re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", out.stdout)
    badging = subprocess.run([str(aapt2), "dump", "badging", str(apk)], capture_output=True, text=True, errors="replace").stdout
    pkg = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging)
    if not pkg:
        die("aapt2 could not read the APK's package line.")
    min_sdk = re.search(r"(?:minSdkVersion|sdkVersion):'(\d+)'", badging)
    return {
        "package": pkg.group(1), "versionCode": int(pkg.group(2)), "versionName": pkg.group(3),
        "minSdk": int(min_sdk.group(1)) if min_sdk else None,
        "debuggable": "application-debuggable" in badging,
        "signers": signers,
    }


def dex_strings_blob(apk):
    with zipfile.ZipFile(apk) as z:
        return b"".join(z.read(n) for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n))


def check_apk(apk, previous):
    """Everything that, if wrong, would break updates for people who install this build."""
    info = inspect_apk(apk)
    if info["package"] != CONFIG["package"]:
        die(f"APK package is {info['package']}, the channel is for {CONFIG['package']}.")
    ok(f"package {info['package']}")
    if info["debuggable"]:
        die("this is a debuggable build. Build with assembleStandardRelease.")
    ok("release build (not debuggable)")
    if info["signers"] != [CONFIG["signer_sha256"]]:
        die(f"APK is signed by {info['signers']}, installed apps only accept {CONFIG['signer_sha256']}. "
            f"Android would refuse to install it over the current app.")
    ok("signed with the AniFlow release key")
    if previous and info["versionCode"] <= previous["versionCode"]:
        die(f"versionCode {info['versionCode']} is not above the published {previous['versionCode']}. "
            f"Bump versionCode in app/build.gradle.kts.")
    ok(f"version {info['versionName']} ({info['versionCode']})")
    if info["minSdk"] is None:
        die("could not read minSdk from the APK.")
    if info["minSdk"] > CONFIG["min_sdk"]:
        print(f"  WARN minSdk rose {CONFIG['min_sdk']} -> {info['minSdk']}: devices below {info['minSdk']} stop "
              f"getting updates (the app skips the offer instead of failing the install).")
    ok(f"minSdk {info['minSdk']}")

    # The new build must still be able to update itself: the same trusted keys and at least one of
    # today's mirrors compiled in. Searched as raw strings in the dex, so R8 can't hide a drift.
    dex = dex_strings_blob(apk)
    pubs = {k: spki_b64(load_private(k).public_key()) for k in CONFIG["keys"]}
    for key_id, b64 in pubs.items():
        if b64.encode() not in dex:
            die(f"key '{key_id}' is not compiled into this APK. Anyone who installs it could never take "
                f"another update. Check UpdateConfig.MANIFEST_KEYS.")
    ok("both trusted update keys are inside the APK")
    present = [m for m in CONFIG["mirrors"] if m.encode() in dex]
    if not present:
        die("none of the channel's mirrors is compiled into this APK; installs would never hear of an update. "
            "UpdateConfig.MANIFEST_MIRRORS and tools/update_channel.json must agree.")
    if len(present) < len(CONFIG["mirrors"]):
        print(f"  WARN only {len(present)}/{len(CONFIG['mirrors'])} mirrors are in the APK: {present}")
    ok(f"{len(present)} update mirror(s) inside the APK")
    return info


# ------------------------------------------------------------------------------------ manifest

def read_current():
    path = DIST / "update.json"
    if not path.exists():
        return None
    return open_envelope(json.loads(path.read_text(encoding="utf-8")))


def next_sequence(previous):
    seq = int(time.time() * 1000)
    return max(seq, (previous or {}).get("sequence", 0) + 1)


def write_dist(manifest, keep_apks):
    DIST.mkdir(parents=True, exist_ok=True)
    env = sign_envelope(manifest, CONFIG["keys"][0])
    open_envelope(env)  # never write something the app would reject
    tmp = DIST / "update.json.tmp"
    tmp.write_text(json.dumps(env, indent=1), encoding="utf-8")
    tmp.replace(DIST / "update.json")
    (DIST / "_headers").write_text(
        "/update.json\n  Cache-Control: no-cache, no-store, must-revalidate\n  Access-Control-Allow-Origin: *\n"
        "/*.apk\n  Cache-Control: public, max-age=31536000, immutable\n"
        "  Content-Type: application/vnd.android.package-archive\n"
        "/*.mp4\n  Cache-Control: public, max-age=86400\n", encoding="utf-8")
    for f in DIST.glob("*.apk"):
        if f.name not in keep_apks:
            f.unlink()
    # The host rejects files over its per-file limit.
    big = [f.name for f in DIST.iterdir() if f.is_file() and f.stat().st_size > MAX_HOSTED_FILE]
    if big:
        die(f"{', '.join(big)} too large for the host (GitHub Releases: under 2 GiB per file).")


def cmd_publish(args):
    apk = Path(args.apk).resolve()
    if not apk.is_file():
        die(f"{apk} not found.")
    notes = Path(args.notes_file).read_text(encoding="utf-8").strip() if args.notes_file else ""
    if not notes:
        die("--notes-file is required and must not be empty; users see these notes.")
    previous = read_current()
    print(f"\nChecking {apk.name}")
    info = check_apk(apk, previous)

    name = f"AniFlow-{info['versionName']}-{info['versionCode']}.apk"
    DIST.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(apk, DIST / name)
    data = (DIST / name).read_bytes()
    manifest = {
        "schema": SCHEMA,
        "sequence": next_sequence(previous),
        "channel": "stable",
        "versionCode": info["versionCode"],
        "versionName": info["versionName"],
        "notes": notes,
        "minSdk": info["minSdk"],
        "minVersionCode": args.min_version if args.min_version is not None else (previous or {}).get("minVersionCode", 0),
        "rollout": args.rollout,
        "apk": {"file": name, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data),
                "signer": CONFIG["signer_sha256"]},
    }
    if manifest["minVersionCode"] > info["versionCode"]:
        die("--min-version is above the version being published; nobody could satisfy it.")
    # Keep the previous APK one more round: an app that read the old manifest seconds before the
    # upload can still finish its download.
    keep = {name} | ({previous["apk"]["file"]} if previous else set())
    write_dist(manifest, keep)
    print(f"\nWrote {DIST}\n  update.json  (sequence {manifest['sequence']}, rollout {manifest['rollout']}%)\n  {name}  "
          f"({len(data) / 1e6:.1f} MB, sha256 {manifest['apk']['sha256'][:16]}...)")
    print_upload_hint()


def cmd_set(args):
    manifest = read_current()
    if not manifest:
        die("nothing published yet in dist/update/.")
    if args.halt:
        manifest["rollout"] = 0
    elif args.rollout is not None:
        manifest["rollout"] = args.rollout
    if args.maintenance:
        if not args.hours or args.hours <= 0 or args.hours > 72:
            die("--maintenance needs --hours between 0 and 72. It switches itself off after that, so a "
                "forgotten flag can never lock users out for good.")
        manifest["maintenance"] = {"message": args.maintenance, "until": int(time.time() + args.hours * 3600)}
    if args.clear_maintenance:
        manifest.pop("maintenance", None)
    manifest["sequence"] = next_sequence(manifest)
    write_dist(manifest, {manifest["apk"]["file"]} | {f.name for f in DIST.glob("*.apk")})
    print(f"Re-signed: rollout {manifest['rollout']}%, maintenance "
          f"{'until ' + time.strftime('%Y-%m-%d %H:%M', time.localtime(manifest['maintenance']['until'])) if 'maintenance' in manifest else 'off'}.")
    print_upload_hint()


def print_upload_hint():
    m = json.loads(base64.b64decode(json.loads((DIST / "update.json").read_text(encoding="utf-8"))["payload"]))
    tag, apk = f"v{m['versionName']}", DIST / m["apk"]["file"]
    print("\nNext, GitHub Releases (the newest release is what every install reads):\n"
          f"  new version:     gh release create {tag} \"{apk}\" \"{DIST / 'update.json'}\" --title \"AniFlow {m['versionName']}\" --notes-file <notes> --latest\n"
          f"  rollout/halt/maintenance on the current release:\n"
          f"                   gh release upload {tag} \"{DIST / 'update.json'}\" --clobber\n"
          "then run\n"
          "  python tools/update_channel.py verify --live\n"
          "and do not tell anyone about the release until it passes on all mirrors.")


# -------------------------------------------------------------------------------------- verify

def fetch(url, limit):
    req = urllib.request.Request(url, headers={"User-Agent": "AniFlow-update-verify", "Cache-Control": "no-cache"})
    with urllib.request.urlopen(req, timeout=30) as r:
        if not r.geturl().startswith("https://"):
            raise ValueError(f"redirected off HTTPS to {r.geturl()}")
        data = r.read(limit + 1)
        if len(data) > limit:
            raise ValueError("response too large")
        return data


def check_manifest(m, apk_bytes_for):
    if m.get("schema") != SCHEMA:
        raise ValueError(f"schema {m.get('schema')}")
    a = m["apk"]
    data = apk_bytes_for(a["file"])
    if len(data) != a["size"]:
        raise ValueError(f"APK size {len(data)} != manifest {a['size']}")
    if hashlib.sha256(data).hexdigest() != a["sha256"]:
        raise ValueError("APK sha256 does not match the manifest")
    if a["signer"] != CONFIG["signer_sha256"]:
        raise ValueError("manifest names a different signer")
    return f"{m['versionName']} ({m['versionCode']}), seq {m['sequence']}, rollout {m['rollout']}%" + \
        (", MAINTENANCE ON" if "maintenance" in m else "")


def cmd_verify(args):
    failures = 0
    if args.live:
        if not CONFIG["mirrors"]:
            die("no mirrors configured in tools/update_channel.json.")
        seqs = set()
        for mirror in CONFIG["mirrors"]:
            try:
                env = json.loads(fetch(mirror, 256 * 1024))
                m = open_envelope(env)
                base = mirror.rsplit("/", 1)[0] + "/"
                summary = check_manifest(m, lambda f: fetch(urllib.parse.urljoin(base, f), 300 * 1024 * 1024))
                seqs.add(m["sequence"])
                ok(f"{mirror}: {summary}")
            except Exception as e:  # noqa: BLE001 - report every mirror
                failures += 1
                print(f"  FAIL {mirror}: {e}")
        if len(seqs) > 1:
            failures += 1
            print(f"  FAIL mirrors disagree (sequences {sorted(seqs)}): one of them has not been uploaded yet.")
    else:
        d = Path(args.dir) if args.dir else DIST
        try:
            m = open_envelope(json.loads((d / "update.json").read_text(encoding="utf-8")))
            ok(f"{d}: {check_manifest(m, lambda f: (d / f).read_bytes())}")
        except Exception as e:  # noqa: BLE001
            failures += 1
            print(f"  FAIL {d}: {e}")
    if failures:
        die(f"{failures} problem(s). Users on a failing mirror fall back to the others, but fix it now.")
    print("\nAll good.")


def main():
    p = argparse.ArgumentParser(description="AniFlow update channel")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("keygen").set_defaults(fn=cmd_keygen)
    pp = sub.add_parser("publish")
    pp.add_argument("apk")
    pp.add_argument("--notes-file", required=True)
    pp.add_argument("--rollout", type=int, default=100, choices=range(0, 101), metavar="0-100")
    pp.add_argument("--min-version", type=int, help="installs below this versionCode must update")
    pp.set_defaults(fn=cmd_publish)
    ps = sub.add_parser("set")
    ps.add_argument("--rollout", type=int, choices=range(0, 101), metavar="0-100")
    ps.add_argument("--halt", action="store_true", help="stop offering the current version (rollout 0)")
    ps.add_argument("--maintenance", metavar="MESSAGE")
    ps.add_argument("--hours", type=float)
    ps.add_argument("--clear-maintenance", action="store_true")
    ps.set_defaults(fn=cmd_set)
    pv = sub.add_parser("verify")
    pv.add_argument("--dir")
    pv.add_argument("--live", action="store_true")
    pv.set_defaults(fn=cmd_verify)
    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
