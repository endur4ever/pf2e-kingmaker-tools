/* Backfill sheet XP that past expeditions earned but never wrote.
   Paste into Foundry's F12 console as GM. Run STEP 1 first (read-only).

   Every companion's module-side XP pool ("shadow XP") is the exact total their
   expeditions awarded, because nothing has ever spent it — no level-up offer could
   fire (the same null-lookup bug killed that button too). So sheet XP should equal
   shadow XP, and STEP 2 sets it to that rather than adding, which makes a second
   run a no-op instead of double-paying. */

/* ── STEP 1 — READ-ONLY. Shows what STEP 2 would change. ───────────────────── */
(async () => {
  const MOD = "pf2e-kingmaker-tools";
  const ka = game.actors.find(a => a.getFlag(MOD, "kingdom-sheet"));
  if (!ka) return console.warn("KM BACKFILL: no kingdom actor");
  const k = ka.getFlag(MOD, "kingdom-sheet");
  const rows = [];
  for (const c of (k.companions || [])) {
    const a = c.actorUuid ? await fromUuid(c.actorUuid) : null;
    rows.push({
      companion: c.name,
      linked: a ? `${a.name} (${a.type})` : "— unlinked —",
      shadowXp: c.xp ?? 0,
      sheetXpNow: a?.system?.details?.xp?.value ?? "n/a",
      wouldSetTo: a?.type === "character" ? (c.xp ?? 0) : "skipped (not a character)",
    });
  }
  console.table(rows);
  console.log("Looks right? Run STEP 2.");
})()

/* ── STEP 2 — MUTATES SHEETS. Paste separately after checking STEP 1. ───────
(async () => {
  const MOD = "pf2e-kingmaker-tools";
  const ka = game.actors.find(a => a.getFlag(MOD, "kingdom-sheet"));
  const k = ka.getFlag(MOD, "kingdom-sheet");
  const done = [];
  for (const c of (k.companions || [])) {
    const a = c.actorUuid ? await fromUuid(c.actorUuid) : null;
    if (!a || a.type !== "character") continue;
    const target = c.xp ?? 0;
    if (a.system.details.xp.value === target) continue;
    await a.update({ "system.details.xp.value": target });
    done.push(`${a.name}: ${target}`);
  }
  ui.notifications.info(done.length ? `Backfilled sheet XP — ${done.join(", ")}` : "Nothing to backfill.");
  console.log("KM BACKFILL:", done);
})()
   ──────────────────────────────────────────────────────────────────────────── */
