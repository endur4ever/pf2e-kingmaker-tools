#!/usr/bin/env python3
"""Guard: an OPTIONAL form section must never fall back to a literal on submit.

A sheet template can render one section instead of another (camping-sheet.hbs renders the watch
grid *instead of* the recipe list). The submitted value then has no `recipes` object at all, so
`value.recipes?.<field>` is null for EVERY row -- and a literal elvis fallback quietly rewrites
every stored row with that literal.

That is how `skill = value.recipes?.selectedSkill?.get(it.id) ?: "survival"` reset every camper's
stored cooking skill to survival on any submit made while the recipe list was not on screen. The
tell is the `?.` on the SECTION itself: it says the whole section may be absent, and an absent
section must leave stored state ALONE, not overwrite it with a default.

The rule: when the section is optional, branch on the section (`if (section == null) stored else
...`) or fall back to the STORED value -- never to a literal.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/jsMain/kotlin"

# `value.<section>?.` ... `?: <literal>` on one expression.
PATTERN = re.compile(
    r'value\.([A-Za-z]\w*)\?\.[^\n=]*?\?:\s*("(?:[^"\\]|\\.)*"|-?\d+|true|false)'
)

# Sites where a literal default IS the right answer because the row is genuinely NEW, not stored.
# Add an entry only after confirming the expression cannot run against an existing stored row.
ALLOWED = set()


def main() -> int:
    problems = []
    for path in sorted(SRC.rglob("*.kt")):
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        for num, line in enumerate(text.splitlines(), 1):
            match = PATTERN.search(line)
            if not match:
                continue
            rel = path.relative_to(ROOT).as_posix()
            if f"{rel}:{num}" in ALLOWED:
                continue
            problems.append((rel, num, match.group(1), match.group(2), line.strip()))

    if problems:
        print("[optional-section] Optional form section falling back to a literal:")
        for rel, num, section, literal, line in problems:
            print(f"  {rel}:{num}")
            print(f"    {line}")
            print(f"    `value.{section}?.` says the section may be ABSENT; when it is, every row"
                  f" is rewritten with {literal}.")
        print("  Fix: branch on the section and keep the stored value when it is null,")
        print("  or fall back to the stored field instead of a literal.")
        return 1

    print("[optional-section] OK - no optional form section falls back to a literal on submit.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
