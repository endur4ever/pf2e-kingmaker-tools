# Plan: Petition Inbox — NPC audiences addressed to each leadership role

Card: `t_4875e9eb`. Child: `t_72ea7cdb` (Personal Holdings).

## 1. Problem statement

Between sessions the kingdom sheet is a dashboard: numbers change, nobody has a reason to look. The
eight leadership roles are assigned to specific PCs, and nothing ever addresses a player *as* the
Magister or the Treasurer.

A petition is a named resident asking one office for a decision, with a deadline and two or three
answers that actually cost something. It gives each player a personal reason to open the sheet, and
it turns the population roster from set dressing into a cast.

## 2. Data model

```kotlin
// jsMain: kingdom/data/RawPetition.kt
@JsPlainObject
external interface RawPetition {
    var id: String
    /** RawNpcEntry.id of the petitioner, plus their name captured at creation so a deleted
     *  roster entry still renders a readable petition. */
    var petitionerId: String
    var petitionerName: String
    var settlementId: String?
    /** Leader.value -- ruler | counselor | emissary | general | magister | treasurer | viceroy | warden */
    var targetRole: String
    /** Catalog id; its text is an i18n key, never stored prose. */
    var templateId: String
    var createdTurn: Int
    var dueTurn: Int
    /** open | answered | expired */
    var status: String
    /** Which option was taken; null while open or expired unanswered. */
    var chosenOptionId: String?
    var resolvedTurn: Int?
}
```

Options are **not** stored on the petition. They live in the template catalog, keyed by
`templateId`, so a wording or balance fix reaches petitions already sitting in inboxes and the
persisted record stays small. The petition stores only `chosenOptionId`.

An earlier draft declared `data class RawPetition(...)` with `targetRole: Leader`,
`status: PetitionStatus`, and nested `Array<PetitionOption>` / `Array<PetitionConsequence>` holding
a `value: String` that meant `"-1"` or `"quest_id_xyz"` by context. None of that survives a Foundry
flag: persisted types here are `@JsPlainObject external interface`s of primitives, which is why
`RawWarThreat.status` and `RawCompanionExpedition.status` are `String`. Enums live in `commonMain`
and convert at the boundary with `fromCamelCase`; an unrecognised `targetRole` or `status` **drops
that petition rather than throwing**, so one bad row cannot empty every inbox.

**Storage and migration.** `var petitions: Array<RawPetition>?` on `KingdomData`.
An earlier draft said "no immediate migration required… since this is a new feature", then
contradicted itself a line later. The convention here is unambiguous — every nullable array on
`KingdomData` is seeded: `quests` (Migration23), `companionExpeditions` (40), `caravans` (44),
`warThreats` (46), `shipmentHistory` (61). This takes **`Migration64`**; 62 is claimed by the
downtime-projects plan and 63 by the scheduled-pressure plan, and all three must land in
`Migrations.kt` and `MigrationChainTest`'s range.

**Cap.** `PETITION_HISTORY_CAP = 100` answered-or-expired petitions, oldest trimmed first, in the
shape of `appendShipmentHistory`. **Open petitions are never pruned** — trimming an unanswered
petition silently robs a player of a decision.

## 3. Template catalog

Data-driven JSON under `data/petitions/`, one file per template, validated by a new
`schemas/petition.json` in the `./gradlew check` sweep alongside `validateMilestones`. Text is
i18n keys only; no prose in the data files.

```json
{
  "id": "tax-revolt",
  "targetRole": "treasurer",
  "weight": 10,
  "requiresStructure": null,
  "requiresEvent": null,
  "options": [
    { "id": "concede",  "consequences": [{ "kind": "rp",       "amount": -2 }] },
    { "id": "enforce",  "consequences": [{ "kind": "unrest",   "amount":  1 }] },
    { "id": "negotiate","consequences": [{ "kind": "standing", "amount": -1, "ref": "{faction}" }] }
  ]
}
```

### Starter catalog — five per role

Option letters map to the three columns; each option's consequence is given in the closed vocabulary
of §4. These are starting values, balanced to be small: a petition should nudge, not swing a turn.

**Ruler**

| id | premise | A | B | C |
| --- | --- | --- | --- | --- |
| `succession-question` | A noble asks whom the realm answers to if the ruler falls | unrest −1 | standing +1 | quest |
| `pardon-plea` | A family begs clemency for a condemned poacher | unrest −1 | unrest +1 | rumor |
| `border-oath` | A frontier village asks to swear directly to the crown | rp −1, standing +1 | unrest +1 | — |
| `royal-wedding` | A minor house proposes a marriage alliance | standing +2 | standing −1 | quest |
| `festival-petition` | The capital asks for a feast day at the treasury's expense | rp −2, unrest −1 | unrest +1 | — |

**Counselor**

| id | premise | A | B | C |
| --- | --- | --- | --- | --- |
| `temple-dispute` | Two faiths claim the same shrine plot | unrest −1, rp −1 | unrest +1 | quest |
| `festival-rites` | Elders want an old rite restored | unrest −1 | rumor | — |
| `censorship-appeal` | A playwright's satire has offended the court | unrest +1 | standing −1 | rumor |
| `orphanage-appeal` | A matron asks for support for war orphans | rp −2, unrest −1 | unrest +1 | — |
| `foreign-creed` | Immigrants ask to build their own shrine | unrest −1, standing +1 | unrest +1 | — |

**Emissary**

| id | premise | A | B | C |
| --- | --- | --- | --- | --- |
| `envoy-insult` | A neighbour's envoy claims an insult at court | standing +1, rp −1 | standing −1 | quest |
| `trade-concession` | A partner asks for favourable terms | rp −2, standing +2 | standing −1 | — |
| `hostage-exchange` | A rival offers a prisoner swap | standing +1 | unrest +1 | quest |
| `refugee-column` | Refugees from a rival's war ask entry | unrest +1, standing −1 | standing +1 | — |
| `spy-accusation` | A resident is accused of spying for a rival | standing −1 | unrest +1 | quest |

**General**

| id | premise | A | B | C |
| --- | --- | --- | --- | --- |
| `militia-request` | A border village asks to raise a militia | rp −2 | unrest +1 | quest |
| `veteran-pension` | Veterans ask for support after a campaign | rp −2, unrest −1 | unrest +1 | — |
| `deserter-trial` | A deserter's family pleads for mercy | unrest −1 | unrest +1 | — |
| `road-patrol` | Merchants ask for escorted roads | rp −1 | rumor | quest |
| `monster-bounty` | A hunter asks a bounty for a beast in the hills | rp −1 | unrest +1 | quest |

**Magister**

| id | premise | A | B | C |
| --- | --- | --- | --- | --- |
| `arcane-dispute` | Two mages claim the same discovery | unrest −1 | standing −1 | quest |
| `cursed-heirloom` | A family asks the crown to cleanse an heirloom | rp −1 | unrest +1 | quest |
| `apprentice-licence` | A hedge-wizard asks to teach openly | unrest −1 | unrest +1 | rumor |
| `ley-survey` | A scholar asks funding to survey the hexes | rp −2 | rumor | — |
| `banned-tome` | A librarian asks whether to burn a dangerous book | unrest +1 | rumor | quest |

**Treasurer**

| id | premise | A | B | C |
| --- | --- | --- | --- | --- |
| `tax-revolt` | A ward refuses this season's assessment | rp −2 | unrest +1 | standing −1 |
| `guild-charter` | A guild asks for a monopoly charter | rp +2, unrest +1 | unrest −1 | — |
| `debt-forgiveness` | Farmers ask relief after a bad harvest | rp −2, unrest −1 | unrest +1 | — |
| `coin-debasement` | A mintmaster proposes stretching the coin | rp +2, unrest +1 | unrest −1 | rumor |
| `customs-exemption` | A partner's traders ask exemption | rp −1, standing +1 | standing −1 | — |

**Viceroy**

| id | premise | A | B | C |
| --- | --- | --- | --- | --- |
| `land-claim` | Two settlers claim the same lot | unrest −1 | unrest +1 | — |
| `drainage-works` | A hamlet asks for flood works | rp −2, unrest −1 | unrest +1 | quest |
| `resettlement` | Displaced farmers ask for new land | rp −1, unrest −1 | unrest +1 | — |
| `road-toll` | A village asks to levy its own toll | rp +1, unrest +1 | unrest −1 | — |
| `absentee-lord` | A landholder has abandoned their charge | rp +1 | unrest +1 | quest |

**Warden**

| id | premise | A | B | C |
| --- | --- | --- | --- | --- |
| `poaching-ring` | Foresters report organised poaching | unrest +1 | rp −1 | quest |
| `wolf-season` | Herders ask for a cull | rp −1 | unrest +1 | quest |
| `logging-dispute` | A camp is felling beyond its grant | rp +1, unrest +1 | unrest −1 | — |
| `sacred-grove` | Druids ask that a grove be left unclaimed | unrest −1, standing +1 | rp +1, unrest +1 | quest |
| `stray-beast` | Something large was seen near a worksite | rumor | rp −1 | quest |

**Generation cadence and volume.** At End Turn, for each role whose leader slot is filled, roll
against a per-role chance and pick a template by `weight`, filtered by `requiresStructure` /
`requiresEvent`. Hard caps: **at most 2 new petitions per turn across all roles**, and **at most 1
open petition per role at a time**. Without both, eight roles generate eight petitions a turn and the
inbox becomes the chore it was meant to replace. `dueTurn = createdTurn + 3`.

Petitioners are cast from the settlement's `RawPopulationRoster` (`RawNpcEntry { id, name,
occupation, notes? }`), preferring an occupation the template names; when the roster is empty, the
petition is **not** generated rather than inventing a name.

## 4. Consequence vocabulary

A closed set, each mapped to the applier that already owns it:

| kind | Applied by | Notes |
| --- | --- | --- |
| `unrest` | `kingdom.unrest += amount`, the same expression the offer buttons use (`ChatButtons.kt:303`) | clamped at 0 |
| `rp` | `kingdom.resourcePoints.now += amount` | clamped at 0 |
| `standing` | the faction's `RawGroup.standing`, matched by name | `ref` names the group; a missing group tells the GM rather than silently doing nothing |
| `quest` | the existing quest generator | spawns one linked quest |
| `rumor` | the existing rumor generator | spawns one rumor |

Nothing else. A consequence kind not in this list is a new feature, not a new template.

## 5. Who answers, and how

`getOwnedLeaderRoles(game, kingdom)` (`Leaders.kt:15`) already returns the set of `Leader` roles
whose assigned actor `isOwner` for the current user. The inbox filters on exactly that — the gating
work this builds on is already in place and needs no new ownership logic.

**Player picks, GM confirms** — the module's standing philosophy:

1. The role's player clicks an option in their inbox. This writes **nothing** to the kingdom.
2. A GM-whispered offer card names the role, the petition, and the chosen option.
3. The GM confirms; only then are §4's consequences applied and `status` set to `answered` with
   `chosenOptionId` and `resolvedTurn`.

The player-facing click therefore needs no GM guard, because it applies nothing; the **confirm**
handler begins `if (!game.user.isGM) return`. Players are OWNERs of the party actor, so a template
conditional is layout, not authorization.

**Overdue.** At End Turn, an `open` petition whose `dueTurn <= currentTurn` becomes `expired` and
posts a GM offer for its overdue consequence — `unrest +1` by default, per template override. Expiry
is an **offer**, not an automatic penalty: a table that spent the session elsewhere should not be
fined without the GM saying so. `resolvedTurn` records when.

## 6. UI

An **Inbox** section on the existing `MainNavEntry.PARTY` tab — it is addressed to players, and PARTY
is where per-PC material already lives. No new nav entry.

| Piece | Path |
| --- | --- |
| Template | `applications/kingdom/sections/party/petitions.hbs` |
| Context | `kingdom/sheet/contexts/PetitionContext.kt` |
| Offer card | `chatmessages/petition-answer.hbs` |
| Styles | `.km-petition*` in `applications/kingdom/kingdom-sheet.css` |
| i18n | `pf2e-kingmaker-tools.kingdom.petitions.*` |

**Unread badge — specified, per the card.** "Seen" is per *user*, not per world, so it cannot live on
the kingdom. It is a flag on the **User** document via `setAppFlag`/`getAppFlag`
(`utils/Document.kt:84`/`:91`), keyed `seenPetitions`, holding an `Array<String>` of petition ids.
The badge count is `petitions.filter { it.targetRole in ownedRoles && it.status == "open" }` minus
those ids. Opening the section marks its visible petitions seen. A user with no owned roles gets no
badge. Two GMs see independent badges, which is correct — they are different people.

**Two repo constraints.** `petitions.hbs` is a registered partial with no parent frame: inside
`{{#each}}` use `@root`, never `../`. And every label — role names, option labels, status, premise —
must be a **literal** i18n key; `t("petitions.$templateId.premise")` is invisible to
`check_i18n_keys.py` and ships as a raw key with every guard green. Because the catalog is
data-driven, this needs a guard: extend `check_i18n_keys.py` with a check that every id in
`data/petitions/` has `premise` and per-option `label` keys in **all eight** locales, the same shape
as the setup-wizard check added for exactly this failure.

**Tone.** Premises are two sentences, in-world, addressed to the office rather than the player
("Your Magistracy…"). Option labels are verbs from the office's point of view. No outcome is
telegraphed in the label: the consequence table is the GM's, not a menu of known prices.

## 7. Interactions and out of scope

**Reads:** `Leaders.kt` ownership, `RawPopulationRoster`, `kingdom.groups` (standing), settlements
(structure gating), `TurnTickingEngine` End Turn.
**Writes:** `kingdom.petitions`, the User `seenPetitions` flag, and on confirm the §4 appliers.

**Out of scope:** petitions from factions rather than residents (that is the faction-agenda plan);
LLM-generated text; player-authored petitions; petitions that chain into each other; per-PC rewards
(that is the XP ledger and the renown card); auto-confirming any consequence.

## 8. Test plan

**commonTest** (`PetitionsTest`)
- A petition due this turn expires; one due next turn does not — both sides of the boundary.
- An expired petition is not expired twice, and does not re-offer.
- The per-role cap: a role with an open petition generates none; the per-turn cap of 2 holds when
  all eight roles are eligible.
- An empty population roster generates nothing rather than an unnamed petitioner.
- The history cap trims answered/expired only and **never** an open petition, even past the cap.
- An unknown `targetRole` or `status` drops that petition and leaves the rest.
- Unread count: only open petitions in owned roles, minus seen ids; zero for a user owning no roles.

**jsTest** — Raw↔model round trip preserving `chosenOptionId = null`; catalog JSON parsed against the
schema; `PetitionContext` filtered by `getOwnedLeaderRoles`; the confirm handler refusing a non-GM.

**Guards** — `./gradlew check` validates `data/petitions/*.json`; the new `check_i18n_keys.py`
catalog check covers all eight locales.

**Mutation-check every new test**: make the due comparison `<`, drop the per-role cap, prune open
petitions, count seen ids as unread — and confirm the mutation *compiled* before believing a
"survived" result.

**Manual Foundry checklist**
1. End Turn with all eight roles filled → at most 2 new petitions, at most 1 per role.
2. Log in as the Magister's player → only Magister petitions; badge shows the unread count.
3. Open the section → badge clears for that user; a second player's badge is unaffected.
4. Answer as the player → **nothing changes in the kingdom**; a GM offer card appears.
5. GM confirms → unrest/RP/standing move by the table's amount, status becomes answered.
6. Let one lapse past its due turn → it expires and offers an overdue consequence; declining applies
   nothing and it does not re-offer next turn.
7. Empty the population roster → no petitions generate.
8. Log in as a player owning no role → no badge, no petitions.

## 9. Phasing

**Phase 1 — pure core.** `Petitions.kt` in `commonMain`: enums, expiry, caps, unread count, cap
trimming. Full commonTest suite. Nothing wired.

**Phase 2 — catalog and data.** `schemas/petition.json`, the 40 starter templates, `RawPetition`,
`kingdom.petitions`, `Migration64`, and the `check_i18n_keys.py` catalog check.

**Phase 3 — generation and expiry.** End Turn generation with both caps and petitioner casting;
expiry offers.

**Phase 4 — inbox and answering.** The PARTY section, `PetitionContext`, the User seen-flag badge,
the answer offer card and confirm handler, i18n across all eight locales.

Phases 1 and 2 ship nothing user-visible, which is what makes 3 and 4 safe.
