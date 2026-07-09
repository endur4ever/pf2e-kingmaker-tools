package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.RawCouncilCooldowns
import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.data.kingdom.leaders.LeaderActors
import at.posselt.pfrpg2e.data.kingdom.leaders.Vacancies

data class CouncilMissionsStatus(
    val canAudit: Boolean,
    val canScrying: Boolean,
    val canLockdown: Boolean,
    val canFeast: Boolean,
    val auditCooldownTurns: Int,
    val scryingCooldownTurns: Int,
    val lockdownCooldownTurns: Int,
    val feastCooldownTurns: Int,
    val auditAffordable: Boolean,
    val scryingAffordable: Boolean,
    val lockdownAffordable: Boolean,
    val feastAffordable: Boolean,
)

fun deriveCouncilMissionsStatus(
    kingdom: KingdomData,
    leaderActors: LeaderActors,
    vacancies: Vacancies,
): CouncilMissionsStatus {
    val cooldowns = kingdom.councilCooldowns ?: RawCouncilCooldowns(0, 0, 0, 0)

    val auditAffordable = true
    val scryingAffordable = (kingdom.resourcePoints?.now ?: 0) >= 4
    val lockdownAffordable = (kingdom.resourcePoints?.now ?: 0) >= 2
    val feastAffordable = (kingdom.commodities?.now?.food ?: 0) >= 3

    val canAudit = kingdom.settings?.enableCouncilMissions == true &&
            !vacancies.resolveVacancy(Leader.TREASURER) &&
            leaderActors.resolve(Leader.TREASURER) != null &&
            cooldowns.audit <= 0

    val canScrying = kingdom.settings?.enableCouncilMissions == true &&
            !vacancies.resolveVacancy(Leader.MAGISTER) &&
            leaderActors.resolve(Leader.MAGISTER) != null &&
            scryingAffordable &&
            cooldowns.scrying <= 0

    val canLockdown = kingdom.settings?.enableCouncilMissions == true &&
            !vacancies.resolveVacancy(Leader.WARDEN) &&
            leaderActors.resolve(Leader.WARDEN) != null &&
            lockdownAffordable &&
            cooldowns.lockdown <= 0

    val canFeast = kingdom.settings?.enableCouncilMissions == true &&
            !vacancies.resolveVacancy(Leader.COUNSELOR) &&
            leaderActors.resolve(Leader.COUNSELOR) != null &&
            feastAffordable &&
            cooldowns.feast <= 0

    return CouncilMissionsStatus(
        canAudit = canAudit,
        canScrying = canScrying,
        canLockdown = canLockdown,
        canFeast = canFeast,
        auditCooldownTurns = cooldowns.audit,
        scryingCooldownTurns = cooldowns.scrying,
        lockdownCooldownTurns = cooldowns.lockdown,
        feastCooldownTurns = cooldowns.feast,
        auditAffordable = auditAffordable,
        scryingAffordable = scryingAffordable,
        lockdownAffordable = lockdownAffordable,
        feastAffordable = feastAffordable,
    )
}