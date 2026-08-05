# AGENTS.md — conventions for AI agents working in this repo

This is a **FoundryVTT module** compiled from **Kotlin/JS** via Gradle. Read this
before editing — these are the failure modes that have repeatedly broken the build.

## i18n / localization (MOST COMMON BUG)

Localization goes through **i18next** (`src/jsMain/.../utils/Localization.kt`),
used as `{{localizeKM "a.b.c"}}` in `.hbs` and `t("a.b.c")` in Kotlin.

i18next resolves `"a.b.c"` by walking the **nested** object `a → b → c` under the
`pf2e-kingmaker-tools` namespace in `lang/en.json`. Therefore:

- ✅ **DO** nest keys as objects:
  ```json
  "kingdom": { "clocks": { "manage": "Manage Clocks" } }
  ```
- ❌ **DO NOT** write a flat dotted key — this renders the raw key in the UI:
  ```json
  "kingdom.clocks.manage": "Manage Clocks"   // BROKEN: i18next can't reach it
  ```
- ❌ **DO NOT** put a key under the wrong parent block (e.g. a `kingdom.*`
  lookup whose value lives under `chatMessages.*`).
- A key cannot be both a string and a parent. `"population": "Population"` and
  `"population": { "title": ... }` conflict — pick one (use `population.title`).

**Before committing any i18n change, run the guard:**
```bash
python3 scripts/check_i18n_keys.py
```
It fails (exit 1) on any flat-dotted key or any `localizeKM`/`t("...")` reference
that doesn't resolve. Keep it green.

## Document lookups: pass the classes, not a generic argument

`fromUuidOfTypes` / `fromUuidsOfTypes` filter on their vararg `KClass` list, not on the
generic type parameter. `fromUuidOfTypes<PF2ECharacter>(uuid)` leaves that list empty, so
the internal `types.any { it.isInstance(document) }` is false for every document and the
call returns `null` **unconditionally** — it compiles, type-checks, and passes unit tests
while the feature never fires at runtime.

```kotlin
val pc = fromUuidOfTypes(uuid, PF2ECharacter::class)                    // ✅
val any = fromUuidOfTypes(uuid, PF2ECharacter::class, PF2ENpc::class)   // ✅
val bad = fromUuidOfTypes<PF2ECharacter>(uuid)                          // ❌ always null
```

```bash
python3 scripts/check_uuid_lookups.py
```

## Handlebars: `../` does not escape a partial

Inside `{{#each}}`, a bare identifier resolves against the ROW only — `{{#if isGM}}`
is silently falsy when `isGM` lives on the parent context. The usual fix is `../isGM`.
But a partial gets no frame above its own context, so at a partial's top level `../isGM`
is just as falsy. Verified against Handlebars 4.7.9 (the build Foundry serves):

```
{{#each rows}}{{> p this}}{{/each}}
p = "[bare:{{#if isGM}}Y{{else}}N{{/if}} parent:{{#if ../isGM}}Y{{else}}N{{/if}} root:{{#if @root.isGM}}Y{{else}}N{{/if}}]"
-> [bare:N parent:N root:Y]
```

Use `../x` only when the enclosing `{{#each}}`/`{{#with}}` is in the SAME file;
otherwise use `@root.x`. Note `{{#if}}` does not add a frame, only each/with do.

```bash
python3 scripts/check_hbs_scope.py
```

## Building (needs JDK 25 + JDK 17)

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew createDummyTranslations \
          assemble \
          -Dorg.gradle.java.installations.paths=/path/to/jdk-17
```
- `createDummyTranslations` is required once (regenerates `lang/*.json` dummies).
  **Do not commit the regenerated `lang/de.json` / other non-`en` lang files** —
  that overwrites real translations. Only `lang/en.json` is hand-edited.
- A combined `createDummyTranslations assemble` may error once on a fresh tree;
  just re-run `assemble`.
- Tests: `./gradlew jsTest` (browser/Karma). Firefox-headless times out under
  WSL; use Chrome (`CHROME_BIN=/usr/bin/google-chrome`, add a throwaway
  `karma.config.d/*.js` registering `karma-chrome-launcher`, run with
  `-x kotlinStoreYarnLock`, delete the file after).

## Workspace trap (Hermes kanban workers)

Roadmap cards have historically been `workspace_kind=scratch`, which gives an
autonomous worker an **empty directory** — it cannot see this repo and produces
non-compiling, half-wired code that gets left in the live tree. If you are an
autonomous worker and your workspace does not contain this repo's `build.gradle.kts`,
**stop and report** instead of guessing. Verify your edits with a real
`./gradlew assemble` before marking a task done.

## General

- Every `.hbs` ApplicationV2 part must render a **single root element**.
- Prefer small, verified changes; do not replace working files with stubs.
- After changes: `./gradlew assemble` must succeed and `jsTest` must stay green.

## Packs consistency (LevelDB)

Packs under `packs/` are LevelDB databases. Each pack directory contains:
- `CURRENT` — a text file naming the active manifest (e.g. `MANIFEST-002281`)
- `MANIFEST-*` — the active manifest (referenced by `CURRENT`) and stale manifests
- `*.ldb` — LevelDB data files
- `LOCK`, `LOG*`, `lost/` — runtime files (gitignored)

**Rule:** `packs/` is the shipped source of truth. The file named in `CURRENT` **must exist and be tracked**. Stale `MANIFEST-*` and `*.ldb` files not referenced by `CURRENT` must not linger untracked (they indicate a pack edit that wasn't committed).

**Guard script:** `scripts/check_packs_consistency.py` verifies this. It runs in CI and fails with exact `git add` / `git clean` remediation lines. Before committing any pack edit, run it locally:

```bash
python3 scripts/check_packs_consistency.py
```

If it fails, stage the file `CURRENT` points to (or clean the stale file if `CURRENT` points elsewhere). Never `gitignore` `MANIFEST-*` wholesale — the active manifest must ship.
