# Release Checklist

Follow these steps for every release to ensure consistency and avoid common mistakes.

1. **Tree Clean:** Ensure `git status --short` is empty. Commit any pending pack rotations on their own first.
2. **All Guards Green:** Run all nine Python guard scripts and verify they pass:
   - `python3 scripts/check_i18n_keys.py --all`
   - `python3 scripts/check_hbs_scope.py`
   - `python3 scripts/check_packs_consistency.py`
   - `python3 scripts/check_uuid_lookups.py`
   - `python3 scripts/check_submit_merges.py`
   - `python3 scripts/check_dead_cores.py`
   - `python3 scripts/check_hook_names.py`
   - `python3 scripts/check_chat_dataset_keys.py`
   - `python3 scripts/check_optional_section_fallbacks.py`
3. **Build & Tests:** Run the build and tests with Chrome Headless:
   `JAVA_HOME=/home/grego/.local/jdks/jdk-25.0.3+9 ./gradlew assemble jsBrowserTest check -PuseChromeHeadless -x kotlinStoreYarnLock -Dorg.gradle.java.installations.paths=/home/grego/.local/share/jvm/jdk-17` (Ensure `CHROME_BIN=/usr/bin/google-chrome` is set).
4. **Changelog Verification:** Ensure `CHANGELOG.md` has a `## [<version>] - <date>` section corresponding to the version in `module.json`, and that the `[Unreleased]` section is empty.
5. **Version Bump & Dist Rebuild:** Increment the version in `module.json` and rebuild the `dist/` directory.
6. **Bundle Integrity Check:** Verify the served bundle matches the local build:
   `md5sum dist/main.js == curl http://localhost:30000/modules/pf2e-kingmaker-tools/dist/main.js | md5sum`

## Manual Steps (Post-Automation)

7. **Live Render Check:** Run the live check script:
   `GM_PW=<your_password> node scripts/live/live_check.mjs`
8. **Commit & Tag:** Commit changes via the isolated index, then create an annotated tag: `git tag -a v<version> -m "Release v<version>"` and push tags (`git push --tags`).
9. **Announce Release:** Post the new changelog section to the release card/issue.
