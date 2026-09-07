#!/usr/bin/env python3
"""Guard against hardcoded compendium UUIDs pointing at non-existent documents.

Foundry compendium references like:
    Compendium.pf2e-kingmaker-tools.<pack-name>.<DocumentType>.<id>
or legacy 4-segment forms:
    Compendium.pf2e-kingmaker-tools.<pack-name>.<id>
must resolve to real documents that exist in the shipped LevelDB packs under `packs/`.

A broken compendium UUID fails silently at runtime: Foundry's TextEditor.enrichHTML
or fromUuid/fromUuidSync returns null or an empty link without throwing, shipping
broken UI buttons or unclickable chat links.

This script parses the LevelDB SSTables (.ldb) offline using a pure-Python parser
supporting Snappy block decompression and entry key prefix decoding, extracts every
document key across all shipped packs, and asserts that every hardcoded compendium
UUID in src/ resolves to a known document ID.
"""
import os
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src"
PACKS = ROOT / "packs"

COMPENDIUM_RE = re.compile(
    r"Compendium\.pf2e-kingmaker-tools\.([a-zA-Z0-9_-]+)(?:\.[a-zA-Z0-9_-]+)?\.([a-zA-Z0-9]{16})"
)


def snappy_decompress(data: bytes) -> bytes:
    """Decompress a Snappy-compressed block in pure Python."""
    pos = 0
    length = 0
    shift = 0
    while pos < len(data):
        b = data[pos]
        pos += 1
        length |= (b & 0x7F) << shift
        if not (b & 0x80):
            break
        shift += 7

    out = bytearray()
    while pos < len(data) and len(out) < length:
        tag = data[pos]
        pos += 1
        elem_type = tag & 0x03
        if elem_type == 0:
            # Literal
            lit_len = tag >> 2
            if lit_len < 60:
                lit_len += 1
            elif lit_len == 60:
                lit_len = data[pos] + 1
                pos += 1
            elif lit_len == 61:
                lit_len = (data[pos] | (data[pos + 1] << 8)) + 1
                pos += 2
            elif lit_len == 62:
                lit_len = (data[pos] | (data[pos + 1] << 8) | (data[pos + 2] << 16)) + 1
                pos += 3
            elif lit_len == 63:
                lit_len = (
                    data[pos]
                    | (data[pos + 1] << 8)
                    | (data[pos + 2] << 16)
                    | (data[pos + 3] << 24)
                ) + 1
                pos += 4
            out.extend(data[pos : pos + lit_len])
            pos += lit_len
        elif elem_type == 1:
            # Copy 1-byte offset
            copy_len = ((tag >> 2) & 0x07) + 4
            offset = ((tag >> 5) << 8) | data[pos]
            pos += 1
            for _ in range(copy_len):
                out.append(out[-offset])
        elif elem_type == 2:
            # Copy 2-byte offset
            copy_len = (tag >> 2) + 1
            offset = data[pos] | (data[pos + 1] << 8)
            pos += 2
            for _ in range(copy_len):
                out.append(out[-offset])
        elif elem_type == 3:
            # Copy 4-byte offset
            copy_len = (tag >> 2) + 1
            offset = (
                data[pos]
                | (data[pos + 1] << 8)
                | (data[pos + 2] << 16)
                | (data[pos + 3] << 24)
            )
            pos += 4
            for _ in range(copy_len):
                out.append(out[-offset])

    return bytes(out)


def read_varint(buf: bytes, offset: int) -> tuple[int, int]:
    res = 0
    shift = 0
    while offset < len(buf):
        b = buf[offset]
        offset += 1
        res |= (b & 0x7F) << shift
        if not (b & 0x80):
            break
        shift += 7
    return res, offset


def parse_block(block: bytes) -> list[tuple[bytes, bytes]]:
    if len(block) < 4:
        return []
    num_restarts = struct.unpack("<I", block[-4:])[0]
    restart_offset = len(block) - 4 - 4 * num_restarts
    p = 0
    last_key = bytearray()
    entries = []
    while p < restart_offset:
        shared, p = read_varint(block, p)
        unshared, p = read_varint(block, p)
        val_len, p = read_varint(block, p)
        key = last_key[:shared] + block[p : p + unshared]
        p += unshared
        val = block[p : p + val_len]
        p += val_len
        last_key = bytearray(key)
        entries.append((bytes(key), bytes(val)))
    return entries


def read_ldb_keys(raw: bytes) -> set[str]:
    # Footer is 48 bytes, magic is last 8 bytes: 0xdb4775248b80fb57
    if len(raw) < 48 or raw[-8:] != b"\x57\xfb\x80\x8b\x24\x75\x47\xdb":
        return set()
    footer = raw[-48:]
    meta_offset, p = read_varint(footer, 0)
    meta_size, p = read_varint(footer, p)
    index_offset, p = read_varint(footer, p)
    index_size, p = read_varint(footer, p)
    index_block = raw[index_offset : index_offset + index_size]
    if raw[index_offset + index_size] == 1:
        index_block = snappy_decompress(index_block)

    keys = set()
    for _, handle in parse_block(index_block):
        b_offset, hp = read_varint(handle, 0)
        b_size, hp = read_varint(handle, hp)
        data = raw[b_offset : b_offset + b_size]
        if raw[b_offset + b_size] == 1:
            data = snappy_decompress(data)
        for k, _ in parse_block(data):
            # Internal key format: user_key + 8-byte sequence/type trailer
            user_key = k[:-8].decode("ascii", errors="ignore")
            m = re.search(r"!([a-zA-Z0-9]{16})$", user_key)
            if m:
                keys.add(m.group(1))
    return keys


def load_all_pack_keys() -> dict[str, set[str]]:
    pack_keys: dict[str, set[str]] = {}
    if not PACKS.exists():
        return pack_keys

    for pack_dir in sorted(PACKS.iterdir()):
        if not pack_dir.is_dir() or pack_dir.name.startswith("."):
            continue
        keys = set()
        for ldb in pack_dir.glob("*.ldb"):
            keys.update(read_ldb_keys(ldb.read_bytes()))
        pack_keys[pack_dir.name] = keys

    return pack_keys


def main() -> int:
    pack_keys = load_all_pack_keys()
    if not pack_keys:
        print("[compendium-uuids] ERROR - no packs found under packs/", file=sys.stderr)
        return 1

    failures = []
    checked = 0

    for path in sorted(SRC.rglob("*.kt")):
        rel = path.relative_to(ROOT).as_posix()
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            continue

        for lineno, line in enumerate(lines, 1):
            for m in COMPENDIUM_RE.finditer(line):
                checked += 1
                pack_name = m.group(1)
                doc_id = m.group(2)
                full_uuid = m.group(0)

                if pack_name not in pack_keys:
                    failures.append(
                        (rel, lineno, f"unknown pack '{pack_name}'", full_uuid)
                    )
                elif doc_id not in pack_keys[pack_name]:
                    failures.append(
                        (
                            rel,
                            lineno,
                            f"document '{doc_id}' not found in pack '{pack_name}'",
                            full_uuid,
                        )
                    )

    if failures:
        print(
            f"[compendium-uuids] FAIL - {len(failures)} compendium UUID(s) do not resolve to shipped documents:"
        )
        for rel, lineno, reason, full_uuid in failures:
            print(f"  {rel}:{lineno} -> {full_uuid} ({reason})")
        print("Hardcoded compendium UUIDs must name real documents in the shipped packs under packs/.")
        return 1

    print(
        f"[compendium-uuids] OK - {checked} hardcoded compendium UUID(s) across {len(pack_keys)} pack(s) all resolve."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
