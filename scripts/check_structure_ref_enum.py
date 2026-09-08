#!/usr/bin/env python3
"""The structure-ref schema's `ref` enum must list every shipped structure id.

A ref-form structure actor names the structure it copies through this enum, so an id the schema
does not know cannot be referenced at all -- silently, because the actor simply fails validation
against a list nobody reads. The enum fell four ids behind the data directory once; this keeps it
honest.
"""
import glob
import json
import sys

SCHEMA = "src/commonMain/resources/schemas/structure-ref.json"


def enum_node(node):
    if isinstance(node, dict):
        if isinstance(node.get("enum"), list):
            return node
        for value in node.values():
            found = enum_node(value)
            if found is not None:
                return found
    elif isinstance(node, list):
        for value in node:
            found = enum_node(value)
            if found is not None:
                return found
    return None


def main() -> int:
    node = enum_node(json.load(open(SCHEMA)))
    if node is None:
        print(f"[structure-ref] FAIL — no enum found in {SCHEMA}")
        return 1
    listed = set(node["enum"])
    shipped = {json.load(open(f))["id"] for f in glob.glob("data/structures/*.json")}

    missing = sorted(shipped - listed)
    unknown = sorted(listed - shipped)
    if missing or unknown:
        if missing:
            print(f"[structure-ref] FAIL — shipped but not referenceable: {', '.join(missing)}")
        if unknown:
            print(f"[structure-ref] FAIL — listed but no such structure: {', '.join(unknown)}")
        print(f"[structure-ref] fix {SCHEMA} so its enum matches data/structures/*.json")
        return 1

    print(f"[structure-ref] OK — all {len(shipped)} structure ids are referenceable")
    return 0


if __name__ == "__main__":
    sys.exit(main())
