package at.posselt.pfrpg2e.kingdom.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import at.posselt.pfrpg2e.kingdom.*
import at.posselt.pfrpg2e.kingdom.data.*
import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.data.kingdom.leaders.LeaderActors
import at.posselt.pfrpg2e.data.kingdom.leaders.LeaderActor
import at.posselt.pfrpg2e.data.kingdom.leaders.LeaderType
import at.posselt.pfrpg2e.data.kingdom.leaders.Vacancies
import at.posselt.pfrpg2e.data.actor.SkillRanks

class CouncilMissionsContextTest {

    private fun createTestKingdom(
        enableCouncilMissions: Boolean = true,
        auditCooldown: Int = 0,
        scryingCooldown: Int = 0,
        lockdownCooldown: Int = 0,
        feastCooldown: Int = 0,
        rp: Int = 10,
        food: Int = 5,
    ): KingdomData {
        val kingdom = js("{}").unsafeCast<KingdomData>()
        kingdom.settings = js("{}").unsafeCast<KingdomSettings>().apply {
            this.enableCouncilMissions = enableCouncilMissions
        }
        kingdom.councilCooldowns = RawCouncilCooldowns(auditCooldown, scryingCooldown, lockdownCooldown, feastCooldown)
        kingdom.resourcePoints = js("{}").unsafeCast<RawResources>().apply {
            now = rp
        }
        kingdom.commodities = js("{}").unsafeCast<RawCurrentCommodities>().apply {
            now = js("{}").unsafeCast<RawCommodities>().apply { this.food = food }
        }
        return kingdom
    }

    private fun mockLeader(name: String): LeaderActor = LeaderActor(
        level = 5,
        type = LeaderType.PC,
        ranks = SkillRanks(),
        invested = true,
        uuid = "actor-$name",
        img = null,
        name = name,
    )

    @Test
    fun testAllMissionsReadyAndAffordable() {
        val kingdom = createTestKingdom(enableCouncilMissions = true, rp = 10, food = 5)
        val leaderActors = LeaderActors(
            treasurer = mockLeader("Treasurer"),
            magister = mockLeader("Magister"),
            warden = mockLeader("Warden"),
            counselor = mockLeader("Counselor")
        )
        val vacancies = Vacancies()

        val status = deriveCouncilMissionsStatus(kingdom, leaderActors, vacancies)

        assertTrue(status.canAudit)
        assertTrue(status.canScrying)
        assertTrue(status.canLockdown)
        assertTrue(status.canFeast)

        assertTrue(status.auditAffordable)
        assertTrue(status.scryingAffordable)
        assertTrue(status.lockdownAffordable)
        assertTrue(status.feastAffordable)

        assertEquals(0, status.auditCooldownTurns)
        assertEquals(0, status.scryingCooldownTurns)
        assertEquals(0, status.lockdownCooldownTurns)
        assertEquals(0, status.feastCooldownTurns)
    }

    @Test
    fun testMissionsBlockedByCooldowns() {
        val kingdom = createTestKingdom(
            enableCouncilMissions = true,
            auditCooldown = 2,
            scryingCooldown = 1,
            lockdownCooldown = 3,
            feastCooldown = 4,
            rp = 10,
            food = 5
        )
        val leaderActors = LeaderActors(
            treasurer = mockLeader("Treasurer"),
            magister = mockLeader("Magister"),
            warden = mockLeader("Warden"),
            counselor = mockLeader("Counselor")
        )
        val vacancies = Vacancies()

        val status = deriveCouncilMissionsStatus(kingdom, leaderActors, vacancies)

        assertFalse(status.canAudit)
        assertFalse(status.canScrying)
        assertFalse(status.canLockdown)
        assertFalse(status.canFeast)

        assertEquals(2, status.auditCooldownTurns)
        assertEquals(1, status.scryingCooldownTurns)
        assertEquals(3, status.lockdownCooldownTurns)
        assertEquals(4, status.feastCooldownTurns)
    }

    @Test
    fun testMissionsBlockedByAffordability() {
        val kingdom = createTestKingdom(enableCouncilMissions = true, rp = 1, food = 2)
        val leaderActors = LeaderActors(
            treasurer = mockLeader("Treasurer"),
            magister = mockLeader("Magister"),
            warden = mockLeader("Warden"),
            counselor = mockLeader("Counselor")
        )
        val vacancies = Vacancies()

        val status = deriveCouncilMissionsStatus(kingdom, leaderActors, vacancies)

        assertTrue(status.canAudit) // Free
        assertFalse(status.canScrying) // Cost: 4 RP
        assertFalse(status.canLockdown) // Cost: 2 RP
        assertFalse(status.canFeast) // Cost: 3 Food

        assertTrue(status.auditAffordable)
        assertFalse(status.scryingAffordable)
        assertFalse(status.lockdownAffordable)
        assertFalse(status.feastAffordable)
    }

    @Test
    fun testMissionsBlockedByVacancies() {
        val kingdom = createTestKingdom(enableCouncilMissions = true, rp = 10, food = 5)
        // Empty leaders
        val leaderActors = LeaderActors()
        val vacancies = Vacancies()

        val status = deriveCouncilMissionsStatus(kingdom, leaderActors, vacancies)

        assertFalse(status.canAudit)
        assertFalse(status.canScrying)
        assertFalse(status.canLockdown)
        assertFalse(status.canFeast)
    }

    @Test
    fun testMissionsBlockedByVacancyDespitePresentLeaders() {
        // The seats are marked VACANT even though a leader actor is present, so canX must be false
        // via the !resolveVacancy term (NOT the null-leader term — testMissionsBlockedByVacancies
        // above only exercises the null path). Guards against dropping the vacancy check.
        val kingdom = createTestKingdom(enableCouncilMissions = true, rp = 10, food = 5)
        val leaderActors = LeaderActors(
            treasurer = mockLeader("Treasurer"),
            magister = mockLeader("Magister"),
            warden = mockLeader("Warden"),
            counselor = mockLeader("Counselor")
        )
        val vacancies = Vacancies(treasurer = true, magister = true, warden = true, counselor = true)

        val status = deriveCouncilMissionsStatus(kingdom, leaderActors, vacancies)

        assertFalse(status.canAudit)
        assertFalse(status.canScrying)
        assertFalse(status.canLockdown)
        assertFalse(status.canFeast)
        // Affordability is independent of vacancy — the resources are still there.
        assertTrue(status.scryingAffordable)
        assertTrue(status.lockdownAffordable)
        assertTrue(status.feastAffordable)
    }

    @Test
    fun testAffordabilityBoundaries() {
        val present = LeaderActors(
            treasurer = mockLeader("T"), magister = mockLeader("M"),
            warden = mockLeader("W"), counselor = mockLeader("C")
        )
        val vac = Vacancies()
        // Exactly enough: scrying needs 4 RP, lockdown 2 RP, feast 3 food (>= boundaries).
        val exact = deriveCouncilMissionsStatus(createTestKingdom(rp = 4, food = 3), present, vac)
        assertTrue(exact.scryingAffordable)   // 4 >= 4
        assertTrue(exact.lockdownAffordable)  // 4 >= 2
        assertTrue(exact.feastAffordable)     // 3 >= 3
        // One below the scrying/feast thresholds; lockdown still met at 3 RP.
        val short = deriveCouncilMissionsStatus(createTestKingdom(rp = 3, food = 2), present, vac)
        assertFalse(short.scryingAffordable)  // 3 < 4
        assertTrue(short.lockdownAffordable)  // 3 >= 2
        assertFalse(short.feastAffordable)    // 2 < 3
        // Lockdown boundary: exactly 2 RP passes, 1 RP fails.
        assertTrue(deriveCouncilMissionsStatus(createTestKingdom(rp = 2, food = 0), present, vac).lockdownAffordable)
        assertFalse(deriveCouncilMissionsStatus(createTestKingdom(rp = 1, food = 0), present, vac).lockdownAffordable)
    }

    @Test
    fun testMasterToggleDisablesAllMissions() {
        // enableCouncilMissions = false must block every mission even with leaders present + affordable.
        val present = LeaderActors(
            treasurer = mockLeader("T"), magister = mockLeader("M"),
            warden = mockLeader("W"), counselor = mockLeader("C")
        )
        val status = deriveCouncilMissionsStatus(
            createTestKingdom(enableCouncilMissions = false, rp = 10, food = 5), present, Vacancies()
        )
        assertFalse(status.canAudit)
        assertFalse(status.canScrying)
        assertFalse(status.canLockdown)
        assertFalse(status.canFeast)
    }
}
