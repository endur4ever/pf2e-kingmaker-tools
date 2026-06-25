import fs from 'fs';
const DATA = JSON.parse(fs.readFileSync('/tmp/companions_full.json', 'utf8'));
const VAULT = '/home/grego/hermes-workspace/memory/Campaigns/Companions';

const PB = 'modules/pf2e-kingmaker/assets/actor-portraits/';
const ART = { Amiri:'kingmaker/amiri', Ekundayo:'kingmaker/ekundayo', Jubilost:'kingmaker/jubilost', Linzi:'kingmaker/linzi',
  'Nok-Nok':'kingmaker/nok-nok', Tristian:'kingmaker/tristian', Valerie:'kingmaker/valerie', Harrim:'npc/harrim',
  Jaethal:'kingmaker/jaethal', Kalikke:'npc/kalikke', Kanerah:'npc/kanerah', Octavia:'npc/octavia', Regongar:'npc/regongar' };
const LORE = {
  Amiri:    ['Chaotic Neutral', null, 'Exiled Kellid warrior from the Realm of the Mammoth Lords, seeking glory on her own terms.'],
  Ekundayo: ['Lawful Good', null, 'Stoic ranger hunting the hill giant Gragglebeard, who slew his family at Bristlehill. At Influence 8 → −2 Construction DC on Lumber structures.'],
  Jubilost: ['Chaotic Neutral', null, 'Arrogant gnome alchemist-cartographer from Taldor. At Influence 8 → a settlement gains a free alchemy laboratory.'],
  Linzi:    ['Neutral Good', null, 'Cheerful halfling bard chronicling the party’s rise to royalty.'],
  'Nok-Nok':['Chaotic Evil → Good', null, 'Irongut goblin who dreams of becoming the fifth goblin hero-god. Can become the kingdom’s Emissary.'],
  Tristian: ['Neutral Good', null, 'Aasimar cleric of Sarenrae, unaware of his celestial heritage. At Influence 8 → free orphanage + shrine.'],
  Valerie:  ['Lawful Neutral', null, 'Disciplined sword-and-board fighter, a former ward of a temple of Erastil.'],
  Harrim:   ['Chaotic Neutral', 'Magister', 'Dwarf doom-priest of Groetus; grants the “Evangelize the End” downtime activity (reduces Unrest/Crime/Corruption/Strife). Homebrew (no official stat block).'],
  Jaethal:  ['Neutral Evil', 'Emissary', 'Undead elf of Urgathoa (harmed by vitality, healed by void); takes the Cleric archetype at level 2. Homebrew.'],
  Kalikke:  ['Chaotic Good', 'Counselor', 'Gentle water twin sharing one cursed body with Kanerah; a Kineticist in concept, built as a water-elemental Sorcerer. Homebrew.'],
  Kanerah:  ['Lawful Evil', 'Treasurer', 'Fierce fire twin; a Kineticist in concept, built as a fire-elemental Sorcerer. Homebrew.'],
  Octavia:  ['Chaotic Good', 'Magister', 'Half-elf wizard, former Technic League captive and Regongar’s partner. Homebrew.'],
  Regongar: ['Chaotic Evil', 'General', 'Half-orc magus, former Technic League captive who trusts only Octavia. Homebrew.'],
};
const OFFICIAL = new Set(['Amiri','Ekundayo','Jubilost','Linzi','Nok-Nok','Tristian','Valerie']);
const ANC_SPEED = { Dwarf:20, Elf:30, Gnome:25, Goblin:25, Halfling:25, Human:25, Orc:25 };
const score = m => (m==null?'—':`${10+2*m} (${m>=0?'+':''}${m})`);
const sign = m => (m==null?'—':(m>=0?'+':'')+m);
const cap = s => s? s[0].toUpperCase()+s.slice(1):s;

function note(a){
  const lore = LORE[a.name]||['—',null,''];
  const art = ART[a.name] ? PB+ART[a.name]+'.webp' : '';
  const L=[];
  L.push('---');
  L.push('tags: [kingmaker, companion, pf2e, character-sheet'+(OFFICIAL.has(a.name)?']':', homebrew]'));
  L.push('campaign: "[[Kingmaker]]"');
  L.push(`ancestry: ${a.ancestry||'—'}`);
  L.push(`class: ${a.cls||'—'}`);
  L.push(`level: ${a.level}`);
  L.push(`alignment: ${lore[0]}`);
  if(art) L.push(`portrait: ${art}`);
  L.push(`source: "Extracted from the live Foundry actor (Actors → Kingmaker → Companions), 2026-06-20"`);
  L.push('---');
  L.push('');
  L.push(`# ${a.name} — Level ${a.level} ${a.cls||''}`.trim());
  L.push('');
  L.push(`> ${OFFICIAL.has(a.name)?'Official PF2e Kingmaker companion PC':'Homebrew companion PC (no official PF2e stat block)'}, reflecting the actual Foundry character sheet. ${lore[1]?'**Kingdom role:** '+lore[1]+'. ':''}${lore[2]}`);
  L.push('');
  L.push('## Overview');
  L.push('| | |');
  L.push('|---|---|');
  L.push(`| **Ancestry / Heritage** | ${a.ancestry||'—'}${a.heritage?(' ('+a.heritage+')'):''} |`);
  L.push(`| **Background** | ${a.background||'—'} |`);
  L.push(`| **Class** | ${a.cls||'—'}${a.keyability?(' (key '+a.keyability.toUpperCase()+')'):''} |`);
  if(a.deity) L.push(`| **Deity** | ${a.deity} |`);
  L.push(`| **Alignment** | ${lore[0]} |`);
  if(a.languages?.length) L.push(`| **Languages** | ${a.languages.map(cap).join(', ')} |`);
  L.push('');
  L.push('## Abilities');
  L.push('| STR | DEX | CON | INT | WIS | CHA |');
  L.push('|:--:|:--:|:--:|:--:|:--:|:--:|');
  const ab=a.abilities;
  L.push(`| ${score(ab.str)} | ${score(ab.dex)} | ${score(ab.con)} | ${score(ab.int)} | ${score(ab.wis)} | ${score(ab.cha)} |`);
  L.push('');
  L.push('## Defenses & Senses');
  const speed = a.speed ?? ANC_SPEED[a.ancestry] ?? 25;
  L.push(`- **AC** ${a.ac} · **HP** ${a.hp} · **Speed** ${speed} ft · **Perception** ${sign(a.perception)}`);
  L.push(`- **Saves** — Fort ${sign(a.saves.fort)}, Ref ${sign(a.saves.ref)}, Will ${sign(a.saves.will)}`);
  if(a.classDC) L.push(`- **Class DC** ${a.classDC}`);
  L.push('');
  // skills (trained+)
  const sk = Object.entries(a.skills).filter(([k,v])=>v.rank>=1).sort((x,y)=>y[1].mod-x[1].mod);
  if(sk.length){ L.push('## Skills'); L.push(sk.map(([k,v])=>`${k} ${sign(v.mod)}`).join(' · ')); L.push(''); }
  // strikes
  const st = a.strikes.filter(s=>s.name!=='Unarmed Attack');
  if(st.length){ L.push('## Strikes'); for(const s of st) L.push(`- **${s.name}** ${sign(s.bonus)}${s.dmg?(' — '+s.dmg):''}${s.traits?.length?(' ('+s.traits.join(', ')+')'):''}`); L.push(''); }
  // spells
  if(a.casting?.length){
    L.push('## Spells');
    for(const c of a.casting){
      const hdr = `**${c.name}** — ${cap(c.tradition)} ${c.mode}${c.dc?(', DC '+c.dc):''}${c.atk!=null?(', attack '+sign(c.atk)):''}`;
      L.push('- '+hdr);
      const order=['cantrips','rank1','rank2','rank3'];
      for(const r of order){ if(c.byRank[r]) L.push(`    - ${r==='cantrips'?'Cantrips':('Rank '+r.replace('rank',''))}: *${c.byRank[r].join(', ')}*`); }
    }
    L.push('');
  }
  // feats + class features
  if(a.featList?.length || a.classFeatures?.length){
    L.push('## Feats & Class Features');
    if(a.featList?.length) L.push('- **Feats:** '+a.featList.map(f=>f.name+(f.cat?` (${f.cat})`:'')).join(', '));
    if(a.classFeatures?.length) L.push('- **Class features:** '+a.classFeatures.join(', '));
    L.push('');
  }
  // inventory
  if(a.inv?.length){
    L.push('## Inventory');
    L.push(a.inv.map(i=>i.name+(i.qty>1?` ×${i.qty}`:'')).join(', '));
    L.push('');
  }
  if(a.bio && a.bio.trim()){ L.push('## Background'); L.push(a.bio.replace(/<\/?p>/g,'').replace(/<\/?strong>/g,'**').replace(/<\/?em>/g,'*').trim()); L.push(''); }
  L.push(`*Part of [[Kingmaker]] · see [[Companions Compendium]]. Portrait & token: \`${ART[a.name]||'default'}\` (pf2e-kingmaker module).*`);
  return L.join('\n');
}

let written=[];
for(const a of DATA){
  const f = `${VAULT}/${a.name}.md`;
  fs.writeFileSync(f, note(a));
  written.push(a.name);
}
// remove stale duplicate note
try { fs.unlinkSync(`${VAULT}/Jubilost Narthropple.md`); written.push('(removed Jubilost Narthropple.md)'); } catch {}
console.log('wrote', written.length, 'notes:', written.join(', '));
