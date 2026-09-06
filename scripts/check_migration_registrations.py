#!/usr/bin/env python3
"""Guard: every defined Migration class must be registered and imported in Migrations.kt.

Background:
In this module, schema migrations are defined as individual `class MigrationNN : Migration(NN)`
classes in `src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/migrations/`. They must be registered
in `internal val migrations = listOf(...)` inside `Migrations.kt` to actually execute when a world
upgrades across schema versions.

Migrations 41-48 were once authored as classes but never registered in Migrations.kt, remaining
unregistered for weeks (fixed in 89ad01c0). World upgrades silently skipped backfilling essential
fields like accessGrants, bankedBonuses, autoSucceedInClaimedHexes, etc. Unit tests inspecting only
the `migrations` list cannot catch defined-but-unregistered classes because the unreferenced classes
simply sit dormant in the package.

Rules enforced:
1. Every `MigrationNN` class defined in `migrations/migrations/MigrationNN.kt` must be registered
   in `internal val migrations = listOf(...)` in `Migrations.kt`.
2. Every migration registered in `Migrations.kt` must have a corresponding class definition file.
3. Every registered migration must be imported in `Migrations.kt`.
4. Migration versions must form a strictly contiguous sequence without gaps.
5. Registrations in `listOf(...)` must appear in strictly ascending version order without duplicates.
"""

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIGRATIONS_DIR = ROOT / "src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/migrations"
REGISTRY_FILE = ROOT / "src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/Migrations.kt"


def strip_comments(text: str) -> str:
    """Remove single-line comments so that parentheses inside comments don't disrupt parsing."""
    return re.sub(r"//.*$", "", text, flags=re.M)


def main() -> int:
    if not MIGRATIONS_DIR.is_dir():
        print(f"[migrations] Error: migrations directory not found at {MIGRATIONS_DIR}")
        return 1
    if not REGISTRY_FILE.is_file():
        print(f"[migrations] Error: registry file not found at {REGISTRY_FILE}")
        return 1

    # 1. Collect all defined MigrationNN classes
    defined: dict[int, tuple[str, Path]] = {}
    for path in sorted(MIGRATIONS_DIR.glob("Migration*.kt")):
        if path.name == "Migration.kt":
            continue
        content = path.read_text(encoding="utf-8")
        match = re.search(r"\bclass\s+(Migration(\d+))\b", content)
        if match:
            version = int(match.group(2))
            defined[version] = (match.group(1), path)

    if not defined:
        print("[migrations] Error: no Migration classes found in migrations directory.")
        return 1

    # 2. Parse Migrations.kt registry
    reg_raw = REGISTRY_FILE.read_text(encoding="utf-8")
    reg_clean = strip_comments(reg_raw)

    list_match = re.search(
        r"internal\s+val\s+migrations\s*=\s*listOf\((.*?)\n\)",
        reg_clean,
        re.S,
    )
    if not list_match:
        print("[migrations] Error: could not locate `internal val migrations = listOf(...)` in Migrations.kt")
        return 1

    registered_items = [
        int(m) for m in re.findall(r"\bMigration(\d+)\s*\(\s*\)", list_match.group(1))
    ]
    registered_set = set(registered_items)
    defined_set = set(defined.keys())

    # 3. Parse imports in Migrations.kt
    imports = set(
        int(m)
        for m in re.findall(
            r"import\s+at\.posselt\.pfrpg2e\.migrations\.migrations\.Migration(\d+)",
            reg_raw,
        )
    )

    problems: list[str] = []

    # Check 1: defined but unregistered
    unregistered = sorted(defined_set - registered_set)
    if unregistered:
        problems.append(
            f"{len(unregistered)} defined Migration class(es) missing from Migrations.kt `migrations = listOf(...)`:"
        )
        for v in unregistered:
            name, p = defined[v]
            problems.append(f"  - {name} (defined in {p.relative_to(ROOT)})")
        problems.append(
            "  REMEDIATION: Add `MigrationNN(),` to `internal val migrations = listOf(...)` in Migrations.kt."
        )

    # Check 2: registered but undefined
    missing_defs = sorted(registered_set - defined_set)
    if missing_defs:
        problems.append(
            f"{len(missing_defs)} registered Migration class(es) missing class definition in migrations/migrations/:"
        )
        for v in missing_defs:
            problems.append(f"  - Migration{v}")
        problems.append(
            "  REMEDIATION: Define `class MigrationNN : Migration(NN)` in `src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/migrations/MigrationNN.kt`, or remove from Migrations.kt."
        )

    # Check 3: registered but unimported
    unimported = sorted(registered_set - imports)
    if unimported:
        problems.append(
            f"{len(unimported)} registered Migration class(es) missing import in Migrations.kt:"
        )
        for v in unimported:
            problems.append(f"  - Migration{v}")
        problems.append(
            "  REMEDIATION: Add `import at.posselt.pfrpg2e.migrations.migrations.MigrationNN` to Migrations.kt."
        )

    # Check 4: contiguity
    min_v = min(defined_set)
    max_v = max(defined_set)
    expected_contiguous = set(range(min_v, max_v + 1))
    gaps = sorted(expected_contiguous - defined_set)
    if gaps:
        problems.append(
            f"Migration versions are not contiguous between v{min_v} and v{max_v}: missing {gaps}"
        )
        problems.append("  REMEDIATION: Migration versions must be strictly contiguous without numbering gaps.")

    # Check 5: registration ordering and duplicates
    if registered_items != sorted(registered_items):
        problems.append(
            "Migrations in Migrations.kt `listOf(...)` are not in strictly ascending order by version number."
        )
        problems.append("  REMEDIATION: Order MigrationNN() entries sequentially by version.")

    if len(registered_items) != len(registered_set):
        duplicates = sorted({v for v in registered_items if registered_items.count(v) > 1})
        problems.append(f"Duplicate migration registrations in `listOf(...)`: {duplicates}")
        problems.append("  REMEDIATION: Remove duplicate registrations in Migrations.kt.")

    if problems:
        print("[migrations] VIOLATIONS DETECTED:")
        for p in problems:
            print(f"  {p}")
        return 1

    print(
        f"[migrations] OK -- {len(defined)} migration(s) (v{min_v}..v{max_v}) defined, contiguous, imported, and registered."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
