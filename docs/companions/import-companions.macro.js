/*
 * Kingmaker companions → Actors → Kingmaker → Companions
 * Imports the OFFICIAL level-1 companion PCs (full builds + inventory) from the
 * pf2e.kingmaker-bestiary compendium into the Companions folder.
 *
 * How to run: Foundry → Macros → create a new "script" macro → paste this → Execute.
 * Safe to re-run: skips any companion already in the folder.
 */
const pack = game.packs.get("pf2e.kingmaker-bestiary");
if (!pack) {
  ui.notifications.error("Compendium 'pf2e.kingmaker-bestiary' not found (is the PF2e Kingmaker content enabled?).");
} else {
  // Find (or create) the Kingmaker > Companions actor folder.
  let companions = game.folders.get("1TVpRFrvRMJkfIVD");
  if (!companions || companions.type !== "Actor") {
    let kingmaker = game.folders.find(f => f.type === "Actor" && f.name === "Kingmaker" && !f.folder)
      ?? await Folder.create({ name: "Kingmaker", type: "Actor" });
    companions = game.folders.find(f => f.type === "Actor" && f.name === "Companions" && f.folder?.id === kingmaker.id)
      ?? await Folder.create({ name: "Companions", type: "Actor", folder: kingmaker.id });
  }

  // Level-1 PCs in pf2e.kingmaker-bestiary.
  const wanted = {
    "Ekundayo": "1SEyDYO9l6mcFhoy",
    "Nok-Nok":  "sY8owbk9TFeygFL9",
    "Tristian": "hdFT5WIarw2Do3Sy",
    // "Jubilost":  "KqWZZBucIAA1MzjF", // already in your folder; uncomment to (re)add
  };

  const present = new Set(game.actors.filter(a => a.folder?.id === companions.id).map(a => a.name.split(" (")[0]));
  const created = [];
  for (const [name, id] of Object.entries(wanted)) {
    if (present.has(name)) continue;
    const data = (await pack.getDocument(id)).toObject();
    delete data._id;
    data.name = name;            // strip the "(Level 1)" suffix to match your folder
    data.folder = companions.id;
    await Actor.create(data, { keepId: false });
    created.push(name);
  }
  ui.notifications.info(created.length ? `Imported companions: ${created.join(", ")}` : "All companions already present.");
}
