#!/usr/bin/env python3
"""
Packs consistency check for pf2e-kingmaker-tools.

Foundry VTT packs are LevelDB databases. When a pack is edited in Foundry,
LevelDB compacts and writes a NEW MANIFEST-* file, updating CURRENT to point
to it. The old MANIFEST-* file becomes orphaned but remains on disk.

.gitignore excludes LOCK/LOG/*.log but NOT MANIFEST-* or CURRENT or *.ldb.
This is intentional: the CURRENT file references ONE manifest that MUST be
tracked so a fresh clone has working compendia. But we must not accumulate
untracked MANIFEST-* files.

This script verifies for each pack in packs/:
  1. The file named in CURRENT exists and is tracked by git.
  2. No untracked MANIFEST-* files linger.
  3. No untracked *.ldb files linger (except the one tracked in CURRENT's manifest).

On failure, it prints exact `git add` / `git rm` remediation commands.

Usage:
  python3 scripts/check_packs_consistency.py          # check all packs, exit 1 on any issue
  python3 scripts/check_packs_consistency.py --test   # self-test mode (stages a temp file)
"""

import os
import sys
import subprocess
import tempfile
import shutil
from pathlib import Path

ROOT = Path(__file__).parent.parent.resolve()
PACKS_DIR = ROOT / "packs"


def run_cmd(cmd, cwd=None):
    """Run command and return (exit_code, stdout, stderr)."""
    result = subprocess.run(cmd, cwd=cwd or ROOT, capture_output=True, text=True, shell=True)
    return result.returncode, result.stdout.strip(), result.stderr.strip()


def get_tracked_files():
    """Return set of files tracked by git (relative to repo root)."""
    code, out, err = run_cmd("git ls-files")
    if code != 0:
        print(f"[ERROR] git ls-files failed: {err}", file=sys.stderr)
        sys.exit(1)
    return set(out.splitlines()) if out else set()


def get_untracked_files():
    """Return set of untracked files (relative to repo root)."""
    code, out, err = run_cmd("git ls-files --others --exclude-standard")
    if code != 0:
        print(f"[ERROR] git ls-files --others failed: {err}", file=sys.stderr)
        sys.exit(1)
    return set(out.splitlines()) if out else set()


def check_pack(pack_dir, tracked_files, untracked_files):
    """Check a single pack directory. Returns list of error messages (empty = OK)."""
    errors = []
    pack_name = pack_dir.name
    rel_pack = pack_dir.relative_to(ROOT)

    # 1. Read CURRENT
    current_file = pack_dir / "CURRENT"
    if not current_file.exists():
        errors.append(f"[{pack_name}] MISSING CURRENT file")
        return errors

    current_content = current_file.read_text().strip()
    if not current_content:
        errors.append(f"[{pack_name}] CURRENT file is empty")
        return errors

    # The CURRENT file contains the manifest filename (e.g., "MANIFEST-002281")
    manifest_name = current_content
    manifest_path = pack_dir / manifest_name
    rel_manifest = manifest_path.relative_to(ROOT)

    # 2. Check that the manifest named in CURRENT exists
    if not manifest_path.exists():
        errors.append(
            f"[{pack_name}] CURRENT references missing manifest: {manifest_name}\n"
            f"  REMEDIATION: git rm {rel_pack}/CURRENT  # then restore CURRENT from a known-good commit"
        )
        return errors

    # 3. Check that the manifest is tracked
    if str(rel_manifest) not in tracked_files:
        errors.append(
            f"[{pack_name}] Manifest {manifest_name} (referenced by CURRENT) is UNTRACKED\n"
            f"  REMEDIATION: git add {rel_manifest}"
        )

    # 4. Check CURRENT itself is tracked
    rel_current = current_file.relative_to(ROOT)
    if str(rel_current) not in tracked_files:
        errors.append(
            f"[{pack_name}] CURRENT is UNTRACKED\n"
            f"  REMEDIATION: git add {rel_current}"
        )

    # 5. Check for untracked MANIFEST-* files
    for f in pack_dir.glob("MANIFEST-*"):
        rel_f = f.relative_to(ROOT)
        if str(rel_f) in untracked_files:
            errors.append(
                f"[{pack_name}] Untracked MANIFEST file: {f.name}\n"
                f"  REMEDIATION: git add {rel_f}   # if CURRENT points here, also update CURRENT\n"
                f"  OR:          git clean -f {rel_f}  # if stale (CURRENT points elsewhere)"
            )

    # 6. Check for untracked *.ldb files
    for f in pack_dir.glob("*.ldb"):
        rel_f = f.relative_to(ROOT)
        if str(rel_f) in untracked_files:
            errors.append(
                f"[{pack_name}] Untracked .ldb file: {f.name}\n"
                f"  REMEDIATION: git add {rel_f}"
            )

    return errors


def main():
    parser = argparse.ArgumentParser(description="Check packs/ LevelDB consistency")
    parser.add_argument("--test", action="store_true", help="Run self-test (stages a temp file)")
    args = parser.parse_args()

    if not PACKS_DIR.exists():
        print(f"[ERROR] Packs directory not found: {PACKS_DIR}", file=sys.stderr)
        sys.exit(1)

    # Self-test mode: create a temp untracked MANIFEST in one pack, verify script catches it
    if args.test:
        pack_dirs = [d for d in PACKS_DIR.iterdir() if d.is_dir() and not d.name.startswith('.')]
        test_pack = sorted(pack_dirs)[0]
        test_manifest = test_pack / "MANIFEST-SELFTEST"
        test_manifest.write_text("selftest\n")
        print(f"[TEST] Created temp manifest: {test_manifest.relative_to(ROOT)}")
        try:
            # Run the check (it should fail)
            tracked = get_tracked_files()
            untracked = get_untracked_files()
            errors = check_pack(test_pack, tracked, untracked)
            if errors:
                print("[TEST] PASS: Script correctly detected untracked manifest")
                for e in errors:
                    print(f"  {e}")
                sys.exit(0)
            else:
                print("[TEST] FAIL: Script did not detect the untracked manifest", file=sys.stderr)
                sys.exit(1)
        finally:
            test_manifest.unlink(missing_ok=True)
            print(f"[TEST] Cleaned up temp manifest")
        return

    # Normal check mode
    tracked_files = get_tracked_files()
    untracked_files = get_untracked_files()

    all_errors = []
    pack_dirs = [d for d in PACKS_DIR.iterdir() if d.is_dir() and not d.name.startswith('.')]
    for pack_dir in sorted(pack_dirs):
        errors = check_pack(pack_dir, tracked_files, untracked_files)
        all_errors.extend(errors)

    if all_errors:
        print(f"\n[FAIL] Packs consistency check found {len(all_errors)} issue(s):\n")
        for err in all_errors:
            print(err)
            print()
        sys.exit(1)
    else:
        print("[OK] All packs consistent: CURRENT manifests tracked, no untracked MANIFEST/*.ldb files")
        sys.exit(0)


if __name__ == "__main__":
    import argparse
    main()