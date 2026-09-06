package at.posselt.pfrpg2e.utils

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.takeIfInstance
import com.foundryvtt.core.Game
import com.foundryvtt.core.documents.Actor
import com.foundryvtt.core.documents.ChatMessage
import com.foundryvtt.core.documents.GetSpeakerOptions
import js.objects.recordOf
import js.objects.unsafeJso
import kotlinx.browser.document
import kotlinx.coroutines.await
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

suspend fun postDegreeOfSuccess(
    degreeOfSuccess: DegreeOfSuccess,
    originalDegreeOfSuccess: DegreeOfSuccess? = null,
    message: String? = null,
    rollMode: RollMode? = null,
    metaHtml: String = "",
    preHtml: String = "",
    postHtml: String = "",
    title: String = "",
) {
    val original = if (originalDegreeOfSuccess != null && originalDegreeOfSuccess != degreeOfSuccess) {
        t(originalDegreeOfSuccess)
    } else {
        null
    }
    postChatTemplate(
        "chatmessages/degree-of-success.hbs",
        recordOf(
            "isCriticalFailure" to (DegreeOfSuccess.CRITICAL_FAILURE == degreeOfSuccess),
            "isFailure" to (DegreeOfSuccess.FAILURE == degreeOfSuccess),
            "isSuccess" to (DegreeOfSuccess.SUCCESS == degreeOfSuccess),
            "isCriticalSuccess" to (DegreeOfSuccess.CRITICAL_SUCCESS == degreeOfSuccess),
            "degree" to t(degreeOfSuccess),
            "meta" to metaHtml,
            "message" to message,
            "original" to original,
            "postHtml" to postHtml,
            "preHtml" to preHtml,
            "title" to title,
        ),
        rollMode = rollMode,
    )
}


suspend fun postChatTemplate(
    templatePath: String,
    templateContext: Any? = unsafeJso(),
    rollMode: RollMode? = null,
    speaker: Actor? = null,
    whisper: Array<String>? = null,
) {
    val message = tpl(templatePath, templateContext)
    postChatMessage(message, rollMode, speaker = speaker, isHtml = true, whisper = whisper)
}

suspend fun postChatMessage(
    message: String,
    rollMode: RollMode? = null,
    speaker: Actor? = null,
    isHtml: Boolean = false,
    whisper: Array<String>? = null,
) {
    val value = if (isHtml) message else escapeHtml(message)
    val fixedMessage = if (rollMode == RollMode.BLINDROLL) {
        "<div hidden class=\"km-hide-from-user\"></div>$value"
    } else {
        value
    }
    val data = recordOf<String, Any?>(
        "content" to fixedMessage,
    )
    if (speaker != null) {
        data["speaker"] = ChatMessage.getSpeaker(GetSpeakerOptions(actor = speaker))
    }
    if (whisper != null && whisper.isNotEmpty()) {
        data["whisper"] = whisper
    }
    rollMode?.let { ChatMessage.applyMode(data, it.toMessageMode()) }
    ChatMessage.create(data).await()
}

fun fixVisibility(game: Game, html: HTMLElement, message: ChatMessage) {
    if (!game.user.isGM
        && message.blind
        && html.querySelector(".km-hide-from-user") != null
    ) {
        html.hidden = true
    }
}

/**
 * Marks a one-shot chat button spent, synchronously, before its handler runs.
 *
 * Camping handlers apply their effect and then await one or more document round-trips, and no
 * camping card disables its own buttons -- so a second click inside that window applies the effect
 * twice. Kingdom cards carry an applied-flag; camping had none anywhere. Claiming the button in the
 * DOM before the handler starts closes the window for the case that actually happens: one person
 * clicking again because a card looks unresponsive.
 *
 * Per-CLIENT, and honestly so: two people clicking two copies of the same whispered card remain
 * last-write-wins, the same residual the rumor store documents for camping data generally.
 */
private fun claimOnce(button: HTMLElement): Boolean {
    if (button.hasAttribute("data-km-consumed")) return false
    button.setAttribute("data-km-consumed", "1")
    button.setAttribute("disabled", "disabled")
    // Deliberately NOT marking the whole .chat-message resolved. `.km-card-resolved` is
    // `opacity: 0.55` on whatever carries it, and a digest card routinely holds several
    // INDEPENDENT one-shot offers -- a pressure digest, a rival digest, an XP digest, a faction
    // move row. Fading the card because one of them was answered dimmed offers the GM had not
    // answered yet and made a live card look spent. Settling a group is the handler's job, and
    // the mark*Done helpers already do it at the right scope; this claim's only job is to stop
    // the same button applying twice.
    return true
}

/**
 * Undo a [claimOnce] so a click that never got to DO anything does not leave a dead button.
 *
 * The claim has to happen BEFORE the handler runs -- that is the entire point, the window it
 * closes is the one between the click and the server acknowledging the write. But two failure
 * paths reach the handler and then refuse: the card's actor attribute failing to resolve, and the
 * handler throwing. Burning the button on either would turn a transient failure into the worst
 * bug class this module has -- a card the GM can see and can never action.
 *
 * Only the button is restored. A claim never touches the card's styling, so there is nothing
 * else to undo -- settling a group is the handler's job.
 */
fun releaseChatClickClaim(button: HTMLElement) {
    button.removeAttribute("data-km-consumed")
    button.removeAttribute("disabled")
}

fun bindChatClick(
    targetSelector: String,
    parentSelector: String = ".chat-message",
    /** True for a button whose effect must apply at most once: it is disabled before the handler runs. */
    once: Boolean = false,
    callback: (event: Event, target: HTMLElement, parent: HTMLElement) -> Unit
) {
    listOf("chat-notifications", "chat")
        .mapNotNull { document.getElementById(it) }
        .forEach { elem ->
            // bindChatButtons re-runs on EVERY renderChatLog hook, but these container elements
            // persist across renders — so an unguarded addEventListener accumulated one listener
            // per button per render (20 buttons x 2 containers each time). A single click then ran
            // its handler N times: N-1 spurious "already applied" warnings, N world-time advances
            // on pass-time, and so on. Mark the container per selector and bind once. A container
            // that Foundry re-creates loses the marker and is bound again, which is correct.
            val marker = "data-km-bound-" + targetSelector.filter { it.isLetterOrDigit() || it == '-' }
            if (elem.hasAttribute(marker)) return@forEach
            elem.setAttribute(marker, "1")
            elem.addEventListener("click", { event ->
                // Resolve descendant clicks (e.g. a click on the <i> icon INSIDE a button) up to
                // the matching button; closest() returns the element itself when it already matches,
                // so this is safe for text-only buttons too.
                val clicked = (event.target as? HTMLElement)?.closest(targetSelector)?.takeIfInstance<HTMLElement>()
                if (clicked != null && (!once || claimOnce(clicked))) {
                    clicked.closest(parentSelector)
                        ?.takeIfInstance<HTMLElement>()
                        ?.let { callback(event, clicked, it) }
                }
            })
        }
}