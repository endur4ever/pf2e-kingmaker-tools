const { chromium } = require('playwright');
const fs = require('fs');
const env = Object.fromEntries(
  fs.readFileSync('/home/grego/code/pf2e-kingmaker-tools/.foundry-creds.env', 'utf8')
    .split('\n').filter(Boolean).map(l => { const i = l.indexOf('='); return [l.slice(0, i), l.slice(i + 1)]; })
);
const URL = env.FOUNDRY_URL || 'http://localhost:30000';
const USER = env.FOUNDRY_USER, PASS = env.FOUNDRY_PASSWORD;

const SETART = async () => {
  const FOLDER = '1TVpRFrvRMJkfIVD';
  const PB = 'modules/pf2e-kingmaker/assets/actor-portraits/';
  const TB = 'modules/pf2e-kingmaker/assets/actor-tokens/';
  const ART = {
    'Amiri':['kingmaker/amiri','kingmaker/amiri'], 'Ekundayo':['kingmaker/ekundayo','kingmaker/ekundayo'],
    'Jubilost':['kingmaker/jubilost','kingmaker/jubilost'], 'Linzi':['kingmaker/linzi','kingmaker/linzi'],
    'Nok-Nok':['kingmaker/nok-nok','kingmaker/nok-nok'], 'Tristian':['kingmaker/tristian','kingmaker/tristian'],
    'Valerie':['kingmaker/valerie','kingmaker/valerie'], 'Harrim':['npc/harrim','npc/harrim'],
    'Jaethal':['kingmaker/jaethal','npc/jaethal'], 'Kalikke':['npc/kalikke','npc/kalikke'],
    'Kanerah':['npc/kanerah','npc/kanerah'], 'Octavia':['npc/octavia','npc/octavia'],
    'Regongar':['npc/regongar','npc/regongar'],
  };
  const isPlaceholder = s => !s || /default-icons|mystery-man|svg$/i.test(s);
  const report = [];
  for (const actor of game.actors.filter(a => a.folder?.id === FOLDER && a.type === 'character')) {
    const m = ART[actor.name]; if (!m) { report.push({name:actor.name, skip:'no-art-mapping'}); continue; }
    const port = PB + m[0] + '.webp', tok = TB + m[1] + '.webp';
    const upd = {};
    if (isPlaceholder(actor.img)) upd.img = port;
    const curTok = actor.prototypeToken?.texture?.src;
    if (isPlaceholder(curTok)) upd['prototypeToken.texture.src'] = tok;
    if (Object.keys(upd).length) { await actor.update(upd); report.push({name:actor.name, setImg: !!upd.img, setTok: !!upd['prototypeToken.texture.src']}); }
    else report.push({name:actor.name, skip:'already set', img:actor.img, tok:curTok});
  }
  return report;
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
    const r = await page.evaluate(SETART);
    console.log('RESULT=' + JSON.stringify(r, null, 2));
  } catch (e) { console.log('ERROR:', e.message); }
  finally { await browser.close(); }
})();
