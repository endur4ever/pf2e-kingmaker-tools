const { chromium } = require('playwright');
const fs = require('fs');
const env = Object.fromEntries(
  fs.readFileSync('/home/grego/code/pf2e-kingmaker-tools/.foundry-creds.env', 'utf8')
    .split('\n').filter(Boolean).map(l => { const i = l.indexOf('='); return [l.slice(0, i), l.slice(i + 1)]; })
);
const URL = env.FOUNDRY_URL || 'http://localhost:30000';
const USER = env.FOUNDRY_USER, PASS = env.FOUNDRY_PASSWORD;

const EXTRACT = async () => {
  const FOLDER = '1TVpRFrvRMJkfIVD';
  const num = v => (typeof v === 'number' ? v : null);
  const out = [];
  for (const actor of game.actors.filter(a => a.folder?.id === FOLDER && a.type === 'character')) {
    const sys = actor.system;
    const ab = {};
    for (const k of ['str','dex','con','int','wis','cha']) ab[k] = num(sys.abilities?.[k]?.mod);
    const find = t => actor.items.find(i => i.type === t)?.name || null;
    const feats = actor.items.filter(i => i.type === 'feat');
    const featList = feats.filter(f => f.system?.category !== 'classfeature').map(f => ({ name: f.name, cat: f.system?.category || '' }));
    const classFeatures = feats.filter(f => f.system?.category === 'classfeature').map(f => f.name);
    const skills = {};
    for (const [slug, st] of Object.entries(actor.skills || {})) {
      if (st && typeof st.mod === 'number') skills[st.label || slug] = { mod: st.mod, rank: st.rank ?? null };
    }
    const strikes = (sys.actions || []).filter(a => a.type === 'strike').map(a => {
      let dmg = '';
      try { const d = a.item?.system?.damage; if (d) dmg = `${d.dice || 1}${d.die || ''}${d.modifier ? '+' + d.modifier : ''} ${d.damageType || ''}`.trim(); } catch {}
      let traits = [];
      try { traits = (a.weaponTraits || a.traits || []).map(t => t.label || t.name || t.value || t).filter(Boolean).slice(0, 8); } catch {}
      return { name: a.label, bonus: num(a.totalModifier), dmg, traits };
    });
    const inv = actor.items.filter(i => ['weapon','armor','shield','equipment','consumable','backpack','treasure','ammo'].includes(i.type))
      .map(i => ({ name: i.name, qty: i.system?.quantity ?? 1, type: i.type }));
    const casting = [];
    for (const e of actor.items.filter(i => i.type === 'spellcastingEntry')) {
      let dc = null, atk = null;
      try { dc = e.statistic?.dc?.value ?? null; atk = e.statistic?.check?.mod ?? null; } catch {}
      const byRank = {};
      for (const sp of actor.items.filter(i => i.type === 'spell' && i.system?.location?.value === e.id)) {
        const isCantrip = sp.system?.traits?.value?.includes('cantrip');
        const r = isCantrip ? 'cantrips' : ('rank' + (sp.system?.level?.value ?? 1));
        (byRank[r] = byRank[r] || []).push(sp.name);
      }
      casting.push({ name: e.name, tradition: e.system?.tradition?.value || '', mode: e.system?.prepared?.value || '', ability: e.system?.ability?.value || '', dc, atk, byRank });
    }
    out.push({
      name: actor.name, level: num(sys.details?.level?.value),
      ancestry: find('ancestry'), heritage: find('heritage'), background: find('background'), cls: find('class'), deity: find('deity'),
      keyability: sys.details?.keyability?.value || null,
      rarity: sys.traits?.rarity || null, size: sys.traits?.size?.value || null,
      languages: sys.details?.languages?.value || [],
      ac: num(sys.attributes?.ac?.value), hp: num(sys.attributes?.hp?.max), speed: num(sys.attributes?.speed?.value),
      perception: num(actor.perception?.mod ?? sys.perception?.mod),
      saves: { fort: num(actor.saves?.fortitude?.mod), ref: num(actor.saves?.reflex?.mod), will: num(actor.saves?.will?.mod) },
      classDC: num(sys.attributes?.classDC?.value),
      abilities: ab, skills, featList, classFeatures, strikes, inv, casting,
      bio: (sys.details?.biography?.backstory || sys.details?.biography?.appearance || '').toString(),
    });
  }
  return out;
};

(async () => {
  const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage(); page.setDefaultTimeout(60000);
  try {
    await page.goto(URL, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('select[name="userid"], #join-game', { timeout: 30000 });
    await page.selectOption('select[name="userid"]', { label: USER });
    await page.fill('input[name="password"]', PASS);
    await Promise.all([ page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }).catch(() => {}), page.click('button[name="join"], button[type="submit"]') ]);
    await page.waitForFunction(() => window.game && game.ready === true, { timeout: 90000 });
    const data = await page.evaluate(EXTRACT);
    fs.writeFileSync('/tmp/companions_full.json', JSON.stringify(data, null, 2));
    console.log('extracted', data.length, 'actors ->', data.map(d => `${d.name}(L${d.level})`).join(', '));
  } catch (e) { console.log('ERROR:', e.message); }
  finally { await browser.close(); }
})();
