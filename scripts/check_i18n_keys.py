#!/usr/bin/env python3
"""
i18n key guard for pf2e-kingmaker-tools.

This module localizes via **i18next** (see src/.../utils/Localization.kt), which
resolves a key like "a.b.c" by walking the NESTED object a -> b -> c under the
`pf2e-kingmaker-tools` namespace in lang/en.json. Two mistakes break this and
render the raw key in the UI:

  1. FLAT DOTTED KEYS: a property literally named "a.b.c" (with dots in the key)
     is NOT reachable by i18next's nested lookup. Keys must be nested objects.
  2. MISSING / MISPLACED KEYS: a key referenced in code/templates that has no
     nested entry (or sits under the wrong parent block).

This script fails (exit 1) if either is found, so any contributor — human or
agent — gets immediate, unambiguous feedback instead of shipping raw keys.

Usage:  python3 scripts/check_i18n_keys.py
"""
import json, re, glob, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "pf2e-kingmaker-tools"
LANG = os.path.join(ROOT, "lang", "en.json")


def load_root():
    with open(LANG, encoding="utf-8") as f:
        data = json.load(f)
    return data[NS]


def find_flat_dotted(node, path=""):
    """Keys whose NAME contains a '.' — unreachable by i18next nested lookup."""
    bad = []
    if isinstance(node, dict):
        for k, v in node.items():
            here = f"{path}/{k}" if path else k
            if "." in k:
                bad.append(here)
            bad.extend(find_flat_dotted(v, here))
    return bad


def resolve(root, key):
    cur = root
    for part in key.split("."):
        if isinstance(cur, dict) and part in cur:
            cur = cur[part]
        else:
            return False
    return isinstance(cur, str)


def is_dynamic(k):
    # keys built at runtime (e.g. "kingdomMainNav.$value") or partial prefixes
    return ("$" in k) or ("{" in k) or k.endswith(".") or k == "" or " " in k


def collect_refs():
    refs = set()
    pat_hbs = re.compile(r'localizeKM\s+"([^"]+)"')
    pat_kt = re.compile(r'\bt\(\s*"([^"]+)"')
    for f in glob.glob(os.path.join(ROOT, "src/**/*.hbs"), recursive=True):
        s = open(f, encoding="utf-8").read()
        for m in pat_hbs.finditer(s):
            refs.add((m.group(1), f))
    for f in glob.glob(os.path.join(ROOT, "src/**/*.kt"), recursive=True):
        s = open(f, encoding="utf-8").read()
        for m in pat_kt.finditer(s):
            refs.add((m.group(1), f))
    return refs


def main():
    root = load_root()
    problems = 0

    flat = find_flat_dotted(root)
    if flat:
        problems += len(flat)
        print(f"[i18n] {len(flat)} FLAT DOTTED key(s) in lang/en.json "
              f"(must be nested objects, not 'a.b.c' literal keys):")
        for k in flat:
            print(f"    ✗ {k}")

    unresolved = []
    for key, f in sorted(collect_refs()):
        if is_dynamic(key):
            continue
        if not resolve(root, key):
            unresolved.append((key, os.path.relpath(f, ROOT)))
    if unresolved:
        problems += len(unresolved)
        print(f"[i18n] {len(unresolved)} UNRESOLVED key(s) referenced in code/templates "
              f"but missing from lang/en.json:")
        for k, f in unresolved:
            print(f"    ✗ {k}   ({f})")

    if problems:
        print(f"\n[i18n] FAILED: {problems} problem(s). "
              f"Add the keys as nested objects under '{NS}' in lang/en.json.")
        return 1
    print("[i18n] OK — no flat-dotted keys, all referenced keys resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
