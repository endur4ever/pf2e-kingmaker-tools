#!/bin/bash
set -e

# scripts/release_check.sh
# Automated release checklist for pf2e-kingmaker-tools

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SKIP_BUILD=0
for arg in "$@"; do [[ "$arg" == "--skip-build" ]] && SKIP_BUILD=1; done

echo -e "${YELLOW}Starting automated release checks...${NC}"

# 1. Tree clean
if [[ -n $(git status --short) ]]; then
    echo -e "${RED}FAILURE: Git tree is not clean.${NC}"
    echo -e "REMEDIATION: Commit all changes, especially pack rotations, before releasing."
    exit 1
fi
echo -e "${GREEN}✓ Tree clean${NC}"

# 2. Every guard green.
# DISCOVERED from disk, never hand-listed: the hand-written list here silently fell one
# behind when check_migration_registrations.py was added, so the release check was not
# running the guard that proves every schema migration is registered.
GUARDS=()
for g in scripts/check_*.py; do
    # the i18n guard only checks every locale when asked
    if [[ "$g" == *check_i18n_keys.py ]]; then GUARDS+=("$g --all"); else GUARDS+=("$g"); fi
done
if [[ ${#GUARDS[@]} -eq 0 ]]; then
    echo -e "${RED}FAILURE: no guards found in scripts/check_*.py${NC}"
    echo -e "REMEDIATION: run this from the repository root."
    exit 1
fi

for guard in "${GUARDS[@]}"; do
    if ! python3 $guard; then
        echo -e "${RED}FAILURE: Guard failed: $guard${NC}"
        echo -e "REMEDIATION: Fix the errors reported by $guard before proceeding."
        exit 1
    fi
done
echo -e "${GREEN}✓ All ${#GUARDS[@]} guards green${NC}"

# 3. Build + tests. This used to assert only that JAVA_HOME was non-empty and ./gradlew
# existed, and print "Build environment looks okay" -- a release could pass this check with a
# red build. It now RUNS the suite. Pass --skip-build to skip it deliberately.
if [[ ! -f "./gradlew" ]]; then
    echo -e "${RED}FAILURE: ./gradlew not found.${NC}"
    echo -e "REMEDIATION: run this from the repository root."
    exit 1
fi
if [[ "$SKIP_BUILD" == "1" ]]; then
    echo -e "${YELLOW}SKIPPING build + tests (--skip-build).${NC}"
else
    if [[ -z "$JAVA_HOME" ]]; then
        echo -e "${RED}FAILURE: JAVA_HOME is not set.${NC}"
        echo -e "REMEDIATION: export JAVA_HOME=/home/grego/.local/jdks/jdk-25.0.3+9"
        exit 1
    fi
    if [[ -z "$CHROME_BIN" ]]; then
        echo -e "${RED}FAILURE: CHROME_BIN is not set (the JS tests need headless Chrome).${NC}"
        echo -e "REMEDIATION: export CHROME_BIN=/usr/bin/google-chrome"
        exit 1
    fi
    echo -e "${YELLOW}Running build + tests (this takes a minute)...${NC}"
    if ! ./gradlew assemble jsBrowserTest check -PuseChromeHeadless -x kotlinStoreYarnLock \
        -Dorg.gradle.java.installations.paths=/home/grego/.local/share/jvm/jdk-17 > /tmp/release_check_build.log 2>&1; then
        echo -e "${RED}FAILURE: build or tests failed.${NC}"
        grep -E "^e:|FAILED" /tmp/release_check_build.log | head -20
        echo -e "REMEDIATION: full log in /tmp/release_check_build.log"
        exit 1
    fi
    echo -e "${GREEN}✓ Build + tests green${NC}"
fi

# 4. Changelog & module.json version match
VERSION=$(python3 -c "import json; print(json.load(open('module.json'))['version'])")
echo -e "${YELLOW}Verifying version: $VERSION${NC}"

# Check that the FIRST [Unreleased] section carries no bullets. Scoped to the first one on
# purpose: CHANGELOG.md has carried a second [Unreleased] heading down in the 0.21.x history
# since the first commit, and a range match picked that up too.
UNRELEASED_BULLETS=$(awk '/^## \[Unreleased\]/{f=1;next} /^## \[/{if(f)exit} f' CHANGELOG.md | grep -c '^[*-] ' || true)
if [[ "$UNRELEASED_BULLETS" -gt 0 ]]; then
    echo -e "${RED}FAILURE: [Unreleased] in CHANGELOG.md still has $UNRELEASED_BULLETS entr(y/ies).${NC}"
    echo -e "REMEDIATION: Move them under '## [$VERSION] - <date>' and leave [Unreleased] empty."
    exit 1
fi

# Check if the current version has a header in CHANGELOG.md
if ! grep -q "## \[$VERSION\]" CHANGELOG.md; then
    echo -e "${RED}FAILURE: Version $VERSION not found in CHANGELOG.md.${NC}"
    echo -e "REMEDIATION: Add a '## [$VERSION] - <date>' section to CHANGELOG.md."
    exit 1
fi
echo -e "${GREEN}✓ Changelog version match and [Unreleased] clean${NC}"

# 5. module.json version bump & dist rebuild (check if dist/main.js exists)
if [[ ! -f "dist/main.js" ]]; then
    echo -e "${RED}FAILURE: dist/main.js not found.${NC}"
    echo -e "REMEDIATION: Run ./gradlew assemble to rebuild the distribution."
    exit 1
fi
echo -e "${GREEN}✓ Distribution exists${NC}"

# 6. MD5 check (Attempted, but requires a running server)
echo -e "${YELLOW}Checking Bundle Integrity...${NC}"
if curl -s --head http://localhost:30000/modules/pf2e-kingmaker-tools/dist/main.js | grep "200 OK" > /dev/null; then
    LOCAL_MD5=$(md5sum dist/main.js | cut -d' ' -f1)
    REMOTE_MD5=$(curl -s http://localhost:30000/modules/pf2e-kingmaker-tools/dist/main.js | md5sum | cut -d' ' -f1)
    if [[ "$LOCAL_MD5" != "$REMOTE_MD5" ]]; then
        echo -e "${RED}FAILURE: MD5 mismatch between local and remote bundle.${NC}"
        echo -e "REMEDIATION: Ensure the local build matches what is being served at localhost:30000."
        exit 1
    fi
    echo -e "${GREEN}✓ Bundle integrity verified (MD5 match)${NC}"
else
    echo -e "${YELLOW}SKIPPING MD5 Check: Local server not running or unreachable.${NC}"
fi

echo -e "\n${GREEN}ALL AUTOMATED CHECKS PASSED!${NC}"
echo -e "${YELLOW}Remaining Manual Steps:${NC}"
echo -e "  7. Live Render Check: GM_PW=<your_password> node scripts/live/live_check.mjs"
echo -e "  8. Commit & Tag: git tag -a v$VERSION -m \"Release v$VERSION\" && git push --tags"
echo -e "  9. Post the changelog section to the release card."
