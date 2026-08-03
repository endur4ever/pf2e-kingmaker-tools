package at.posselt.pfrpg2e.data.actor

/**
 * PF2e condition slugs that carry a VALUE (Player Core, "Gaining and Losing Conditions"): these can
 * be increased/decreased (clumsy 1 -> clumsy 2). Every other condition — unconscious, prone,
 * blinded, fatigued, ... — is binary: it is either on or off and must be toggled, never "increased".
 *
 * Applying a binary condition through the PF2e increase-condition API is a misuse (unconscious has
 * no value); callers must branch on [isValuedCondition] and toggle binary conditions instead.
 */
val VALUED_CONDITION_SLUGS: Set<String> = setOf(
    "clumsy",
    "doomed",
    "drained",
    "dying",
    "enfeebled",
    "frightened",
    "sickened",
    "slowed",
    "stunned",
    "stupefied",
    "wounded",
)

/** Whether [slug] is a valued PF2e condition (increase/decrease) vs a binary one (toggle). */
fun isValuedCondition(slug: String): Boolean = slug in VALUED_CONDITION_SLUGS
