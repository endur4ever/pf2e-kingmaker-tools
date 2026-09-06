# Release Checklist

Follow these steps for every release.

1.  **Tree Clean:** Ensure `git status --short` is empty. Commit pack rotations on their are own first.
2.  **Guards Green:** All nine guards must pass:
    *   `scripts/check_i18n_keys.py --all`
    *   `scripts/check_hbs_scope.py`
    *   `scripts/check_packs_consistency.py`
    *   `scripts/check_uuid_lookups.py`
    *   `scripts/check_submit_merges.py`
    *   `scripts/check_dead_cores.py`
    *   `scripts/check_hook_names.py`
    *   `scripts/check_chat_dataset_keys.py`
    *   `scripts/check_optional_section_fallbacks.py`
3.  **Build & Test:** Run `./gradlew assemble jsBrowserTest check -PuseChromeHeadless -x kotlinStoreYarnLock` (using required `JAVA_HOME` and `CHROME_BIN`).
4.  **CHANGELOG Check:** Verify `CHANGELOG.md` has a `## [<version>] - <date>` section for the current version and that the `[Unreleased]` section is empty.
5.  **Version Bump & Rebuild:** Ensure `module.json` version is bumped and `dist/` is rebuilt.
6.  **Bundle Integrity:** Verify `md5sum dist/main.js` matches the checksum from the served bundle: `curl http://localhost:30000/modules/pf2e-kingmaker-tools/dist/main.js | md5sum`.
7.  **Live Render Check (Manual):** Run `GM_PW=... node scripts/live/live_check.mjs` to verify the live server is working correctly.
8.  **Git Tagging:** Commit changes via an isolated index, then create an annotated tag `v<version>`.
9.  **Release Announcement:** Post the changelog section to the release card.

## Automation
Run `scripts/release_check.sh` to automate steps 1–6.
