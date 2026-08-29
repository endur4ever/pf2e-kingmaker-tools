package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawInfluenceEncounter
import at.posselt.pfrpg2e.kingdom.data.RawResearchProject
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemStore
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Read/write funnel for the world-scoped subsystem store. The setting holds ONE JSON blob;
 * reads parse defensively ("{}", blank, or corrupt JSON all read as the empty store, because a
 * broken blob must cost the GM a tracker refresh, not the trackers), and every write goes
 * through [updateSubsystemStore] so there is a single read-modify-write path.
 */
fun Game.getSubsystemStore(): RawSubsystemStore =
    runCatching {
        val raw = settings.pfrpg2eKingdomCampingWeather.getSubsystemStore()
        if (raw.isBlank()) null else JSON.parse<RawSubsystemStore>(raw)
    }.getOrNull() ?: RawSubsystemStore(influenceEncounters = emptyArray(), researchProjects = emptyArray())

/** The store's encounters, never null. */
fun RawSubsystemStore.encounters(): Array<RawInfluenceEncounter> = influenceEncounters ?: emptyArray()

/** The store's projects, never null. */
fun RawSubsystemStore.projects(): Array<RawResearchProject> = researchProjects ?: emptyArray()

/** Serializes same-client writes: two quick clicks must not read the same snapshot and clobber. */
private val subsystemStoreMutex = Mutex()

/**
 * THE write funnel: read once, transform, serialize once. GM-gated -- players interact with the
 * trackers only through revealed context rows and GM-confirmed offers.
 */
suspend fun Game.updateSubsystemStore(block: (RawSubsystemStore) -> RawSubsystemStore) {
    if (!user.isGM) return
    subsystemStoreMutex.withLock {
        val raw = settings.pfrpg2eKingdomCampingWeather.getSubsystemStore()
        val current = if (raw.isBlank() || raw == "{}") {
            RawSubsystemStore(influenceEncounters = emptyArray(), researchProjects = emptyArray())
        } else {
            runCatching { JSON.parse<RawSubsystemStore>(raw) }.getOrNull()
        }
        if (current == null) {
            // a corrupt blob still READS as the empty store so the trackers keep working, but
            // writing that emptiness back would destroy whatever the blob still holds
            ui.notifications.error(t("subsystems.corruptStore"))
            return
        }
        val next = block(current)
        settings.pfrpg2eKingdomCampingWeather.setSubsystemStore(JSON.stringify(next))
    }
}
