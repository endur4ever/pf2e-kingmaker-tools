#!/usr/bin/env python3
"""Every pure commonMain engine must have at least one jsMain caller.

Two whole features shipped "done" with a complete engine, a green test suite, and no adapter:
the petition inbox and settlement life events (both wired 2026-09-01). Detector tests cannot
see this -- they exercise the engine, and the engine is correct. Only the wiring is missing.

Rule: a commonMain package with at least MIN_FUNS public top-level functions must have at least
one of them referenced somewhere in src/jsMain. A facade package whose helpers are consumed by a
single entry point still passes (the entry point is referenced). Packages listed in EXEMPT are
data-only and are expected to be consumed via types rather than calls.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMMON = os.path.join(ROOT, "src/commonMain/kotlin/at/posselt/pfrpg2e")
JS = os.path.join(ROOT, "src/jsMain/kotlin")
MIN_FUNS = 4
EXEMPT = set()

FUN_RE = re.compile(r"^\s*(?:public\s+)?(?:suspend\s+)?(?:inline\s+)?fun\s+(?:<[^>]+>\s+)?(?:[\w.]+\.)?(\w+)\s*\(", re.M)


def read_all(root):
    out = []
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith(".kt"):
                with open(os.path.join(dirpath, f), encoding="utf-8") as fh:
                    out.append(fh.read())
    return "\n".join(out)


def main():
    js_src = read_all(JS)
    problems = []
    checked = 0
    for dirpath, _, files in os.walk(COMMON):
        kts = [f for f in files if f.endswith(".kt")]
        if not kts:
            continue
        pkg = os.path.relpath(dirpath, COMMON)
        if pkg in EXEMPT:
            continue
        funs = set()
        for f in kts:
            with open(os.path.join(dirpath, f), encoding="utf-8") as fh:
                src = fh.read()
            # top-level only: strip class bodies crudely by ignoring indented declarations
            for m in FUN_RE.finditer(src):
                line_start = src.rfind("\n", 0, m.start()) + 1
                if src[line_start:m.start()].strip() == "" and not src[line_start:m.start()].startswith(" "):
                    funs.add(m.group(1))
        if len(funs) < MIN_FUNS:
            continue
        checked += 1
        called = [fn for fn in funs if re.search(r"\b" + re.escape(fn) + r"\s*\(", js_src)]
        if not called:
            problems.append((pkg, len(funs)))
    if problems:
        print("[dead-cores] FAIL - commonMain engines with no jsMain caller at all:")
        for pkg, n in sorted(problems):
            print(f"  {pkg}: {n} public function(s), none referenced from src/jsMain")
        print("A green engine with no adapter is not a feature. Wire it, or add the package to EXEMPT with a reason.")
        return 1
    print(f"[dead-cores] OK - {checked} engine package(s) all have a jsMain caller.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
