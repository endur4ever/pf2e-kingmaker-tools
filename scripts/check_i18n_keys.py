#!/usr/bin/env python3
"""
i18n key guard for pf2e-kingmaker-tools.

This module localizes via **i18next** (see src/.../utils/Localization.kt),
which resolves a key like "a.b.c" by walking the NESTED object a -> b -> c under the
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
  5. CROSS-LANGUAGE PARITY (--parity): all lang/*.json files must have exactly the
     same set of nested keys as en.json. Placeholder names in values must match.

Usage:
  python3 scripts/check_i18n_keys.py           # runs checks 1-4 (default)
  python3 scripts/check_i18n_keys.py --parity  # runs check 5 only
  python3 scripts/check_i18n_keys.py --all     # runs checks 1-5
"""
import json, re, glob, os, sys, argparse


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


def lookup_value(root, key):
    """Like resolve(), but returns the STRING value rather than a boolean."""
    cur = root
    for part in key.split("."):
        if isinstance(cur, dict) and part in cur:
            cur = cur[part]
        else:
            return None
    return cur if isinstance(cur, str) else None


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


def load_lang_file(path):
    """Load a language file and return its nested namespace dict."""
    with open(path, encoding="utf-8") as f:
        data = json.load(f, object_pairs_hook=_reject_duplicate_keys)
    return data.get(NS, {})


def get_all_keys(d, prefix=""):
    """Returns a set of all nested keys as dotted paths."""
    keys = set()
    for k, v in d.items():
        path = f"{prefix}.{k}" if prefix else k
        if isinstance(v, dict):
            keys.update(get_all_keys(v, path))
        else:
            keys.add(path)
    return keys


def get_placeholders(value):
    """Extract placeholder names from a string.
    Handles both simple {name} and ICU message format {name, plural, ...}.
    Returns just the placeholder name (before any comma).
    Ignores nested braces inside ICU format (those are literal text)."""
    placeholders = set()
    i = 0
    while i < len(value):
        if value[i] == '{':
            # Find the matching closing brace at the same nesting level
            j = i + 1
            depth = 1
            while j < len(value) and depth > 0:
                if value[j] == '{':
                    depth += 1
                elif value[j] == '}':
                    depth -= 1
                j += 1
            if depth == 0:
                content = value[i+1:j-1].strip()
                # In ICU format, the placeholder name is before the first comma
                name = content.split(",")[0].strip()
                placeholders.add(name)
                i = j
            else:
                i += 1
        else:
            i += 1
    return placeholders


def check_parity():
    """Check 5: cross-language parity against en.json."""
    problems = 0
    en = load_lang_file(LANG)
    en_keys = get_all_keys(en)
    
    lang_dir = os.path.join(ROOT, "lang")
    lang_files = sorted([
        f for f in os.listdir(lang_dir)
        if f.endswith(".json") and f != "en.json" and not f.endswith(".bak")
    ])
    
    for lang_file in lang_files:
        lang_path = os.path.join(lang_dir, lang_file)
        lang_data = load_lang_file(lang_path)
        lang_keys = get_all_keys(lang_data)
        
        missing = en_keys - lang_keys
        extra = lang_keys - en_keys
        
        if missing:
            problems += len(missing)
            print(f"[i18n] {lang_file}: {len(missing)} MISSING key(s) vs en.json:")
            for k in sorted(missing):
                print(f"    ✗ {k}")
        
        if extra:
            problems += len(extra)
            print(f"[i18n] {lang_file}: {len(extra)} EXTRA key(s) not in en.json:")
            for k in sorted(extra):
                print(f"    ✗ {k}")
        
        # Check placeholder parity for shared keys
        shared = en_keys & lang_keys
        for key in shared:
            en_val = get_value_by_key(en, key)
            lang_val = get_value_by_key(lang_data, key)
            if en_val and lang_val:
                en_ph = get_placeholders(en_val)
                lang_ph = get_placeholders(lang_val)
                if en_ph != lang_ph:
                    problems += 1
                    print(f"[i18n] {lang_file}: PLACEHOLDER MISMATCH for '{key}':")
                    print(f"    en:    {en_ph}")
                    print(f"    {lang_file[:5]}: {lang_ph}")
    
    if problems == 0:
        print("[i18n] PARITY OK — all language files have identical key sets and placeholder names.")
    return problems


def get_value_by_key(d, key):
    """Get a nested value by dotted key path."""
    cur = d
    for part in key.split("."):
        if isinstance(cur, dict) and part in cur:
            cur = cur[part]
        else:
            return None
    return cur if isinstance(cur, str) else None


GAZETTE_SRC = os.path.join(ROOT, "src", "jsMain", "kotlin", "at", "posselt", "pfrpg2e",
                           "kingdom", "TurnHistory.kt")


def check_gazette_resolver():
    """Check 6: formatTurnGazette's defaultLocalize must match lang/en.json verbatim.

    formatTurnGazette takes an injected localizer so its pure unit tests stay green in the
    headless harness, where t() returns the raw key. defaultLocalize is therefore a SECOND,
    hand-maintained copy of the same English strings that production reads from en.json.

    Nothing else can catch a divergence: the i18n guard cannot see keys that only appear as
    string literals inside a Kotlin `when`, and the gazette tests assert against defaultLocalize,
    so they stay green while production silently renders different text. This check is what makes
    the two copies stay honest.
    """
    if not os.path.exists(GAZETTE_SRC):
        return 0
    src = open(GAZETTE_SRC, encoding="utf-8").read()
    start = src.find("fun defaultLocalize")
    if start == -1:
        return 0
    block = src[start:src.index("\n}", start)]
    pairs = re.findall(r'"(kingdom\.turnGazette\.[\w]+)"\s*->\s*"(.*?)"\s*$', block, re.M)
    if not pairs:
        print("[i18n] GAZETTE: could not parse defaultLocalize — check the guard, not the code")
        return 1
    root = load_root()
    problems = []
    for key, kotlin in pairs:
        # Kotlin writes ${dyn.foo}; en.json writes {foo}
        normalised = re.sub(r"\$\{dyn\.(\w+)\}", r"{\1}", kotlin)
        expected = lookup_value(root, key)
        if expected is None:
            problems.append((key, normalised, "<missing from lang/en.json>"))
        elif expected != normalised:
            problems.append((key, normalised, expected))
    if problems:
        print(f"[i18n] {len(problems)} GAZETTE DIVERGENCE(S) between defaultLocalize "
              f"(TurnHistory.kt) and lang/en.json — the two copies must stay identical:")
        for key, kotlin, expected in problems:
            print(f"    \u2717 {key}")
            print(f"        defaultLocalize: {kotlin}")
            print(f"        lang/en.json   : {expected}")
        return 1
    print(f"[i18n] GAZETTE OK — all {len(pairs)} defaultLocalize strings match lang/en.json.")
    return 0


SETUP_CHECKS_SRC = "src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/SetupWizardChecks.kt"


def check_setup_wizard_keys():
    """Check 7: every setup check id and action id must have a label in every locale.

    The health-check card localizes its rows with t("setupWizard.check.$id") and its buttons with
    t("setupWizard.action.$actionId") -- keys assembled at runtime from the ids in
    SetupWizardChecks.kt. Check 2 scans for literal key strings, so a dynamically built key is
    invisible to it: adding a check to the pure function and forgetting its label ships a row
    titled with the raw key, and every guard still passes. This reads the ids out of the source
    and demands a label for each, in all locales.
    """
    if not os.path.exists(SETUP_CHECKS_SRC):
        return 0
    src = open(SETUP_CHECKS_SRC, encoding="utf-8").read()
    check_ids = re.findall(r'^\s*id = "([\w-]+)",', src, re.M)
    action_ids = re.findall(r'actionId = if \([^)]*\) null else "([\w-]+)"', src)
    if not check_ids:
        print("[i18n] SETUP: could not parse check ids -- check the guard, not the code")
        return 1
    problems = []
    for path in sorted(glob.glob("lang/*.json")):
        root = json.load(open(path, encoding="utf-8")).get(NS, {})
        for cid in check_ids:
            if not isinstance(lookup_value(root, f"setupWizard.check.{cid}"), str):
                problems.append((path, f"setupWizard.check.{cid}"))
        for aid in set(action_ids):
            if not isinstance(lookup_value(root, f"setupWizard.action.{aid}"), str):
                problems.append((path, f"setupWizard.action.{aid}"))
    if problems:
        print(f"[i18n] SETUP: {len(problems)} missing setup wizard key(s):")
        for path, key in problems:
            print(f"  {path}: {key}")
        return 1
    print(f"[i18n] SETUP OK -- {len(check_ids)} check(s) and {len(set(action_ids))} action(s) localized everywhere.")
    return 0


RIVAL_PROFILE_DIR = "data/rival-growth-profiles"

def check_rival_profile_keys():
    """Check 8: every rival growth profile id must have a label in every locale.

    Profile ids come from data/rival-growth-profiles/*.json and the dropdown labels them with
    t("kingdom.rivalRealms.profile.$id") -- a composed key, invisible to check 2's literal-string
    scan. A new profile file with no label would ship a dropdown entry titled with the raw key
    while every other check stays green. Same shape and rationale as check_setup_wizard_keys.
    """
    paths = sorted(glob.glob(f"{RIVAL_PROFILE_DIR}/*.json"))
    if not paths:
        return 0
    ids = []
    for p in paths:
        pid = json.load(open(p, encoding="utf-8")).get("id")
        if not isinstance(pid, str) or not pid:
            print(f"[i18n] RIVAL: {p} has no string id -- fix the profile file")
            return 1
        ids.append(pid)
    problems = [(path, f"kingdom.rivalRealms.profile.{i}")
                for path in sorted(glob.glob("lang/*.json"))
                for i in ids
                if not isinstance(
                    lookup_value(json.load(open(path, encoding="utf-8")).get(NS, {}),
                                 f"kingdom.rivalRealms.profile.{i}"), str)]
    if problems:
        print(f"[i18n] RIVAL: {len(problems)} missing rival profile label(s):")
        for path, key in problems:
            print(f"  {path}: {key}")
        return 1
    print(f"[i18n] RIVAL OK -- {len(ids)} growth profile(s) labelled in every locale.")
    return 0


NPC_MEMORY_RULE_DIR = "data/npc-memory-rules"


def _kebab_to_camel(s):
    parts = s.split("-")
    return parts[0] + "".join(w.capitalize() for w in parts[1:])


def check_npc_memory_rule_keys():
    """Check 10: every NPC memory rule id must have its entry text in every locale.

    Rule ids come from data/npc-memory-rules/*.json and the memory log renders them through a
    literal-key `when` keyed by rule id (plan section 8) -- so a NEW rule file whose entry key was
    never added would ship a raw key with every other check green. Same shape as check 8.
    """
    paths = sorted(glob.glob(f"{NPC_MEMORY_RULE_DIR}/*.json"))
    if not paths:
        return 0
    ids = []
    for p in paths:
        rid = json.load(open(p, encoding="utf-8")).get("id")
        if not isinstance(rid, str) or not rid:
            print(f"[i18n] NPCMEMORY: {p} has no string id -- fix the rule file")
            return 1
        ids.append(rid)
    problems = [(path, f"kingdom.npcMemory.{_kebab_to_camel(i)}.entry")
                for path in sorted(glob.glob("lang/*.json"))
                for i in ids
                if not isinstance(
                    lookup_value(json.load(open(path, encoding="utf-8")).get(NS, {}),
                                 f"kingdom.npcMemory.{_kebab_to_camel(i)}.entry"), str)]
    if problems:
        print(f"[i18n] NPCMEMORY: {len(problems)} missing rule entry key(s):")
        for path, key in problems:
            print(f"  {path}: {key}")
        return 1
    print(f"[i18n] NPCMEMORY OK -- {len(ids)} memory rule(s) have entry text in every locale.")
    return 0


RENOWN_ENGINE_SRC = "src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/RenownEngine.kt"

def check_epithet_keys():
    """Check 9: every epithet in EPITHET_CATALOG must have a label in every locale.

    The offer card and the granted-announcement both localize with
    t("kingdom.renown.epithet.$id") -- composed at runtime from the catalog, so check 2's
    literal-string scan cannot see it. Adding a thirteenth epithet without its label would
    announce a PC as "kingdom.renown.epithet.theWhatever" in public chat while CI stayed green.
    Same shape and rationale as check_rival_profile_keys and check_setup_wizard_keys.
    """
    if not os.path.exists(RENOWN_ENGINE_SRC):
        return 0
    src = open(RENOWN_ENGINE_SRC, encoding="utf-8").read()
    catalog = src.split("val EPITHET_CATALOG", 1)[-1]
    ids = re.findall(r'^\s*id = "([\w-]+)",', catalog, re.M)
    if not ids:
        print("[i18n] EPITHET: could not parse catalog ids -- check the guard, not the code")
        return 1
    problems = [(path, f"kingdom.renown.epithet.{i}")
                for path in sorted(glob.glob("lang/*.json"))
                for i in ids
                if not isinstance(
                    lookup_value(json.load(open(path, encoding="utf-8")).get(NS, {}),
                                 f"kingdom.renown.epithet.{i}"), str)]
    if problems:
        print(f"[i18n] EPITHET: {len(problems)} missing epithet label(s):")
        for path, key in problems:
            print(f"  {path}: {key}")
        return 1
    print(f"[i18n] EPITHET OK -- {len(ids)} epithet(s) labelled in every locale.")
    return 0


def check_petition_catalog_keys():
    """Check 10: every petition template needs a premise and an option label in every locale.

    The inbox and both chat cards localize with t("petitions.$templateId.premise") and
    t("petitions.$templateId.$optionId.label") -- composed at runtime from data/petitions/, so
    check 2's literal-string scan cannot see any of them. A template shipped without its strings
    would render a raw key where the audience's request should be, with every guard green; the
    plan calls for exactly this catalog check for exactly that reason.
    """
    files = sorted(glob.glob("data/petitions/*.json"))
    if not files:
        return 0
    expected = []
    for f in files:
        template = json.load(open(f, encoding="utf-8"))
        tid = template.get("id")
        if not tid:
            print(f"[i18n] PETITION: {f} has no id -- check the data, not the guard")
            return 1
        expected.append(f"petitions.{tid}.premise")
        for option in template.get("options") or []:
            oid = option.get("id")
            if not oid:
                print(f"[i18n] PETITION: {f} has an option with no id")
                return 1
            expected.append(f"petitions.{tid}.{oid}.label")
    problems = [(path, key)
                for path in sorted(glob.glob("lang/*.json"))
                for key in expected
                if not isinstance(
                    lookup_value(json.load(open(path, encoding="utf-8")).get(NS, {}), key), str)]
    if problems:
        print(f"[i18n] PETITION: {len(problems)} missing petition string(s):")
        for path, key in problems[:20]:
            print(f"  {path}: {key}")
        if len(problems) > 20:
            print(f"  ... and {len(problems) - 20} more")
        return 1
    print(f"[i18n] PETITION OK -- {len(files)} template(s), "
          f"{len(expected)} string(s) present in every locale.")
    return 0


def main():
    parser = argparse.ArgumentParser(description="i18n key guard for pf2e-kingmaker-tools")
    parser.add_argument("--parity", action="store_true", help="Run cross-language parity check only")
    parser.add_argument("--all", action="store_true", help="Run all checks (1-6)")
    args = parser.parse_args()
    
    if args.parity:
        return check_parity()
    
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
    
    # Run parity check if --all specified
    if args.all:
        failed = 0
        failed += check_parity() or 0
        failed += check_gazette_resolver() or 0
        failed += check_setup_wizard_keys() or 0
        failed += check_rival_profile_keys() or 0
        failed += check_epithet_keys() or 0
        failed += check_npc_memory_rule_keys() or 0
        failed += check_petition_catalog_keys() or 0
        if failed:
            return 1
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
