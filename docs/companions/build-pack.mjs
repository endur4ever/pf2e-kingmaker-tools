import { ClassicLevel } from 'classic-level';

// ---------- deterministic 16-char Foundry ids (idempotent re-runs) ----------
const ALPHA = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
function rngFrom(seed) {
  let h = 2166136261 >>> 0;
  for (const c of seed) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619) >>> 0; }
  let s = h || 123456789;
  return () => { s ^= s << 13; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
}
function fid(seed) { const r = rngFrom(seed); let o = ''; for (let i = 0; i < 16; i++) o += ALPHA[Math.floor(r() * ALPHA.length)]; return o; }

const STATS = { coreVersion: '14.360', systemId: 'pf2e', systemVersion: '8.1.2', compendiumSource: null };
const PUB = (title = 'Pathfinder Kingmaker') => ({ license: 'OGL', remaster: false, title });
const MIG = { version: 0.959, previous: null };

// ---------- item builders ----------
function lore(actor, name, mod) {
  const id = fid(actor + ':lore:' + name);
  return { _id: id, img: 'systems/pf2e/icons/default-icons/lore.svg', name, sort: 0, type: 'lore',
    _stats: { ...STATS }, effects: [],
    system: { description: { value: '' }, mod: { value: mod }, proficient: { value: 1 }, publication: PUB(''), rules: [], slug: null, _migration: { ...MIG } } };
}
function strike(actor, name, bonus, damage, damageType, traits, range) {
  const id = fid(actor + ':strike:' + name);
  const dkey = fid(actor + ':dmg:' + name).slice(0, 16);
  return { _id: id, img: 'systems/pf2e/icons/default-icons/melee.svg', name, sort: 0, type: 'melee',
    _stats: { ...STATS }, effects: [],
    system: { attack: { value: '' }, attackEffects: { value: [] }, bonus: { value: bonus },
      damageRolls: { [dkey]: { damage, damageType } }, description: { value: '' }, publication: PUB(''),
      range: range ?? null, rules: [], slug: null,
      traits: { rarity: 'common', value: traits || [] }, _migration: { ...MIG } } };
}
function action(actor, name, actionType, actions, desc, traits, img) {
  const id = fid(actor + ':action:' + name);
  const defImg = actionType === 'passive' ? 'systems/pf2e/icons/actions/Passive.webp'
    : actionType === 'reaction' ? 'systems/pf2e/icons/actions/Reaction.webp'
    : actions === 2 ? 'systems/pf2e/icons/actions/TwoActions.webp' : 'systems/pf2e/icons/actions/OneAction.webp';
  return { _id: id, img: img || defImg, name, sort: 0, type: 'action', _stats: { ...STATS }, effects: [],
    system: { actionType: { value: actionType }, actions: { value: actions }, category: null,
      description: { value: `<p>${desc}</p>` }, publication: PUB(''), rules: [], slug: null,
      traits: { rarity: 'common', value: traits || [] }, _migration: { ...MIG } } };
}
function spell(actor, name, rank, traditions, traits, desc) {
  const id = fid(actor + ':spell:' + name + ':' + rank);
  return { _id: id, img: 'systems/pf2e/icons/default-icons/spell.svg', name, sort: 0, type: 'spell',
    _stats: { ...STATS }, effects: [],
    system: { area: null, cost: { value: '' }, counteraction: false, damage: {}, defense: null,
      description: { value: desc ? `<p>${desc}</p>` : '' }, duration: { sustained: false, value: '' },
      level: { value: rank }, location: { value: null }, publication: PUB(''), range: { value: '' },
      requirements: '', rules: [], slug: null, target: { value: '' }, time: { value: '2' },
      traits: { rarity: 'common', traditions, value: traits || [] }, _migration: { ...MIG } } };
}
function entry(actor, name, mode, tradition, ability, dc, atk, slots) {
  const id = fid(actor + ':entry:' + name);
  const prepared = mode === 'prepared' ? { flexible: false, value: 'prepared' } : { value: mode };
  return { _id: id, img: 'systems/pf2e/icons/default-icons/spellcastingEntry.svg', name, sort: 0,
    type: 'spellcastingEntry', _stats: { ...STATS }, effects: [],
    system: { ability: { value: ability }, autoHeightenLevel: { value: null }, description: { value: '' },
      prepared, proficiency: { slug: '', value: 2 }, publication: PUB(''), rules: [],
      showSlotlessLevels: { value: false }, slots: slots || {}, slug: null,
      spelldc: { dc, value: atk }, tradition: { value: tradition }, _migration: { ...MIG } } };
}

// ---------- actor builder ----------
function npc({ key, name, level, size, traits, languages, senses, abilities, ac, hp, speed, otherSpeeds,
  saves, perception, skills, resistances, weaknesses, immunities, focus, blurb, publicNotes, gmNotes, items }) {
  const aid = fid('actor:' + key);
  // give every item its _stats + collect ids
  const sys = {
    abilities: Object.fromEntries(Object.entries(abilities).map(([k, v]) => [k, { mod: v }])),
    attributes: {
      ac: { details: '', value: ac },
      allSaves: { value: '' },
      hp: { details: '', max: hp, temp: 0, value: hp },
      speed: { otherSpeeds: otherSpeeds || [], value: speed },
      ...(resistances ? { resistances } : {}),
      ...(weaknesses ? { weaknesses } : {}),
      ...(immunities ? { immunities } : {}),
    },
    details: {
      alliance: 'party',
      blurb: blurb || '',
      languages: { details: '', value: languages },
      level: { value: level },
      privateNotes: gmNotes || '',
      publicNotes: publicNotes || '',
      publication: PUB(),
    },
    initiative: { statistic: 'perception' },
    perception: { details: '', mod: perception, senses: senses || [] },
    ...(focus ? { resources: { focus } } : {}),
    saves: {
      fortitude: { saveDetail: '', value: saves.fort },
      reflex: { saveDetail: '', value: saves.ref },
      will: { saveDetail: '', value: saves.will },
    },
    skills: Object.fromEntries(Object.entries(skills || {}).map(([k, v]) => [k, { base: v }])),
    traits: { rarity: 'unique', size: { value: size }, value: traits },
    _migration: { ...MIG },
  };
  const actor = { _id: aid, name, type: 'npc', img: 'systems/pf2e/icons/default-icons/npc.svg',
    system: sys, items: items.map(i => i._id), effects: [], _stats: { ...STATS } };
  return { actor, items };
}

// =================================================================================
// EKUNDAYO — Human Ranger 6
// =================================================================================
const ekItems = [
  strike('ekundayo', 'longsword', 5, '1d8+2', 'slashing', ['versatile-p']),
  strike('ekundayo', 'longbow', 7, '1d8', 'piercing', ['deadly-d10', 'range-increment-100', 'volley-30'], { increment: 100, max: null }),
  lore('ekundayo', 'Giant Lore', 3),
  lore('ekundayo', 'Guild Lore', 3),
  lore('ekundayo', 'Hunting Lore', 3),
  action('ekundayo', 'Hunt Prey', 'action', 1, 'Designate a single creature as your prey. You gain a +2 circumstance bonus to Perception checks to Seek it and ignore the first range increment penalty when attacking it.', ['concentrate', 'ranger']),
  action('ekundayo', 'Hunted Shot', 'action', 1, 'Make two Strikes against your hunted prey with a ranged weapon; combine into a single action (once per round).', ['flourish', 'ranger']),
  action('ekundayo', 'Hunter’s Edge: Precision', 'passive', null, 'The first time each round Ekundayo hits his hunted prey, he deals 1d8 additional precision damage.', ['ranger']),
];
const ekundayo = npc({
  key: 'ekundayo', name: 'Ekundayo', level: 1, size: 'med', traits: ['human', 'humanoid'],
  languages: ['common'], senses: [],
  abilities: { str: 2, dex: 4, con: 2, int: 0, wis: 1, cha: 0 },
  ac: 18, hp: 21, speed: 25,
  saves: { fort: 7, ref: 9, will: 4 }, perception: 6,
  skills: { acrobatics: 7, athletics: 5, crafting: 3, nature: 4, stealth: 7, survival: 4 },
  blurb: 'Human ranger · giant hunter (Level 1)',
  publicNotes: '<p><strong>Human Ranger 1 (Skilled Heritage, Artisan).</strong> A quiet, dour bounty-hunter-turned-carpenter who lost his wife and daughter when the hill giant <strong>Gragglebeard</strong> destroyed the village of Bristlehill. He now hunts Gragglebeard and all giants, accompanied by an anxious dog he rescued. Those who earn his respect may call him “Ekun.”</p><hr /><p><strong>Feats:</strong> General Training (Toughness), Hunted Shot, Toughness, Specialty Crafting (woodworking, +4 Crafting to woodworking). <strong>Gear:</strong> longbow (20 arrows), longsword, leather armor, woodworker’s tools.</p><p><em>Note: at 1st level the dog is a standard Riding Dog and is not yet his animal companion — Ekundayo gains it when he takes the Animal Companion feat at 2nd level.</em></p>',
  gmNotes: '<p><strong>Influence (max 8):</strong> 2 = Indifferent (fights at your side); 4 = Friendly (shares giant-hunting methods); 8 = Helpful (reduces Construction DC of Lumber-cost structures by 2, gifts a hooked rune, asks to be called “Ekun”). XP: Influence 6 → 10 XP, 8 → +30 XP.</p><p><strong>Discovery DCs:</strong> Hunting Lore 13 / Perception 15 / Nature 17. <strong>Influence skills:</strong> Giant Lore 13, Survival 15, Crafting 17, Diplomacy 19, Deception 21, Intimidation 23.</p><p><strong>Weakness:</strong> promising to help slay giants / hunt Gragglebeard = +2 to Influence. <strong>Penalty:</strong> appeals via pity or promises to help him = +2 DC until he is Friendly.</p>',
  items: ekItems,
});

// =================================================================================
// DOG — Ekundayo's dog (standard Riding Dog, Creature 1; pre-companion)
// =================================================================================
const dogItems = [
  strike('dog', 'Jaws', 7, '1d6+2', 'piercing', ['unarmed']),
  action('dog', 'Pack Attack', 'passive', null, 'The dog’s Strikes deal 1d4 extra damage to any creature that is within reach of at least two of the dog’s allies.', ['animal']),
];
const dog = npc({
  key: 'dog', name: 'Dog (Ekundayo’s Riding Dog)', level: 1, size: 'med', traits: ['animal'],
  languages: [], senses: [{ type: 'low-light-vision' }, { acuity: 'imprecise', range: 30, type: 'scent' }],
  abilities: { str: 2, dex: 2, con: 2, int: -4, wis: 2, cha: -1 },
  ac: 16, hp: 20, speed: 35,
  saves: { fort: 7, ref: 5, will: 5 }, perception: 7,
  skills: { acrobatics: 5, athletics: 7, survival: 5 },
  blurb: 'Ekundayo’s anxious dog (Riding Dog)',
  publicNotes: '<p>An anxious dog Ekundayo rescued. At 1st level it fights as a standard Riding Dog (<em>Pathfinder Monster Core</em>); it becomes his proper animal companion once he takes the Animal Companion feat at 2nd level.</p>',
  items: dogItems,
});

// =================================================================================
// JUBILOST NARTHROPPLE — Gnome Alchemist 1 (Companion Guide p.32)
// =================================================================================
const juInnate = entry('jubilost', 'Primal Innate Spells', 'innate', 'cha', 16, 6);
const juSpells = [
  spell('jubilost', 'Know Direction', 1, ['divine', 'occult', 'primal'], ['cantrip', 'concentrate', 'detection'], 'You know which way is north.'),
].map(s => { s.system.location.value = juInnate._id; return s; });
const juItems = [
  strike('jubilost', 'dagger', 5, '1d4-1', 'piercing', ['agile', 'finesse', 'thrown-10', 'versatile-s']),
  strike('jubilost', 'lesser alchemist’s fire', 5, '1d8', 'fire', ['alchemical', 'bomb', 'fire', 'range-increment-30', 'splash'], { increment: 30, max: null }),
  strike('jubilost', 'crossbow', 5, '1d8', 'piercing', ['range-increment-120', 'reload-1'], { increment: 120, max: null }),
  lore('jubilost', 'Academia Lore', 7),
  lore('jubilost', 'First World Lore', 7),
  lore('jubilost', 'Forest Lore', 7),
  lore('jubilost', 'Hill Lore', 7),
  lore('jubilost', 'River Lore', 7),
  action('jubilost', 'Quick Alchemy', 'action', 1, 'Spend a batch of infused reagents to create a single alchemical consumable of your advanced alchemy level that lasts until the start of your next turn.', ['alchemist', 'manipulate']),
  action('jubilost', 'Far Lobber', 'passive', null, 'Jubilost’s thrown bombs have a range increment of 30 feet instead of 20.', ['alchemist']),
  juInnate, ...juSpells,
];
const jubilost = npc({
  key: 'jubilost', name: 'Jubilost Narthropple', level: 1, size: 'sm', traits: ['fey', 'gnome', 'humanoid'],
  languages: ['common', 'draconic', 'gnomish', 'hallit', 'jotun', 'kelish', 'sylvan'],
  senses: [{ type: 'low-light-vision' }],
  abilities: { str: -1, dex: 2, con: 2, int: 4, wis: 1, cha: 1 },
  ac: 16, hp: 18, speed: 25,
  saves: { fort: 7, ref: 7, will: 4 }, perception: 4,
  skills: { crafting: 7, nature: 4, society: 7, survival: 4 },
  blurb: 'Fey-touched gnome alchemist · bomber (Level 1)',
  publicNotes: '<p><strong>Fey-touched Gnome Alchemist 1 (Bomber, Scholar).</strong> A brilliant, arrogant, biting-tongued cartographer-scholar from Taldor who took to the Stolen Lands to gather real-world experience and stave off the Bleaching. Eager to debate any theory — often just for the joy of the argument.</p><hr /><p><strong>Feats:</strong> Fey Fellowship, Far Lobber, Alchemical Crafting, Assurance (Nature). <strong>Formula book:</strong> lesser acid flask, lesser alchemist’s fire, lesser antidote, lesser bottled lightning, minor elixir of life, lesser feyfoul, lesser tanglefoot bag, sunrod. <strong>Gear:</strong> dagger, crossbow (10 bolts), alchemist’s tools, padded armor, formula book, hooded lantern. <strong>Prepared infused:</strong> lesser alchemist’s fire (4), minor elixir of life (2). <em>(His lesser alchemist’s fire also deals 1 persistent fire + 1 splash.)</em></p>',
  gmNotes: '<p><strong>Influence (max 8):</strong> 2 = Friendly (fights at your side); 4 = lets PCs choose his daily infused items, unlocks his unique items/skill feats; 6 = 10 XP; 8 = Helpful, sets up shop granting a settlement a free <strong>alchemy laboratory</strong>, gifts each PC up to 10 gp of alchemical items, +30 XP.</p><p><strong>Discovery DCs:</strong> Academia Lore 13 / Crafting 15 / Perception 17. <strong>Influence skills:</strong> Academia Lore 13, Crafting 15, any other Lore 17, Intimidation 19, Diplomacy 21, Deception 23.</p><p><strong>Weakness:</strong> a gnome or alchemist gets +1 (gnome alchemist +2). <strong>Penalty:</strong> appearing slow-witted / crit-failing an Int-based Influence check = –2 to further checks.</p>',
  items: juItems,
});

// =================================================================================
// NOK-NOK — Goblin Rogue 1 (Companion Guide p.56)
// =================================================================================
const nokItems = [
  strike('noknok', 'kukri', 4, '1d6+1', 'slashing', ['agile', 'finesse', 'trip']),
  strike('noknok', 'shortbow', 7, '1d6', 'piercing', ['deadly-d10', 'range-increment-60'], { increment: 60, max: null }),
  lore('noknok', 'Underworld Lore', 3),
  action('noknok', 'Sneak Attack', 'passive', null, 'Nok-Nok deals 1d6 extra precision damage to flat-footed creatures he Strikes with an agile, finesse, or ranged weapon.', ['rogue']),
  action('noknok', 'Surprise Attack', 'passive', null, 'On the first round of combat, creatures that haven’t acted are flat-footed to Nok-Nok.', ['rogue']),
  action('noknok', 'Twin Feint', 'action', 2, 'Make two Strikes with two melee weapons against the same creature; the target is flat-footed against the second Strike.', ['flourish', 'rogue']),
];
const noknok = npc({
  key: 'noknok', name: 'Nok-Nok', level: 1, size: 'sm', traits: ['goblin', 'humanoid'],
  languages: ['common', 'goblin'], senses: [{ type: 'darkvision' }],
  abilities: { str: 1, dex: 4, con: 3, int: 0, wis: -1, cha: 2 },
  ac: 18, hp: 17, speed: 25,
  saves: { fort: 6, ref: 9, will: 4 }, perception: 14,
  skills: { acrobatics: 7, athletics: 4, deception: 5, diplomacy: 5, intimidation: 5, performance: 5, religion: 2, society: 3, stealth: 7, survival: 2, thievery: 7 },
  blurb: 'Irongut goblin rogue · would-be hero-god (Level 1)',
  publicNotes: '<p><strong>Irongut Goblin Rogue 1 (Scoundrel, Criminal).</strong> The sole survivor of the goblin village of Mudsip, convinced Lamashtu rewarded him with a pair of kukris for “defeating” the hydra that destroyed his home. He latches onto the PCs as heroic role models, hoping to become the fifth goblin hero-god — and is on his best behaviour while he thinks they’re watching.</p><hr /><p><strong>Feats:</strong> Very Sneaky, Twin Feint, Experienced Smuggler, Pickpocket. <strong>Gear:</strong> kukris (2), shortbow (20 arrows), leather armor, thieves’ tools, wooden religious symbol, torches (6).</p>',
  gmNotes: '<p><strong>⚠️ Perception:</strong> the Guide prints <strong>+14</strong> at level 1, which is almost certainly a misprint carried from his higher-level block (a 1st-level Nok-Nok with Wis −1 should be roughly +7). Printed value used here — adjust to +7 if it feels too high.</p><p><strong>Influence (max 12):</strong> 2 = the heroic idea takes root, one PC becomes his inspiration (+1 to Influence him); 4 = Friendly, follows orders in combat; 6 = alignment shifts to Chaotic Neutral (reverts to CE at 0/hostile); 8 = Helpful, can serve in the kingdom (well-suited as <strong>Emissary</strong>); 12 = Chaotic Good, reveals a buried <strong>+1 shifting dogslicer</strong>.</p><p><strong>Discovery DCs:</strong> Religion 18 / Perception 20 / Goblin Lore 22. (Influence-skill DCs not specified in the Guide.)</p><p><strong>Weakness:</strong> witnessing chaotic acts of mayhem/irreverence = +2 to Influence. <strong>Penalty:</strong> witnessing acts of good/charity while he is evil = –2.</p>',
  items: nokItems,
});

// =================================================================================
// TRISTIAN — Aasimar Human Cleric of Sarenrae 1 (Cloistered) (Companion Guide p.68)
// =================================================================================
const trDivine = entry('tristian', 'Divine Prepared Spells', 'prepared', 'wis', 17, 7);
const trFocus = entry('tristian', 'Cleric Focus Spells', 'focus', 'wis', 17, 7);
const prep = []; // collect spell items
function addPrep(name, rank, traits, desc) { const s = spell('tristian', name, rank, ['divine'], traits, desc); s.system.location.value = trDivine._id; prep.push(s); return s; }
function addFocus(name, rank, traits, desc) { const s = spell('tristian', name, rank, ['divine'], ['focus', ...traits], desc); s.system.location.value = trFocus._id; prep.push(s); return s; }
// 1st rank (3 heal via divine font + 2 normal slots)
const r1 = [
  addPrep('Heal', 1, ['healing', 'manipulate', 'vitality'], 'Restore Hit Points or damage undead.'),
  addPrep('Heal', 1, ['healing', 'manipulate', 'vitality']),
  addPrep('Heal', 1, ['healing', 'manipulate', 'vitality']),
  addPrep('Protection', 1, ['concentrate', 'manipulate'], 'Ward a creature against a foe type.'),
  addPrep('Sanctuary', 1, ['concentrate', 'manipulate'], 'Protect a creature from attacks.'),
];
const r0 = [
  addPrep('Disrupt Undead', 0, ['attack', 'cantrip', 'concentrate', 'holy', 'manipulate', 'vitality'], 'Damage an undead creature.'),
  addPrep('Divine Lance', 0, ['attack', 'cantrip', 'concentrate', 'manipulate', 'sanctified'], 'A lance of divine energy.'),
  addPrep('Forbidding Ward', 0, ['cantrip', 'concentrate', 'manipulate'], 'Protect an ally from a chosen foe.'),
  addPrep('Shield', 0, ['cantrip', 'concentrate'], 'Raise a magical shield.'),
  addPrep('Stabilize', 0, ['cantrip', 'concentrate', 'healing', 'manipulate', 'vitality'], 'Stabilize a dying creature.'),
];
trDivine.system.slots = {
  slot0: { max: r0.length, prepared: r0.map(s => ({ id: s._id })) },
  slot1: { max: r1.length, prepared: r1.map(s => ({ id: s._id })) },
};
const trFocusSpells = [
  addFocus('Healer’s Blessing', 1, ['concentrate', 'manipulate'], 'Bless an ally so healing magic restores more HP (Healing domain).'),
];
const trItems = [
  strike('tristian', 'scimitar', 3, '1d6', 'slashing', ['forceful', 'sweep']),
  strike('tristian', 'crossbow', 4, '1d8', 'piercing', ['range-increment-120', 'reload-1'], { increment: 120, max: null }),
  lore('tristian', 'Forest Lore', 4),
  action('tristian', 'Divine Font (Heal)', 'passive', null, 'Tristian prepares additional heal spells each day equal to 1 + his Charisma modifier.', ['cleric']),
  trDivine, trFocus, ...prep,
];
const tristian = npc({
  key: 'tristian', name: 'Tristian', level: 1, size: 'med', traits: ['aasimar', 'human', 'humanoid'],
  languages: ['aklo', 'celestial', 'common', 'kelish', 'sylvan'], senses: [{ type: 'low-light-vision' }],
  abilities: { str: 0, dex: 1, con: 1, int: 1, wis: 4, cha: 2 },
  ac: 14, hp: 18, speed: 25,
  focus: { max: 1, value: 1 },
  saves: { fort: 4, ref: 4, will: 9 }, perception: 7,
  skills: { diplomacy: 5, medicine: 7, nature: 4, occultism: 4, religion: 4, society: 4, survival: 7 },
  blurb: 'Aasimar cleric of Sarenrae · healer (Level 1)',
  publicNotes: '<p><strong>Aasimar Human Cleric of Sarenrae 1 (Cloistered, Nomad).</strong> A quiet, soft-spoken, kind healer raised in a Qadiran orphanage of Sarenite priests, with no memory of his parents. He does not yet realise the truth of his own celestial heritage, and is deeply curious about magical afflictions and curses.</p><hr /><p><strong>Feats:</strong> Angelkin, Domain Initiate (Healing), Assurance (Survival), Multilingual. <strong>Gear:</strong> scimitar, crossbow (20 bolts), explorer’s clothing, minor healing potion, healer’s tools, religious text & silver symbol of Sarenrae.</p>',
  gmNotes: '<p><strong>Influence (max 8):</strong> 2 = Friendly (takes offensive actions, follows PC suggestions); 4 = lets PCs help choose prepared spells, grants access to rare <em>blazing blade</em> and <em>light of revelation</em>; 6 = 10 XP; 8 = Helpful, grants two free settlement structures (an <strong>orphanage</strong> and a <strong>shrine</strong>), gifts each PC up to 12 gp of healing potions/scrolls of heal, +30 XP.</p><p><strong>Discovery DCs:</strong> Sarenrae Lore 13 / Religion 15 / Perception 17. <strong>Influence skills:</strong> Sarenrae Lore 13, Medicine 15, Religion 17, Diplomacy 19, Deception 21, Intimidation 23.</p><p><strong>Weakness:</strong> a PC he believes is a fellow orphan gets +2 (lying loses the bonus AND 3 Influence if discovered). <strong>Penalty:</strong> witnessing cruelty / mean jokes = –2.</p>',
  items: trItems,
});

// =================================================================================
// WRITE PACK (split-key ClassicLevel format)
// =================================================================================
const all = [ekundayo, dog, jubilost, noknok, tristian];
const outDir = process.argv[2];
const db = new ClassicLevel(outDir, { keyEncoding: 'utf8', valueEncoding: 'json' });
await db.open();
const batch = db.batch();
for (const { actor, items } of all) {
  batch.put('!actors!' + actor._id, actor);
  for (const it of items) batch.put('!actors.items!' + actor._id + '.' + it._id, it);
}
await batch.write();
await db.close();
console.log('WROTE', all.length, 'actors to', outDir);
for (const { actor, items } of all) console.log(' -', actor.name, '(L' + actor.system.details.level.value + ')', actor._id, '|', items.length, 'items');
