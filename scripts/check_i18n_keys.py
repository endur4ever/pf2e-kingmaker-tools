#!/usr/bin/env python3
"""
i18n key guard for pf2e-kingmaker-tools.

This module localizes via **i18next** (see src/.../utils/Localization.kt), which
resolves a key like "a.b.c" by walking the NESTED object a -> b -> c under the
`pf2e-kingmaker-tools` namespace in lang/en.json. Several mistakes break this and
render the raw key in the UI; this script fails (exit 1) on any of them so any
contributor — human or agent — gets immediate feedback instead of shipping raw
keys.

Checks:
  1. FLAT DOTTED KEYS in en.json: a property literally named "a.b.c" (dots in the
     key) is NOT reachable by i18next's nested lookup. Keys must be nested objects.
  2. UNRESOLVED CODE REFS: a key referenced via t("...") / {{localizeKM "..."}}
     in code/templates that has no nested entry (or sits under the wrong parent).
  3. UNRESOLVED CATALOG DATA KEYS: the @JsModule JSON catalogs under data/ store
     i18n keys in their `name` / `message` / modifier fields, localized at runtime
     by a translateXxx() that does t(value). A missing/misplaced key there renders
     raw too — but is invisible to check #2 because it lives in data, not code.
  4. UNWIRED TRANSLATORS: a top-level `fun translateXxx()` that is defined but not
     called from initLocalization() never populates its cache, so its catalog comes
     back EMPTY at runtime (this is exactly how the Launch Expedition activity
     dropdown shipped empty: translateExpeditionActivities was never wired in).

Usage:  python3 scripts/check_i18n_keys.py
"""
import json, re, glob, os, sys


def _reject_duplicate_keys(pairs):
    """json object_pairs_hook: duplicate keys silently shadow each other (last wins in JS
    and Python alike), which shipped a dead sendoff/homecoming pair once — fail loudly."""
    seen = {}
    for key, value in pairs:
        if key in seen:
            raise SystemExit(f"[i18n] FAILED: duplicate key '{key}' within one object — the first occurrence is dead text")
        seen[key] = value
    return seen

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "pf2e-kingmaker-tools"
LANG = os.path.join(ROOT, "lang", "en.json")
DATA_DIR = os.path.join(ROOT, "data")
LOCALIZATION_KT = os.path.join(
    ROOT, "src", "jsMain", "kotlin", "at", "posselt", "pfrpg2e", "utils", "Localization.kt"
)

# A catalog string is treated as an i18n key when it is a dotted path whose first
# segment starts LOWERCASE. Real keys here are camelCase ("expeditionActivities.x");
# Foundry document references ("Compendium.…", "Item.…", "JournalEntry.…") and most
# data values start uppercase or have no dot, so they are excluded automatically.
CANDIDATE_KEY = re.compile(r"^[a-z][A-Za-z0-9_-]*(\.[A-Za-z][A-Za-z0-9_-]*)+$")
ASSET_EXT = re.compile(r"\.(png|webp|svg|jpe?g|json|hbs|html|css|js|mjs|gif|woff2?)$", re.I)

# Pre-existing catalog-key debt, grandfathered so the guard stays green while the
# debt stays visible. Any NEW unresolved catalog key still fails the build.
#   - activities.new-leadership[-vk].modifiers.*.buttonLabel: passive circumstance
#     modifiers reference a buttonLabel key that was never added to en.json.
#   - events.natures-blessing.…modifiers.glow.{name,buttonLabel}: data uses the
#     plural "modifiers"; en.json nests these under the singular "modifier".
# Fix these by reconciling data/ <-> en.json, then delete the entry here.
CATALOG_KEY_ALLOWLIST = {
    "activities.new-leadership-vk.modifiers.appointingRulerPenalty.buttonLabel",
    "activities.new-leadership-vk.modifiers.matchingSkillBonus.buttonLabel",
    "activities.new-leadership.modifiers.appointingRulerPenalty.buttonLabel",
    "activities.new-leadership.modifiers.matchingSkillBonus.buttonLabel",
    "events.natures-blessing.stage-0.criticalSuccess.modifiers.glow.buttonLabel",
    "events.natures-blessing.stage-0.criticalSuccess.modifiers.glow.name",
    "events.natures-blessing.stage-0.success.modifiers.glow.buttonLabel",
    "events.natures-blessing.stage-0.success.modifiers.glow.name",
}


def load_root():
    with open(LANG, encoding="utf-8") as f:
        data = json.load(f, object_pairs_hook=_reject_duplicate_keys)
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


def collect_catalog_keys():
    """Every candidate i18n-key string found in the data/ JSON catalogs, with the
    file it came from. These are localized at runtime via translateXxx()/t()."""
    found = {}
    for f in glob.glob(os.path.join(DATA_DIR, "**/*.json"), recursive=True):
        try:
            with open(f, encoding="utf-8") as fh:
                doc = json.load(fh, object_pairs_hook=_reject_duplicate_keys)
        except (ValueError, OSError):
            continue
        rel = os.path.relpath(f, ROOT)

        def walk(n):
            if isinstance(n, dict):
                for v in n.values():
                    walk(v)
            elif isinstance(n, list):
                for v in n:
                    walk(v)
            elif isinstance(n, str) and CANDIDATE_KEY.match(n) and not ASSET_EXT.search(n):
                found.setdefault(n, rel)

        walk(doc)
    return found


def init_localization_body():
    """Text of the initLocalization() function body in Localization.kt."""
    if not os.path.exists(LOCALIZATION_KT):
        return None
    lines = open(LOCALIZATION_KT, encoding="utf-8").read().splitlines()
    start = next((i for i, ln in enumerate(lines) if ln.startswith("fun initLocalization(")), None)
    if start is None:
        return None
    body = []
    for ln in lines[start + 1:]:
        if ln == "}":  # top-level closing brace, column 0
            break
        body.append(ln)
    return "\n".join(body)


def collect_translate_defs():
    """Top-level `fun translateXxx(` catalog-translator definitions across .kt."""
    pat = re.compile(r"\bfun\s+(translate[A-Z]\w*)\s*\(")
    defs = {}
    for f in glob.glob(os.path.join(ROOT, "src/**/*.kt"), recursive=True):
        s = open(f, encoding="utf-8").read()
        for m in pat.finditer(s):
            defs.setdefault(m.group(1), os.path.relpath(f, ROOT))
    return defs


def main():
    root = load_root()
    problems = 0

    # 1. flat-dotted keys in en.json
    flat = find_flat_dotted(root)
    if flat:
        problems += len(flat)
        print(f"[i18n] {len(flat)} FLAT DOTTED key(s) in lang/en.json "
              f"(must be nested objects, not 'a.b.c' literal keys):")
        for k in flat:
            print(f"    ✗ {k}")

    # 2. unresolved code/template references
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

    # 3. unresolved catalog data keys (name/message/modifier fields under data/)
    cat_unresolved = []
    for key, f in sorted(collect_catalog_keys().items()):
        if key in CATALOG_KEY_ALLOWLIST:
            continue
        if not resolve(root, key):
            cat_unresolved.append((key, f))
    if cat_unresolved:
        problems += len(cat_unresolved)
        print(f"[i18n] {len(cat_unresolved)} UNRESOLVED CATALOG key(s) in data/ JSON "
              f"(referenced as name/message but missing from lang/en.json):")
        for k, f in cat_unresolved:
            print(f"    ✗ {k}   ({f})")

    # 4. translateXxx() catalog translators not wired into initLocalization()
    body = init_localization_body()
    if body is None:
        problems += 1
        print("[i18n] could not locate initLocalization() in Localization.kt to verify "
              "translator wiring.")
    else:
        unwired = []
        for fn, f in sorted(collect_translate_defs().items()):
            if not re.search(rf"\b{re.escape(fn)}\s*\(", body):
                unwired.append((fn, f))
        if unwired:
            problems += len(unwired)
            print(f"[i18n] {len(unwired)} catalog translator(s) defined but NOT called from "
                  f"initLocalization() (their catalog will be EMPTY at runtime):")
            for fn, f in unwired:
                print(f"    ✗ {fn}()   ({f})")

    if problems:
        print(f"\n[i18n] FAILED: {problems} problem(s). Keys must be nested objects under "
              f"'{NS}' in lang/en.json; new catalogs must be wired into initLocalization().")
        return 1
    print("[i18n] OK — no flat-dotted keys; all code, template, and catalog keys resolve; "
          "all translators wired.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
