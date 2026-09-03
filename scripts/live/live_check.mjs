// Headless render check against the LIVE Foundry server, as the spare GM.
//
//   GM_PW='…' node scripts/live/live_check.mjs            # defaults: http://localhost:30000, user Gamemaster2
//   FOUNDRY_URL=… GM_USER=… GM_PW=… node scripts/live/live_check.mjs
//
// What it proves, without mutating campaign data:
//   * the world loads and our module is active (migrations run on first GM login -- they would anyway)
//   * the module's Kingdom sheet renders every main tab with NO raw i18n keys and NO unresolved {placeholders}
//   * kingdom chat cards bind: a whispered-to-self card is posted, its Dismiss clicked, the handler's
//     greying observed, and the message deleted
// Exit 0 pass, 1 assertion failed, 2 spare GM not free (never kicks anyone), 3 world never became ready.
import { chromium } from '/home/grego/hermes-workspace/node_modules/playwright/index.mjs';

const URL = process.env.FOUNDRY_URL || 'http://localhost:30000';
const USER = process.env.GM_USER || 'Gamemaster2';
const PW = process.env.GM_PW || '';
const errors = [];
const browser = await chromium.launch({ headless: true, executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox', '--window-size=1600,1000'] });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.on('console', m => { if (m.type() === 'error' && !/screen resolution/.test(m.text())) errors.push(m.text().slice(0, 300)); });
page.on('pageerror', e => errors.push('PAGEERROR ' + String(e).slice(0, 300)));

await page.goto(URL + '/join', { waitUntil: 'networkidle', timeout: 90000 });
const users = await page.$$eval('select[name="userid"] option', os => os.map(o => ({ v: o.value, t: o.textContent.trim(), disabled: o.disabled })));
const spare = users.find(u => u.t.toLowerCase() === USER.toLowerCase() && !u.disabled);
if (!spare) { console.log(`${USER} is not free -- aborting rather than kicking anyone`); await browser.close(); process.exit(2); }
await page.selectOption('select[name="userid"]', spare.v);
if (PW) await page.fill('input[name="password"]', PW);
await page.click('button[name="join"]');
try {
  await page.waitForFunction(() => window.game?.ready === true, undefined, { timeout: 240000, polling: 1000 });
} catch {
  console.log('world never became ready; url=', page.url()); errors.forEach(e => console.log('  -', e));
  await browser.close(); process.exit(3);
}
await new Promise(r => setTimeout(r, 4000));

const report = await page.evaluate(async () => {
  const out = { failures: [] };
  const mod = game.modules.get('pf2e-kingmaker-tools');
  out.module = { active: mod?.active, version: mod?.version };
  const actor = game.actors.find(a => a.getFlag('pf2e-kingmaker-tools', 'kingdom-sheet'));
  if (!actor) { out.failures.push('no kingdom actor in world'); return out; }

  // 1. every main tab renders clean
  await game.pf2eKingmakerTools.macros.openSheet('kingdom', actor.id);
  let root = null;
  for (let i = 0; i < 30 && !root; i++) { await new Promise(r => setTimeout(r, 500)); root = document.querySelector('[id^="kmKingdomSheet-"]'); }
  if (!root) { out.failures.push('kingdom sheet did not render'); return out; }
  const links = Array.from(root.querySelectorAll('.km-tabs a[data-action="change-nav"]')).map(a => a.dataset.link);
  out.tabs = {};
  for (const link of links) {
    const a = document.querySelector(`[id^="kmKingdomSheet-"] .km-tabs a[data-link="${link}"]`);
    if (!a) continue;
    a.click(); await new Promise(r => setTimeout(r, 1800));
    const el = document.querySelector('[id^="kmKingdomSheet-"]');
    const text = el?.textContent || '';
    const raw = (text.match(/pf2e-kingmaker-tools\.[a-zA-Z.]+/g) || []);
    const icu = (text.match(/\{[a-zA-Z]+\}/g) || []);
    out.tabs[link] = { rawKeys: raw.slice(0, 3), unresolved: icu.slice(0, 3) };
    if (raw.length) out.failures.push(`tab ${link}: raw i18n key ${raw[0]}`);
    if (icu.length) out.failures.push(`tab ${link}: unresolved placeholder ${icu[0]}`);
  }

  // 2. chat cards bind (no campaign mutation: whisper to self, click Dismiss, delete)
  const tpl = foundry.applications.handlebars.renderTemplate;
  const cases = [
    ['petition-answer', { actorUuid: actor.uuid, petitionId: 'probe', optionId: 'option-a', roleLabel: 'Ruler', petitionerName: 'Probe', premise: 'probe', optionLabel: 'probe', effects: [], needsFaction: false, factions: [] }, '.km-petition-dismiss', '.km-chat-card'],
    ['settlement-life-digest', { actorUuid: actor.uuid, turn: 1, rows: [{ settlementId: 'probe', settlementName: 'Probe', recordId: 'probe', gazetteLine: 'probe', hookKind: 'rp-delta', hookLabel: '+1 RP' }] }, '.km-offer-life-event[data-hook-kind="dismiss"]', '.km-life-row'],
  ];
  out.cards = {};
  for (const [name, ctx, sel, rowSel] of cases) {
    let msg = null;
    try {
      const content = await tpl(`modules/pf2e-kingmaker-tools/dist/chatmessages/${name}.hbs`, ctx);
      msg = await ChatMessage.create({ content, whisper: [game.userId] });
      await new Promise(r => setTimeout(r, 1500));
      const btn = document.querySelector(`li.chat-message[data-message-id="${msg.id}"] ${sel}`);
      if (!btn) { out.cards[name] = 'button not rendered'; out.failures.push(`${name}: button not rendered`); continue; }
      btn.click(); await new Promise(r => setTimeout(r, 1500));
      const ok = !!btn.closest(rowSel)?.classList.contains('km-card-resolved');
      out.cards[name] = ok ? 'handler ran' : 'handler did NOT run';
      if (!ok) out.failures.push(`${name}: handler did not run (actor unresolved?)`);
    } catch (e) { out.cards[name] = 'THREW ' + String(e).slice(0, 160); out.failures.push(`${name}: threw`); }
    finally { if (msg) await msg.delete().catch(() => {}); }
  }
  return out;
});
console.log(JSON.stringify(report, null, 1));
const moduleErrors = errors.filter(e => /pf2e-kingmaker-tools/.test(e));
console.log('console errors (all):', errors.length, ' from our module:', moduleErrors.length);
moduleErrors.forEach(e => console.log('  -', e));
await browser.close();
process.exit(report.failures.length || moduleErrors.length ? 1 : 0);
