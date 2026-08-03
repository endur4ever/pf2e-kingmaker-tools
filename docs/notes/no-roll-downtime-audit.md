# Audit: No-Roll Camping Activities Downtime Budget Leak

This audit notes every camping activity that resolves without a skill roll and traces how downtime hours are charged. It identifies why no-roll activities currently bypass the downtime budget and recommends a concrete fix.

---

## 1. No-Roll Camping Activities
No-roll activities are defined in `data/camping-activities/` with an empty `"skills": []` array (meaning `requiresACheck()` returns `false`). The following 13 activities do not require a skill roll:

1. **Healer's Blessing** (`healers-blessing`)
2. **Set Traps** (`set-traps`, Nok-Nok companion activity)
3. **Intimidating Posture** (`intimidating-posture`, Regongar companion activity)
4. **Enhance Campfire** (`enhance-campfire`, Kanerah companion activity)
5. **Water Hazards** (`water-hazards`, Kalikke companion activity)
6. **Blend Into The Night** (`blend-into-the-night`, Harrim companion activity)
7. **Set Alarms** (`set-alarms`, Octavia companion activity)
8. **Bolster Confidence** (`bolster-confidence`, Linzi companion activity)
9. **Wilderness Survival** (`wilderness-survival`, Ekundayo companion activity)
10. **Enhance Weapons** (`enhance-weapons`, Amiri companion activity)
11. **Undead Guardians** (`undead-guardians`, Jaethal companion activity)
12. **Maintain Armor** (`maintain-armor`, Valerie companion activity)
13. **Cook Meal** (`cook-meal`) — *Note: Cook Meal requires no check on the activity tile itself; instead, recipe checks are rolled via `rollRecipeCheck` on the recipe card.*

---

## 2. Root Cause Analysis
Downtime hours spent during a camping session are tracked in `downtimeHoursSpent: Record<String, Int>?` (on `CampingData.kt`) and displayed via `downtimeHoursRemaining(actorUuid: String): Int`.

Currently, downtime hours are only incremented inside the `rollCheck` function in [CampingSheet.kt:L638](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/camping/CampingSheet.kt#L638):
```kotlin
        checkActor.campingActivityCheck(
            data = campingCheckData,
            overrideDc = recipe?.cookingLoreDC,
        )?.let { result ->
            camping.campingActivities[activityId]?.result = result.toCamelCase()
            if (!activity.isPrepareCampsite()) {
                camping.spendDowntimeHours(actorUuid, CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY)
                console.log("KM_SPEND_0602 spent 2h, remaining", camping.downtimeHoursRemaining(actorUuid), "for", actorUuid)
            }
            actor.setCamping(camping)
```

Because `camping-tile.hbs` renders the roll button conditionally using `{{#if requiresCheck}}`, no roll button is ever rendered for no-roll activities. As a result:
1. `rollCheck` is never called for these activities.
2. `camping.spendDowntimeHours` is never invoked.
3. Actors assigned to no-roll activities instantly receive their benefits (synced via `syncCampingEffects` during `onPreUpdateActor`) but their remaining downtime hours remain unchanged (at 8h), allowing them to exceed their downtime budget.

---

## 3. Concrete Fix Recommendation

To fix this leak, we should charge 2h of downtime when a no-roll activity is assigned to a character.

### A. Modify `CampingSheet.assignActivityTo`
We recommend adding a check in `assignActivityTo` in [CampingSheet.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/camping/CampingSheet.kt) to charge downtime if the assigned activity does not require a check:

```diff
                     is CampingActivityScheduler.SchedulingResult.Allowed -> {
                         val skill = activityActor
                             .findCampingActivitySkills(activity, camping.ignoreSkillRequirements)
                             .filterNot { it.validateOnly }
                             .firstOrNull()
                         val existing = camping.campingActivities[activityId]
                         if (existing == null) {
                             camping.campingActivities[activity.id] = CampingActivity(
                                 actorUuid = actorUuid,
                                 selectedSkill = skill?.attribute?.value,
                             )
                         } else {
                             // Assigning a character is a fresh attempt: drop the previous roll
                             // result so downtime hours are only consumed once it is re-rolled.
                             existing.actorUuid = actorUuid
                             existing.selectedSkill = skill?.attribute?.value
                             existing.result = null
                         }
+                        if (!activity.requiresACheck()) {
+                            camping.spendDowntimeHours(actorUuid, CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY)
+                        }
                         actor.setCamping(camping)
                     }
```

This ensures that assigning a no-roll activity immediately decrements the actor's downtime budget by 2h, consistent with how rolled activities behave when resolved.

### B. Add `downtimeHoursSpent` to `CampingUpdateBuilder.kt`
We should also update `CampingUpdateBuilder` to support `downtimeHoursSpent` updates in case partial updates are used:
```kotlin
val downtimeHoursSpent = PropertyUpdateBuilder<Record<String, Int>?>(basePath, updates, "downtimeHoursSpent")
```
