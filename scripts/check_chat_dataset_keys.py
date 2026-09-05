#!/usr/bin/env python3
"""Every dataset key a chat-button handler reads must be emitted by a template that renders it.

Two binders own chat buttons and neither validates its own inputs:
  * kingdom/ChatButtons.kt  -- ChatButton("km-foo") { ... button.dataset["barId"] ... }
  * camping/CampingChat.kt  -- bindChatClick(".km-foo") { ... el.dataset["barId"] ... }
Handlers uniformly read a key and bail on null. A template that forgets one therefore renders a
button that binds, fires, reads null and silently does nothing -- the exact shape that has already
shipped here three times (a card carrying the wrong actor attribute, a hook bound to a renamed
class, a tracker reading a field the module does not store). Nothing in the build can see it: the
class exists, the handler is registered, the template compiles, the suite stays green.

This guard pairs each button class with every template that renders it and fails when a key the
handler reads is emitted by NO such template. Keys only some cards carry are fine -- the rule is
that SOMETHING must provide it, or the read is dead code.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEMPLATE_DIRS = ["src/jsMain/resources/chatmessages"]
BINDERS = [
    ("kingdom", "src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/ChatButtons.kt",
     r'(?:ChatButton|WorldChatButton)\("([\w-]+)"'),
    ("camping", "src/jsMain/kotlin/at/posselt/pfrpg2e/camping/CampingChat.kt",
     r'bindChatClick\("\.([\w-]+)"'),
]

# key -> reason it is read but never needs to come from the template
EXEMPT = {
    # supplied by the chat message itself rather than a data- attribute
}


def camel_to_kebab(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "-", name).lower()


def direct_reads(body):
    """Keys read straight off the bound element.

    Only `button.dataset["x"]` / `el.dataset["x"]` count. A handler may also reach a sibling of its
    own card (km-gain-lose reads activityId off a .km-upgrade-result span built in Kotlin), and
    those reads are not the template's contract to satisfy.
    """
    return set(re.findall(r'\b(?:button|el)\.dataset\["(\w+)"\]', body))


def _block_after(src, pos):
    """The braces-matched lambda body that follows the registration at [pos]."""
    start = src.find("{", pos)
    if start < 0:
        return ""
    depth, i = 0, start
    while i < len(src):
        c = src[i]
        if c == '"':                      # skip string literals so a brace inside one is ignored
            i += 1
            while i < len(src) and src[i] != '"':
                i += 2 if src[i] == "\\" else 1
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[start:i + 1]
        i += 1
    return src[start:]


GROUPS = []


def handler_keys(path, class_pattern):
    """{buttonClass: {datasetKey, ...}} for one binder file, bodies matched by braces.

    Bodies are brace-matched rather than sliced to the next registration: km-view-settlement moved
    to worldButtons and the naive slice then swallowed everything after it, inventing eight keys.
    Classes registered inside a loop over (selector to action) pairs share that loop's body.
    """
    src = open(os.path.join(ROOT, path), encoding="utf-8").read()
    out = {}
    groups = []
    for m in re.finditer(class_pattern, src):
        body = _block_after(src, m.end())
        out.setdefault(m.group(1), set()).update(direct_reads(body))
    # loop-registered camping selectors: `for (spec in listOf(".km-a" to "x", ".km-b" to "y"))`
    for lm in re.finditer(r'for \([^)]*in listOf\((.*?)\)\s*\)', src, re.S):
        classes = re.findall(r'"\.([\w-]+)"\s+to\s+"', lm.group(1))
        if not classes:
            continue
        body = _block_after(src, lm.end())
        keys = direct_reads(body)
        for cls in classes:
            out.setdefault(cls, set()).update(keys)
        # one body serves several selectors and passes every key through; each card carries only
        # the keys ITS action needs, so the group satisfies a key collectively
        groups.append((set(classes), keys))
    GROUPS.extend(groups)
    return out


def template_attrs():
    """{buttonClass: {kebabAttr, ...}} across every chat template."""
    out = {}
    for d in TEMPLATE_DIRS:
        base = os.path.join(ROOT, d)
        if not os.path.isdir(base):
            continue
        for name in os.listdir(base):
            if not name.endswith(".hbs"):
                continue
            text = open(os.path.join(base, name), encoding="utf-8").read()
            # each <button ...> element, with the classes and data- attributes it carries
            for tag in re.findall(r"<button\b[^>]*>", text, re.S):
                classes = re.findall(r'class="([^"]*)"', tag)
                attrs = set(re.findall(r'data-([\w-]+)=', tag))
                for cls in " ".join(classes).split():
                    out.setdefault(cls, set()).update(attrs)
    return out


def main():
    attrs = template_attrs()
    problems = []
    checked = 0
    for label, path, pattern in BINDERS:
        if not os.path.exists(os.path.join(ROOT, path)):
            continue
        for cls, keys in sorted(handler_keys(path, pattern).items()):
            emitted = attrs.get(cls)
            if emitted is None:
                continue  # rendered outside chatmessages/ (sheet DOM etc.) -- not this guard's job
            checked += 1
            # a key shared by a loop-registered group counts as emitted when ANY class in the
            # group carries it -- the shared body forwards all of them, each action uses its own
            siblings = set()
            for group_classes, group_keys in GROUPS:
                if cls in group_classes:
                    siblings |= group_classes
            reachable = set(emitted)
            for sib in siblings:
                reachable |= attrs.get(sib, set())
            for key in sorted(keys):
                if key in EXEMPT:
                    continue
                if camel_to_kebab(key) not in reachable:
                    problems.append((label, cls, key, camel_to_kebab(key)))
    if problems:
        print("[chat-keys] FAIL - handlers reading dataset keys no template emits:")
        for label, cls, key, kebab in problems:
            print(f"  ({label}) .{cls}: reads dataset[\"{key}\"], no button carries data-{kebab}")
        print("\nThe button renders and binds, then reads null and does nothing. Emit the attribute,")
        print("or drop the read if the handler no longer needs it.")
        return 1
    print(f"[chat-keys] OK - {checked} chat-button class(es); every dataset key they read is emitted.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
