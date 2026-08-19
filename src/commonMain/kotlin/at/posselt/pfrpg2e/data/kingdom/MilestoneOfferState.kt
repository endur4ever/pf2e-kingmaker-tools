package at.posselt.pfrpg2e.data.kingdom

/**
 * Whether an auto-detected milestone offer has already been ANSWERED and must not be posted again.
 *
 * The two auto-detected milestones are level-triggered on standing world state — a road stays
 * built, a claimed region stays claimed — so End Turn re-detects them every single turn. Something
 * has to remember that the GM was already asked, and "awarded" is not enough on its own: a GM who
 * declines the house rule never sets `completed`, so suppressing on that alone re-posts the
 * identical card every turn for the rest of the campaign with no way to stop it.
 *
 * [offerDismissed] is nullable because kingdoms saved before the field existed have no value for
 * it; absent means the GM has not refused, which is the safe direction (they get asked once).
 *
 * See card t_2ce9f49f.
 */
fun milestoneOfferAnswered(completed: Boolean, offerDismissed: Boolean?): Boolean =
    completed || offerDismissed == true
