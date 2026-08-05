#!/usr/bin/env python3
"""Guard against silently-null document lookups.

`fromUuidOfTypes` / `fromUuidsOfTypes` filter on their vararg `KClass` list, NOT on the
generic type parameter:

    ?.takeIf { document -> types.any { type -> type.isInstance(document) } } as T?

Calling them with only a generic argument -- `fromUuidOfTypes<PF2ECharacter>(uuid)` --
leaves `types` empty, so `types.any { }` is false for every document and the call returns
null unconditionally. It compiles, type-checks, and passes unit tests; at runtime the
feature just never fires. That shipped the expedition sheet-XP award, the companion and
army level-up buttons, and the expedition skill roll as silent no-ops.

Always pass the classes: `fromUuidOfTypes(uuid, PF2ECharacter::class)`.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src"
IMPL = "utils/Document.kt"  # the declarations themselves

# fromUuidOfTypes<...>(  /  fromUuidsOfTypes<...>(  -- explicit generic argument
GENERIC_CALL = re.compile(r"\bfromUuids?OfTypes\s*<")
# fromUuidOfTypes(x)  -- single argument, no ::class list
EMPTY_VARARG = re.compile(r"\bfromUuids?OfTypes\s*\(\s*[^,()]+\s*\)")


def main() -> int:
    failures = []
    for path in SRC.rglob("*.kt"):
        if path.as_posix().endswith(IMPL):
            continue
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            rel = path.relative_to(ROOT)
            if GENERIC_CALL.search(line):
                failures.append((rel, lineno, "generic type argument instead of ::class list", line.strip()))
            elif EMPTY_VARARG.search(line):
                failures.append((rel, lineno, "no ::class arguments -- always returns null", line.strip()))

    if failures:
        print("[uuid-lookups] FAIL — these calls resolve to null at runtime:\n")
        for rel, lineno, why, text in failures:
            print(f"  {rel}:{lineno}: {why}")
            print(f"      {text}")
        print("\nPass the document classes explicitly, e.g.")
        print("  fromUuidOfTypes(uuid, PF2ECharacter::class, PF2ENpc::class)")
        return 1

    print("[uuid-lookups] OK — every fromUuidOfTypes/fromUuidsOfTypes call passes a ::class list.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
