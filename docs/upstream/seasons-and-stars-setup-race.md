# Draft issue for rayners/fvtt-seasons-and-stars — v0.26.0

**Title:** `setup` hook restores the active calendar before variant calendar packs finish loading

**Environment:** Foundry v14.363, PF2e 8.1.2, Seasons & Stars 0.26.0, active calendar `golarion-pf2e(absalom-reckoning)`.

**What happens:** every world load logs

```
[S&S ERROR] Calendar not found: golarion-pf2e(absalom-reckoning)
```

Stack, captured by wrapping `console.error` before any module script ran:

```
Logger.error                     modules/seasons-and-stars/module.js:65
CalendarManager.setActiveCalendarSync   modules/seasons-and-stars/module.js:6261
Object.setup [as fn]             modules/seasons-and-stars/module.js:20093   <- S&S's own `setup` hook
Hooks.callAll / Game.setupGame   scripts/foundry.mjs
```

At `setup` the saved id is restored synchronously via `setActiveCalendarSync`, but the variant
calendars (`golarion-pf2e(absalom-reckoning)`, `(imperial-calendar)`, …) are still being registered
asynchronously, so the lookup fails and the error is logged. By `ready`, `manager.getAllCalendars()`
lists all eight ids including the variant and `manager.getActiveCalendar().id` is correct, so the
state self-heals — but any module that reads the active calendar between `setup` and `ready` sees
the fallback, and the console error fires on every load.

**Expected:** either defer the restore until the async pack registration resolves (await it, or run
the restore on `ready`), or resolve variant ids by their base id (`golarion-pf2e`) when the variant
is not yet registered and re-apply once it is.

**Workaround we ship** in pf2e-kingmaker-tools: on `ready` (and `seasons-stars:ready`), if the
active calendar is not the saved one, re-apply the saved id via `manager.setActiveCalendar`.

---

**How to file.** This needs a GitHub account, so it is Gregory's to submit:

    https://github.com/rayners/fvtt-seasons-and-stars/issues/new

Paste the **Title** line above as the issue title and everything from **Environment** down to
**Workaround we ship** as the body. Nothing else needs editing — the stack trace was captured from
this world, and the version numbers are the ones actually running (Foundry 14.363, PF2e 8.1.2,
Seasons & Stars 0.26.0).

Once filed, drop the issue URL on card t_533b14be so the workaround we ship can cite it and be
removed when upstream fixes the race.
