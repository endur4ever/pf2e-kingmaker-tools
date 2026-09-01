#!/usr/bin/env python3
"""Guard against Handlebars parent-scope references that escape their own file.

Handlebars v4 (Foundry ships 4.7.9) resolves `../x` against the enclosing block frame.
A partial does NOT get a frame above its own context: at the top level of a partial,
`../x` resolves to nothing, silently. Empirically, with Handlebars 4.7.9:

    {{#each rows}}{{> p this}}{{/each}}
    p = "[bare:{{#if isGM}}Y{{else}}N{{/if}} parent:{{#if ../isGM}}Y{{else}}N{{/if}} root:{{#if @root.isGM}}Y{{else}}N{{/if}}]"
    -> [bare:N parent:N root:Y]

Passing an explicit context (`{{> p this}}`) does not help, and neither does omitting it.
Only `@root.x` reaches the outer context from inside a partial.

That is how two GM-only action blocks shipped dead: `{{#if isGM}}` inside `{{#each}}`
was "fixed" to `{{#if ../isGM}}` in files that are themselves partials, which is just as
falsy. The quest cards rendered with no GM buttons at all.

This check counts each/with nesting depth per file and flags any `../` chain deeper than
the depth available inside that file. Use `@root.x` there instead.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src" / "jsMain" / "resources"

BLOCK_OPEN = re.compile(r"\{\{#\s*(?:each|with)\b")
BLOCK_CLOSE = re.compile(r"\{\{/\s*(?:each|with)\s*\}\}")
PARENT_REF = re.compile(r"((?:\.\./)+)([A-Za-z0-9_.@]+)")


def check_kingdom_card_actor_attribute() -> int:
    """Every kingdom chat card must carry the attribute findKingdomActor actually reads.

    Kingdom ChatButton handlers resolve their actor with
    `querySelector("[data-kingdom-actor-uuid]")` (ContextMenus.kt). The CAMPING cards use a
    different resolver and a different attribute, `data-actor-uuid`, so copying a camping card as
    the model for a kingdom card produces buttons that render, bind, fire, fail to resolve, and
    apply nothing. Nothing else in the build notices: the classes exist, the handlers are
    registered, the templates compile, and the whole suite stays green.
    """
    buttons_src = ROOT / "src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/ChatButtons.kt"
    if not buttons_src.exists():
        return 0
    src = buttons_src.read_text(encoding="utf-8")
    # only actor-bound buttons; WorldChatButton cards deliberately carry no actor
    before_world = src.split("private val worldButtons", 1)[0]
    actor_bound = set(re.findall(r'ChatButton\("([\w-]+)"', before_world))
    actor_bound -= set(re.findall(r'WorldChatButton\("([\w-]+)"', src))
    if not actor_bound:
        return 0

    failures = []
    for path in sorted((ROOT / "src/jsMain/resources/chatmessages").rglob("*.hbs")):
        text = path.read_text(encoding="utf-8")
        used = sorted(c for c in actor_bound if 'class="%s"' % c in text)
        if used and "data-kingdom-actor-uuid" not in text:
            failures.append((path.relative_to(ROOT), used))

    if failures:
        print("[hbs-actor] FAIL - kingdom chat cards whose buttons can never resolve an actor:")
        for rel, used in failures:
            print("  %s: %s" % (rel, ", ".join(used)))
        print("")
        print('Add data-kingdom-actor-uuid="{{actorUuid}}" to the button (or a hidden span).')
        print("data-actor-uuid is the CAMPING convention and findKingdomActor never reads it.")
        return 1

    print("[hbs-actor] OK - every kingdom chat card exposes data-kingdom-actor-uuid.")
    return 0


def main() -> int:
    failures = []
    scanned = refs = 0

    for path in sorted(RESOURCES.rglob("*.hbs")):
        scanned += 1
        depth = 0
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            # Evaluate references at the depth in effect when the line starts; a block
            # opened on this same line cannot enclose a reference written before it.
            for match in PARENT_REF.finditer(line):
                refs += 1
                levels = match.group(1).count("../")
                if levels > depth:
                    failures.append(
                        (path.relative_to(ROOT), lineno, depth, levels, match.group(0), line.strip())
                    )
            depth += len(BLOCK_OPEN.findall(line))
            depth -= len(BLOCK_CLOSE.findall(line))

    if failures:
        print("[hbs-scope] FAIL — these `../` references resolve to undefined at render time:\n")
        for rel, lineno, depth, levels, ref, text in failures:
            print(f"  {rel}:{lineno}: `{ref}` climbs {levels} level(s) but only {depth} are open here")
            print(f"      {text}")
        print("\nInside a partial (or above the outermost each/with), use @root instead:")
        print("  {{#if @root.isGM}}")
        return 1

    print(f"[hbs-scope] OK — {refs} `../` reference(s) across {scanned} templates all stay in scope.")
    return check_kingdom_card_actor_attribute()


if __name__ == "__main__":
    sys.exit(main())
