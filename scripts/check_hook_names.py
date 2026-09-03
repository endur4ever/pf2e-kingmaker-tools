#!/usr/bin/env python3
"""Every pf2e-kingmaker hook binding must name a class that exists in the SERVED bundle.

Foundry derives render/close hook names from the application's class name. pf2e-kingmaker 2.3
renamed its hex editor to `HexEditor`, so `closeKingmakerHexEdit` stopped firing -- and the
module's on-close overlay resync and sheet re-render were silently dead for a whole release
(fixed a1d52cc4). Nothing in the build could see it: the binding compiled, the listener
registered, Foundry simply never called it.

This guard reads every `on("renderX")` / `on("closeX")` in the Kingmaker bindings, fetches the
served bundle from the local Foundry (or a cached copy), and checks `class X ` exists. Bindings
marked @Deprecated are exempt. When neither the server nor a cache is reachable the check is
SKIPPED with a notice rather than failed, so CI away from the live server still passes.
"""
import os
import re
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOOKS = os.path.join(ROOT, "src/jsMain/kotlin/com/foundryvtt/kingmaker/Hooks.kt")
FOUNDRY = os.environ.get("KM_FOUNDRY_URL", "http://localhost:30000")
MODULE_JSON = f"{FOUNDRY}/modules/pf2e-kingmaker/module.json"
CACHE = os.environ.get("KM_BUNDLE_CACHE", "/tmp/km.mjs")


def fetch(url, timeout=6):
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return r.read().decode("utf-8", "replace")


def load_bundle():
    try:
        import json
        mj = json.loads(fetch(MODULE_JSON))
        es = (mj.get("esmodules") or [None])[0]
        if es:
            text = fetch(f"{FOUNDRY}/modules/pf2e-kingmaker/{es}", timeout=30)
            try:
                with open(CACHE, "w", encoding="utf-8") as f:
                    f.write(text)
            except OSError:
                pass
            return text, "live"
    except Exception:
        pass
    if os.path.exists(CACHE):
        with open(CACHE, encoding="utf-8") as f:
            return f.read(), "cache"
    return None, None


def main():
    if not os.path.exists(HOOKS):
        print(f"[hook-names] {HOOKS} not found -- run from the repo")
        return 1
    src = open(HOOKS, encoding="utf-8").read()
    bindings = []
    heads = list(re.finditer(r"fun\s+<O>\s+HooksEventListener\.(\w+)\s*\(", src))
    for i, h in enumerate(heads):
        seg_end = heads[i + 1].start() if i + 1 < len(heads) else len(src)
        seg = src[h.end():seg_end]
        hook = re.search(r'on\("(\w+)"', seg)
        if not hook:
            continue
        prev_end = heads[i - 1].end() if i > 0 else 0
        gap = src[prev_end:h.start()]
        # the annotation belongs to THIS binding only if it appears after the previous binding's on("...")
        prev_on = gap.rfind('on("')
        deprecated = "@Deprecated" in gap[prev_on:] if prev_on >= 0 else "@Deprecated" in gap
        bindings.append((h.group(1), hook.group(1), deprecated))
    if not bindings:
        print("[hook-names] could not parse any bindings -- check the guard, not the code")
        return 1
    bundle, source = load_bundle()
    if bundle is None:
        print("[hook-names] SKIPPED - no local Foundry at :30000 and no cached bundle; "
              f"run once with the server up to populate {CACHE}.")
        return 0
    problems = []
    checked = 0
    for fn, hook, deprecated in bindings:
        m = re.match(r"(render|close)(\w+)$", hook)
        if not m or deprecated:
            continue
        cls = m.group(2)
        checked += 1
        if not re.search(r"\bclass\s+" + re.escape(cls) + r"\b", bundle):
            problems.append((fn, hook, cls))
    if problems:
        print(f"[hook-names] FAIL - bindings naming classes the served bundle ({source}) does not have:")
        for fn, hook, cls in problems:
            print(f"  {fn} -> on(\"{hook}\"): no `class {cls}` in pf2e-kingmaker")
        print("Foundry derives hook names from the class name; a renamed class makes the listener silently dead.")
        return 1
    print(f"[hook-names] OK - {checked} render/close binding(s) name classes present in the served bundle ({source}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
