const { chromium } = require('playwright');
const fs = require('fs');
const env = Object.fromEntries(
  fs.readFileSync('/home/grego/code/pf2e-kingmaker-tools/.foundry-creds.env', 'utf8')
    .split('\n').filter(Boolean).map(l => { const i = l.indexOf('='); return [l.slice(0, i), l.slice(i + 1)]; })
);
const URL = env.FOUNDRY_URL || 'http://localhost:30000';
const USER = env.FOUNDRY_USER, PASS = env.FOUNDRY_PASSWORD;

const SPECS = [
  { name:'Harrim', ancestry:'Dwarf', heritage:'Death Warden Dwarf', background:'Acolyte', class:'Cleric',
    deity:'Groetus', key:'wis', free:['wis','con','str','dex'], priority:['wis','con','str'],
    feats:[['feats-srd','Dwarven Lore'],['feats-srd','Reach Spell']],
    gear:[['equipment-srd','Scale Mail',1,true],['equipment-srd','Warhammer',1,true],['equipment-srd','Steel Shield',1,false],['equipment-srd','Religious Symbol (Wooden)',1,false],['equipment-srd','Adventurer’s Pack',1,false]],
    cast:{tradition:'divine',ability:'wis',mode:'prepared',cantrips:['Divine Lance','Light','Stabilize','Shield'],spells1:['Heal','Bless']} },
  { name:'Jaethal', ancestry:'Elf', heritage:'Ancient Elf', background:'Acolyte', class:'Champion',
    deity:'Urgathoa', key:'str', free:['str','con','dex','cha'], priority:['str','con','dex'],
    feats:[['feats-srd','Deity’s Domain'],['feats-srd','Ancestral Longevity']],
    gear:[['equipment-srd','Breastplate',1,true],['equipment-srd','Scythe',1,true],['equipment-srd','Religious Symbol (Wooden)',1,false],['equipment-srd','Adventurer’s Pack',1,false]],
    cast:null, rules:[{key:'FastHealing',type:'void',value:0}] },
  { name:'Kalikke', ancestry:'Human', heritage:'Nephilim', background:'Hermit', class:'Sorcerer',
    deity:null, key:'cha', free:['cha','con','dex','wis'], priority:['cha','con','dex'],
    feats:[['feats-srd','Familiar']],
    gear:[['equipment-srd','Explorer’s Clothing',1,true],['equipment-srd','Dagger',1,true],['equipment-srd','Crossbow',1,true],['equipment-srd','Adventurer’s Pack',1,false]],
    cast:{tradition:'arcane',ability:'cha',mode:'spontaneous',cantrips:['Frostbite','Shield','Detect Magic','Telekinetic Projectile'],spells1:['Hydraulic Push','Gust of Wind']} },
  { name:'Kanerah', ancestry:'Human', heritage:'Nephilim', background:'Hermit', class:'Sorcerer',
    deity:null, key:'cha', free:['cha','con','dex','wis'], priority:['cha','con','dex'],
    feats:[['feats-srd','Familiar']],
    gear:[['equipment-srd','Explorer’s Clothing',1,true],['equipment-srd','Dagger',1,true],['equipment-srd','Crossbow',1,true],['equipment-srd','Adventurer’s Pack',1,false]],
    cast:{tradition:'arcane',ability:'cha',mode:'spontaneous',cantrips:['Ignition','Shield','Detect Magic','Telekinetic Projectile'],spells1:['Breathe Fire','Grease']} },
  { name:'Octavia', ancestry:'Human', heritage:'Aiuvarin', background:'Scholar', class:'Wizard',
    deity:null, key:'int', free:['int','dex','con','wis'], priority:['int','dex','con'],
    feats:[['feats-srd','Reach Spell']],
    gear:[['equipment-srd','Explorer’s Clothing',1,true],['equipment-srd','Staff',1,true],['equipment-srd','Crossbow',1,true],['equipment-srd','Spellbook',1,false],['equipment-srd','Adventurer’s Pack',1,false]],
    cast:{tradition:'arcane',ability:'int',mode:'prepared',cantrips:['Telekinetic Projectile','Shield','Detect Magic','Light'],spells1:['Force Barrage','Grease']} },
  { name:'Regongar', ancestry:'Human', heritage:'Dromaar', background:'Warrior', class:'Magus',
    deity:null, key:'str', free:['str','int','con','dex'], priority:['str','int','con'],
    feats:[['feats-srd','Natural Ambition']],
    gear:[['equipment-srd','Studded Leather Armor',1,true],['equipment-srd','Bastard Sword',1,true],['equipment-srd','Adventurer’s Pack',1,false]],
    cast:{tradition:'arcane',ability:'int',mode:'prepared',cantrips:['Shield','Telekinetic Projectile'],spells1:['Thunderstrike','Force Barrage']} },
];

const BIOS = {
  Harrim: '<p>A grumpy dwarf cleric of <strong>Groetus</strong>, God of the End Times. His youthful clumsiness broke his faith in Torag; he found clarity in Groetus and the inevitability of the End. Gloomy but well-meaning, he throws himself into danger to save others and loves a philosophical debate over haggis. <em>Excellent Magister.</em> (CN)</p>',
  Jaethal: '<p>An <strong>undead elf</strong> from Kyonin who harvested her own flesh in a dark ritual to <strong>Urgathoa</strong> to restore her vivacity after centuries of vicious political intrigue. Harmed by vitality, healed by void. Built as a champion who takes the cleric archetype at 2nd level. <em>Excellent Emissary.</em> (NE)</p>',
  Kalikke: '<p>The gentle, soft-spoken half of cursed tiefling twins who share a single body. She bargained with the balor "Forefather" to resurrect her sister Kanerah, who bound their souls so they can never exist at once. A water-elemental sorcerer (a kineticist in concept). <em>Excellent Counselor.</em> (CG)</p>',
  Kanerah: '<p>The quick-tempered, impetuous half of the tiefling twins — killed and resurrected into a shared body with Kalikke. A fire-elemental sorcerer (a kineticist in concept), her violence stops just shy of cruelty. <em>Serves as Treasurer.</em> (LE)</p>',
  Octavia: '<p>A half-elf wizard, former captive of the villainous Technic League alongside her partner Regongar. She hides an inner darkness behind cheerful optimism and an obsession with beauty, and will gladly break laws for her friends. <em>Excellent Magister.</em> (CG)</p>',
  Regongar: '<p>A half-orc magus raised in captivity by the Technic League, where he learned that power is the only measure of worth and laws are fantasies for the weak. He trusts no one but Octavia. A handsome, inexorable warrior. <em>Excellent General.</em> (CE)</p>',
};
SPECS.forEach(s => { s.bio = BIOS[s.name]; });

const BUILD = async (SPECS) => {
  const R = foundry.utils.randomID;
  // folder
  let companions = game.folders.get('1TVpRFrvRMJkfIVD');
  if (!companions || companions.type !== 'Actor') {
    let km = game.folders.find(f => f.type==='Actor' && f.name==='Kingmaker' && !f.folder) || await Folder.create({name:'Kingmaker',type:'Actor'});
    companions = game.folders.find(f => f.type==='Actor' && f.name==='Companions' && f.folder?.id===km.id) || await Folder.create({name:'Companions',type:'Actor',folder:km.id});
  }
  const packIdx = {};
  async function find(pack, name) {
    const key = 'pf2e.'+pack; const p = game.packs.get(key); if(!p) return null;
    if(!packIdx[key]) packIdx[key] = await p.getIndex();
    let e = packIdx[key].find(x => x.name.toLowerCase()===name.toLowerCase())
         || packIdx[key].find(x => x.name.toLowerCase().replace(/[’']/g,"'")===name.toLowerCase().replace(/[’']/g,"'"))
         || packIdx[key].find(x => x.name.toLowerCase().startsWith(name.toLowerCase()));
    if(!e) return null;
    const o = (await p.getDocument(e._id)).toObject(); return o;
  }
  function fillBoosts(item, priority) {
    const used = new Set(); let pi = 0;
    const next = () => { while(pi<priority.length && used.has(priority[pi])) pi++; const v = priority[pi]||'con'; used.add(v); return v; };
    const b = item.system?.boosts; if(!b) return;
    for(const k of Object.keys(b)) {
      const entry = b[k];
      const vals = entry.value || [];
      if(vals.length === 1) { used.add(vals[0]); continue; }      // fixed
      if(vals.length === 0) continue;                              // empty slot
      entry.selected = next();                                    // free choice
    }
  }

  const results = [];
  for (const s of SPECS) {
    try {
      // delete existing same-name in folder (idempotent)
      for (const a of game.actors.filter(a => a.folder?.id===companions.id && a.name===s.name)) await a.delete();

      const unresolved = [];
      const items = [];
      const reqd = async (pack,name,kind) => { const o = await find(pack,name); if(!o){unresolved.push(kind+':'+name);return null;} return o; };

      const anc = await reqd('ancestries', s.ancestry, 'ancestry');
      const her = await reqd('heritages', s.heritage, 'heritage');
      const bg  = await reqd('backgrounds', s.background, 'background');
      const cls = await reqd('classes', s.class, 'class');
      if(anc) { fillBoosts(anc, s.priority); items.push(anc); }
      if(her) items.push(her);
      if(bg)  { fillBoosts(bg, s.priority); items.push(bg); }
      if(cls) items.push(cls);
      if(s.deity){ const d = await reqd('deities', s.deity, 'deity'); if(d) items.push(d); }
      for(const [pack,fname] of (s.feats||[])){ const f = await find(pack,fname); if(f) items.push(f); else unresolved.push('feat:'+fname); }
      for(const [pack,gname,qty,equip] of (s.gear||[])){
        const g = await find(pack,gname); if(!g){unresolved.push('gear:'+gname);continue;}
        if(qty) g.system.quantity = qty;
        if(equip && g.system.equipped) g.system.equipped.carryType = 'worn';
        items.push(g);
      }
      // spellcasting
      let castInfo = null;
      if(s.cast){
        const entryId = R();
        const cantrips = []; const spells1 = [];
        for(const nm of s.cast.cantrips){ const sp = await find('spells-srd', nm); if(!sp){unresolved.push('cantrip:'+nm);continue;} sp._id=R(); sp.system.location={value:entryId}; cantrips.push(sp); }
        for(const nm of s.cast.spells1){ const sp = await find('spells-srd', nm); if(!sp){unresolved.push('spell:'+nm);continue;} sp._id=R(); sp.system.location={value:entryId}; spells1.push(sp); }
        const slots = {
          slot0: { max: cantrips.length, prepared: s.cast.mode==='prepared'? cantrips.map(c=>({id:c._id})) : [] },
          slot1: { max: s.cast.mode==='prepared'? spells1.length : 3, prepared: s.cast.mode==='prepared'? spells1.map(c=>({id:c._id})) : [] },
        };
        const entry = { _id: entryId, name: s.cast.mode==='spontaneous'?'Spontaneous Spells':'Prepared Spells', type:'spellcastingEntry',
          system:{ ability:{value:s.cast.ability}, prepared:{value:s.cast.mode, flexible:false}, proficiency:{value:1},
            tradition:{value:s.cast.tradition}, slots, showSlotlessLevels:{value:false}, autoHeightenLevel:{value:null} } };
        items.push(entry, ...cantrips, ...spells1);
        castInfo = { entry: entry.name, cantrips: cantrips.length, spells1: spells1.length };
      }

      const actorData = {
        name: s.name, type:'character', folder: companions.id,
        system:{ details:{ level:{value:1}, keyability:{value:s.key}, biography:{ backstory: s.bio||'' } },
                 build:{ attributes:{ boosts:{ '1': s.free } } } },
        items,
      };
      if(s.rules){ /* attach void-healing note via flags */ actorData.flags = { world:{ note:'undead / void healing' } }; }
      const actor = await Actor.create(actorData, { keepId:false });
      const ab = actor.system.abilities;
      results.push({ name:s.name, type:actor.type, level:actor.system.details.level.value,
        abilities: ab?{str:ab.str.mod,dex:ab.dex.mod,con:ab.con.mod,int:ab.int.mod,wis:ab.wis.mod,cha:ab.cha.mod}:null,
        ac: actor.system.attributes?.ac?.value, hp: actor.system.attributes?.hp?.max,
        classDC: actor.system.attributes?.classDC?.value,
        items: actor.items.size, cast: castInfo, unresolved });
    } catch(e) { results.push({ name:s.name, error: e.message }); }
  }
  return { folder: companions.name, results };
};

(async () => {
  const browser = await chromium.launch({ executablePath:'/usr/bin/google-chrome', headless:true, args:['--no-sandbox'] });
  const page = await browser.newPage(); page.setDefaultTimeout(60000);
  try {
    await page.goto(URL, { waitUntil:'domcontentloaded' });
    await page.waitForSelector('select[name="userid"], #join-game', { timeout:30000 });
    await page.selectOption('select[name="userid"]', { label: USER });
    await page.fill('input[name="password"]', PASS);
    await Promise.all([ page.waitForNavigation({waitUntil:'domcontentloaded',timeout:60000}).catch(()=>{}), page.click('button[name="join"], button[type="submit"]') ]);
    await page.waitForFunction(() => window.game && game.ready===true, { timeout:90000 });
    const result = await page.evaluate(BUILD, SPECS);
    console.log('RESULT=' + JSON.stringify(result, null, 2));
  } catch(e){ console.log('ERROR:', e.message); }
  finally { await browser.close(); }
})();
